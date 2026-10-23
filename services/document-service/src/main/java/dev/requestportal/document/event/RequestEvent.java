package dev.requestportal.document.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A workflow event from request-service (docs/events.md), with the fields this service reads. */
record RequestEvent(UUID eventId, String type, int version, Instant occurredAt, Request request, Map<String, Object> data) {

    record Request(long id, String code, Person requester) {
    }

    record Person(UUID id, String name) {
    }

    List<String> strings(String key) {
        return data.get(key) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    List<UUID> uuids(String key) {
        return strings(key).stream().map(UUID::fromString).toList();
    }

    String text(String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    UUID finishedBy() {
        return data.get("finishedBy") instanceof Map<?, ?> actor && actor.get("id") != null
                ? UUID.fromString(actor.get("id").toString())
                : null;
    }
}
