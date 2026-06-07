#!/usr/bin/env bash
# Bootstrap local development: install Java 21 + Gradle 8.10 via SDKMAN,
# then generate the Gradle wrapper (gradlew + gradle-wrapper.jar).
#
# Run once after cloning. Safe to re-run — all steps are idempotent.
#
# Usage:
#   ./scripts/dev/bootstrap.sh
#
# Requirements:
#   - curl (for SDKMAN install)
#   - bash 4+, unzip, zip (standard on Linux / macOS)
#
# After this script succeeds, use ./gradlew for all Java builds.
# The gradle-wrapper.jar it generates is in .gitignore — re-run this
# script on a fresh clone instead of committing the binary JAR.

set -eo pipefail

JAVA_CANDIDATE="21.0.7-tem"   # Temurin 21 LTS
GRADLE_VERSION="8.10"

cd "$(git rev-parse --show-toplevel)"

# ── 1. SDKMAN ───────────────────────────────────────────────────────────────

SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"

if [ ! -f "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    echo "==> Installing SDKMAN..."
    curl -s "https://get.sdkman.io" | bash
fi

# shellcheck disable=SC1090
source "$SDKMAN_DIR/bin/sdkman-init.sh"

# ── 2. Java 21 ──────────────────────────────────────────────────────────────

if ! sdk list java 2>/dev/null | grep -q "${JAVA_CANDIDATE}.*installed\|${JAVA_CANDIDATE}.*current"; then
    echo "==> Installing Java ${JAVA_CANDIDATE}..."
    SDKMAN_AUTO_ANSWER=true sdk install java "$JAVA_CANDIDATE"
fi

echo "==> Using Java ${JAVA_CANDIDATE}"
SDKMAN_AUTO_ANSWER=true sdk use java "$JAVA_CANDIDATE"
java -version

# ── 3. Gradle 8.10 ──────────────────────────────────────────────────────────

if ! sdk list gradle 2>/dev/null | grep -q "${GRADLE_VERSION}.*installed\|${GRADLE_VERSION}.*current"; then
    echo "==> Installing Gradle ${GRADLE_VERSION}..."
    SDKMAN_AUTO_ANSWER=true sdk install gradle "$GRADLE_VERSION"
fi

echo "==> Using Gradle ${GRADLE_VERSION}"
SDKMAN_AUTO_ANSWER=true sdk use gradle "$GRADLE_VERSION"
gradle --version | head -3

# ── 4. Generate Gradle wrapper ──────────────────────────────────────────────

echo "==> Generating Gradle wrapper (gradlew + gradle-wrapper.jar)..."
gradle wrapper --gradle-version="$GRADLE_VERSION"
chmod +x gradlew

echo
echo "==> Verifying wrapper..."
./gradlew --version | head -3

echo
echo "Bootstrap complete. Daily workflow:"
echo "  ./gradlew assemble        — build all Java modules"
echo "  ./gradlew allTests        — run all unit tests"
echo "  ./scripts/dev/start-dev-stack.sh  — bring up Docker infra"
echo
echo "To make Java ${JAVA_CANDIDATE} the default for new shells:"
echo "  sdk default java ${JAVA_CANDIDATE}"
