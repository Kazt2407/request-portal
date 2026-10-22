package dev.requestportal.request.workflow;

/**
 * {@code PENDING → IN_PROGRESS → COMPLETED}, or {@code REJECTED} by staff, or {@code CANCELLED} when the
 * request ends before the step is reached. "Overdue" is not a state: it is derived from the due date.
 */
public enum StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED
}
