package dev.requestportal.document.file;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** @param unattachedTtl how long an upload may stay unused before it is deleted */
@ConfigurationProperties("portal.files")
public record FilesProperties(DataSize maxSize, Duration unattachedTtl, Duration cleanupInterval) {
}
