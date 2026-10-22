package dev.requestportal.request.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Events waiting to be published: should stay near zero; growth means Kafka or the relay is stuck. */
@Component
class OutboxMetrics implements MeterBinder {

    private final JdbcClient jdbc;

    OutboxMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.pending", () -> jdbc.sql("select count(*) from outbox_event where published_at is null")
                        .query(Long.class)
                        .single())
                .description("Workflow events not yet published to Kafka")
                .register(registry);
    }
}
