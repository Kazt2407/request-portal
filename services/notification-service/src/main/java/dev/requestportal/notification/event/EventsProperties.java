package dev.requestportal.notification.event;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param processedRetention how long handled event ids are remembered (longer than Kafka's retention)
 */
@ConfigurationProperties("portal.events")
public record EventsProperties(
        String topic, String deadLetterTopic, int partitions, short replicas, Duration processedRetention) {
}
