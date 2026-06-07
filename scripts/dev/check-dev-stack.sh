#!/usr/bin/env bash
#
# Integration smoke test for the local dev infrastructure stack.
#
# Confirms each service is not just "Up" but actually serving, AND that the
# cross-service wiring works (Prometheus scrapes the collector, Grafana has its
# datasources, and a trace flows end-to-end SDK → collector → Jaeger).
#
# Pre-req:  ./scripts/dev/start-dev-stack.sh
# Run:      ./scripts/dev/check-dev-stack.sh           # full check incl. live trace
#           ./scripts/dev/check-dev-stack.sh --no-trace # skip the Gradle trace emit
#
# Exit code = number of failed checks (0 = everything healthy).

set -uo pipefail   # NOT -e: we want to run every check and tally failures

cd "$(git rev-parse --show-toplevel)"

COMPOSE_FILE=infra/docker-compose/compose.dev.yaml

# ── endpoints (override via env if you remapped ports) ───────────────────────
OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
DASHBOARDS_URL="${DASHBOARDS_URL:-http://localhost:5601}"
PROM_URL="${PROM_URL:-http://localhost:9091}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
OTEL_HEALTH_URL="${OTEL_HEALTH_URL:-http://localhost:13133}"
OTEL_GRPC_HOST="${OTEL_GRPC_HOST:-localhost}"
OTEL_GRPC_PORT="${OTEL_GRPC_PORT:-4317}"
LOGS_INDEX="${LOGS_INDEX:-ems-logs}"

RUN_TRACE=1
[ "${1:-}" = "--no-trace" ] && RUN_TRACE=0

# ── output helpers ───────────────────────────────────────────────────────────
if [ -t 1 ]; then
    G="\033[32m"; R="\033[31m"; Y="\033[33m"; B="\033[1m"; N="\033[0m"
else
    G=""; R=""; Y=""; B=""; N=""
fi
PASS=0; FAIL=0
pass() { printf "  ${G}✓${N} %s\n" "$1"; PASS=$((PASS+1)); }
fail() { printf "  ${R}✗${N} %s\n"  "$1"; FAIL=$((FAIL+1)); }
section() { printf "\n${B}%s${N}\n" "$1"; }

# Retry an HTTP check: $1 url, $2 grep-pattern (optional), $3 description, $4 tries
http_check() {
    local url=$1 pat=$2 desc=$3 tries=${4:-1} body
    for ((i=1; i<=tries; i++)); do
        body=$(curl -fsS --max-time 5 "$url" 2>/dev/null)
        if [ $? -eq 0 ]; then
            if [ -z "$pat" ] || printf '%s' "$body" | grep -q "$pat"; then
                pass "$desc"
                return 0
            fi
        fi
        [ "$i" -lt "$tries" ] && sleep 2
    done
    fail "$desc ($url)"
    return 1
}

# ── 1. service liveness ──────────────────────────────────────────────────────
section "Service liveness"

# Postgres — real query, not just a port probe
if docker compose -f "$COMPOSE_FILE" exec -T postgres \
        psql -U ems -d ems -tAc 'SELECT 1' 2>/dev/null | grep -q '^1$'; then
    pass "Postgres accepts queries (SELECT 1 on db 'ems')"
else
    fail "Postgres query failed (db 'ems' / user 'ems')"
fi

http_check "$OPENSEARCH_URL/_cluster/health" '"status":"\(green\|yellow\)"' \
    "OpenSearch cluster health green/yellow" 3
http_check "$DASHBOARDS_URL/api/status" "" \
    "OpenSearch Dashboards API responding" 5
http_check "$PROM_URL/-/healthy" "Healthy" \
    "Prometheus server healthy" 3
http_check "$GRAFANA_URL/api/health" '"database": *"ok"' \
    "Grafana healthy (database ok)" 3
http_check "$JAEGER_URL/api/services" '"data"' \
    "Jaeger query API responding" 3
http_check "$OTEL_HEALTH_URL/" "" \
    "OTel collector health_check (13133)" 3

# OTel collector OTLP gRPC port open (where EMS services connect)
if bash -c "exec 3<>/dev/tcp/${OTEL_GRPC_HOST}/${OTEL_GRPC_PORT}" 2>/dev/null; then
    exec 3>&- 2>/dev/null
    pass "OTel collector OTLP gRPC port ${OTEL_GRPC_PORT} open"
else
    fail "OTel collector OTLP gRPC port ${OTEL_GRPC_PORT} not reachable"
fi

# ── 2. cross-service wiring ──────────────────────────────────────────────────
section "Integration wiring"

# Prometheus is actually scraping the collector (not just up)
if curl -fsS --max-time 5 "$PROM_URL/api/v1/targets" 2>/dev/null \
        | tr ',' '\n' | grep -A2 '"scrapePool":"otel-collector"' | grep -q '"health":"up"' \
   || curl -fsS --max-time 5 "$PROM_URL/api/v1/targets" 2>/dev/null \
        | grep -q '"scrapePool":"otel-collector".*"health":"up"\|"health":"up".*otel-collector'; then
    pass "Prometheus → otel-collector scrape target is UP"
else
    # fall back: target present and at least one target up (scrape interval may lag)
    if curl -fsS --max-time 5 "$PROM_URL/api/v1/targets" 2>/dev/null | grep -q '"job":"otel-collector"'; then
        fail "Prometheus knows the otel-collector target but it is not UP yet (scrape lag? retry in ~15s)"
    else
        fail "Prometheus has no otel-collector scrape target"
    fi
fi

# Grafana datasources provisioned
DS=$(curl -fsS --max-time 5 "$GRAFANA_URL/api/datasources" 2>/dev/null)
if printf '%s' "$DS" | grep -q '"type":"prometheus"'; then
    pass "Grafana has a Prometheus datasource"
else
    fail "Grafana missing Prometheus datasource"
fi
if printf '%s' "$DS" | grep -q '"type":"jaeger"'; then
    pass "Grafana has a Jaeger datasource"
else
    fail "Grafana missing Jaeger datasource"
fi

# ── 3. end-to-end trace flow ─────────────────────────────────────────────────
# Helper: OpenSearch doc count for an index (0 if the index doesn't exist yet)
os_doc_count() {
    local n
    n=$(curl -fsS --max-time 5 "$OPENSEARCH_URL/$1/_count" 2>/dev/null \
        | grep -o '"count":[0-9]*' | head -1 | grep -o '[0-9]*')
    echo "${n:-0}"
}

if [ "$RUN_TRACE" -eq 1 ]; then
    section "End-to-end telemetry flow (SDK → collector → Jaeger + OpenSearch)"

    traces_before=$(curl -fsS --max-time 5 \
        "$JAEGER_URL/api/traces?service=ems-otel-toy&limit=100&lookback=1h" 2>/dev/null \
        | grep -o '"traceID"' | wc -l)
    logs_before=$(os_doc_count "$LOGS_INDEX")

    echo "  emitting toy telemetry via ./scripts/dev/run-otel-toy.sh ..."
    if ./scripts/dev/run-otel-toy.sh > /tmp/check-dev-stack-toy.log 2>&1; then
        if grep -q "SEVERE\|Failed to export" /tmp/check-dev-stack-toy.log; then
            fail "Toy ran but logged a span-export error (see /tmp/check-dev-stack-toy.log)"
        else
            pass "OtelToyTrace ran with no export errors"
        fi
    else
        fail "OtelToyTrace failed to run (see /tmp/check-dev-stack-toy.log)"
    fi

    # Poll Jaeger for a NEW trace to appear (proves live collector → Jaeger flow)
    found=0
    for _ in $(seq 1 15); do
        sleep 2
        traces_after=$(curl -fsS --max-time 5 \
            "$JAEGER_URL/api/traces?service=ems-otel-toy&limit=100&lookback=1h" 2>/dev/null \
            | grep -o '"traceID"' | wc -l)
        if [ "$traces_after" -gt "$traces_before" ]; then
            found=1; break
        fi
    done
    if [ "$found" -eq 1 ]; then
        pass "New ems-otel-toy trace landed in Jaeger (${traces_before} → ${traces_after})"
    else
        fail "No new trace appeared in Jaeger within 30s (collector → Jaeger broken?)"
    fi

    # Verify the trace structure (root + 3 stage spans)
    if curl -fsS --max-time 5 \
            "$JAEGER_URL/api/traces?service=ems-otel-toy&limit=1&lookback=1h" 2>/dev/null \
            | grep -q "ems-toy-root"; then
        pass "Trace contains expected root span 'ems-toy-root'"
    else
        fail "Trace missing expected 'ems-toy-root' span"
    fi

    # Logs path: the same toy run emits log records → collector → OpenSearch.
    logs_found=0
    for _ in $(seq 1 15); do
        sleep 2
        logs_after=$(os_doc_count "$LOGS_INDEX")
        if [ "$logs_after" -gt "$logs_before" ]; then
            logs_found=1; break
        fi
    done
    if [ "$logs_found" -eq 1 ]; then
        pass "New log records landed in OpenSearch '${LOGS_INDEX}' (${logs_before} → ${logs_after})"
    else
        fail "No new logs in OpenSearch '${LOGS_INDEX}' within 30s (collector → OpenSearch broken?)"
    fi

    # Metrics path: the counter is re-exported by the collector on :8889 and
    # scraped by Prometheus. Scrape interval is 15s, so allow up to ~40s.
    metric_found=0
    for _ in $(seq 1 20); do
        sleep 2
        if curl -fsS --max-time 5 \
                "$PROM_URL/api/v1/query?query=ems_toy_stages_processed_total" 2>/dev/null \
                | grep -q '"result":\[{'; then
            metric_found=1; break
        fi
    done
    if [ "$metric_found" -eq 1 ]; then
        pass "Metric ems_toy_stages_processed_total scraped by Prometheus"
    else
        fail "Metric not in Prometheus within 40s (collector :8889 → Prometheus scrape broken?)"
    fi
    rm -f /tmp/check-dev-stack-toy.log
else
    section "End-to-end trace flow"
    printf "  ${Y}–${N} skipped (--no-trace)\n"
fi

# ── summary ──────────────────────────────────────────────────────────────────
section "Summary"
TOTAL=$((PASS+FAIL))
if [ "$FAIL" -eq 0 ]; then
    printf "  ${G}${B}ALL %d CHECKS PASSED${N} — dev stack is healthy.\n\n" "$TOTAL"
    exit 0
else
    printf "  ${R}${B}%d/%d checks FAILED${N} (%d passed).\n" "$FAIL" "$TOTAL" "$PASS"
    printf "  Inspect logs:  docker compose -f %s logs <service>\n\n" "$COMPOSE_FILE"
    exit "$FAIL"
fi
