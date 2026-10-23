package dev.requestportal.document.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfiguration {

    @Bean
    NewTopic requestEventsTopic(EventsProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(properties.replicas()).build();
    }

    /** This consumer's own dead-letter topic, so its failures do not mix with notification-service's. */
    @Bean
    NewTopic deadLetterTopic(EventsProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, EventsProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 2));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
