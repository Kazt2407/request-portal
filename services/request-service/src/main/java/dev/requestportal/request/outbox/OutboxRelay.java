package dev.requestportal.request.outbox;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka in insertion order, at least once.
 *
 * <p>Only one instance publishes at a time: each run takes a transaction-scoped PostgreSQL advisory lock,
 * so running several replicas cannot reorder a request's events. A failed send stops the run, so later
 * events wait for earlier ones; consumers deduplicate by {@code eventId}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Arbitrary constant identifying the relay's advisory lock. */
    private static final long RELAY_LOCK_ID = 0x0B07_E17L;

    private record Pending(long seq, String eventId, String topic, String key, String type, String payload) {
    }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxRelay(
            JdbcClient jdbc,
            KafkaTemplate<String, String> kafka,
            PlatformTransactionManager transactionManager,
            OutboxProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${portal.outbox.poll-interval}")
    void publishPending() {
        int published;
        do {
            published = publishBatch();
        } while (published == properties.batchSize());
    }

    /** Publishes up to one batch; returns how many events were published. */
    public int publishBatch() {
        Integer published = transaction.execute(status -> {
            Boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(:lock)")
                    .param("lock", RELAY_LOCK_ID)
                    .query(Boolean.class)
                    .single();
            if (!locked) {
                return 0;
            }
            List<Pending> batch = jdbc.sql("""
                            select seq, event_id, topic, message_key, event_type, payload::text as payload
                            from outbox_event where published_at is null order by seq limit :limit
                            """)
                    .param("limit", properties.batchSize())
                    .query((row, number) -> new Pending(row.getLong("seq"), row.getString("event_id"),
                            row.getString("topic"), row.getString("message_key"), row.getString("event_type"),
                            row.getString("payload")))
                    .list();

            int sent = 0;
            for (Pending event : batch) {
                try {
                    send(event);
                    jdbc.sql("update outbox_event set published_at = :now, attempts = attempts + 1 where seq = :seq")
                            .param("now", Timestamp.from(clock.instant()))
                            .param("seq", event.seq())
                            .update();
                    sent++;
                } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                    recordFailure(event, exception);
                    break;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (sent > 0) {
                log.debug("Published {} outbox events", sent);
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    /** Deletes events published longer ago than the retention period. */
    @Scheduled(cron = "${portal.outbox.cleanup-cron}")
    void deleteOldEvents() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int deleted = jdbc.sql("delete from outbox_event where published_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, cutoff);
        }
    }

    private void send(Pending event) throws ExecutionException, TimeoutException, InterruptedException {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.key(), event.payload());
        record.headers().add("eventId", event.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.type().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void recordFailure(Pending event, Exception exception) {
        log.warn("Publishing outbox event {} ({}) failed; will retry", event.eventId(), event.type(), exception);
        jdbc.sql("update outbox_event set attempts = attempts + 1, last_error = :error where seq = :seq")
                .param("error", String.valueOf(exception.getMessage()))
                .param("seq", event.seq())
                .update();
    }
}
