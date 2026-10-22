package dev.requestportal.request.workflow;

/** Where a changed request's events go; implemented by the outbox. */
public interface WorkflowEventSink {

    /** Records the request's pending events in the current transaction. The request must have an id. */
    void append(ServiceRequest request);
}
