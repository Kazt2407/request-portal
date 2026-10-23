package dev.requestportal.document.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class RequestEventListener {

    private final JsonMapper json;
    private final RequestEventHandler handler;

    RequestEventListener(JsonMapper json, RequestEventHandler handler) {
        this.json = json;
        this.handler = handler;
    }

    @KafkaListener(topics = "${portal.events.topic}")
    void onEvent(ConsumerRecord<String, String> record) {
        RequestEvent event;
        try {
            event = json.readValue(record.value(), RequestEvent.class);
        } catch (JacksonException exception) {
            throw new InvalidEventException("Message is not a valid event: " + exception.getOriginalMessage(), exception);
        }
        if (event.eventId() == null || event.type() == null || event.request() == null || event.data() == null) {
            throw new InvalidEventException("Event is missing eventId, type, request or data", null);
        }
        handler.handle(event);
    }
}
