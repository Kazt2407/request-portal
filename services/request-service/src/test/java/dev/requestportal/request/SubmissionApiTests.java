package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SubmissionApiTests extends ApiTestSupport {

    @Test
    void submissionCreatesARequestAndStartsTheFirstStep() throws Exception {
        long templateId = createAcadThenFinTemplate();
        String fileId = UUID.randomUUID().toString();

        var result = submit(AN, templateId, """
                {"reason": "  Visa application  ", "purpose": "Visa", "term": "2026-2027-1", "evidence": ["%s"]}
                """.formatted(fileId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("false");
        var body = json(result);
        assertThat(body.read("$.code", String.class)).matches("ACAD-\\d{4}");
        assertThat(body.read("$.status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(body.read("$.currentStep", Integer.class)).isEqualTo(1);
        assertThat(body.read("$.requester.name", String.class)).isEqualTo("Nguyễn Văn An");
        assertThat(strings(body, "$.steps[*].status")).containsExactly("IN_PROGRESS", "PENDING");

        Instant submittedAt = Instant.parse(body.read("$.submittedAt"));
        Instant dueAt = Instant.parse(body.read("$.steps[0].dueAt"));
        assertThat(Duration.between(submittedAt, dueAt)).isEqualTo(Duration.ofDays(2));

        assertThat(body.read("$.values[0].value", String.class)).isEqualTo("Visa application");
        assertThat(body.read("$.values[0].label", String.class)).isEqualTo("Reason");
        assertThat(strings(body, "$.values[3].value")).containsExactly(fileId);
        assertThat(strings(body, "$.history[*].action")).containsExactly("SUBMITTED");
    }

    @Test
    void invalidValuesAreReportedPerField() throws Exception {
        long templateId = createAcadThenFinTemplate();

        var result = submit(AN, templateId, """
                {"purpose": "Holiday", "term": "1999-2000-1", "evidence": ["not-a-file-id"], "extra": "x"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var errors = json(result);
        assertThat(errors.read("$.errors.reason", String.class)).isEqualTo("is required");
        assertThat(errors.read("$.errors.purpose", String.class)).isEqualTo("must be one of: Loan, Visa");
        assertThat(errors.read("$.errors.term", String.class)).isEqualTo("is not a known academic term");
        assertThat(errors.read("$.errors.evidence", String.class)).isEqualTo("must be a list of file ids");
        assertThat(errors.read("$.errors.extra", String.class)).isEqualTo("is not a field of this template");
    }

    @Test
    void templatesForAnotherAudienceCannotBeUsed() throws Exception {
        long lecturerTemplate = createTemplate("""
                {"title": "%s", "departmentCode": "ACAD", "audience": "LECTURER", "status": "ACTIVE",
                 "fields": [], "steps": [{"departmentCode": "ACAD", "description": "Handle", "slaDays": 1}]}
                """.formatted(uniqueTitle("Lecturers only")));

        assertThat(submit(AN, lecturerTemplate, "{}")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(submit(LECTURER, lecturerTemplate, "{}")).hasStatus(HttpStatus.CREATED);
        assertThat(submit(ACAD_STAFF, lecturerTemplate, "{}")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void retryWithTheSameIdempotencyKeyReturnsTheOriginalRequest() throws Exception {
        long templateId = createAcadThenFinTemplate();
        String key = UUID.randomUUID().toString();

        var first = submitWithKey(AN, templateId, key, "Loan");
        var retry = submitWithKey(AN, templateId, key, "Loan");

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(retry.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(json(retry).read("$.id", Long.class)).isEqualTo(json(first).read("$.id", Long.class));

        var reusedForSomethingElse = submitWithKey(AN, templateId, key, "Visa");
        assertThat(reusedForSomethingElse).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void concurrentRetriesCreateOnlyOneRequest() throws Exception {
        long templateId = createAcadThenFinTemplate();
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcTestResult> attempt = () -> {
            start.await();
            return submitWithKey(BINH, templateId, key, "Loan");
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<MvcTestResult>> attempts = List.of(
                    executor.submit(attempt), executor.submit(attempt),
                    executor.submit(attempt), executor.submit(attempt));
            start.countDown();
            List<Long> ids = new java.util.ArrayList<>();
            for (Future<MvcTestResult> future : attempts) {
                MvcTestResult result = future.get();
                assertThat(result).hasStatus(HttpStatus.CREATED);
                ids.add(json(result).read("$.id", Long.class));
            }
            assertThat(ids).containsOnly(ids.getFirst());
        }
        int requestsOfBinh = jdbc.sql("select count(*) from service_request where requester_id = ? and template_id = ?")
                .params(BINH.id(), templateId)
                .query(Integer.class)
                .single();
        assertThat(requestsOfBinh).isEqualTo(1);
    }

    @Test
    void requestersListOnlyTheirOwnRequestsAndCannotOpenOthers() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long ofAn = submitValid(AN, templateId);

        var anList = json(mvc.get().uri("/api/v1/requests/mine").header(AUTHORIZATION, bearer(AN)).exchange());
        assertThat(anList.<List<Integer>>read("$.items[*].id")).contains((int) ofAn);

        var binhList = json(mvc.get().uri("/api/v1/requests/mine").header(AUTHORIZATION, bearer(BINH)).exchange());
        assertThat(binhList.<List<Integer>>read("$.items[*].id")).doesNotContain((int) ofAn);

        assertThat(mvc.get().uri("/api/v1/requests/{id}", ofAn).header(AUTHORIZATION, bearer(BINH)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/requests/{id}", ofAn).header(AUTHORIZATION, bearer(FIN_STAFF)))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/requests/{id}", ofAn).header(AUTHORIZATION, bearer(STU_STAFF)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void editingATemplateDoesNotChangeSubmittedRequests() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long requestId = submitValid(AN, templateId);

        var template = json(mvc.get().uri("/api/v1/templates/{id}", templateId)
                .header(AUTHORIZATION, bearer(ADMIN)).exchange());
        assertThat(mvc.put().uri("/api/v1/templates/{id}", templateId)
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"title": "%s", "departmentCode": "ACAD", "audience": "STUDENT", "status": "ACTIVE",
                         "fields": [{"key": "reason", "label": "Why?", "type": "TEXT"}],
                         "steps": [{"departmentCode": "STU", "description": "Brand new step", "slaDays": 9}],
                         "version": %d}
                        """.formatted(template.read("$.title", String.class), template.read("$.version", Integer.class))))
                .hasStatusOk();

        var request = detail(AN, requestId);
        assertThat(strings(request, "$.steps[*].departmentCode")).containsExactly("ACAD", "FIN");
        assertThat(request.read("$.values[0].label", String.class)).isEqualTo("Reason");
    }

    private MvcTestResult submitWithKey(TestUser user, long templateId, String key, String purpose) {
        return mvc.post().uri("/api/v1/requests")
                .header(AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", key)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"templateId": %d, "values": {"reason": "Retry test", "purpose": "%s"}}
                        """.formatted(templateId, purpose))
                .exchange();
    }
}
