package dev.requestportal.notification.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.sender")
public record SenderProperties(int batchSize, int maxAttempts) {
}
