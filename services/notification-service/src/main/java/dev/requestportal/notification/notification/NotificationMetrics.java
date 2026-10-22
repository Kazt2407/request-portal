package dev.requestportal.notification.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Queue health: emails waiting to be sent and emails that gave up and need an admin. */
@Component
class NotificationMetrics implements MeterBinder {

    private final JdbcClient jdbc;

    NotificationMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (NotificationStatus status : new NotificationStatus[] {NotificationStatus.PENDING, NotificationStatus.FAILED}) {
            Gauge.builder("notifications.queued", () -> jdbc.sql("select count(*) from notification where status = ?")
                            .param(status.name())
                            .query(Long.class)
                            .single())
                    .tag("status", status.name().toLowerCase())
                    .description("Notifications by delivery status")
                    .register(registry);
        }
    }
}
