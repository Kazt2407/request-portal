package dev.requestportal.request.outbox;

import dev.requestportal.request.workflow.Actor;
import dev.requestportal.request.workflow.ServiceRequest;
import dev.requestportal.request.workflow.WorkflowEvent;
import dev.requestportal.request.workflow.WorkflowEventSink;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the events a request recorded into outbox rows. Must be called inside the transaction that changed
 * the request, after it has been saved (so it has an id). The JSON format is documented in docs/events.md.
 */
@Component
public class OutboxWriter implements WorkflowEventSink {

    static final int ENVELOPE_VERSION = 1;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final OutboxProperties properties;
    private final Tracer tracer;

    OutboxWriter(JdbcClient jdbc, JsonMapper json, OutboxProperties properties, ObjectProvider<Tracer> tracer) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
    }

    @Override
    public void append(ServiceRequest request) {
        String traceParent = currentTraceParent();
        for (WorkflowEvent event : request.drainEvents()) {
            UUID eventId = UUID.randomUUID();
            jdbc.sql("""
                            insert into outbox_event
                                (event_id, topic, message_key, event_type, payload, created_at, trace_parent)
                            values (:eventId, :topic, :key, :type, cast(:payload as jsonb), :createdAt, :traceParent)
                            """)
                    .param("eventId", eventId)
                    .param("topic", properties.topic())
                    .param("key", request.getId().toString())
                    .param("type", event.type().name())
                    .param("payload", json.writeValueAsString(envelope(eventId, request, event)))
                    .param("createdAt", Timestamp.from(event.occurredAt()))
                    .param("traceParent", traceParent)
                    .update();
        }
    }

    /** W3C traceparent of the current span, or {@code null} when nothing is being traced. */
    private String currentTraceParent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        if (context.traceId().length() != 32 || context.spanId().length() != 16) {
            return null;
        }
        return "00-" + context.traceId() + "-" + context.spanId() + "-"
                + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    private static Map<String, Object> envelope(UUID eventId, ServiceRequest request, WorkflowEvent event) {
        Map<String, Object> requester = new LinkedHashMap<>();
        requester.put("id", request.getRequesterId().toString());
        requester.put("name", request.getRequesterName());
        requester.put("email", request.getRequesterEmail());

        Map<String, Object> about = new LinkedHashMap<>();
        about.put("id", request.getId());
        about.put("code", request.getCode());
        about.put("title", request.getTitle());
        about.put("departmentCode", request.getDepartmentCode());
        about.put("requester", requester);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("type", event.type().name());
        envelope.put("version", ENVELOPE_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("request", about);
        envelope.put("data", data(event));
        return envelope;
    }

    private static Map<String, Object> data(WorkflowEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        switch (event) {
            case WorkflowEvent.RequestSubmitted submitted -> {
                data.put("stepCount", submitted.stepCount());
                data.put("expectedDays", submitted.expectedDays());
            }
            case WorkflowEvent.StepStarted started -> {
                data.put("position", started.position());
                data.put("departmentCode", started.departmentCode());
                data.put("description", started.description());
                data.put("dueAt", started.dueAt().toString());
            }
            case WorkflowEvent.StepFinished finished -> {
                data.put("position", finished.position());
                data.put("departmentCode", finished.departmentCode());
                data.put("status", finished.status().name());
                data.put("finishedBy", actor(finished.finishedBy()));
                data.put("note", finished.note());
            }
            case WorkflowEvent.RequestCompleted completed ->
                    data.put("resultDepartmentCode", completed.resultDepartmentCode());
            case WorkflowEvent.RequestRejected rejected -> {
                data.put("position", rejected.position());
                data.put("departmentCode", rejected.departmentCode());
                data.put("reason", rejected.reason());
            }
            case WorkflowEvent.RequestCancelled cancelled -> {
                // The envelope says it all.
            }
        }
        return data;
    }

    private static Map<String, Object> actor(Actor actor) {
        if (actor == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", actor.id().toString());
        map.put("name", actor.name());
        return map;
    }
}
