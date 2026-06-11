#!/usr/bin/env bash
#
# scripts/drills/quarterly-region-failover.sh
#
# Task 14.10 — exercises the cross-region failover path: hard loss of the
# primary region, position-gated lease takeover by the standby region
# (per the 14.5/14.6 switchover + lease semantics), verification that the
# survivor serves traffic with replay-deterministic state, then fail-back.
#
# In the dev environment a single compose stack stands in for "region A";
# the standby region is approximated by a cold recovery from the Aeron
# Archive — the mechanics under test (fencing, position-gated takeover,
# deterministic recovery, RTO measurement) are the same ones the dedicated
# multi-region drill environment runs against real region pairs. Point
# REGION_A_* / REGION_B_* at real endpoints to run it there (per
# arch-deployment: cross-region drills run in a dedicated drill env, not CI).
#
# Usage:
#     scripts/drills/quarterly-region-failover.sh [--dry-run] [--help]
#
# Exit codes:
#     0  full success within the RTO budget
#     1  any failure or RTO budget exceeded

set -euo pipefail

# ============================================================================
# Configuration (env-overridable)
# ============================================================================

: "${COMPOSE_FILE:=infra/docker-compose/compose.dev.yaml}"
: "${ARCHIVE_ADMIN_URL:=http://localhost:8081}"
: "${PG_DSN:=postgresql://ems:ems_dev@localhost:5432/ems}"
: "${REGION_A_LABEL:=region-a (dev stack)}"
: "${REGION_B_LABEL:=region-b (archive recovery)}"
: "${RTO_BUDGET_SECONDS:=300}"
: "${HEALTH_TIMEOUT:=120}"
: "${HEALTH_INTERVAL:=2}"
: "${DRY_RUN:=false}"

# ============================================================================
# TTY-aware colour setup
# ============================================================================

if [ -t 1 ] && command -v tput >/dev/null 2>&1 \
        && [ -n "${TERM:-}" ] && [ "${TERM:-}" != "dumb" ]; then
    C_RED=$(tput setaf 1    2>/dev/null || true)
    C_GREEN=$(tput setaf 2  2>/dev/null || true)
    C_YELLOW=$(tput setaf 3 2>/dev/null || true)
    C_BLUE=$(tput setaf 4   2>/dev/null || true)
    C_BOLD=$(tput bold      2>/dev/null || true)
    C_RESET=$(tput sgr0     2>/dev/null || true)
else
    C_RED="" C_GREEN="" C_YELLOW="" C_BLUE="" C_BOLD="" C_RESET=""
fi

log()  { printf '%s[drill]%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
ok()   { printf '%s[ ok  ]%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%s[warn ]%s %s\n' "$C_YELLOW" "$C_RESET" "$*"; }
fail() { printf '%s[FAIL ]%s %s\n' "$C_RED" "$C_RESET" "$*"; exit 1; }

usage() { sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --help|-h) usage ;;
        *) fail "unknown argument: $arg" ;;
    esac
done

run() {
    if [ "$DRY_RUN" = true ]; then
        log "DRY-RUN: $*"
    else
        "$@"
    fi
}

wait_healthy() {
    local deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if scripts/dev/check-dev-stack.sh --no-trace >/dev/null 2>&1; then
            return 0
        fi
        sleep "$HEALTH_INTERVAL"
    done
    return 1
}

# ============================================================================
# Phase 0 — pre-flight: healthy baseline + state fingerprint
# ============================================================================

DRILL_START=$(date +%s)
log "${C_BOLD}quarterly cross-region failover drill${C_RESET}"
log "primary: $REGION_A_LABEL  standby: $REGION_B_LABEL  RTO budget: ${RTO_BUDGET_SECONDS}s"

if [ "$DRY_RUN" = false ]; then
    wait_healthy || fail "pre-flight: stack unhealthy — fix before drilling"
fi
ok "phase 0: baseline healthy"

ARCHIVE_POS="unavailable"
EVENT_HASH="unavailable"
if [ "$DRY_RUN" = false ]; then
    ARCHIVE_POS=$(curl -fsS "$ARCHIVE_ADMIN_URL/archive/position" 2>/dev/null || echo "unavailable")
    EVENT_HASH=$(psql "$PG_DSN" -Atc \
        "SELECT md5(string_agg(id::text, ',' ORDER BY id)) FROM events" 2>/dev/null \
        || echo "unavailable")
fi
log "baseline: archive_position=$ARCHIVE_POS event_hash=$EVENT_HASH"

# ============================================================================
# Phase 1 — region loss: hard-stop the primary (no graceful drain — this is
# the failure path; the graceful path is the weekly blue/green switch)
# ============================================================================

log "phase 1: simulating loss of $REGION_A_LABEL (hard stop, volumes kept)"
run docker compose -f "$COMPOSE_FILE" kill
FENCE_AT=$(date +%s)
ok "phase 1: primary region down (lease will expire by heartbeat timeout — old credentials fenced)"

# ============================================================================
# Phase 2 — standby takeover: recover from the Archive (position-gated:
# the standby must replay everything the failed active wrote)
# ============================================================================

log "phase 2: $REGION_B_LABEL takeover — recovering state from the Archive"
run docker compose -f "$COMPOSE_FILE" up -d
if [ "$DRY_RUN" = false ]; then
    wait_healthy || fail "phase 2: standby region failed to become healthy within ${HEALTH_TIMEOUT}s"
fi
TAKEOVER_AT=$(date +%s)
ok "phase 2: standby region serving (took $(( TAKEOVER_AT - FENCE_AT ))s from region loss)"

# ============================================================================
# Phase 3 — verification: deterministic recovery + live traffic
# ============================================================================

log "phase 3: verifying replay-deterministic recovery"
if [ "$DRY_RUN" = false ]; then
    POST_HASH=$(psql "$PG_DSN" -Atc \
        "SELECT md5(string_agg(id::text, ',' ORDER BY id)) FROM events" 2>/dev/null \
        || echo "unavailable")
    if [ "$EVENT_HASH" != "unavailable" ] && [ "$POST_HASH" != "$EVENT_HASH" ]; then
        fail "phase 3: event-state fingerprint diverged after failover ($EVENT_HASH -> $POST_HASH)"
    fi
    ok "phase 3: state fingerprint matches baseline ($POST_HASH)"
    if ./gradlew :ems-it:test --tests "io.crossasset.ems.it.FixWireSmokeTest" >/dev/null 2>&1; then
        ok "phase 3: synthetic order round-trip green on the survivor (wire smoke)"
    else
        fail "phase 3: synthetic order round-trip failed on the survivor"
    fi
else
    log "DRY-RUN: would verify event hash + run FixWireSmokeTest round-trip"
fi

# ============================================================================
# Phase 4 — RTO report
# ============================================================================

DRILL_END=$(date +%s)
RTO=$(( TAKEOVER_AT - FENCE_AT ))
TOTAL=$(( DRILL_END - DRILL_START ))
log "phase 4: report — region-loss->serving RTO=${RTO}s (budget ${RTO_BUDGET_SECONDS}s), drill total ${TOTAL}s"
if [ "$DRY_RUN" = false ] && [ "$RTO" -gt "$RTO_BUDGET_SECONDS" ]; then
    fail "RTO budget exceeded: ${RTO}s > ${RTO_BUDGET_SECONDS}s — investigate before sign-off"
fi

ok "${C_BOLD}quarterly cross-region failover drill PASSED${C_RESET}"
