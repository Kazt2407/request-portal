package dev.requestportal.notification.org;

import static java.util.stream.Collectors.toMap;

import dev.requestportal.security.ServiceAccountTokenInterceptor;
import dev.requestportal.web.UpstreamServiceException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads departments from org-service as the notification-service service account (there is no signed-in
 * user in a background job). Caches them for five minutes; an unknown code triggers one early reload.
 */
@Component
class OrgServiceDepartments implements DepartmentDirectory {

    static final String CLIENT_REGISTRATION_ID = "org-service";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(10);

    private record Snapshot(Map<String, Department> byCode, Instant loadedAt) {
    }

    private final RestClient client;
    private final Clock clock;
    private volatile Snapshot snapshot;

    OrgServiceDepartments(@Value("${portal.org-service.url}") String baseUrl,
            ClientRegistrationRepository clientRegistrations, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new ServiceAccountTokenInterceptor(clientRegistrations, CLIENT_REGISTRATION_ID))
                .build();
        this.clock = clock;
    }

    @Override
    public Optional<Department> find(String code) {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || current.loadedAt().plus(MAX_AGE).isBefore(now)) {
            current = reload();
        }
        if (!current.byCode().containsKey(code) && current.loadedAt().plus(MIN_REFRESH_INTERVAL).isBefore(now)) {
            current = reload();
        }
        return Optional.ofNullable(current.byCode().get(code));
    }

    private synchronized Snapshot reload() {
        List<Department> departments;
        try {
            departments = client.get()
                    .uri("/api/v1/departments")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Department>>() {});
        } catch (RestClientException | IllegalStateException exception) {
            throw new UpstreamServiceException("GET /api/v1/departments on org-service failed",
                    "Department data is unavailable right now", exception);
        }
        Snapshot loaded = new Snapshot(
                departments.stream().collect(toMap(Department::code, Function.identity())), clock.instant());
        snapshot = loaded;
        return loaded;
    }
}
