package dev.requestportal.document.bulk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("portal.bulk-downloads")
public record BulkDownloadProperties(
        int maxRequests, int maxFiles, DataSize maxSize, Duration ttl, Duration pollInterval, Duration staleAfter) {
}
