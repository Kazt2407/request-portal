package dev.requestportal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import dev.requestportal.notification.reminder.ReminderJob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

class NotificationFlowTests extends NotificationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    KafkaContainer kafkaContainer;

    @Autowired
    ReminderJob reminderJob;

    @Test
    void requesterIsEmailedWhenTheirRequestIsSubmitted() throws Exception {
        Request request = newRequest("Official transcript");

        publish("REQUEST_SUBMITTED", request, Map.of("stepCount", 2, "expectedDays", 4));

        DocumentContext email = await().atMost(WAIT).until(
                () -> emailsWithSubject("[" + request.code() + "]"), found -> !found.isEmpty()).getFirst();
        assertThat(email.read("$.Subject", String.class))
                .isEqualTo("[" + request.code() + "] We received your request: Official transcript");
        assertThat(email.read("$.To[0].Address", String.class)).isEqualTo("an.nguyen@student.example.edu");
        assertThat(email.read("$.HTML", String.class))
                .contains("Nguyễn Văn An", "up to <strong>4</strong> day(s)", "/requests/" + request.id());
    }

    @Test
    void aRedeliveredEventIsHandledOnce() throws Exception {
        Request request = newRequest("Duplicate delivery");
        UUID eventId = UUID.randomUUID();
        String event = envelope(eventId, "REQUEST_CANCELLED", request, Map.of());
        String key = String.valueOf(request.id());

        kafka.send(TOPIC, key, event).get();
        kafka.send(TOPIC, key, event).get();
        UUID marker = publish("STEP_FINISHED", request, Map.of("position", 1, "departmentCode", "ACAD",
                "status", "CANCELLED"));

        // Same key, same partition: once the later marker is handled, both copies have been seen.
        await().atMost(WAIT).until(() -> processed(marker));
        int notifications = jdbc.sql("select count(*) from notification where event_id = ?")
                .param(eventId)
                .query(Integer.class)
                .single();
        assertThat(notifications).isEqualTo(1);
    }

    @Test
    void departmentMailboxIsEmailedWhenAStepStartsAndTheStepIsTracked() throws Exception {
        Request request = newRequest("Tuition fee receipt");
        Instant dueAt = Instant.parse("2026-10-27T03:00:00Z");

        publish("STEP_STARTED", request, Map.of("position", 1, "departmentCode", "FIN",
                "description", "Match the payment", "dueAt", dueAt.toString()));

        DocumentContext email = await().atMost(WAIT).until(
                () -> emailsWithSubject("[" + request.code() + "] New request waiting"), found -> !found.isEmpty())
                .getFirst();
        assertThat(email.read("$.To[0].Address", String.class)).isEqualTo("finance@example.edu");
        assertThat(email.read("$.HTML", String.class))
                .contains("Finance and Accounting Office", "Match the payment", "27/10/2026 10:00");

        UUID finished = publish("STEP_FINISHED", request, Map.of("position", 1, "departmentCode", "FIN",
                "status", "COMPLETED"));
        await().atMost(WAIT).until(() -> processed(finished));
        Timestamp finishedAt = jdbc.sql("select finished_at from tracked_step where request_id = ? and position = 1")
                .param(request.id())
                .query(Timestamp.class)
                .single();
        assertThat(finishedAt).isNotNull();
    }

    @Test
    void completedAndRejectedEmailsNameTheDepartment() throws Exception {
        Request completed = newRequest("Student status confirmation");
        Request rejected = newRequest("Official transcript");

        publish("REQUEST_COMPLETED", completed, Map.of("resultDepartmentCode", "FIN"));
        publish("REQUEST_REJECTED", rejected, Map.of("position", 1, "departmentCode", "ACAD",
                "reason", "Student ID does not match"));

        DocumentContext ready = await().atMost(WAIT).until(
                () -> emailsWithSubject("[" + completed.code() + "] Your request is ready"), found -> !found.isEmpty())
                .getFirst();
        assertThat(ready.read("$.HTML", String.class)).contains("Finance and Accounting Office");

        DocumentContext no = await().atMost(WAIT).until(
                () -> emailsWithSubject("[" + rejected.code() + "] Your request was rejected"), found -> !found.isEmpty())
                .getFirst();
        assertThat(no.read("$.HTML", String.class)).contains("Academic Affairs Office", "Student ID does not match");
    }

    @Test
    void aDepartmentWithoutMailboxIsSkipped() throws Exception {
        Request request = newRequest("Dormitory registration");

        publish("STEP_STARTED", request, Map.of("position", 1, "departmentCode", "STU",
                "description", "Assign a room", "dueAt", Instant.now().plus(3, ChronoUnit.DAYS).toString()));

        await().atMost(WAIT).until(() -> "SKIPPED".equals(statusOf(request.id(), "STEP_WAITING")));
    }

    @Test
    void unreadableMessagesGoToTheDeadLetterTopic() throws Exception {
        String key = "broken-" + UUID.randomUUID();
        kafka.send(TOPIC, key, "this is not an event").get();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("request-events.DLT"));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(WAIT).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(250)).forEach(received::add);
                assertThat(received).anySatisfy(record -> {
                    assertThat(record.key()).isEqualTo(key);
                    assertThat(record.value()).isEqualTo("this is not an event");
                });
            });
        }
    }

    @Test
    void remindersAreQueuedOncePerStep() {
        Instant now = Instant.now();
        long dueSoon = track(now.minus(3, ChronoUnit.DAYS), now.plus(2, ChronoUnit.HOURS), null);
        long overdue = track(now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS), null);
        long shortDeadline = track(now.minus(10, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), null);
        long finished = track(now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS), now);

        reminderJob.run();
        reminderJob.run();

        assertThat(kindsFor(dueSoon)).containsExactly("STEP_DUE_SOON");
        assertThat(kindsFor(overdue)).containsExactly("STEP_OVERDUE");
        assertThat(kindsFor(shortDeadline)).isEmpty();
        assertThat(kindsFor(finished)).isEmpty();
    }

    private long track(Instant startedAt, Instant dueAt, Instant finishedAt) {
        Request request = newRequest("Reminder test");
        jdbc.sql("""
                        insert into tracked_step (request_id, position, request_code, title, department_code,
                                                  description, started_at, due_at, finished_at)
                        values (?, 1, ?, ?, 'ACAD', 'Check the records', ?, ?, ?)
                        """)
                .params(request.id(), request.code(), request.title(), Timestamp.from(startedAt),
                        Timestamp.from(dueAt), finishedAt == null ? null : Timestamp.from(finishedAt))
                .update();
        return request.id();
    }

    private List<String> kindsFor(long requestId) {
        return jdbc.sql("select kind from notification where request_id = ? order by id")
                .param(requestId)
                .query(String.class)
                .list();
    }

    private String statusOf(long requestId, String kind) {
        return jdbc.sql("select status from notification where request_id = ? and kind = ?")
                .params(requestId, kind)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
