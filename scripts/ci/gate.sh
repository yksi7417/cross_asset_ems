#!/usr/bin/env bash
#
# gate.sh — the single quality gate for the whole repository.
#
#   scripts/ci/gate.sh fast      # compile + unit tests + fmt/lint, all languages
#   scripts/ci/gate.sh full      # fast + sanitizers + conformance + study guide
#   scripts/ci/gate.sh nightly   # full + MSan + Valgrind + Miri + long fuzz
#   scripts/ci/gate.sh <lane> --list    # print the steps, run nothing
#
# Every CI job invokes this script. .githooks/pre-push invokes `fast`. That is
# the point: a CI failure is reproducible locally with one command, and adding a
# language cannot reopen the local/CI divergence gap.
#
# See docs/polyglot/gate.md for the full reference, and
# docs/decisions/0004-defensive-gate-stack.md for why it is shaped this way.
#
# Exit codes: 0 gate passed | 1 a step failed | 2 usage error

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT" || exit 2

# shellcheck source=lib/steps.sh
. "$REPO_ROOT/scripts/ci/lib/steps.sh"

CPP_BUILD_DIR=build/cpp
CPP_ASAN_DIR=build/cpp-asan
CPP_TSAN_DIR=build/cpp-tsan
CPP_MSAN_DIR=build/cpp-msan

# ── lanes ────────────────────────────────────────────────────────────────────

FAST_STEPS=(
    shellcheck
    ci-check-tests
    fsm-sync
    java-build
    java-test
    java-format
    java-nullmarked
    cpp-build
    cpp-test
    rust-format
    rust-lint
    rust-test
    anti-stub
)

FULL_EXTRA_STEPS=(
    schema-lint
    java-coverage
    cpp-asan-ubsan
    cpp-tsan
    rust-deny
    conformance
    fsm-coverage
    study-guide
)

NIGHTLY_EXTRA_STEPS=(
    cpp-msan
    cpp-valgrind
    rust-miri
    fuzz-long
)

lane_steps() {
    case "$1" in
        fast)    printf '%s\n' "${FAST_STEPS[@]}" ;;
        full)    printf '%s\n' "${FAST_STEPS[@]}" "${FULL_EXTRA_STEPS[@]}" ;;
        nightly) printf '%s\n' "${FAST_STEPS[@]}" "${FULL_EXTRA_STEPS[@]}" "${NIGHTLY_EXTRA_STEPS[@]}" ;;
        *)       return 2 ;;
    esac
}

# ── helpers ──────────────────────────────────────────────────────────────────

have_rust_tree() { [ -f rust/Cargo.toml ]; }
have_conformance() { [ -x conformance/harness/run.sh ]; }

gradle() { ./gradlew --no-daemon "$@"; }

shell_scripts() {
    # Hooks have no extension, so they are collected by directory rather than by
    # name. Everything under .githooks/ is bash.
    {
        find scripts -type f \( -name '*.sh' -o -name '*.bash' \) -not -path '*/build/*'
        find .githooks -type f ! -name '*.md' ! -name '.*'
    } 2>/dev/null | sort -u
}

# ── step implementations ─────────────────────────────────────────────────────

do_shellcheck() {
    local scripts=()
    mapfile -t scripts < <(shell_scripts)
    [ "${#scripts[@]}" -eq 0 ] && return 0
    shellcheck "${scripts[@]}"
}

do_ci_check_tests() {
    python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py'
}

do_fsm_sync() {
    # Regenerate from schemas/fsm/*.fsm.yaml and assert nothing moved. A
    # hand-edit to any generated FSM in any language fails here.
    python3 tools/codegen/fsm_codegen.py --java-only >/dev/null || return 1
    python3 tools/codegen/fsm_codegen.py --cpp-only >/dev/null || return 1
    local paths=(java/ems-fsm/src/main/generated/ cpp/fsm/generated/)
    [ -d rust/ems-fsm/src/generated ] && paths+=(rust/ems-fsm/src/generated/)
    git diff --exit-code -- "${paths[@]}"
}

do_schema_lint() {
    local rc=0
    if have_tool yamllint; then
        yamllint -d "{rules: {line-length: {max: 200}, indentation: {spaces: 2}}}" \
            schemas/fsm/ || rc=1
    fi
    if have_tool xmllint; then
        find schemas/sbe -name '*.xml' -print0 \
            | xargs -0 -r -I{} xmllint --noout --schema schemas/sbe/sbe.xsd {} || rc=1
    fi
    return "$rc"
}

do_cpp_configure_build() {
    local dir=$1
    shift
    # A build directory left over from a different generator or a different
    # sanitizer configuration makes cmake refuse to configure. Wipe and retry
    # once rather than making the developer guess.
    if ! cmake -S cpp -B "$dir" -G Ninja -DCMAKE_CXX_STANDARD=20 "$@"; then
        echo "cmake configure failed — wiping $dir and retrying once" >&2
        rm -rf "${dir:?}"
        cmake -S cpp -B "$dir" -G Ninja -DCMAKE_CXX_STANDARD=20 "$@" || return 1
    fi
    cmake --build "$dir"
}

do_cpp_test() {
    local dir=$1
    # --no-tests=error is the whole point: a green ctest over zero tests is
    # exactly the failure mode this repo already demonstrates in cpp/.
    ctest --test-dir "$dir" --output-on-failure --no-tests=error
}

do_anti_stub() {
    python3 scripts/ci/checks/anti_stub.py
}

do_study_guide() {
    python3 scripts/ci/checks/study_guide.py
}

# ── dispatch ─────────────────────────────────────────────────────────────────
#
# Steps whose subject does not exist yet skip with the sub-project that will
# deliver them named in the reason. Under EMS_GATE_STRICT=1 (CI) a skip is a
# failure, so a lane can never silently do nothing.

run_step() {
    case "$1" in
    shellcheck)
        step_needs_tool shellcheck shellcheck 'shellcheck not installed' -- do_shellcheck ;;
    ci-check-tests)
        step_run ci-check-tests do_ci_check_tests ;;
    fsm-sync)
        step_run fsm-sync do_fsm_sync ;;
    java-build)
        step_run java-build gradle assemble ;;
    java-test)
        step_run java-test gradle allTests ;;
    java-format)
        step_run java-format gradle spotlessCheck ;;
    java-coverage)
        step_run java-coverage gradle jacocoRootReport ;;
    java-nullmarked)
        step_run java-nullmarked python3 scripts/ci/checks/nullmarked_ratchet.py ;;
    cpp-build)
        step_needs_tool cpp-build cmake 'cmake not installed' -- \
            do_cpp_configure_build "$CPP_BUILD_DIR" ;;
    cpp-test)
        step_needs_tool cpp-test ctest 'ctest not installed' -- \
            do_cpp_test "$CPP_BUILD_DIR" ;;
    cpp-asan-ubsan)
        step_needs_tool cpp-asan-ubsan cmake 'cmake not installed' -- \
            do_cpp_configure_build "$CPP_ASAN_DIR" \
                -DCMAKE_BUILD_TYPE=RelWithDebInfo \
                -DEMS_SANITIZER=address-undefined ;;
    cpp-tsan)
        step_needs_tool cpp-tsan cmake 'cmake not installed' -- \
            do_cpp_configure_build "$CPP_TSAN_DIR" \
                -DCMAKE_BUILD_TYPE=RelWithDebInfo \
                -DEMS_SANITIZER=thread ;;
    cpp-msan)
        if [ -n "${EMS_MSAN_LIBCXX:-}" ]; then
            step_needs_tool cpp-msan cmake 'cmake not installed' -- \
                do_cpp_configure_build "$CPP_MSAN_DIR" \
                    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
                    -DEMS_SANITIZER=memory
        else
            # Honestly marked as not running rather than silently passing:
            # MSan needs an MSan-instrumented libc++ (EMS_MSAN_LIBCXX=<path>).
            step_skip cpp-msan 'no MSan-instrumented libc++ (set EMS_MSAN_LIBCXX)'
        fi ;;
    cpp-valgrind)
        step_skip cpp-valgrind 'no slice binary yet — sub-project 5' ;;
    rust-format)
        if have_rust_tree; then
            step_needs_tool rust-format cargo 'cargo not installed' -- \
                cargo fmt --manifest-path rust/Cargo.toml --all --check
        else
            step_skip rust-format 'rust/ not present — sub-project 3'
        fi ;;
    rust-lint)
        if have_rust_tree; then
            step_needs_tool rust-lint cargo 'cargo not installed' -- \
                cargo clippy --manifest-path rust/Cargo.toml --all-targets -- -D warnings
        else
            step_skip rust-lint 'rust/ not present — sub-project 3'
        fi ;;
    rust-test)
        if have_rust_tree; then
            step_needs_tool rust-test cargo 'cargo not installed' -- \
                cargo test --manifest-path rust/Cargo.toml --all
        else
            step_skip rust-test 'rust/ not present — sub-project 3'
        fi ;;
    rust-deny)
        if have_rust_tree; then
            step_needs_tool rust-deny cargo-deny 'cargo-deny not installed' -- \
                cargo deny --manifest-path rust/Cargo.toml check
        else
            step_skip rust-deny 'rust/ not present — sub-project 3'
        fi ;;
    rust-miri)
        if have_rust_tree; then
            step_needs_tool rust-miri cargo 'cargo not installed' -- \
                cargo +nightly miri test --manifest-path rust/Cargo.toml
        else
            step_skip rust-miri 'rust/ not present — sub-project 3'
        fi ;;
    anti-stub)
        step_run anti-stub do_anti_stub ;;
    study-guide)
        step_run study-guide do_study_guide ;;
    conformance)
        if have_conformance; then
            step_run conformance conformance/harness/run.sh
        else
            step_skip conformance 'conformance harness not present — sub-project 2'
        fi ;;
    fsm-coverage)
        if [ -f conformance/harness/fsm_coverage.py ]; then
            step_run fsm-coverage python3 conformance/harness/fsm_coverage.py
        else
            step_skip fsm-coverage 'conformance corpus not present — sub-project 2'
        fi ;;
    fuzz-long)
        step_skip fuzz-long 'no fuzz targets yet — sub-projects 4 and 5' ;;
    *)
        printf 'gate.sh: unknown step %s\n' "$1" >&2
        return 2 ;;
    esac
}

# ── main ─────────────────────────────────────────────────────────────────────

usage() {
    cat >&2 <<'EOF'
usage: scripts/ci/gate.sh {fast|full|nightly} [--list]

  fast     compile + unit tests + fmt/lint, all languages   (pre-push, feature CI)
  full     fast + sanitizers + conformance + study guide    (PR CI, main CI)
  nightly  full + MSan + Valgrind + Miri + long fuzz        (scheduled CI)

  --list   print the steps the lane would run, then exit

env:
  EMS_GATE_STRICT=1  a skipped step is a failure (set automatically when CI is set)
  EMS_MSAN_LIBCXX    path to an MSan-instrumented libc++, enables the cpp-msan step

docs: docs/polyglot/gate.md
EOF
    exit 2
}

main() {
    local lane=${1:-}
    case "$lane" in
        fast|full|nightly) shift ;;
        *) usage ;;
    esac

    local steps=()
    mapfile -t steps < <(lane_steps "$lane")

    if [ "${1:-}" = "--list" ]; then
        printf '%s\n' "${steps[@]}"
        return 0
    fi
    [ $# -gt 0 ] && usage

    printf '%sgate: %s lane — %d steps (strict=%s)%s\n\n' \
        "$C_BOLD" "$lane" "${#steps[@]}" "$EMS_GATE_STRICT" "$C_RESET"

    local step
    for step in "${steps[@]}"; do
        run_step "$step"
    done

    steps_summary
}

main "$@"
