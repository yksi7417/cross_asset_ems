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

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib/steps.sh
. "$REPO_ROOT/scripts/ci/lib/steps.sh"

CPP_BUILD_DIR=build/cpp
CPP_ASAN_DIR=build/cpp-asan
CPP_TSAN_DIR=build/cpp-tsan
CPP_MSAN_DIR=build/cpp-msan

# ── lanes ────────────────────────────────────────────────────────────────────

FAST_STEPS=(
    exec-bits
    shellcheck
    ci-check-tests
    fsm-sync
    java-build
    java-test
    java-format
    java-nullmarked
    java-clock
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

gradle() {
    local log rc
    log=$(mktemp)
    ./gradlew --no-daemon "$@" 2>&1 | tee "$log"
    rc=${PIPESTATUS[0]}

    # Spotless caches formatter state in .gradle/configuration-cache and fails
    # outright when that cache is stale — a local-only failure with a mechanical
    # fix (diffplug/spotless#987). Clearing and retrying once beats making every
    # developer learn the incantation.
    if [ "$rc" -ne 0 ] && grep -q "Spotless JVM-local cache is stale" "$log"; then
        echo "gate: stale Spotless configuration cache — clearing and retrying once" >&2
        rm -rf .gradle/configuration-cache
        ./gradlew --no-daemon "$@" 2>&1 | tee "$log"
        rc=${PIPESTATUS[0]}
    fi

    rm -f "$log"
    return "$rc"
}

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

    # -x follows `source`d files, so sourcing scripts/ci/lib/steps.sh is checked
    # rather than reported as SC1091.
    if have_tool shellcheck; then
        shellcheck -x "${scripts[@]}"
    elif have_tool podman; then
        podman run --rm --security-opt label=disable -v "$(pwd):/mnt:ro" -w /mnt \
            docker.io/koalaman/shellcheck:stable -x "${scripts[@]}"
    else
        docker run --rm -v "$(pwd):/mnt:ro" -w /mnt \
            docker.io/koalaman/shellcheck:stable -x "${scripts[@]}"
    fi
}

have_shellcheck() {
    have_tool shellcheck || have_tool podman || have_tool docker
}

do_exec_bits() {
    # A script committed without its executable bit fails only in CI, where
    # nothing has already chmod'd it locally. Caught here instead.
    #
    # Files under a lib/ directory are exempt: they are sourced, not run, and
    # marking them executable would advertise an entry point they do not have.
    local offenders
    offenders=$(git ls-files -s scripts .githooks conformance 2>/dev/null \
        | awk '$1 != "100755" && $4 !~ /\/lib\// && ($4 ~ /\.sh$/ || $4 ~ /^\.githooks\//) { print $4 }')
    if [ -n "$offenders" ]; then
        echo "not executable (git mode is not 100755):" >&2
        echo "$offenders" >&2
        echo "fix with: git update-index --chmod=+x <path>" >&2
        return 1
    fi
    return 0
}

do_ci_check_tests() {
    python3 -m unittest discover -s scripts/ci/checks -p 'test_*.py' || return 1
    if [ -d conformance/harness ]; then
        python3 -m unittest discover -s conformance/harness -p 'test_*.py' || return 1
    fi
    return 0
}

do_fsm_sync() {
    # Regenerate from schemas/fsm/*.fsm.yaml and assert nothing moved. A
    # hand-edit to any generated FSM in any language fails here.
    python3 tools/codegen/fsm_codegen.py --java-only >/dev/null || return 1
    python3 tools/codegen/fsm_codegen.py --cpp-only >/dev/null || return 1
    python3 tools/codegen/fsm_codegen.py --rust-only >/dev/null || return 1
    local paths=(
        java/ems-fsm/src/main/generated/
        cpp/fsm/generated/
        rust/ems-fsm/src/generated/
    )

    # `git diff` only sees TRACKED files, so a newly emitted file that has never
    # been committed would pass silently — which is exactly what happens the
    # first time a language is added. `git status --porcelain` covers untracked
    # too, and is what makes "the tree matches the schemas" actually true.
    local untracked
    untracked=$(git ls-files --others --exclude-standard -- "${paths[@]}")
    if [ -n "$untracked" ]; then
        echo "fsm-sync: generated files are not committed:" >&2
        echo "$untracked" >&2
        return 1
    fi

    git diff --exit-code -- "${paths[@]}"
}

do_schema_lint() {
    local rc=0

    if have_tool yamllint; then
        yamllint -d "{rules: {line-length: {max: 200}, indentation: {spaces: 2}}}" \
            schemas/fsm/ || rc=1
    else
        echo "schema-lint: yamllint not installed — FSM YAML not linted" >&2
        rc=1
    fi

    if have_tool xmllint; then
        # Well-formedness always. This is blocking and always has been checkable.
        find schemas/sbe -name '*.xml' -print0 \
            | xargs -0 -r -I{} xmllint --noout {} || rc=1

        # Validation against the SBE XSD only when the schema is present.
        # schemas/sbe/sbe.xsd is not vendored in this repo, so this half is
        # currently not running — said out loud rather than passed over. The
        # previous CI job hid the same gap behind `|| true`.
        if [ -f schemas/sbe/sbe.xsd ]; then
            find schemas/sbe -name '*.xml' -print0 \
                | xargs -0 -r -I{} xmllint --noout --schema schemas/sbe/sbe.xsd {} || rc=1
        else
            echo "schema-lint: schemas/sbe/sbe.xsd absent — XML checked for" \
                 "well-formedness only, NOT validated against the SBE schema" >&2
        fi
    else
        echo "schema-lint: xmllint not installed — SBE XML not checked" >&2
        rc=1
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

# sanitizer_available FLAGS...
# True when the toolchain can actually link a program with these flags. A
# distro can ship the compiler without the runtime (libasan/libtsan), in which
# case the failure is a missing dependency, not a defect in the code — so it
# must be reported as a missing-tool skip, which CI's strict mode still fails on.
sanitizer_available() {
    local probe status
    probe=$(mktemp -d)
    printf 'int main() { return 0; }\n' > "$probe/probe.cpp"
    if ${CXX:-c++} "$@" "$probe/probe.cpp" -o "$probe/probe" >/dev/null 2>&1; then
        status=0
    else
        status=1
    fi
    rm -rf "$probe"
    return "$status"
}

# Build under a sanitizer AND run the tests under it. Building alone proves the
# flags compile, which is not what a sanitizer is for.
do_cpp_sanitize() {
    local dir=$1
    shift
    do_cpp_configure_build "$dir" "$@" || return 1
    ctest --test-dir "$dir" --output-on-failure --no-tests=error
}

do_cpp_test() {
    local dir=$1
    # --no-tests=error is the whole point: a green ctest over zero tests is
    # exactly the failure mode this repo already demonstrates in cpp/.
    ctest --test-dir "$dir" --output-on-failure --no-tests=error
}

# The conformance harness runs three binaries. Build whichever ones this
# checkout can build — the harness itself reports any it could not find, and
# fails outright if none exist.
do_conformance() {
    gradle :ems-it:installDist || return 1
    if have_tool cmake; then
        do_cpp_configure_build "$CPP_BUILD_DIR" || return 1
    fi
    if have_rust_tree && have_tool cargo; then
        cargo build --manifest-path rust/Cargo.toml --release || return 1
    fi
    conformance/harness/run.sh
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
# deliver them named in the reason — that skip is honest and is never a
# failure. A step skipped because a TOOL is missing is different: tolerable on a
# laptop, unacceptable in CI, so EMS_GATE_STRICT=1 turns it into a failure.

run_step() {
    case "$1" in
    exec-bits)
        step_run exec-bits do_exec_bits ;;
    shellcheck)
        if have_shellcheck; then
            step_run shellcheck do_shellcheck
        else
            step_skip_tool shellcheck 'no shellcheck, podman or docker'
        fi ;;
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
    schema-lint)
        step_run schema-lint do_schema_lint ;;
    java-nullmarked)
        step_run java-nullmarked python3 scripts/ci/checks/nullmarked_ratchet.py ;;
    java-clock)
        step_run java-clock python3 scripts/ci/checks/no_raw_clock.py ;;
    cpp-build)
        step_needs_tool cpp-build cmake 'cmake not installed' -- \
            do_cpp_configure_build "$CPP_BUILD_DIR" ;;
    cpp-test)
        step_needs_tool cpp-test ctest 'ctest not installed' -- \
            do_cpp_test "$CPP_BUILD_DIR" ;;
    cpp-asan-ubsan)
        if ! have_tool cmake; then
            step_skip_tool cpp-asan-ubsan 'cmake not installed'
        elif ! sanitizer_available -fsanitize=address,undefined; then
            step_skip_tool cpp-asan-ubsan 'no ASan/UBSan runtime (install libasan/libubsan)'
        else
            step_run cpp-asan-ubsan do_cpp_sanitize "$CPP_ASAN_DIR" \
                -DCMAKE_BUILD_TYPE=RelWithDebInfo \
                -DEMS_SANITIZER=address-undefined
        fi ;;
    cpp-tsan)
        if ! have_tool cmake; then
            step_skip_tool cpp-tsan 'cmake not installed'
        elif ! sanitizer_available -fsanitize=thread; then
            step_skip_tool cpp-tsan 'no TSan runtime (install libtsan)'
        else
            step_run cpp-tsan do_cpp_sanitize "$CPP_TSAN_DIR" \
                -DCMAKE_BUILD_TYPE=RelWithDebInfo \
                -DEMS_SANITIZER=thread
        fi ;;
    cpp-msan)
        if [ -n "${EMS_MSAN_LIBCXX:-}" ]; then
            step_needs_tool cpp-msan cmake 'cmake not installed' -- \
                do_cpp_sanitize "$CPP_MSAN_DIR" \
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
            step_run conformance do_conformance
        else
            step_skip conformance 'conformance harness not present — sub-project 2'
        fi ;;
    fsm-coverage)
        if [ -f conformance/harness/fsm_coverage.py ]; then
            step_run fsm-coverage python3 conformance/harness/fsm_coverage.py
        else
            step_skip fsm-coverage 'no FSM in the slice yet — lands with the order FSM component'
        fi ;;
    fuzz-long)
        step_skip fuzz-long 'no fuzz targets yet — sub-projects 4 and 5' ;;
    *)
        # A lane listing a step with no dispatch case must FAIL, not print a
        # note and carry on — that is how `schema-lint` silently did nothing.
        step_run "$1" false
        printf 'gate.sh: step %s has no dispatch case in run_step()\n' "$1" >&2 ;;
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
  EMS_GATE_STRICT=1  a step skipped for a MISSING TOOL is a failure
                     (set automatically when CI is set)
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
