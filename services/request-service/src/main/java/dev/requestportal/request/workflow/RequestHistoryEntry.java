package dev.requestportal.request.workflow;

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
import java.time.Instant;
import java.util.UUID;

/** One line of a request's audit trail. Never updated. */
@Entity
@Table(name = "request_history")
public class RequestHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", updatable = false)
    private ServiceRequest request;

    @Column(name = "step_position", updatable = false)
    private Integer stepPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private HistoryAction action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", nullable = false, updatable = false, length = 200)
    private String actorName;

    @Column(updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequestHistoryEntry() {
    }

    RequestHistoryEntry(
            ServiceRequest request, HistoryAction action, Integer stepPosition, Actor actor, String note, Instant at) {
        this.request = request;
        this.action = action;
        this.stepPosition = stepPosition;
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.note = note;
        this.createdAt = at;
    }

    public HistoryAction getAction() {
        return action;
    }

    public Integer getStepPosition() {
        return stepPosition;
    }

    public Actor getActor() {
        return new Actor(actorId, actorName);
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
