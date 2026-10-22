package dev.requestportal.request.workflow;

import java.time.Instant;

/**
 * Something that happened to a request, recorded by {@link ServiceRequest} and published to Kafka
 * through the outbox. Consumers get the request's identity and requester in the event envelope.
 */
public sealed interface WorkflowEvent {

    Type type();

    Instant occurredAt();

    enum Type {
        REQUEST_SUBMITTED,
        STEP_STARTED,
        STEP_FINISHED,
        REQUEST_COMPLETED,
        REQUEST_REJECTED,
        REQUEST_CANCELLED
    }

    /** @param expectedDays total of the steps' deadlines, or {@code null} if the template hides it */
    record RequestSubmitted(int stepCount, Integer expectedDays, Instant occurredAt) implements WorkflowEvent {

        public Type type() {
            return Type.REQUEST_SUBMITTED;
        }
    }

    record StepStarted(int position, String departmentCode, String description, Instant dueAt, Instant occurredAt)
            implements WorkflowEvent {

        public Type type() {
            return Type.STEP_STARTED;
        }
    }

    /** @param finishedBy who completed or rejected the step; {@code null} when it was cancelled */
    record StepFinished(int position, String departmentCode, StepStatus status, Actor finishedBy, String note,
            Instant occurredAt) implements WorkflowEvent {

        public Type type() {
            return Type.STEP_FINISHED;
        }
    }

    /** @param resultDepartmentCode the department that hands over the result */
    record RequestCompleted(String resultDepartmentCode, Instant occurredAt) implements WorkflowEvent {

        public Type type() {
            return Type.REQUEST_COMPLETED;
        }
    }

    record RequestRejected(int position, String departmentCode, String reason, Instant occurredAt)
            implements WorkflowEvent {

        public Type type() {
            return Type.REQUEST_REJECTED;
        }
    }

    record RequestCancelled(Instant occurredAt) implements WorkflowEvent {

        public Type type() {
            return Type.REQUEST_CANCELLED;
        }
    }
}
