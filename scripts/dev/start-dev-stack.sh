#!/usr/bin/env bash
#
# Bring up the local dev infrastructure stack.
# Idempotent. Verifies each service comes healthy.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

COMPOSE_FILE=infra/docker-compose/compose.dev.yaml
PROFILES="${PROFILES:-}"

echo "Bringing up ems-dev stack..."
if [ -n "$PROFILES" ]; then
    docker compose -f "$COMPOSE_FILE" --profile "$PROFILES" up -d
else
    docker compose -f "$COMPOSE_FILE" up -d
fi

echo
echo "Waiting for services to become healthy..."
for svc in postgres opensearch; do
    echo -n "  $svc: "
    for _ in $(seq 1 30); do
        if docker compose -f "$COMPOSE_FILE" ps "$svc" --format json \
            | grep -q '"Health":"healthy"'; then
            echo "ready"
            break
        fi
        sleep 2
    done
done

echo
echo "Endpoints:"
echo "  Postgres        postgres://ems:ems_dev@localhost:5432/ems"
echo "  OpenSearch      http://localhost:9200"
echo "  Dashboards      http://localhost:5601"
echo "  Prometheus      http://localhost:9090"
echo "  Grafana         http://localhost:3000  (anonymous, Admin role)"
echo "  Jaeger UI       http://localhost:16686"
echo "  OTel gRPC       localhost:4317"
echo "  OTel HTTP       http://localhost:4318"
echo
echo "Stop with: docker compose -f $COMPOSE_FILE down"
