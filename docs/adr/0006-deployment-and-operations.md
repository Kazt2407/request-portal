# ADR 0006: Deployment and operations

- Status: accepted
- Date: 2026-10-22

## Context

The services need to run somewhere other than a developer's IDE, be observable when something is slow
or broken, and be reproducible from code. The project has no budget for a permanent cloud environment,
so everything must also run on one laptop.

## Decisions

**One container image definition for all services.** A single multi-stage [`Dockerfile`](../../Dockerfile)
takes the service name as a build argument. It splits the Spring Boot jar into layers (libraries, loader,
application) so a code change ships a layer of a few hundred kilobytes, runs as an unprivileged user, and
sizes the heap from the container's memory limit. CI builds and pushes the images to the GitHub
Container Registry on every push to `main`, tagged with the commit.

**One Helm chart for the services; infrastructure is someone else's job.** The chart
([`deploy/helm/request-portal`](../../deploy/helm/request-portal)) generates a Deployment, Service and,
where enabled, a HorizontalPodAutoscaler for each service from one template, plus an ingress for the
gateway only. Pods run as non-root with a read-only filesystem and no Linux capabilities; startup,
liveness and readiness probes use the actuator health groups. Databases, Kafka, Redis, mail and
Keycloak are inputs (host names and a credentials Secret), because in production they are managed
services; the chart would otherwise drift into running stateful systems badly.

- Locally, k3d runs the chart while docker compose provides the infrastructure; pods reach it as
  `host.k3d.internal`. Kafka has a third listener that advertises that name, and Keycloak has a fixed
  public hostname so tokens carry the same issuer whichever address they were fetched from.
- In AWS, Terraform ([`deploy/terraform/aws`](../../deploy/terraform/aws)) creates EKS, RDS, MSK,
  ElastiCache (Valkey), S3 and SES, and outputs the matching Helm values.
- For a plain server, Ansible ([`deploy/ansible`](../../deploy/ansible)) installs k3s and Rancher.

**Observability with OpenTelemetry, exported over OTLP.** Every service exports traces and metrics to
any OTLP collector; locally that is the `grafana/otel-lgtm` image (Tempo, Prometheus, Grafana) behind a
docker compose profile, with a provisioned dashboard. Export is off unless `OTEL_EXPORT_ENABLED=true`, so
tests and plain runs need no collector.

- *Traces cross Kafka.* The outbox stores the W3C `traceparent` of the request that produced each event;
  the relay publishes inside a span that continues it and passes it on in the Kafka headers, and
  notification-service continues the trace when it consumes. One trace shows the HTTP call, the
  asynchronous publish and the email handling, even though they happen at different times on different
  threads.
- *Noise is filtered at the source:* health probes, the polling jobs' own runs and orphaned security
  spans are not traced, so real requests are easy to find.
- *Histogram buckets* for HTTP latency, so p95 and p99 are correct across replicas, and business metrics
  where they explain behaviour: the outbox backlog and the email queue by status.

**Performance is measured, not assumed.** A k6 test with an open workload model runs against the
deployment, and the results, including a failed first run and its cause, are in
[docs/performance.md](../performance.md).

## Consequences

- Deploying to a new environment means providing infrastructure endpoints and a Secret; the chart and
  images are the same everywhere.
- The Terraform and Ansible code is validated (`terraform validate`, `ansible-lint` production profile)
  but has never been applied; a first real apply will need small fixes.
- Local Kubernetes depends on docker compose running on the same machine, which is fine for development
  and demos but is not how a shared environment would be built.
