package dev.requestportal.notification.event;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Remembers handled event ids; the insert is part of the handler's transaction. */
@Component
class ProcessedEvents {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEvents.class);

    private final JdbcClient jdbc;
    private final EventsProperties properties;
    private final Clock clock;

    ProcessedEvents(JdbcClient jdbc, EventsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** Returns false if the event was already handled, in which case the caller must do nothing. */
    boolean markProcessed(UUID eventId, String type) {
        return jdbc.sql("""
                        insert into processed_event (event_id, event_type, processed_at) values (:id, :type, :at)
                        on conflict (event_id) do nothing
                        """)
                .param("id", eventId)
                .param("type", type)
                .param("at", Timestamp.from(clock.instant()))
                .update() == 1;
    }

    @Scheduled(cron = "${portal.cleanup-cron}")
    void forgetOldEvents() {
        Instant cutoff = clock.instant().minus(properties.processedRetention());
        int deleted = jdbc.sql("delete from processed_event where processed_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
        log.info("Forgot {} handled events older than {}", deleted, cutoff);
    }
}
