package dev.requestportal.request.workflow;

import dev.requestportal.request.template.FieldType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JSON views of requests. */
public final class RequestViews {

    private RequestViews() {
    }

    public record Requester(UUID id, String name, String email) {
    }

    /** One row of a request list. */
    public record Summary(
            Long id,
            String code,
            String title,
            String departmentCode,
            Requester requester,
            RequestStatus status,
            CurrentStep currentStep,
            boolean resultReceived,
            Instant submittedAt,
            Instant finishedAt) {

        static Summary of(ServiceRequest request, Instant now) {
            return new Summary(
                    request.getId(),
                    request.getCode(),
                    request.getTitle(),
                    request.getDepartmentCode(),
                    requesterOf(request),
                    request.getStatus(),
                    request.currentStep()
                            .map(step -> new CurrentStep(step.getPosition(), step.getDepartmentCode(),
                                    step.getDescription(), step.getDueAt(), step.isOverdue(now)))
                            .orElse(null),
                    request.isResultReceived(),
                    request.getSubmittedAt(),
                    request.getFinishedAt());
        }
    }

    public record CurrentStep(int position, String departmentCode, String description, Instant dueAt,
            boolean overdue) {
    }

    /** A request with its values, steps and full history. */
    public record Detail(
            Long id,
            String code,
            Long templateId,
            String title,
            String departmentCode,
            Requester requester,
            RequestStatus status,
            Integer currentStep,
            boolean resultReceived,
            Instant submittedAt,
            Instant finishedAt,
            List<Value> values,
            List<Step> steps,
            List<HistoryEntry> history,
            Long version) {

        static Detail of(ServiceRequest request, Instant now) {
            return new Detail(
                    request.getId(),
                    request.getCode(),
                    request.getTemplateId(),
                    request.getTitle(),
                    request.getDepartmentCode(),
                    requesterOf(request),
                    request.getStatus(),
                    request.getCurrentStep(),
                    request.isResultReceived(),
                    request.getSubmittedAt(),
                    request.getFinishedAt(),
                    request.getValues().stream().map(Value::of).toList(),
                    request.getSteps().stream().map(step -> Step.of(step, now)).toList(),
                    request.getHistory().stream()
                            .map(entry -> new HistoryEntry(entry.getAction(), entry.getStepPosition(),
                                    entry.getActor(), entry.getNote(), entry.getCreatedAt()))
                            .toList(),
                    request.getVersion());
        }
    }

    /** @param value text for single-valued fields, a list of file ids for FILE and IMAGE fields */
    public record Value(String key, String label, FieldType type, Object value) {

        static Value of(RequestFieldValue value) {
            Object content = value.getType().holdsFiles() ? value.getValue() : value.getValue().getFirst();
            return new Value(value.getKey(), value.getLabel(), value.getType(), content);
        }
    }

    public record Step(
            int position,
            String departmentCode,
            String description,
            int slaDays,
            boolean requiresAttachment,
            StepStatus status,
            Instant startedAt,
            Instant dueAt,
            boolean overdue,
            Instant finishedAt,
            Actor finishedBy,
            String note,
            List<UUID> attachmentIds) {

        static Step of(RequestStep step, Instant now) {
            return new Step(
                    step.getPosition(),
                    step.getDepartmentCode(),
                    step.getDescription(),
                    step.getSlaDays(),
                    step.isRequiresAttachment(),
                    step.getStatus(),
                    step.getStartedAt(),
                    step.getDueAt(),
                    step.isOverdue(now),
                    step.getFinishedAt(),
                    step.getFinishedBy(),
                    step.getNote(),
                    step.getAttachmentIds());
        }
    }

    public record HistoryEntry(HistoryAction action, Integer stepPosition, Actor actor, String note, Instant at) {
    }

    private static Requester requesterOf(ServiceRequest request) {
        return new Requester(request.getRequesterId(), request.getRequesterName(), request.getRequesterEmail());
    }
}
