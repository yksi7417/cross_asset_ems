#!/usr/bin/env bash
#
# Runs every corpus case against every implementation and diffs the output
# against expected.jsonl byte-for-byte.
#
#   conformance/harness/run.sh                 # every case, every implementation
#   conformance/harness/run.sh --impl rust     # one implementation
#   conformance/harness/run.sh --case happy-path-new-order-to-fill
#   conformance/harness/run.sh --list          # what would run
#
# Invoked by scripts/ci/gate.sh; runnable directly while iterating.
#
# An implementation whose binary is absent is reported as NOT RUN, never
# silently passed over — and if NO implementation is found at all, that is a
# failure. An empty conformance run that exits 0 is the same class of lie as a
# green ctest over zero tests.
#
# See conformance/README.md.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT" || exit 2

CORPUS_DIR=conformance/corpus
DIFFER=conformance/harness/differ.py
TRIANGULATE=conformance/harness/triangulate.py

# Implementation name → binary path. Kept in this order so failures read
# reference-first.
IMPL_NAMES=(java rust cpp)
IMPL_PATHS=(
    "java/ems-it/build/install/ems-slice/bin/ems-slice"
    "rust/target/release/ems-slice"
    "build/cpp/ems-it/ems-slice"
)
IMPL_BUILD_HINTS=(
    "./gradlew :ems-it:installDist"
    "cargo build --manifest-path rust/Cargo.toml --release"
    "cmake --build build/cpp"
)

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_RESET=$'\033[0m'; C_RED=$'\033[31m'; C_GREEN=$'\033[32m'
    C_YELLOW=$'\033[33m'; C_DIM=$'\033[2m'; C_BOLD=$'\033[1m'
else
    C_RESET=''; C_RED=''; C_GREEN=''; C_YELLOW=''; C_DIM=''; C_BOLD=''
fi

usage() {
    cat >&2 <<'EOF'
usage: conformance/harness/run.sh [--impl <java|rust|cpp>] [--case <name>] [--list]

  --impl  restrict to one implementation (repeatable)
  --case  restrict to one corpus case (repeatable)
  --list  print the cases and implementations that would run, then exit

docs: conformance/README.md
EOF
    exit 2
}

WANT_IMPLS=()
WANT_CASES=()
LIST_ONLY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --impl) [ $# -ge 2 ] || usage; WANT_IMPLS+=("$2"); shift 2 ;;
        --case) [ $# -ge 2 ] || usage; WANT_CASES+=("$2"); shift 2 ;;
        --list) LIST_ONLY=1; shift ;;
        -h|--help) usage ;;
        *) usage ;;
    esac
done

# An empty filter means "everything". Under `set -u` an empty array expands to
# a single empty string, so that case has to be recognised explicitly —
# otherwise the filter silently matches nothing and the harness reports an
# empty, green run.
wanted() {
    local needle=$1
    shift
    if [ $# -eq 0 ] || { [ $# -eq 1 ] && [ -z "$1" ]; }; then
        return 0
    fi
    local item
    for item in "$@"; do
        [ "$item" = "$needle" ] && return 0
    done
    return 1
}

# ── discover cases ───────────────────────────────────────────────────────────

CASES=()
if [ -d "$CORPUS_DIR" ]; then
    while IFS= read -r dir; do
        CASES+=("$(basename "$dir")")
    done < <(find "$CORPUS_DIR" -mindepth 1 -maxdepth 1 -type d | sort)
fi

SELECTED_CASES=()
for case_name in "${CASES[@]:-}"; do
    [ -z "$case_name" ] && continue
    if wanted "$case_name" "${WANT_CASES[@]:-}"; then
        SELECTED_CASES+=("$case_name")
    fi
done

if [ "${#SELECTED_CASES[@]}" -eq 0 ]; then
    echo "conformance: no corpus cases found under $CORPUS_DIR" >&2
    exit 1
fi

# ── discover implementations ─────────────────────────────────────────────────

FOUND_NAMES=()
FOUND_PATHS=()
MISSING=()

for i in "${!IMPL_NAMES[@]}"; do
    name=${IMPL_NAMES[$i]}
    wanted "$name" "${WANT_IMPLS[@]:-}" || continue
    if [ -x "${IMPL_PATHS[$i]}" ]; then
        FOUND_NAMES+=("$name")
        FOUND_PATHS+=("${IMPL_PATHS[$i]}")
    else
        MISSING+=("$name (${IMPL_PATHS[$i]}) — build with: ${IMPL_BUILD_HINTS[$i]}")
    fi
done

if [ "$LIST_ONLY" = "1" ]; then
    printf 'cases (%d):\n' "${#SELECTED_CASES[@]}"
    printf '  %s\n' "${SELECTED_CASES[@]}"
    printf 'implementations found (%d):\n' "${#FOUND_NAMES[@]}"
    [ "${#FOUND_NAMES[@]}" -gt 0 ] && printf '  %s\n' "${FOUND_NAMES[@]}"
    if [ "${#MISSING[@]}" -gt 0 ]; then
        printf 'implementations NOT built (%d):\n' "${#MISSING[@]}"
        printf '  %s\n' "${MISSING[@]}"
    fi
    exit 0
fi

if [ "${#FOUND_NAMES[@]}" -eq 0 ]; then
    echo "conformance: no implementation binary found. Looked for:" >&2
    printf '  %s\n' "${MISSING[@]}" >&2
    exit 1
fi

# ── run ──────────────────────────────────────────────────────────────────────

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

failures=0
runs=0

printf '%sconformance: %d case(s) × %d implementation(s)%s\n\n' \
    "$C_BOLD" "${#SELECTED_CASES[@]}" "${#FOUND_NAMES[@]}" "$C_RESET"

for case_name in "${SELECTED_CASES[@]}"; do
    case_dir="$CORPUS_DIR/$case_name"
    input="$case_dir/input.jsonl"
    expected="$case_dir/expected.jsonl"
    seed_file="$case_dir/seed"
    seed=0
    [ -f "$seed_file" ] && seed=$(tr -d '[:space:]' < "$seed_file")

    if [ ! -f "$input" ] || [ ! -f "$expected" ]; then
        printf '%s✘ %s%s missing input.jsonl or expected.jsonl\n' \
            "$C_RED" "$case_name" "$C_RESET"
        failures=$((failures + 1))
        continue
    fi
    if [ ! -f "$case_dir/case.md" ]; then
        # A case with no explanation cannot be reviewed, and an unreviewable
        # expectation is how a bug becomes a specification.
        printf '%s✘ %s%s missing case.md\n' "$C_RED" "$case_name" "$C_RESET"
        failures=$((failures + 1))
        continue
    fi

    # Outputs that both ran and matched, as name=path pairs for triangulation.
    triangle_args=()

    for i in "${!FOUND_NAMES[@]}"; do
        impl=${FOUND_NAMES[$i]}
        binary=${FOUND_PATHS[$i]}
        actual="$WORK_DIR/$case_name.$impl.jsonl"
        runs=$((runs + 1))

        if ! "$binary" --input "$input" --output "$actual" --seed "$seed" >/dev/null; then
            printf '%s✘ %-40s %-5s%s exited non-zero\n' \
                "$C_RED" "$case_name" "$impl" "$C_RESET"
            failures=$((failures + 1))
            continue
        fi

        triangle_args+=("$impl=$actual")

        if python3 "$DIFFER" "$expected" "$actual"; then
            printf '%s✔%s %-40s %s%s%s\n' \
                "$C_GREEN" "$C_RESET" "$case_name" "$C_DIM" "$impl" "$C_RESET"
        else
            printf '%s✘ %-40s %-5s byte mismatch%s\n' \
                "$C_RED" "$case_name" "$impl" "$C_RESET"
            failures=$((failures + 1))
        fi
    done

    # Triangulate whenever two or more implementations produced output. The
    # per-implementation diff above privileges the committed expectation; this
    # asks the symmetric question — do they agree with EACH OTHER — and a
    # divergence is reported as a named 2-1 split rather than "rust != java"
    # (ADR 0009 / T-3). Redundant when every diff above already passed, decisive
    # when it did not: it says which implementation to go and read.
    if [ "${#triangle_args[@]}" -ge 2 ]; then
        if ! python3 "$TRIANGULATE" --expected "$expected" "${triangle_args[@]}"; then
            printf '%s✘ %-40s %-5s not unanimous%s\n' \
                "$C_RED" "$case_name" "3-way" "$C_RESET"
            failures=$((failures + 1))
        fi
    fi
done

echo
if [ "${#MISSING[@]}" -gt 0 ]; then
    printf '%snot run (%d):%s\n' "$C_YELLOW" "${#MISSING[@]}" "$C_RESET"
    printf '  %s\n' "${MISSING[@]}"
fi

if [ "$failures" -gt 0 ]; then
    printf '%sconformance FAILED — %d of %d run(s)%s\n' \
        "$C_RED" "$failures" "$runs" "$C_RESET"
    exit 1
fi

printf '%sconformance passed — %d run(s)%s\n' "$C_GREEN" "$runs" "$C_RESET"
exit 0
