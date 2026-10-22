package dev.requestportal.request.workflow;

import dev.requestportal.request.template.RequestTemplate;
import dev.requestportal.request.template.TemplateStep;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A submitted request and its approval workflow. All state changes go through this aggregate, which
 * enforces the step order and records each change in the history; the {@code version} column turns
 * concurrent changes into a conflict instead of a lost update.
 */
@Entity
@Table(name = "service_request")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "department_code", nullable = false, updatable = false, length = 16)
    private String departmentCode;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "requester_name", nullable = false, updatable = false, length = 200)
    private String requesterName;

    @Column(name = "requester_email", nullable = false, updatable = false, length = 320)
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "result_received", nullable = false)
    private boolean resultReceived;

    @Column(name = "search_text", insertable = false, updatable = false)
    private String searchText;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RequestFieldValue> values = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RequestStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("createdAt asc, id asc")
    private List<RequestHistoryEntry> history = new ArrayList<>();

    protected ServiceRequest() {
    }

    /** Copies the template's steps into the request and starts the first one. */
    public static ServiceRequest submit(
            String code,
            RequestTemplate template,
            Actor requester,
            String requesterEmail,
            List<SubmittedValue> values,
            Instant now) {
        if (template.getSteps().isEmpty()) {
            throw new BusinessRuleException("Template " + template.getId() + " has no steps");
        }
        ServiceRequest request = new ServiceRequest();
        request.code = code;
        request.templateId = template.getId();
        request.title = template.getTitle();
        request.departmentCode = template.getDepartmentCode();
        request.requesterId = requester.id();
        request.requesterName = requester.name();
        request.requesterEmail = requesterEmail;
        request.status = RequestStatus.IN_PROGRESS;
        request.submittedAt = now;
        request.updatedAt = now;
        for (SubmittedValue value : values) {
            request.values.add(new RequestFieldValue(request, value));
        }
        for (TemplateStep step : template.getSteps()) {
            request.steps.add(new RequestStep(request, step));
        }
        RequestStep first = request.steps.getFirst();
        first.start(now);
        request.currentStep = first.getPosition();
        request.record(HistoryAction.SUBMITTED, null, requester, null, now);
        return request;
    }

    /** Completes the current step and starts the next one, or completes the request after the last step. */
    public void completeStep(int position, Actor actor, String note, List<UUID> attachmentIds, Instant now) {
        RequestStep step = requireCurrentStep(position);
        if (step.isRequiresAttachment() && attachmentIds.isEmpty()) {
            throw new BusinessRuleException(
                    "Step " + position + " of request " + code + " requires an attachment to complete");
        }
        step.complete(actor, note, attachmentIds, now);
        record(HistoryAction.STEP_COMPLETED, position, actor, note, now);

        Optional<RequestStep> next = steps.stream()
                .filter(candidate -> candidate.getStatus() == StepStatus.PENDING)
                .findFirst();
        if (next.isPresent()) {
            next.get().start(now);
            currentStep = next.get().getPosition();
        } else {
            finish(RequestStatus.COMPLETED, now);
            record(HistoryAction.COMPLETED, null, actor, null, now);
        }
        updatedAt = now;
    }

    /** Rejects the current step, which ends the request; the steps after it are cancelled. */
    public void rejectStep(int position, Actor actor, String reason, Instant now) {
        RequestStep step = requireCurrentStep(position);
        step.reject(actor, reason, now);
        cancelOpenSteps(now);
        finish(RequestStatus.REJECTED, now);
        record(HistoryAction.STEP_REJECTED, position, actor, reason, now);
    }

    /** The requester withdraws the request, allowed only until a department has completed a step. */
    public void cancel(Actor requester, Instant now) {
        requireInProgress();
        if (steps.stream().anyMatch(step -> step.getStatus() == StepStatus.COMPLETED)) {
            throw new BusinessRuleException(
                    "Request " + code + " can no longer be cancelled because processing has started");
        }
        cancelOpenSteps(now);
        finish(RequestStatus.CANCELLED, now);
        record(HistoryAction.CANCELLED, null, requester, null, now);
    }

    /** Whether the requester has collected the result of a completed request. Repeating a value is a no-op. */
    public void markResultReceived(boolean received, Actor actor, Instant now) {
        if (status != RequestStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Only completed requests can be marked as collected; " + code + " is " + statusText());
        }
        if (resultReceived == received) {
            return;
        }
        resultReceived = received;
        updatedAt = now;
        record(received ? HistoryAction.RESULT_RECEIVED : HistoryAction.RESULT_NOT_RECEIVED, null, actor, null, now);
    }

    public Optional<RequestStep> step(int position) {
        return steps.stream().filter(step -> step.getPosition() == position).findFirst();
    }

    public Optional<RequestStep> currentStep() {
        return currentStep == null ? Optional.empty() : step(currentStep);
    }

    public RequestStep lastStep() {
        return steps.getLast();
    }

    public boolean involvesDepartment(String departmentCode) {
        return departmentCode != null
                && steps.stream().anyMatch(step -> step.getDepartmentCode().equals(departmentCode));
    }

    private RequestStep requireCurrentStep(int position) {
        requireInProgress();
        if (!Objects.equals(currentStep, position)) {
            throw new ConflictException("Step " + position + " is not the current step of request " + code
                    + " (the current step is " + currentStep + "); reload the request");
        }
        return step(position).orElseThrow();
    }

    private void requireInProgress() {
        if (status != RequestStatus.IN_PROGRESS) {
            throw new ConflictException("Request " + code + " is already " + statusText());
        }
    }

    private void cancelOpenSteps(Instant now) {
        steps.stream().filter(RequestStep::isOpen).forEach(step -> step.cancel(now));
    }

    private void finish(RequestStatus finalStatus, Instant now) {
        status = finalStatus;
        currentStep = null;
        finishedAt = now;
        updatedAt = now;
    }

    private void record(HistoryAction action, Integer stepPosition, Actor actor, String note, Instant now) {
        history.add(new RequestHistoryEntry(this, action, stepPosition, actor, note, now));
    }

    private String statusText() {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public String getTitle() {
        return title;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public boolean isResultReceived() {
        return resultReceived;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<RequestFieldValue> getValues() {
        return List.copyOf(values);
    }

    public List<RequestStep> getSteps() {
        return List.copyOf(steps);
    }

    public List<RequestHistoryEntry> getHistory() {
        return List.copyOf(history);
    }
}
