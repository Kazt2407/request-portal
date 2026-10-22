#!/usr/bin/env bash
# Deploys the five services to a local k3d cluster with Helm. The infrastructure must already be running:
#   docker compose -f infra/docker-compose.yml up -d
# Needs k3d, kubectl and helm. Set SKIP_BUILD=true to reuse the jars and images built last time.
set -euo pipefail
cd "$(dirname "$0")/.."

cluster=request-portal
services=(gateway org-service request-service document-service notification-service)

if ! k3d cluster get "$cluster" >/dev/null 2>&1; then
  k3d cluster create --config deploy/k3d/cluster.yaml
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
  if [ -z "${JAVA_HOME:-}" ] && ! command -v javac >/dev/null; then
    echo "Building needs JDK 25: set JAVA_HOME, e.g. JAVA_HOME=/usr/lib/jvm/java-25-openjdk $0" >&2
    exit 1
  fi
  ./mvnw -B -q -DskipTests package
  for service in "${services[@]}"; do
    docker build -q --build-arg SERVICE="$service" -t "request-portal/$service:dev" . >/dev/null
  done
fi
images=()
for service in "${services[@]}"; do
  images+=("request-portal/$service:dev")
done
k3d image import --cluster "$cluster" "${images[@]}"

# HELM_EXTRA_ARGS adds overrides, e.g. HELM_EXTRA_ARGS="--set observability.samplingProbability=0.1"
# shellcheck disable=SC2086
helm upgrade --install request-portal deploy/helm/request-portal -f deploy/k3d/values.yaml ${HELM_EXTRA_ARGS:-}
# The image tag stays "dev", so restart the pods to pick up the images just imported.
kubectl rollout restart deployment -l app.kubernetes.io/part-of=request-portal
for service in "${services[@]}"; do
  kubectl rollout status "deployment/request-portal-$service" --timeout=5m
done

kubectl get pods -l app.kubernetes.io/part-of=request-portal
echo
echo "API: http://localhost:8088/api/v1   Try: API_URL=http://localhost:8088/api/v1 scripts/demo-workflow.sh"
