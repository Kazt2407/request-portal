package dev.requestportal.request.org;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import dev.requestportal.web.UpstreamServiceException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link OrgDirectory} over org-service's REST API, called with the user's token and short timeouts.
 * Departments are read fresh (only admins saving templates need them); terms are cached because every
 * submission with a term field checks them.
 */
@Component
class OrgServiceClient implements OrgDirectory {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_OBJECTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> STRINGS = new ParameterizedTypeReference<>() {};
    private static final Duration CACHE_MAX_AGE = Duration.ofMinutes(5);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(10);

    private final RestClient client;
    private final RefreshingSet termCodes;
    private final RefreshingSet schoolYears;

    OrgServiceClient(@Value("${portal.org-service.url}") String baseUrl, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenRelay())
                .build();
        this.termCodes = new RefreshingSet(
                () -> codesOf(get("/api/v1/terms", JSON_OBJECTS)), CACHE_MAX_AGE, MIN_REFRESH_INTERVAL, clock);
        this.schoolYears = new RefreshingSet(
                () -> Set.copyOf(get("/api/v1/terms/school-years", STRINGS)),
                CACHE_MAX_AGE, MIN_REFRESH_INTERVAL, clock);
    }

    @Override
    public Set<String> unknownDepartments(Collection<String> codes) {
        Set<String> known = codesOf(get("/api/v1/departments", JSON_OBJECTS));
        return codes.stream().filter(code -> !known.contains(code)).collect(toCollection(TreeSet::new));
    }

    @Override
    public boolean termExists(String termCode) {
        return termCodes.contains(termCode);
    }

    @Override
    public boolean schoolYearExists(String schoolYear) {
        return schoolYears.contains(schoolYear);
    }

    private <T> T get(String path, ParameterizedTypeReference<T> type) {
        try {
            return client.get().uri(path).retrieve().body(type);
        } catch (RestClientException exception) {
            throw new UpstreamServiceException(
                    "GET " + path + " on org-service failed",
                    "Organization data is unavailable right now; try again in a moment",
                    exception);
        }
    }

    private static Set<String> codesOf(List<Map<String, Object>> items) {
        return items.stream().map(item -> (String) item.get("code")).collect(toSet());
    }
}
