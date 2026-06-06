#!/usr/bin/env bash
#
# Re-run SBE + FSM codegen across the workspace.
#
# Equivalent to: ./gradlew :ems-transport:sbeCodegen :ems-fsm:fsmCodegen
# Useful when iterating on schemas without running the full build.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [ ! -x ./gradlew ]; then
    echo "gradlew not found. Run: gradle wrapper --gradle-version=8.10"
    exit 1
fi

echo "Regenerating SBE bindings..."
./gradlew --no-daemon :ems-transport:sbeCodegen

echo "Regenerating FSM bindings..."
./gradlew --no-daemon :ems-fsm:fsmCodegen 2>/dev/null || \
    echo "  (fsm codegen task lands in task 1.7; skipping)"

echo
echo "Done. Diff:"
git -c color.diff=always diff --stat -- '**/generated/**' || true
