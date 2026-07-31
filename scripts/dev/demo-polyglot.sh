#!/usr/bin/env bash
#
# demo-polyglot.sh — see the three implementations agree, and see the gate catch
# them disagreeing.
#
#   scripts/dev/demo-polyglot.sh                    # build, run, compare
#   scripts/dev/demo-polyglot.sh --no-build         # skip the builds (faster)
#   scripts/dev/demo-polyglot.sh --case <name>      # deep-dive a different stage
#
# Six steps, each one printing what it did:
#   1. build ems-slice in Java, Rust and C++
#   2. show the input journal
#   3. run all three over it
#   4. prove the three outputs are byte-identical
#   5. introduce a divergence on purpose and watch the differ report it
#   6. walk every stage the slice has reached, in order
#
# Step 6 reads the corpus directory rather than a list kept in this file, so a
# new component's case appears in the demo the moment it lands. A hand-written
# stage list is one of the things docs/polyglot/traps.md exists to record.
#
# See docs/polyglot/README.md.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT" || exit 2

BUILD=1
CASE_NAME=component-01-journal-and-ids

while [ $# -gt 0 ]; do
    case "$1" in
        --no-build) BUILD=0; shift ;;
        --case)     CASE_NAME="${2:?--case needs a corpus case name}"; shift 2 ;;
        *)          echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

CASE_DIR="conformance/corpus/$CASE_NAME"
INPUT="$CASE_DIR/input.jsonl"

if [ ! -f "$INPUT" ]; then
    echo "no such corpus case: $CASE_NAME" >&2
    echo "available:" >&2
    ls conformance/corpus >&2
    exit 2
fi

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
    step "1/6  Building ems-slice in three languages"
    echo "Java  → $JAVA_BIN"
    ./gradlew --no-daemon :ems-it:installDist -q || exit 1
    echo "Rust  → $RUST_BIN"
    cargo build --manifest-path rust/Cargo.toml --release -q || exit 1
    echo "C++   → $CPP_BIN"
    cmake -S cpp -B build/cpp -G Ninja -DCMAKE_CXX_STANDARD=20 >/dev/null || exit 1
    cmake --build build/cpp >/dev/null || exit 1
    echo "${C_GREEN}all three built${C_RESET}"
else
    step "1/6  Skipping builds (--no-build)"
fi

for binary in "$JAVA_BIN" "$RUST_BIN" "$CPP_BIN"; do
    if [ ! -x "$binary" ]; then
        echo "${C_RED}missing: $binary${C_RESET} — run without --no-build" >&2
        exit 1
    fi
done

# ── 2. the input ─────────────────────────────────────────────────────────────

step "2/6  The input journal"
echo "${C_DIM}$INPUT${C_RESET}"
echo
cat "$INPUT"
echo
printf '%s events. What this case is for:\n\n' "$(grep -c '' "$INPUT")"
# From the case's own case.md, so this cannot drift from what the case does.
sed -n 's/^\*\*Covers:\*\* //p; s/^\*\*Why it exists:\*\* //p' "$CASE_DIR/case.md" | head -4

# ── 3. run all three ─────────────────────────────────────────────────────────

step "3/6  Running each implementation"
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

step "4/6  Are they byte-identical?"
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

step "5/6  Now break it on purpose"
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

# ── 6. every stage ───────────────────────────────────────────────────────────

step "6/6  Every stage the slice has reached"

echo "One row per corpus case, in order. Each was added by the component that"
echo "made it possible, and each is checked in all three languages."
echo

stages_ok=1
for case_dir in conformance/corpus/*/; do
    name=$(basename "$case_dir")
    [ -f "$case_dir/input.jsonl" ] || continue

    seed=0
    [ -f "$case_dir/seed" ] && seed=$(tr -d '[:space:]' < "$case_dir/seed")

    ok=1
    for impl in java rust cpp; do
        case "$impl" in
            java) binary=$JAVA_BIN ;;
            rust) binary=$RUST_BIN ;;
            cpp)  binary=$CPP_BIN ;;
            *)    continue ;;
        esac
        if ! "$binary" --input "$case_dir/input.jsonl" \
                       --output "$WORK_DIR/$name.$impl.jsonl" --seed "$seed" >/dev/null; then
            ok=0
        fi
    done
    if [ "$ok" = "1" ]; then
        for impl in rust cpp; do
            cmp -s "$WORK_DIR/$name.java.jsonl" "$WORK_DIR/$name.$impl.jsonl" || ok=0
        done
        # The corpus's committed expectation is the third party: agreeing with
        # each other but not with the recorded output would be three implementations
        # that changed together.
        cmp -s "$WORK_DIR/$name.java.jsonl" "$case_dir/expected.jsonl" || ok=0
    fi

    if [ "$ok" = "1" ]; then
        printf '  %s✔%s %-32s %s events → %s events\n' "$C_GREEN" "$C_RESET" "$name" \
            "$(grep -c '' "$case_dir/input.jsonl")" "$(grep -c '' "$case_dir/expected.jsonl")"
    else
        printf '  %s✘%s %-32s DIVERGED\n' "$C_RED" "$C_RESET" "$name"
        stages_ok=0
    fi
done

echo
if [ "$stages_ok" = "1" ]; then
    echo "${C_GREEN}Every stage agrees, three ways, against its committed expectation.${C_RESET}"
else
    echo "${C_RED}A stage diverged — a real failure, not part of the demo.${C_RESET}"
    exit 1
fi

echo
echo "Deep-dive any one of them:"
echo "    scripts/dev/demo-polyglot.sh --no-build --case <name>"

# ── what next ────────────────────────────────────────────────────────────────

step "What this does and does not prove"
cat <<'EOF'
Proved: three independent implementations agree, byte for byte, on everything
the slice does so far — the journal codec, deterministic identifiers, the
transport seam, the AAA entitlement gate, the layered validation pipeline, the
order FSM (all 31 transitions), route creation, and the route venue lifecycle
(all 29 transitions) including the cascade from a route event to the parent
order.

NOT proved: anything about the venue edge or allocation. Nothing here speaks
FIX — route events arrive as journal entries rather than from a session — and
nothing allocates a fill to an account. Those are components 7 and 8, landing
one at a time and verified the same way.

Also not proved: that Java is right. Java generates each case's expected.jsonl,
so a Java bug faithfully reproduced by both ports passes. That is recorded as
T-3 in docs/TODO.md, with the triangulation that would fix it.

Run the real gate, which does all of the above plus lint, sanitizers and the
study-guide check:

    scripts/ci/gate.sh full

Run just the conformance step:

    conformance/harness/run.sh

Read the plan:

    docs/polyglot/README.md
EOF
