package dev.requestportal.notification.event;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class RequestEventParser {

    private final JsonMapper json;

    RequestEventParser(JsonMapper json) {
        this.json = json;
    }

    RequestEvent parse(String message) {
        try {
            RequestEvent event = json.readValue(message, RequestEvent.class);
            if (event.eventId() == null || event.type() == null || event.request() == null || event.data() == null) {
                throw new InvalidEventException("Event is missing eventId, type, request or data", null);
            }
            return event;
        } catch (JacksonException exception) {
            throw new InvalidEventException("Message is not a valid event: " + exception.getOriginalMessage(), exception);
        }
    }
}
