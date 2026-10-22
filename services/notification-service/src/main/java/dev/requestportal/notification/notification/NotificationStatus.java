package dev.requestportal.notification.notification;

public enum NotificationStatus {
    PENDING,
    SENT,
    /** Gave up after the maximum number of attempts; an admin can retry it. */
    FAILED,
    /** Nothing to send to, e.g. a department without a mailbox. */
    SKIPPED
}
