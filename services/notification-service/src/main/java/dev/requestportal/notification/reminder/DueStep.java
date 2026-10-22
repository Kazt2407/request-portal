package dev.requestportal.notification.reminder;

import java.time.Instant;

/** A step in progress whose deadline needs a reminder. */
public record DueStep(long requestId, int position, String requestCode, String title, String departmentCode,
        String description, Instant dueAt) {
}
