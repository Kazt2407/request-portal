# Performance

Load test: [`load-test/request-portal.js`](../load-test/request-portal.js) (k6), run with
[`scripts/load-test.sh`](../scripts/load-test.sh) against the gateway of the local k3d deployment.

## What the test does

Two scenarios at fixed arrival rates (open model: requests keep arriving even when the system slows down,
so a slow system shows up as latency and dropped iterations rather than as fewer requests):

| Scenario | Traffic |
|---|---|
| Browse | Students list templates and their requests; staff open their department's queue |
| Submit and approve | A student submits a request (with an `Idempotency-Key`), Academic Affairs completes it. Each flow is two writes and produces three Kafka events and three emails |

Thresholds: errors below 1%, p95 below 300 ms for reads and 800 ms for writes.

## Environment

Everything on one laptop (8 cores, 15 GB RAM): k3d with one replica of each service, PostgreSQL, Kafka,
Redis, Keycloak and Mailpit in Docker, the Grafana/Tempo/Prometheus stack, and k6 itself. Tracing sampled
10% of requests. The numbers show the system's behaviour, not a capacity guarantee for real hardware.

## Results (2026-10-22)

Each step ran for 90 seconds after a short warm-up. "Requests/s" counts reads plus two writes per flow.

| Target load | Requests/s | Read p95 | Write p95 | p99 (all) | Errors | Dropped |
|---|---|---|---|---|---|---|
| 10 reads + 1 flow per second | 12 | 28 ms | 48 ms | 49 ms | 0% | 0 |
| 20 reads + 2 flows | 24 | 18 ms | 39 ms | 40 ms | 0% | 0 |
| 30 reads + 3 flows | 36 | 17 ms | 33 ms | 33 ms | 0% | 0 |
| 30 reads + 5 flows (default mix) | 40 | 15 ms | 95 ms | 326 ms | 0% | 0 |
| 60 reads + 8 flows (3 runs) | 74–76 | 12–109 ms | 38–457 ms | 40–2,810 ms | 0% | 0–168 |
| 100 reads + 12 flows | 124 | 27 ms | 43 ms | 79 ms | 0% | 3 |

At around 120 requests per second, a single replica of each service keeps p95 under 50 ms. One of the
three runs at 75 requests per second had a slow minute (p99 2.8 s) that the two repeats did not show; on a
shared laptop a garbage-collection pause, a database vacuum or a burst of email work can do that, which is
why the table shows the range rather than the best run. Higher loads were not tested: with only two test
users, the gateway's per-user rate limit becomes the ceiling first.

## The first run, and what it taught

The first run at the default mix failed badly: p95 around 8 seconds, 904 dropped iterations, throughput
stuck at 31 requests per second. The traces and metrics showed why:

- In Tempo, a slow `GET /templates/available` spent 50 ms in the gateway and security filters and
  **3.2 seconds inside request-service's controller**, for a query that takes milliseconds when idle.
- Prometheus showed up to **98 threads waiting for one of request-service's 10 database connections**,
  and a load average of 35 on 8 cores.

The cause was the deployment, not the code:

1. **CPU requests of 100m.** The autoscalers target 70% of the request, so they added replicas at 70
   millicores of use. Each new JVM spent its first minute compiling code on an already full machine,
   making everything slower, which triggered more scaling.
2. **Every request traced (100% sampling)**, adding span creation and export to every call.

After raising the CPU request to a realistic value (500m in the chart, 250m on the laptop), sampling 10%
of requests and keeping one replica during the measurement, the same load ran at p95 15 ms for reads.

Lessons kept in the chart: CPU requests must reflect real usage, or autoscaling misbehaves; and always
check where the time goes (traces, connection pool metrics) before blaming code or adding replicas.

## Reproduce

```bash
docker compose -f infra/docker-compose.yml --profile observability up -d
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  HELM_EXTRA_ARGS="--set observability.samplingProbability=0.1 --set services.gateway.autoscaling.maxReplicas=1 --set services.request-service.autoscaling.maxReplicas=1" \
  scripts/k3d-up.sh
READ_RATE=30 FLOW_RATE=5 DURATION=2m scripts/load-test.sh
```

Watch it live on the Grafana dashboard at http://localhost:3000/d/request-portal/request-portal.
