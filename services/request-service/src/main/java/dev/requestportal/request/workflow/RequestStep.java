package dev.requestportal.request.workflow;

import dev.requestportal.request.template.TemplateStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A request's own copy of a template step, with its progress. Changed only through {@link ServiceRequest}. */
@Entity
@Table(name = "request_step")
public class RequestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Column(nullable = false)
    private int position;

    @Column(name = "department_code", nullable = false, length = 16)
    private String departmentCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "sla_days", nullable = false)
    private int slaDays;

    @Column(name = "requires_attachment", nullable = false)
    private boolean requiresAttachment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "finished_by_id")
    private UUID finishedById;

    @Column(name = "finished_by_name", length = 200)
    private String finishedByName;

    private String note;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachment_ids", nullable = false)
    private List<UUID> attachmentIds = new ArrayList<>();

    protected RequestStep() {
    }

    RequestStep(ServiceRequest request, TemplateStep template) {
        this.request = request;
        this.position = template.getPosition();
        this.departmentCode = template.getDepartmentCode();
        this.description = template.getDescription();
        this.slaDays = template.getSlaDays();
        this.requiresAttachment = template.isRequiresAttachment();
        this.status = StepStatus.PENDING;
    }

    void start(Instant now) {
        status = StepStatus.IN_PROGRESS;
        startedAt = now;
        dueAt = now.plus(Duration.ofDays(slaDays));
    }

    void complete(Actor actor, String note, List<UUID> attachments, Instant now) {
        finish(StepStatus.COMPLETED, actor, note, now);
        attachmentIds = new ArrayList<>(attachments);
    }

    void reject(Actor actor, String note, Instant now) {
        finish(StepStatus.REJECTED, actor, note, now);
    }

    void cancel(Instant now) {
        status = StepStatus.CANCELLED;
        finishedAt = now;
    }

    private void finish(StepStatus newStatus, Actor actor, String note, Instant now) {
        status = newStatus;
        finishedAt = now;
        finishedById = actor.id();
        finishedByName = actor.name();
        this.note = note;
    }

    public boolean isOpen() {
        return status == StepStatus.PENDING || status == StepStatus.IN_PROGRESS;
    }

    public boolean isOverdue(Instant now) {
        return status == StepStatus.IN_PROGRESS && dueAt.isBefore(now);
    }

    public int getPosition() {
        return position;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getDescription() {
        return description;
    }

    public int getSlaDays() {
        return slaDays;
    }

    public boolean isRequiresAttachment() {
        return requiresAttachment;
    }

    public StepStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Actor getFinishedBy() {
        return finishedById == null ? null : new Actor(finishedById, finishedByName);
    }

    public String getNote() {
        return note;
    }

    public List<UUID> getAttachmentIds() {
        return List.copyOf(attachmentIds);
    }
}
