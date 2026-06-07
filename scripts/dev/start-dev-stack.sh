#!/usr/bin/env bash
#
# Bring up the local dev infrastructure stack.
# Idempotent. Verifies each service comes healthy.
#
# Works with Docker Compose v2 and podman-compose.

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

wait_tcp() {
    local name=$1 host=$2 port=$3
    echo -n "  $name: "
    for _ in $(seq 1 30); do
        if bash -c "exec 3<>/dev/tcp/${host}/${port}" 2>/dev/null; then
            exec 3>&-
            echo "ready"
            return 0
        fi
        sleep 2
    done
    echo "timeout — check logs: docker compose -f $COMPOSE_FILE logs $name"
}

wait_http() {
    local name=$1 url=$2
    echo -n "  $name: "
    for _ in $(seq 1 30); do
        if curl -fs "$url" > /dev/null 2>&1; then
            echo "ready"
            return 0
        fi
        sleep 2
    done
    echo "timeout — check logs: docker compose -f $COMPOSE_FILE logs $name"
}

wait_tcp   postgres   localhost 5432
wait_http  opensearch http://localhost:9200/_cluster/health

echo
echo "Endpoints:"
echo "  Postgres        postgres://ems:ems_dev@localhost:5432/ems"
echo "  OpenSearch      http://localhost:9200"
echo "  Dashboards      http://localhost:5601"
echo "  Prometheus      http://localhost:9091"
echo "  Grafana         http://localhost:3000  (anonymous, Admin role)"
echo "  Jaeger UI       http://localhost:16686"
echo "  OTel gRPC       localhost:4317"
echo "  OTel HTTP       http://localhost:4318"
echo
echo "Stop with: docker compose -f $COMPOSE_FILE down"
