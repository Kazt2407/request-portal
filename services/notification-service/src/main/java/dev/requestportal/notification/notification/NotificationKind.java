package dev.requestportal.notification.notification;

import java.util.Locale;

public enum NotificationKind {
    REQUEST_SUBMITTED,
    REQUEST_COMPLETED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,
    /** A step started and is waiting for the department. */
    STEP_WAITING,
    STEP_DUE_SOON,
    STEP_OVERDUE;

    /** The Thymeleaf template under templates/mail, e.g. request-submitted. */
    String templateName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
