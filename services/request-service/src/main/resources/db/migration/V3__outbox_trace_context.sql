-- W3C traceparent of the request that produced the event, so the trace continues through Kafka even
-- though the relay publishes later, on another thread.
alter table outbox_event add column trace_parent varchar(55);
