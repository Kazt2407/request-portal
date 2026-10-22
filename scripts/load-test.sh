#!/usr/bin/env bash
# Runs the k6 load test in Docker (no local install) against the gateway. Defaults to the k3d cluster;
# for services started locally use API_URL=http://localhost:8080/api/v1. DURATION (2m), READ_RATE (30 reads
# per second) and FLOW_RATE (5 submit-and-approve flows per second) tune the load.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p load-test/results
docker run --rm --network host --user "$(id -u):$(id -g)" \
  -e API_URL="${API_URL:-http://localhost:8088/api/v1}" \
  -e KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}" \
  -e DURATION="${DURATION:-2m}" \
  -e READ_RATE="${READ_RATE:-30}" \
  -e FLOW_RATE="${FLOW_RATE:-5}" \
  -v "$PWD/load-test:/load-test" \
  grafana/k6:2.3.0 run --summary-export=/load-test/results/summary.json /load-test/request-portal.js
