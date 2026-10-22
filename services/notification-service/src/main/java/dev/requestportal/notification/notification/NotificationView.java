package dev.requestportal.notification.notification;

import java.time.Instant;

/** @param recipient an email address, or {@code department:CODE} until the mailbox is looked up */
public record NotificationView(
        Long id,
        long requestId,
        String requestCode,
        NotificationKind kind,
        String recipient,
        String subject,
        NotificationStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant sentAt) {

    static NotificationView of(Notification notification) {
        String recipient = notification.getRecipientEmail() != null
                ? notification.getRecipientEmail()
                : "department:" + notification.getRecipientDepartment();
        return new NotificationView(
                notification.getId(),
                notification.getRequestId(),
                notification.getRequestCode(),
                notification.getKind(),
                recipient,
                notification.getSubject(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getNextAttemptAt(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getSentAt());
    }
}
