package dev.requestportal.notification.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class RequestEventListener {

    private final RequestEventParser parser;
    private final RequestEventHandler handler;

    RequestEventListener(RequestEventParser parser, RequestEventHandler handler) {
        this.parser = parser;
        this.handler = handler;
    }

    @KafkaListener(topics = "${portal.events.topic}")
    void onEvent(ConsumerRecord<String, String> record) {
        handler.handle(parser.parse(record.value()));
    }
}
