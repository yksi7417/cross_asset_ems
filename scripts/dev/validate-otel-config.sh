#!/usr/bin/env bash
#
# Validate the OTel collector config WITHOUT bringing the dev stack up/down.
#
# Runs the collector's built-in `validate` subcommand against the config in a
# throwaway container. Catches schema errors (wrong keys, renamed fields across
# collector versions) in ~2s instead of crash-looping the live container.
#
# Usage:
#   ./scripts/dev/validate-otel-config.sh                 # validates infra/otel-collector/config.yaml
#   ./scripts/dev/validate-otel-config.sh path/to/cfg.yaml
#
# Exit 0 = valid, non-zero = invalid (error printed).

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

CONFIG="${1:-infra/otel-collector/config.yaml}"

if [ ! -f "$CONFIG" ]; then
    echo "ERROR: config not found: $CONFIG" >&2
    exit 1
fi

# Keep the image tag in sync with the otel-collector service in
# infra/docker-compose/compose.dev.yaml.
IMAGE="docker.io/otel/opentelemetry-collector-contrib:0.115.1"

# --security-opt label=disable: SELinux relabel bypass for rootless Podman on
# Fedora/RHEL (harmless on Docker / non-SELinux hosts). See docs/runbooks/podman.md.
echo "Validating $CONFIG against ${IMAGE##*/}..."
if docker run --rm --security-opt label=disable \
    -v "$(pwd)/${CONFIG}:/etc/otelcol/validate.yaml:ro" \
    "$IMAGE" \
    validate --config=/etc/otelcol/validate.yaml; then
    echo "OK: $CONFIG is valid."
else
    echo "FAILED: $CONFIG has errors (see above)." >&2
    exit 1
fi
