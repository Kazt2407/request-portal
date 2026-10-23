package dev.requestportal.document.storage;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * @param endpoint        S3 endpoint this service calls; empty for AWS
 * @param publicEndpoint  endpoint written into pre-signed URLs, reachable by clients; empty for AWS
 * @param accessKey       static key for S3-compatible storage; empty to use the AWS credential chain
 * @param createBucket    create the bucket on startup if missing (local development)
 * @param corsAllowedOrigins web app origins allowed to upload and download directly; applied with createBucket
 * @param urlTtl          how long pre-signed upload and download URLs stay valid
 */
@ConfigurationProperties("portal.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        boolean createBucket,
        @DefaultValue List<String> corsAllowedOrigins,
        Duration urlTtl) {

    boolean hasStaticCredentials() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
}
