package dev.requestportal.notification.event;

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
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }

    /** Same partition count as the main topic, so a failed record keeps its partition. */
    @Bean
    NewTopic deadLetterTopic(EventsProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }

    /**
     * Retries a failing record twice, one second apart (for example while the database restarts), then
     * moves it to the dead-letter topic so one bad record cannot block its partition. Unreadable messages
     * skip the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, EventsProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 2));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
