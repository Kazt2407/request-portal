package dev.requestportal.request.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param topic        Kafka topic for workflow events
 * @param partitions   partitions of the topic when this service creates it
 * @param replicas     replication factor of the topic when this service creates it
 * @param batchSize    events published per relay run
 * @param sendTimeout  how long to wait for Kafka to acknowledge one event
 * @param retention    how long published events are kept for troubleshooting
 */
@ConfigurationProperties("portal.outbox")
public record OutboxProperties(
        String topic, int partitions, short replicas, int batchSize, Duration sendTimeout, Duration retention) {
}
