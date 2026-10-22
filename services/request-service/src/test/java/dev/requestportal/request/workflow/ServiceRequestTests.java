package dev.requestportal.request.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.requestportal.request.template.Audience;
import dev.requestportal.request.template.RequestTemplate;
import dev.requestportal.request.template.TemplateRequest;
import dev.requestportal.request.template.TemplateStatus;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The workflow rules, without Spring or a database. */
class ServiceRequestTests {

    private static final Instant SUBMITTED = Instant.parse("2026-10-22T02:00:00Z");
    private static final Actor STUDENT = new Actor(UUID.randomUUID(), "Nguyễn Văn An");
    private static final Actor CLERK = new Actor(UUID.randomUUID(), "Lê Văn Cường");

    @Test
    void submissionStartsOnlyTheFirstStep() {
        ServiceRequest request = submit(false);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(request.getCurrentStep()).isEqualTo(1);
        assertThat(request.getSteps()).extracting(RequestStep::getStatus)
                .containsExactly(StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING);
        assertThat(request.getSteps().getFirst().getDueAt()).isEqualTo(SUBMITTED.plus(Duration.ofDays(2)));
    }

    @Test
    void completingEachStepInOrderCompletesTheRequest() {
        ServiceRequest request = submit(false);
        Instant later = SUBMITTED.plus(Duration.ofHours(5));

        request.completeStep(1, CLERK, "ok", List.of(), later);
        assertThat(request.getCurrentStep()).isEqualTo(2);
        assertThat(request.step(2).orElseThrow().getDueAt()).isEqualTo(later.plus(Duration.ofDays(1)));

        request.completeStep(2, CLERK, null, List.of(), later);
        request.completeStep(3, CLERK, null, List.of(), later);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCurrentStep()).isNull();
        assertThat(request.getFinishedAt()).isEqualTo(later);
        assertThat(request.getHistory()).extracting(RequestHistoryEntry::getAction).containsExactly(
                HistoryAction.SUBMITTED, HistoryAction.STEP_COMPLETED, HistoryAction.STEP_COMPLETED,
                HistoryAction.STEP_COMPLETED, HistoryAction.COMPLETED);
    }

    @Test
    void onlyTheCurrentStepCanBeCompleted() {
        ServiceRequest request = submit(false);

        assertThatThrownBy(() -> request.completeStep(2, CLERK, null, List.of(), SUBMITTED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("current step is 1");
    }

    @Test
    void stepRequiringAnAttachmentNeedsOne() {
        ServiceRequest request = submit(true);

        assertThatThrownBy(() -> request.completeStep(1, CLERK, null, List.of(), SUBMITTED))
                .isInstanceOf(BusinessRuleException.class);
        request.completeStep(1, CLERK, null, List.of(UUID.randomUUID()), SUBMITTED);
        assertThat(request.getCurrentStep()).isEqualTo(2);
    }

    @Test
    void rejectionEndsTheRequestAndCancelsTheRest() {
        ServiceRequest request = submit(false);
        request.completeStep(1, CLERK, null, List.of(), SUBMITTED);

        request.rejectStep(2, CLERK, "Missing signature", SUBMITTED);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request.getSteps()).extracting(RequestStep::getStatus)
                .containsExactly(StepStatus.COMPLETED, StepStatus.REJECTED, StepStatus.CANCELLED);
        assertThatThrownBy(() -> request.completeStep(2, CLERK, null, List.of(), SUBMITTED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void cancellationIsOnlyPossibleBeforeAnyStepIsCompleted() {
        ServiceRequest fresh = submit(false);
        fresh.cancel(STUDENT, SUBMITTED);
        assertThat(fresh.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(fresh.getSteps()).allMatch(step -> step.getStatus() == StepStatus.CANCELLED);

        ServiceRequest started = submit(false);
        started.completeStep(1, CLERK, null, List.of(), SUBMITTED);
        assertThatThrownBy(() -> started.cancel(STUDENT, SUBMITTED)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resultCanBeMarkedCollectedOnlyWhenCompleted() {
        ServiceRequest request = submit(false);
        assertThatThrownBy(() -> request.markResultReceived(true, CLERK, SUBMITTED))
                .isInstanceOf(BusinessRuleException.class);

        for (int position = 1; position <= 3; position++) {
            request.completeStep(position, CLERK, null, List.of(), SUBMITTED);
        }
        request.markResultReceived(true, CLERK, SUBMITTED);
        request.markResultReceived(true, CLERK, SUBMITTED);

        assertThat(request.isResultReceived()).isTrue();
        assertThat(request.getHistory()).filteredOn(entry -> entry.getAction() == HistoryAction.RESULT_RECEIVED)
                .hasSize(1);
    }

    @Test
    void overdueMeansInProgressPastTheDueDate() {
        ServiceRequest request = submit(false);
        RequestStep first = request.step(1).orElseThrow();

        assertThat(first.isOverdue(SUBMITTED.plus(Duration.ofDays(1)))).isFalse();
        assertThat(first.isOverdue(SUBMITTED.plus(Duration.ofDays(3)))).isTrue();
        assertThat(request.step(2).orElseThrow().isOverdue(SUBMITTED.plus(Duration.ofDays(30)))).isFalse();
    }

    private static ServiceRequest submit(boolean firstStepNeedsAttachment) {
        RequestTemplate template = new RequestTemplate(new TemplateRequest(
                "Transcript", null, "ACAD", Audience.STUDENT, TemplateStatus.ACTIVE, true,
                List.of(),
                List.of(
                        new TemplateRequest.StepDefinition("ACAD", "Compile", 2, firstStepNeedsAttachment),
                        new TemplateRequest.StepDefinition("STU", "Stamp", 1, false),
                        new TemplateRequest.StepDefinition("FIN", "Charge", 1, false)),
                null), SUBMITTED);
        return ServiceRequest.submit("ACAD-0001", template, STUDENT, "an@student.example.edu", List.of(), SUBMITTED);
    }
}
