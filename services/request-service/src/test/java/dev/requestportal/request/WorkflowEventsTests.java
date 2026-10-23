package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.testcontainers.kafka.KafkaContainer;

/** Workflow changes reach Kafka through the outbox, in order, with the documented envelope. */
class WorkflowEventsTests extends ApiTestSupport {

    /** The test broker. Not spring.kafka.bootstrap-servers: that still holds the docker compose default. */
    @Autowired
    KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of("request-events"));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void aRequestsEventsArePublishedInOrderWithItsIdAsKey() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());
        assertThat(complete(ACAD_STAFF, requestId, 1)).hasStatusOk();
        assertThat(complete(FIN_STAFF, requestId, 2)).hasStatusOk();

        List<DocumentContext> events = eventsFor(requestId, 6);

        assertThat(events).extracting(event -> event.read("$.type", String.class)).containsExactly(
                "REQUEST_SUBMITTED", "STEP_STARTED",
                "STEP_FINISHED", "STEP_STARTED",
                "STEP_FINISHED", "REQUEST_COMPLETED");
        assertThat(events).extracting(event -> event.read("$.data.position", Integer.class))
                .containsExactly(null, 1, 1, 2, 2, null);
        assertThat(events.get(2).read("$.data.finishedBy.name", String.class)).isEqualTo("Lê Văn Cường");
        assertThat(events.get(2).<List<String>>read("$.data.attachmentIds")).isEmpty();
        assertThat(events.getLast().read("$.data.resultDepartmentCode", String.class)).isEqualTo("FIN");
    }

    @Test
    void envelopeCarriesTheRequestAndRequester() throws Exception {
        long requestId = submitValid(BINH, createAcadThenFinTemplate());

        List<DocumentContext> events = eventsFor(requestId, 2);
        DocumentContext submitted = events.getFirst();
        DocumentContext stepStarted = events.get(1);

        assertThat(submitted.read("$.version", Integer.class)).isEqualTo(1);
        assertThat(submitted.read("$.eventId", String.class)).hasSize(36);
        assertThat(submitted.read("$.request.code", String.class)).matches("ACAD-\\d{4}");
        assertThat(submitted.read("$.request.requester.name", String.class)).isEqualTo("Trần Thị Bình");
        assertThat(submitted.read("$.request.requester.email", String.class)).isEqualTo(BINH.email());
        assertThat(submitted.read("$.data.stepCount", Integer.class)).isEqualTo(2);
        assertThat(submitted.<List<String>>read("$.data.stepDepartments")).containsExactly("ACAD", "FIN");
        assertThat(submitted.<List<String>>read("$.data.fileIds")).isEmpty();
        assertThat(stepStarted.read("$.data.departmentCode", String.class)).isEqualTo("ACAD");
        assertThat(stepStarted.read("$.data.dueAt", String.class)).isNotBlank();
    }

    @Test
    void nothingIsPublishedForAChangeThatRolledBack() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long first = submitValid(AN, templateId);
        long moved = submitValid(AN, templateId);
        complete(ACAD_STAFF, moved, 1);

        var failedBulk = mvc.post().uri("/api/v1/requests/bulk/complete")
                .header("Authorization", bearer(ACAD_STAFF))
                .contentType("application/json")
                .content("{\"requestIds\": [%d, %d]}".formatted(first, moved))
                .exchange();
        assertThat(failedBulk).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        int outboxRowsForFirst = jdbc.sql("select count(*) from outbox_event where message_key = ?")
                .param(String.valueOf(first))
                .query(Integer.class)
                .single();
        assertThat(outboxRowsForFirst).as("only REQUEST_SUBMITTED and STEP_STARTED").isEqualTo(2);
    }

    private List<String> outboxRows(String key) {
        return jdbc.sql("select event_type || ' published=' || coalesce(published_at::text, '-') || ' attempts=' || attempts"
                        + " || ' error=' || coalesce(last_error, '-') from outbox_event where message_key = ? order by seq")
                .param(key)
                .query(String.class)
                .list();
    }

    /** Polls the topic until the request has {@code count} events, then returns them in order. */
    private List<DocumentContext> eventsFor(long requestId, int count) {
        String key = String.valueOf(requestId);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            consumer.poll(Duration.ofMillis(250)).forEach(received::add);
            assertThat(received.stream().filter(record -> key.equals(record.key())))
                    .as(() -> "outbox rows for request " + key + ": " + outboxRows(key))
                    .hasSize(count);
        });
        return received.stream()
                .filter(record -> key.equals(record.key()))
                .map(record -> {
                    assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                            .isNotBlank();
                    return JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
                            .parse(record.value());
                })
                .toList();
    }
}
