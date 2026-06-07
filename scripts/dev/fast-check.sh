#!/usr/bin/env bash
#
# Fast local check — the inner-loop counterpart to CI's fast lane.
# Compiles and runs unit tests only; skips Spotless and the shell/markdown/
# schema linters. Use this while iterating.
#
# Before opening a PR, run the full gate locally:
#   ./gradlew --no-daemon spotlessApply spotlessCheck assemble allTests
#
# Usage:
#   ./scripts/dev/fast-check.sh             # Java compile + unit tests
#   ./scripts/dev/fast-check.sh --cpp       # also build + ctest the C++ tree
#   ./scripts/dev/fast-check.sh :ems-fsm:test   # pass-through Gradle args

set -euo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

WITH_CPP=0
GRADLE_ARGS=()
for arg in "$@"; do
    if [ "$arg" = "--cpp" ]; then
        WITH_CPP=1
    else
        GRADLE_ARGS+=("$arg")
    fi
done

echo "==> Java: compile + unit tests"
if [ "${#GRADLE_ARGS[@]}" -gt 0 ]; then
    ./gradlew --no-daemon "${GRADLE_ARGS[@]}"
else
    ./gradlew --no-daemon assemble allTests
fi

if [ "$WITH_CPP" -eq 1 ]; then
    echo "==> C++: cmake build + ctest"
    cmake -S cpp -B build/cpp -G Ninja -DCMAKE_CXX_STANDARD=20 >/dev/null
    cmake --build build/cpp
    ctest --test-dir build/cpp --output-on-failure
fi

echo
echo "Fast check passed. Before a PR, also run the linters:"
echo "  ./gradlew --no-daemon spotlessApply spotlessCheck"
