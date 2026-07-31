#!/usr/bin/env bash
#
# demo-polyglot.sh — see the three implementations agree, and see the gate catch
# them disagreeing.
#
#   scripts/dev/demo-polyglot.sh              # build, run, compare
#   scripts/dev/demo-polyglot.sh --no-build   # skip the builds (faster re-runs)
#
# Five steps, each one printing what it did:
#   1. build ems-slice in Java, Rust and C++
#   2. show the input journal
#   3. run all three over it
#   4. prove the three outputs are byte-identical
#   5. introduce a divergence on purpose and watch the differ report it
#
# See docs/polyglot/README.md.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT" || exit 2

BUILD=1
[ "${1:-}" = "--no-build" ] && BUILD=0

CASE_DIR=conformance/corpus/component-01-journal-and-ids
INPUT="$CASE_DIR/input.jsonl"

JAVA_BIN=java/ems-it/build/install/ems-slice/bin/ems-slice
RUST_BIN=rust/target/release/ems-slice
CPP_BIN=build/cpp/ems-it/ems-slice

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
    C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_CYAN=$'\033[36m'
else
    C_RESET=''; C_BOLD=''; C_DIM=''; C_GREEN=''; C_RED=''; C_CYAN=''
fi

step() {
    printf '\n%s── %s ──────────────────────────────────────────%s\n\n' \
        "$C_BOLD$C_CYAN" "$1" "$C_RESET"
}

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

# ── 1. build ─────────────────────────────────────────────────────────────────

if [ "$BUILD" = "1" ]; then
    step "1/5  Building ems-slice in three languages"
    echo "Java  → $JAVA_BIN"
    ./gradlew --no-daemon :ems-it:installDist -q || exit 1
    echo "Rust  → $RUST_BIN"
    cargo build --manifest-path rust/Cargo.toml --release -q || exit 1
    echo "C++   → $CPP_BIN"
    cmake -S cpp -B build/cpp -G Ninja -DCMAKE_CXX_STANDARD=20 >/dev/null || exit 1
    cmake --build build/cpp >/dev/null || exit 1
    echo "${C_GREEN}all three built${C_RESET}"
else
    step "1/5  Skipping builds (--no-build)"
fi

for binary in "$JAVA_BIN" "$RUST_BIN" "$CPP_BIN"; do
    if [ ! -x "$binary" ]; then
        echo "${C_RED}missing: $binary${C_RESET} — run without --no-build" >&2
        exit 1
    fi
done

# ── 2. the input ─────────────────────────────────────────────────────────────

step "2/5  The input journal"
echo "${C_DIM}$INPUT${C_RESET}"
echo
cat "$INPUT"
echo
echo "Five events. Two of them exercise the parts three independent JSON writers"
echo "are most likely to disagree on: an 'ignored' field that must NOT be echoed,"
echo "and a value with UTF-8, an escaped quote and an escaped backslash."

# ── 3. run all three ─────────────────────────────────────────────────────────

step "3/5  Running each implementation"
for impl in java rust cpp; do
    case "$impl" in
        java) binary=$JAVA_BIN ;;
        rust) binary=$RUST_BIN ;;
        cpp)  binary=$CPP_BIN ;;
        *)    continue ;;
    esac
    printf '%-6s %s --input %s --output %s\n' \
        "$impl" "$binary" "$INPUT" "$WORK_DIR/$impl.jsonl"
    "$binary" --input "$INPUT" --output "$WORK_DIR/$impl.jsonl" || exit 1
done

echo
echo "${C_DIM}Java's output (the reference):${C_RESET}"
echo
cat "$WORK_DIR/java.jsonl"

# ── 4. compare ───────────────────────────────────────────────────────────────

step "4/5  Are they byte-identical?"
echo "${C_DIM}sha256 of each output:${C_RESET}"
sha256sum "$WORK_DIR/java.jsonl" "$WORK_DIR/rust.jsonl" "$WORK_DIR/cpp.jsonl" \
    | sed "s|$WORK_DIR/||"
echo

identical=1
for impl in rust cpp; do
    if ! python3 conformance/harness/differ.py "$WORK_DIR/java.jsonl" "$WORK_DIR/$impl.jsonl"; then
        identical=0
    fi
done

if [ "$identical" = "1" ]; then
    echo "${C_GREEN}All three produced the same bytes.${C_RESET}"
    echo "That is the claim the whole project rests on, checked at the byte level."
else
    echo "${C_RED}Divergence — this is a real failure, not part of the demo.${C_RESET}"
    exit 1
fi

# ── 5. break it on purpose ───────────────────────────────────────────────────

step "5/5  Now break it on purpose"
echo "Re-running Rust with --seed 1 instead of 0. Nothing else changes — the"
echo "generated order ids shift by one, and that is enough."
echo
"$RUST_BIN" --input "$INPUT" --output "$WORK_DIR/rust-wrong.jsonl" --seed 1 || exit 1

if python3 conformance/harness/differ.py "$WORK_DIR/java.jsonl" "$WORK_DIR/rust-wrong.jsonl"; then
    echo "${C_RED}The differ did NOT catch it. That is a bug in the differ.${C_RESET}"
    exit 1
fi

echo
echo "${C_GREEN}Caught, with the line number and both versions of the line.${C_RESET}"
echo "The differ compares raw bytes — it does not normalise line endings,"
echo "trailing whitespace or key order, because that would hide exactly this."

# ── what next ────────────────────────────────────────────────────────────────

step "What this does and does not prove"
cat <<'EOF'
Proved: three independent implementations of the journal codec, deterministic
identifier generation and the transport seam produce identical bytes.

NOT proved: anything about the order path. There is no validation, no FSM, no
routing and no venue yet — ems-slice turns an OrderNew into an OrderAccepted and
passes everything else through. Those components land next, one at a time, each
verified the same way.

Run the real gate, which does all of the above plus lint, sanitizers and the
study-guide check:

    scripts/ci/gate.sh full

Run just the conformance step:

    conformance/harness/run.sh

Read the plan:

    docs/polyglot/README.md
EOF
