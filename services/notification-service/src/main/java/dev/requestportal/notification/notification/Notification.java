package dev.requestportal.notification.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** An email to send, with its delivery state. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private long requestId;

    @Column(name = "request_code", nullable = false, updatable = false, length = 32)
    private String requestCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private NotificationKind kind;

    @Column(name = "recipient_email", updatable = false, length = 320)
    private String recipientEmail;

    @Column(name = "recipient_department", updatable = false, length = 16)
    private String recipientDepartment;

    @Column(nullable = false, updatable = false, length = 300)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private Long version;

    protected Notification() {
    }

    private Notification(UUID eventId, long requestId, String requestCode, NotificationKind kind, String recipientEmail,
            String recipientDepartment, String subject, String body, Instant now) {
        this.eventId = eventId;
        this.requestId = requestId;
        this.requestCode = requestCode;
        this.kind = kind;
        this.recipientEmail = recipientEmail;
        this.recipientDepartment = recipientDepartment;
        this.subject = subject;
        this.body = body;
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    static Notification toEmail(UUID eventId, long requestId, String requestCode, NotificationKind kind,
            String email, String subject, String body, Instant now) {
        return new Notification(eventId, requestId, requestCode, kind, email, null, subject, body, now);
    }

    static Notification toDepartment(UUID eventId, long requestId, String requestCode, NotificationKind kind,
            String departmentCode, String subject, String body, Instant now) {
        return new Notification(eventId, requestId, requestCode, kind, null, departmentCode, subject, body, now);
    }

    void sent(Instant now) {
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    /** Schedules another attempt, or gives up after {@code maxAttempts}. */
    void attemptFailed(String error, Instant now, int maxAttempts) {
        attempts++;
        lastError = error;
        if (attempts >= maxAttempts) {
            status = NotificationStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(retryDelay(attempts));
        }
    }

    /** The recipient could not be resolved for a reason that may pass, e.g. org-service is down. */
    void deferred(String reason, Instant now) {
        lastError = reason;
        nextAttemptAt = now.plus(Duration.ofMinutes(1));
    }

    void skipped(String reason) {
        status = NotificationStatus.SKIPPED;
        lastError = reason;
    }

    /** Queues a failed or skipped notification again, with a fresh set of attempts. */
    void retry(Instant now) {
        status = NotificationStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
    }

    /** 1, 5, 15 minutes, then hourly. */
    static Duration retryDelay(int attemptsSoFar) {
        return switch (attemptsSoFar) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
    }

    public Long getId() {
        return id;
    }

    public long getRequestId() {
        return requestId;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getRecipientDepartment() {
        return recipientDepartment;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
