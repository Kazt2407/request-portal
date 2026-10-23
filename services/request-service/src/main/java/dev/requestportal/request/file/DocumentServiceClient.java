package dev.requestportal.request.file;

import static java.util.stream.Collectors.toMap;

import dev.requestportal.security.BearerTokenRelay;
import dev.requestportal.web.UpstreamServiceException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link FileDirectory} over document-service's REST API, called with the user's token. */
@Component
class DocumentServiceClient implements FileDirectory {

    private final RestClient client;

    DocumentServiceClient(@Value("${portal.document-service.url}") String baseUrl) {
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
    public Map<UUID, FileCheck> check(Collection<UUID> fileIds) {
        try {
            List<FileCheck> checks = client.post()
                    .uri("/api/v1/files/checks")
                    .body(Map.of("fileIds", List.copyOf(fileIds)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return checks.stream().collect(toMap(FileCheck::id, Function.identity()));
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("POST /api/v1/files/checks on document-service failed",
                    "Files cannot be checked right now; try again in a moment", exception);
        }
    }
}
