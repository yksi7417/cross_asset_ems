#!/usr/bin/env bash
# Bootstrap local development: install Java 21.
#
# Run once after cloning. Safe to re-run — all steps are idempotent.
#
# Platform support:
#   Fedora / RHEL / CentOS  — uses dnf
#   Ubuntu / Debian         — uses apt
#   macOS                   — uses Homebrew
#
# Usage:
#   ./scripts/dev/bootstrap.sh
#
# The Gradle wrapper (gradlew + gradle-wrapper.jar) is committed to the repo,
# so ./gradlew works immediately after clone — this script only ensures a
# compatible JDK is present.

set -eo pipefail

REQUIRED_JAVA_MAJOR="21"

cd "$(git rev-parse --show-toplevel)"

# ── helpers ──────────────────────────────────────────────────────────────────

die() { echo "ERROR: $*" >&2; exit 1; }

java_major() {
    # Extracts major version from `java -version` output for both old (1.8)
    # and new (17, 21, 25) version strings.
    local ver
    ver=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    # Strip leading "1." for Java 8 and older
    ver="${ver#1.}"
    echo "${ver%%.*}"
}

# ── 1. Java 21 ───────────────────────────────────────────────────────────────

# Check if an appropriate Java is already on PATH
if command -v java &>/dev/null && [ "$(java_major)" -eq "$REQUIRED_JAVA_MAJOR" ]; then
    echo "==> Java ${REQUIRED_JAVA_MAJOR} already active ($(java -version 2>&1 | head -1))"

# Check SDKMAN candidates (may be installed but not on PATH in this shell).
# Use a glob expansion into an array — `[ -x <glob> ]` does not work.
elif SDKMAN_JAVA=$(compgen -G "${HOME}/.sdkman/candidates/java/${REQUIRED_JAVA_MAJOR}.*/bin/java" 2>/dev/null | head -1) \
     && [ -n "$SDKMAN_JAVA" ]; then
    SDKMAN_JAVA="${SDKMAN_JAVA%/bin/java}"
    echo "==> Found SDKMAN Java at ${SDKMAN_JAVA}"
    export JAVA_HOME="${SDKMAN_JAVA}"
    export PATH="${JAVA_HOME}/bin:${PATH}"

else
    echo "==> Installing Java ${REQUIRED_JAVA_MAJOR}..."

    if command -v dnf &>/dev/null; then
        # Fedora / RHEL / CentOS
        sudo dnf install -y "java-${REQUIRED_JAVA_MAJOR}-openjdk-devel"
        # Point the session at Java 21 without permanently changing the system default
        JAVA_HOME="/usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-openjdk"
        export JAVA_HOME
        export PATH="${JAVA_HOME}/bin:${PATH}"

    elif command -v apt-get &>/dev/null; then
        # Ubuntu / Debian — prefer Eclipse Temurin if repo is configured, else OpenJDK
        if apt-cache show "temurin-${REQUIRED_JAVA_MAJOR}-jdk" &>/dev/null; then
            sudo apt-get install -y "temurin-${REQUIRED_JAVA_MAJOR}-jdk"
        else
            sudo apt-get install -y "openjdk-${REQUIRED_JAVA_MAJOR}-jdk"
        fi
        JAVA_HOME="/usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-openjdk-$(dpkg --print-architecture)"
        export JAVA_HOME
        export PATH="${JAVA_HOME}/bin:${PATH}"

    elif command -v brew &>/dev/null; then
        # macOS
        brew install --cask "temurin@${REQUIRED_JAVA_MAJOR}"
        # Homebrew Temurin cask sets JAVA_HOME via /usr/libexec/java_home
        JAVA_HOME="$(/usr/libexec/java_home -v "${REQUIRED_JAVA_MAJOR}" 2>/dev/null)"
        export JAVA_HOME
        export PATH="${JAVA_HOME}/bin:${PATH}"

    else
        die "No supported package manager found (dnf, apt-get, brew). Install Java ${REQUIRED_JAVA_MAJOR} manually, set JAVA_HOME, and re-run."
    fi
fi

java -version
[ "$(java_major)" -eq "$REQUIRED_JAVA_MAJOR" ] \
    || die "Expected Java ${REQUIRED_JAVA_MAJOR} but got $(java_major). Set JAVA_HOME and retry."

# ── 2. Verify the committed Gradle wrapper runs ──────────────────────────────
#
# gradlew + gradle/wrapper/gradle-wrapper.jar are committed, so this just
# confirms the toolchain is wired up (and warms the wrapper distribution cache).

echo "==> Verifying Gradle wrapper..."
./gradlew --version | head -3

echo
echo "Bootstrap complete. Daily workflow:"
echo "  ./gradlew assemble          — build all Java modules"
echo "  ./gradlew allTests          — run all unit tests"
echo "  ./scripts/dev/start-dev-stack.sh  — bring up Docker infra"
echo
echo "NOTE: Java ${REQUIRED_JAVA_MAJOR} is active for this shell session."
echo "To make it the system default on Fedora/RHEL:"
echo "  sudo alternatives --set java /usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-openjdk/bin/java"
echo "  sudo alternatives --set javac /usr/lib/jvm/java-${REQUIRED_JAVA_MAJOR}-openjdk/bin/javac"
