package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

class ProcessingApiTests extends ApiTestSupport {

    @Test
    void requestMovesThroughEachDepartmentToCompletion() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());

        var afterAcad = complete(ACAD_STAFF, requestId, 1);
        assertThat(afterAcad).hasStatusOk();
        var body = json(afterAcad);
        assertThat(body.read("$.currentStep", Integer.class)).isEqualTo(2);
        assertThat(strings(body, "$.steps[*].status")).containsExactly("COMPLETED", "IN_PROGRESS");
        assertThat(body.read("$.steps[0].finishedBy.name", String.class)).isEqualTo("Lê Văn Cường");
        assertThat(body.read("$.steps[0].note", String.class)).isEqualTo("Done");
        assertThat(body.read("$.steps[1].dueAt", String.class)).isNotNull();

        var afterFin = complete(FIN_STAFF, requestId, 2);
        assertThat(afterFin).hasStatusOk();
        body = json(afterFin);
        assertThat(body.read("$.status", String.class)).isEqualTo("COMPLETED");
        assertThat(body.read("$.currentStep", Integer.class)).isNull();
        assertThat(body.read("$.finishedAt", String.class)).isNotNull();
        assertThat(strings(body, "$.history[*].action"))
                .containsExactly("SUBMITTED", "STEP_COMPLETED", "STEP_COMPLETED", "COMPLETED");
    }

    @Test
    void onlyTheStepsDepartmentCanProcessIt() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());

        var wrongDepartment = complete(FIN_STAFF, requestId, 1);
        assertThat(wrongDepartment).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(json(wrongDepartment).read("$.detail", String.class)).endsWith("is handled by department ACAD");

        assertThat(complete(AN, requestId, 1)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(complete(ADMIN, requestId, 1)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void actingOnAStepThatIsNoLongerCurrentIsAConflict() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());
        assertThat(complete(ACAD_STAFF, requestId, 1)).hasStatusOk();

        var again = complete(ACAD_STAFF, requestId, 1);
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(json(again).read("$.detail", String.class)).contains("is not the current step");
    }

    @Test
    void stepsThatRequireAnAttachmentCannotBeCompletedWithoutOne() throws Exception {
        long templateId = createTemplate("""
                {"title": "%s", "departmentCode": "ACAD", "audience": "STUDENT", "status": "ACTIVE", "fields": [],
                 "steps": [{"departmentCode": "ACAD", "description": "Scan and upload", "slaDays": 1,
                            "requiresAttachment": true}]}
                """.formatted(uniqueTitle("Scanned document")));
        long requestId = json(submit(AN, templateId, "{}")).read("$.id", Long.class);

        assertThat(complete(ACAD_STAFF, requestId, 1)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        var foreignFile = mvc.post().uri("/api/v1/requests/{id}/steps/1/complete", requestId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content("{\"attachmentIds\": [\"%s\"]}".formatted(FOREIGN_FILE))
                .exchange();
        assertThat(foreignFile).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(foreignFile).read("$.errors.attachmentIds", String.class)).contains("uploaded by someone else");

        String fileId = UUID.randomUUID().toString();
        var withFile = mvc.post().uri("/api/v1/requests/{id}/steps/1/complete", requestId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content("{\"attachmentIds\": [\"%s\"]}".formatted(fileId))
                .exchange();
        assertThat(withFile).hasStatusOk();
        assertThat(strings(json(withFile), "$.steps[0].attachmentIds")).containsExactly(fileId);
    }

    @Test
    void rejectingEndsTheRequestAndCancelsLaterSteps() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());

        var missingReason = mvc.post().uri("/api/v1/requests/{id}/steps/1/reject", requestId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\": \" \"}")
                .exchange();
        assertThat(missingReason).hasStatus(HttpStatus.BAD_REQUEST);

        var rejected = mvc.post().uri("/api/v1/requests/{id}/steps/1/reject", requestId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\": \"Student ID does not match\"}")
                .exchange();
        assertThat(rejected).hasStatusOk();
        var body = json(rejected);
        assertThat(body.read("$.status", String.class)).isEqualTo("REJECTED");
        assertThat(strings(body, "$.steps[*].status")).containsExactly("REJECTED", "CANCELLED");
        assertThat(body.read("$.steps[0].note", String.class)).isEqualTo("Student ID does not match");
        assertThat(body.read("$.history[1].note", String.class)).isEqualTo("Student ID does not match");
    }

    @Test
    void requesterCanCancelOnlyBeforeAnyStepIsCompleted() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long untouched = submitValid(AN, templateId);
        long started = submitValid(AN, templateId);
        assertThat(complete(ACAD_STAFF, started, 1)).hasStatusOk();

        assertThat(cancel(ACAD_STAFF, untouched)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(cancel(BINH, untouched)).hasStatus(HttpStatus.NOT_FOUND);

        var cancelled = cancel(AN, untouched);
        assertThat(cancelled).hasStatusOk();
        assertThat(json(cancelled).read("$.status", String.class)).isEqualTo("CANCELLED");
        assertThat(strings(json(cancelled), "$.steps[*].status")).containsExactly("CANCELLED", "CANCELLED");

        var tooLate = cancel(AN, started);
        assertThat(tooLate).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(tooLate).read("$.detail", String.class)).contains("processing has started");
    }

    @Test
    void theLastDepartmentRecordsWhetherTheResultWasCollected() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());

        assertThat(setReceived(FIN_STAFF, requestId, true)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        complete(ACAD_STAFF, requestId, 1);
        complete(FIN_STAFF, requestId, 2);
        assertThat(setReceived(ACAD_STAFF, requestId, true)).hasStatus(HttpStatus.FORBIDDEN);

        var received = setReceived(FIN_STAFF, requestId, true);
        assertThat(received).hasStatusOk();
        assertThat(json(received).read("$.resultReceived", Boolean.class)).isTrue();

        var repeated = setReceived(FIN_STAFF, requestId, true);
        assertThat(strings(json(repeated), "$.history[*].action")).containsOnlyOnce("RESULT_RECEIVED");
    }

    @Test
    void staffListsOnlyRequestsInvolvingTheirDepartment() throws Exception {
        long acadThenFin = createAcadThenFinTemplate();
        long studentAffairsOnly = createTemplate("""
                {"title": "%s", "departmentCode": "STU", "audience": "STUDENT", "status": "ACTIVE", "fields": [],
                 "steps": [{"departmentCode": "STU", "description": "Handle", "slaDays": 1}]}
                """.formatted(uniqueTitle("Dormitory")));
        long fromAn = submitValid(AN, acadThenFin);
        long fromBinh = submitValid(BINH, acadThenFin);
        long dormitory = json(submit(AN, studentAffairsOnly, "{}")).read("$.id", Long.class);

        assertThat(ids(FIN_STAFF, "templateId=" + acadThenFin)).containsExactlyInAnyOrder(fromAn, fromBinh);
        assertThat(ids(FIN_STAFF, "templateId=" + studentAffairsOnly)).isEmpty();
        assertThat(ids(STU_STAFF, "templateId=" + studentAffairsOnly)).containsExactly(dormitory);

        assertThat(ids(FIN_STAFF, "awaitingMe=true&templateId=" + acadThenFin)).isEmpty();
        complete(ACAD_STAFF, fromAn, 1);
        assertThat(ids(FIN_STAFF, "awaitingMe=true&templateId=" + acadThenFin)).containsExactly(fromAn);

        assertThat(ids(ACAD_STAFF, "q=nguyen van an&templateId=" + acadThenFin)).containsExactly(fromAn);
        assertThat(ids(ADMIN, "departmentCode=STU&templateId=" + studentAffairsOnly)).containsExactly(dormitory);
        assertThat(mvc.get().uri("/api/v1/requests").header(AUTHORIZATION, bearer(AN)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void bulkCompletionIsAllOrNothing() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long first = submitValid(AN, templateId);
        long second = submitValid(BINH, templateId);
        long alreadyMoved = submitValid(AN, templateId);
        complete(ACAD_STAFF, alreadyMoved, 1);

        var failed = bulkComplete(ACAD_STAFF, first, second, alreadyMoved);
        assertThat(failed).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(failed).read("$.failures['" + alreadyMoved + "']", String.class))
                .contains("is handled by department FIN");
        assertThat(detail(AN, first).read("$.currentStep", Integer.class)).isEqualTo(1);

        var succeeded = bulkComplete(ACAD_STAFF, first, second);
        assertThat(succeeded).hasStatusOk();
        assertThat(json(succeeded).read("$.count", Integer.class)).isEqualTo(2);
        assertThat(detail(AN, first).read("$.currentStep", Integer.class)).isEqualTo(2);
        assertThat(detail(BINH, second).read("$.currentStep", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentCompletionsOfTheSameStepCannotBothSucceed() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcTestResult> attempt = () -> {
            start.await();
            return complete(ACAD_STAFF, requestId, 1);
        };

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcTestResult> one = executor.submit(attempt);
            Future<MvcTestResult> two = executor.submit(attempt);
            start.countDown();
            statuses.add(one.get().getResponse().getStatus());
            statuses.add(two.get().getResponse().getStatus());
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(strings(detail(AN, requestId), "$.history[*].action")).containsOnlyOnce("STEP_COMPLETED");
    }

    @Test
    void overdueIsDerivedFromTheDueDate() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long requestId = submitValid(AN, templateId);
        assertThat(detail(AN, requestId).read("$.steps[0].overdue", Boolean.class)).isFalse();

        jdbc.sql("update request_step set due_at = ? where request_id = ? and position = 1")
                .params(java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), requestId)
                .update();

        assertThat(detail(AN, requestId).read("$.steps[0].overdue", Boolean.class)).isTrue();
        var list = json(mvc.get().uri("/api/v1/requests?templateId=" + templateId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF)).exchange());
        assertThat(list.read("$.items[0].currentStep.overdue", Boolean.class)).isTrue();
    }

    private MvcTestResult cancel(TestUser user, long requestId) {
        return mvc.post().uri("/api/v1/requests/{id}/cancel", requestId).header(AUTHORIZATION, bearer(user)).exchange();
    }

    private MvcTestResult setReceived(TestUser user, long requestId, boolean received) {
        return mvc.put().uri("/api/v1/requests/{id}/result-received", requestId)
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content("{\"received\": " + received + "}")
                .exchange();
    }

    private MvcTestResult bulkComplete(TestUser user, long... requestIds) {
        return mvc.post().uri("/api/v1/requests/bulk/complete")
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content("{\"requestIds\": %s, \"note\": \"Batch\"}".formatted(java.util.Arrays.toString(requestIds)))
                .exchange();
    }

    private List<Long> ids(TestUser user, String query) throws Exception {
        var result = mvc.get().uri("/api/v1/requests?" + query).header(AUTHORIZATION, bearer(user)).exchange();
        assertThat(result).hasStatusOk();
        List<Number> ids = json(result).read("$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
