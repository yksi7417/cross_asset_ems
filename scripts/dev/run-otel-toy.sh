#!/usr/bin/env bash
#
# Verify the OTel pipeline end-to-end: SDK → collector → Jaeger.
#
# Pre-req:  ./scripts/dev/start-dev-stack.sh   (otel-collector + jaeger up)
# Run:      ./scripts/dev/run-otel-toy.sh

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [ ! -x ./gradlew ]; then
    echo "gradlew not found. Run: gradle wrapper --gradle-version=8.10"
    exit 1
fi

export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"

./gradlew --no-daemon :ems-observability:run

echo
echo "Open http://localhost:16686 and look for service ems-otel-toy."
echo "You should see a trace tree:"
echo "  ems-toy-root"
echo "    ├── stage:validate"
echo "    ├── stage:route"
echo "    └── stage:ack"
