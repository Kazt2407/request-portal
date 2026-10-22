package dev.requestportal.notification.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A workflow event from request-service, as documented in docs/events.md.
 *
 * @param data type-specific fields
 */
public record RequestEvent(
        UUID eventId, String type, int version, Instant occurredAt, Request request, Map<String, Object> data) {

    public record Request(long id, String code, String title, String departmentCode, Person requester) {
    }

    public record Person(UUID id, String name, String email) {
    }

    public String text(String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    public Integer number(String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    public Instant instant(String key) {
        String value = text(key);
        return value == null ? null : Instant.parse(value);
    }
}
