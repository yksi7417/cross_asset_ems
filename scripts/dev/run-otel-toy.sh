#!/usr/bin/env bash
#
# Verify the OTel pipeline end-to-end: SDK → collector → Jaeger.
#
# Pre-req:  ./scripts/dev/start-dev-stack.sh   (otel-collector + jaeger up)
# Run:      ./scripts/dev/run-otel-toy.sh

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [ ! -x ./gradlew ]; then
    echo "gradlew not found. Run: ./scripts/dev/bootstrap.sh"
    exit 1
fi

export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"

# Wait for the OTel collector to be ready before emitting spans.
# Without this the gRPC channel may not be established before otel.close() fires,
# causing a noisy SEVERE log even though the trace was exported on an earlier attempt.
echo -n "Waiting for OTel collector (http://localhost:13133/)..."
for i in $(seq 1 15); do
    if curl -fs http://localhost:13133/ > /dev/null 2>&1; then
        echo " ready"
        break
    fi
    if [ "$i" -eq 15 ]; then
        echo
        echo "ERROR: OTel collector did not become ready (http://localhost:13133/). Is the dev stack up?"
        echo "  Run: ./scripts/dev/start-dev-stack.sh"
        exit 1
    fi
    echo -n "."
    sleep 1
done

./gradlew --no-daemon :ems-observability:run

echo
echo "Open http://localhost:16686 and look for service ems-otel-toy."
echo "You should see a trace tree:"
echo "  ems-toy-root"
echo "    ├── stage:validate"
echo "    ├── stage:route"
echo "    └── stage:ack"
