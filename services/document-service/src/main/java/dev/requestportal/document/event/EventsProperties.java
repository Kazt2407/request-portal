package dev.requestportal.document.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.events")
public record EventsProperties(String topic, String deadLetterTopic, int partitions, short replicas) {
}
