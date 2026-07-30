#!/usr/bin/env bash
#
# Step-runner primitives for scripts/ci/gate.sh.
#
# Design notes:
#   - step_run NEVER aborts the gate. One gate run reports every failure rather
#     than only the first, because a developer fixing three things wants to see
#     three things.
#   - A missing tool is a SKIP locally and a FAIL under strict mode (CI). The
#     summary always shows skips, so the gate can never pretend it ran something
#     it did not.
#
# This file is sourced, not executed.

# shellcheck shell=bash

# Strict mode: a skip becomes a failure. Set automatically when running in CI.
: "${EMS_GATE_STRICT:=${CI:+1}}"
: "${EMS_GATE_STRICT:=0}"

STEP_NAMES=()
STEP_STATUS=()
STEP_SECS=()
STEP_NOTES=()

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_RED=$'\033[31m'
    C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BOLD=$'\033[1m'
else
    C_RESET=''; C_DIM=''; C_RED=''; C_GREEN=''; C_YELLOW=''; C_BOLD=''
fi

have_tool() {
    command -v "$1" >/dev/null 2>&1
}

_step_record() {
    STEP_NAMES+=("$1")
    STEP_STATUS+=("$2")
    STEP_SECS+=("$3")
    STEP_NOTES+=("$4")
}

# step_run NAME CMD [ARGS...]
# Runs CMD, records PASS/FAIL, always returns 0 so the gate continues.
step_run() {
    local name=$1
    shift
    local start end elapsed rc
    start=$SECONDS
    printf '%s▶ %s%s\n' "$C_BOLD" "$name" "$C_RESET"
    if "$@"; then
        rc=0
    else
        rc=$?
    fi
    end=$SECONDS
    elapsed=$((end - start))
    if [ "$rc" -eq 0 ]; then
        printf '%s✔ %s%s %s(%ss)%s\n\n' "$C_GREEN" "$name" "$C_RESET" "$C_DIM" "$elapsed" "$C_RESET"
        _step_record "$name" PASS "$elapsed" ''
    else
        printf '%s✘ %s%s %s(%ss, exit %s)%s\n\n' "$C_RED" "$name" "$C_RESET" "$C_DIM" "$elapsed" "$rc" "$C_RESET"
        _step_record "$name" FAIL "$elapsed" "exit $rc"
    fi
    return 0
}

# step_skip NAME REASON
# Records a skip for a step whose SUBJECT does not exist yet — a language tree
# or a harness a later sub-project will deliver. Never a failure: the repo
# genuinely has nothing to check, and the summary says so out loud.
step_skip() {
    local name=$1 reason=$2
    printf '%s⊘ %s%s %s(%s)%s\n\n' "$C_YELLOW" "$name" "$C_RESET" "$C_DIM" "$reason" "$C_RESET"
    _step_record "$name" SKIP 0 "$reason"
    return 0
}

# step_skip_tool NAME REASON
# Records a skip caused by a MISSING TOOL. That is tolerable on a developer
# laptop and unacceptable in CI, so under EMS_GATE_STRICT=1 it is a failure —
# otherwise a CI image losing a package would quietly stop enforcing a check.
step_skip_tool() {
    local name=$1 reason=$2
    if [ "$EMS_GATE_STRICT" = "1" ]; then
        printf '%s✘ %s%s %s(strict mode: %s)%s\n\n' \
            "$C_RED" "$name" "$C_RESET" "$C_DIM" "$reason" "$C_RESET"
        _step_record "$name" FAIL 0 "strict: $reason"
    else
        printf '%s⊘ %s%s %s(%s — install it, or CI will)%s\n\n' \
            "$C_YELLOW" "$name" "$C_RESET" "$C_DIM" "$reason" "$C_RESET"
        _step_record "$name" SKIP 0 "$reason"
    fi
    return 0
}

# step_needs_tool NAME TOOL REASON -- CMD [ARGS...]
# Runs CMD when TOOL is present, otherwise records a missing-tool skip.
step_needs_tool() {
    local name=$1 tool=$2 reason=$3
    shift 3
    [ "${1:-}" = "--" ] && shift
    if have_tool "$tool"; then
        step_run "$name" "$@"
    else
        step_skip_tool "$name" "$reason"
    fi
}

# steps_summary
# Prints the summary table. Returns 1 if any step failed.
steps_summary() {
    local i failed=0 passed=0 skipped=0 total_secs=0
    printf '%s─── gate summary ───────────────────────────────%s\n' "$C_BOLD" "$C_RESET"
    for i in "${!STEP_NAMES[@]}"; do
        local status=${STEP_STATUS[$i]} colour=''
        case "$status" in
            PASS) colour=$C_GREEN;  passed=$((passed + 1)) ;;
            FAIL) colour=$C_RED;    failed=$((failed + 1)) ;;
            SKIP) colour=$C_YELLOW; skipped=$((skipped + 1)) ;;
        esac
        total_secs=$((total_secs + STEP_SECS[i]))
        printf '  %s%-4s%s %-18s %4ss  %s%s%s\n' \
            "$colour" "$status" "$C_RESET" "${STEP_NAMES[$i]}" "${STEP_SECS[$i]}" \
            "$C_DIM" "${STEP_NOTES[$i]}" "$C_RESET"
    done
    printf '  %s%d passed, %d failed, %d skipped in %ss%s\n' \
        "$C_DIM" "$passed" "$failed" "$skipped" "$total_secs" "$C_RESET"
    if [ "$failed" -gt 0 ]; then
        printf '%sgate FAILED%s\n' "$C_RED" "$C_RESET"
        return 1
    fi
    printf '%sgate passed%s\n' "$C_GREEN" "$C_RESET"
    return 0
}
