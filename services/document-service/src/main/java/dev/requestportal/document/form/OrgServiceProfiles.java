package dev.requestportal.document.form;

import dev.requestportal.security.BearerTokenRelay;
import dev.requestportal.web.UpstreamServiceException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class OrgServiceProfiles implements ProfileDirectory {

    private final RestClient client;

    OrgServiceProfiles(@Value("${portal.org-service.url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenRelay())
                .build();
    }

    @Override
    public Optional<Profile> mine() {
        try {
            return Optional.ofNullable(client.get().uri("/api/v1/profiles/me").retrieve().body(Profile.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("GET /api/v1/profiles/me on org-service failed",
                    "Your profile cannot be loaded right now; try again in a moment", exception);
        }
    }
}
