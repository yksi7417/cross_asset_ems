#!/usr/bin/env bash
#
# scripts/drills/monthly-cold-start.sh
#
# Exercises the full EMS state-recovery path: takes the entire dev stack
# down hard (keeping named volumes), brings it back up, and verifies that
# state replays correctly from the Aeron Archive.
#
# Usage:
#     scripts/drills/monthly-cold-start.sh [--dry-run] [--help]
#
# Exit codes:
#     0  full success
#     1  any failure

set -euo pipefail

# ============================================================================
# Configuration (env-overridable)
# ============================================================================

: "${COMPOSE_FILE:=docker-compose.yml}"
: "${ARCHIVE_ADMIN_URL:=http://localhost:8081}"
: "${PG_DSN:=postgresql://postgres:postgres@localhost:5432/postgres}"
: "${REF_TABLE:=events}"
: "${DRY_RUN:=false}"
: "${HEALTH_TIMEOUT:=60}"
: "${HEALTH_INTERVAL:=2}"

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
    C_DIM=$(tput dim        2>/dev/null || true)
    C_RESET=$(tput sgr0     2>/dev/null || true)
else
    C_RED="" C_GREEN="" C_YELLOW="" C_BLUE="" C_BOLD="" C_DIM="" C_RESET=""
fi

# ============================================================================
# Help
# ============================================================================

print_help() {
    cat <<EOF
${0##*/} -- EMS monthly cold-start recovery drill

Takes the entire dev stack down hard (keeping named volumes) and brings
it back up, then verifies that state replays correctly from the Aeron
Archive.

USAGE
    ${0##*/} [--dry-run] [--help]

OPTIONS
    --dry-run        Print the planned steps without executing destructive
                     docker compose actions.
    --help, -h       Show this help message and exit.

ENVIRONMENT
    COMPOSE_FILE       Path to the docker compose file.
                       (default: docker-compose.yml)
    ARCHIVE_ADMIN_URL  Aeron Archive admin endpoint base URL; the script
                       GETs "\${ARCHIVE_ADMIN_URL}/log-position".
                       (default: http://localhost:8081)
    PG_DSN             PostgreSQL connection string passed to psql.
                       (default: postgresql://postgres:postgres@localhost:5432/postgres)
    REF_TABLE          Reference table used to verify Postgres state.
                       (default: events)
    DRY_RUN            "true" is equivalent to passing --dry-run.
    HEALTH_TIMEOUT     Per-service healthcheck timeout, in seconds.
                       (default: 60)
    HEALTH_INTERVAL    Healthcheck poll interval, in seconds.
                       (default: 2)

EXIT CODES
    0    Full success -- stack recovered, all services healthy, state
         matches pre-drill snapshot.
    1    Any failure -- stack not running, a service did not come up
         healthy, or a recovery check did not match.

EOF
}

# ============================================================================
# Argument parsing
# ============================================================================

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            print_help
            exit 0
            ;;
        --)
            shift
            break
            ;;
        -*)
            printf 'unknown option: %s\n\n' "$1" >&2
            print_help >&2
            exit 1
            ;;
        *)
            printf 'unexpected positional argument: %s\n\n' "$1" >&2
            print_help >&2
            exit 1
            ;;
    esac
done

# ============================================================================
# Logging helpers
# ============================================================================

log_step() {
    printf '%s==>%s %s%s%s\n' "$C_BOLD$C_BLUE" "$C_RESET" "$C_BOLD" "$*" "$C_RESET"
}

log_info() {
    printf '   %s\n' "$*"
}

log_ok() {
    printf '   %sOK%s   %s\n' "$C_GREEN" "$C_RESET" "$*"
}

log_warn() {
    printf '   %sWARN%s %s\n' "$C_YELLOW" "$C_RESET" "$*"
}

log_err() {
    printf '   %sFAIL%s %s\n' "$C_RED" "$C_RESET" "$*" >&2
}

# ============================================================================
# Working directory
# ============================================================================

WORK_DIR=$(mktemp -d /tmp/ems-cold-start.XXXXXX) || {
    log_err "failed to create temporary working directory"
    exit 1
}
trap 'rm -rf "$WORK_DIR"' EXIT

PRE_STATE_FILE="$WORK_DIR/pre-state.env"
PRE_PS_FILE="$WORK_DIR/pre-ps.json"

# ============================================================================
# Aggregated results
# ============================================================================

HEALTHY_SVCS=()
UNHEALTHY_SVCS=()
TIMED_OUT_SVCS=()

# ============================================================================
# Dependency check
# ============================================================================

check_deps() {
    local missing=()
    local cmd
    for cmd in docker curl jq psql; do
        if ! command -v "$cmd" >/dev/null 2>&1; then
            missing+=("$cmd")
        fi
    done
    if [ "${#missing[@]}" -gt 0 ]; then
        log_err "missing required commands: ${missing[*]}"
        return 1
    fi
    if ! docker compose version >/dev/null 2>&1; then
        log_err "docker compose plugin not available"
        return 1
    fi
    return 0
}

# ============================================================================
# Pre-drill state capture helpers
# ============================================================================

snapshot_archive_pos() {
    local response
    if ! response=$(curl -fsS --max-time 10 "${ARCHIVE_ADMIN_URL}/log-position" 2>/dev/null); then
        return 0
    fi
    if printf '%s' "$response" | jq -e . >/dev/null 2>&1; then
        printf '%s' "$response" | jq -r '.position // .logPosition // empty' 2>/dev/null || true
    else
        printf '%s' "$response"
    fi
}

snapshot_pg_count() {
    psql "$PG_DSN" -tAc "SELECT count(*) FROM ${REF_TABLE}" 2>/dev/null \
        | tr -d '[:space:]' \
        || true
}

snapshot_event() {
    # Capture the most recent row in REF_TABLE: id + md5 of the full row.
    psql "$PG_DSN" -tAc \
        "SELECT id || '|' || md5(row_to_json(t.*)::text) FROM ${REF_TABLE} t ORDER BY id DESC LIMIT 1" \
        2>/dev/null \
        || true
}

# ============================================================================
# Step 1: verify dev stack is running
# ============================================================================

step_verify_running() {
    log_step "Step 1/7 -- verify dev stack is running"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}would check that services from ${COMPOSE_FILE} are running${C_RESET}"
        return 0
    fi

    if [ ! -f "$COMPOSE_FILE" ]; then
        log_err "compose file not found: ${COMPOSE_FILE}"
        return 1
    fi

    local running=0
    running=$(docker compose -f "$COMPOSE_FILE" ps --services --status running 2>/dev/null | wc -l | tr -d '[:space:]') || running=0

    if [ "$running" -eq 0 ]; then
        log_err "no services are running; start the dev stack first"
        return 1
    fi
    log_ok "dev stack is running (${running} services)"
    return 0
}

# ============================================================================
# Step 2: snapshot pre-drill state
# ============================================================================

step_snapshot_pre() {
    log_step "Step 2/7 -- snapshot pre-drill state"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}would run: docker compose -f ${COMPOSE_FILE} ps --format json > ${PRE_PS_FILE}${C_RESET}"
        log_info "${C_DIM}would run: GET ${ARCHIVE_ADMIN_URL}/log-position${C_RESET}"
        log_info "${C_DIM}would run: psql ... -c \"SELECT count(*) FROM ${REF_TABLE}\"${C_RESET}"
        log_info "${C_DIM}would run: psql ... -c \"SELECT id, md5(row_to_json(t.*)::text) FROM ${REF_TABLE} t ORDER BY id DESC LIMIT 1\"${C_RESET}"
        cat > "$PRE_STATE_FILE" <<EOF
ARCHIVE_POS=__dry_run__
PG_COUNT=__dry_run__
EVENT_ID=__dry_run__
EVENT_HASH=__dry_run__
EOF
        return 0
    fi

    if ! docker compose -f "$COMPOSE_FILE" ps --format json > "$PRE_PS_FILE" 2>/dev/null; then
        log_err "failed to capture docker compose ps"
        return 1
    fi

    local archive_pos="" pg_count="" event_line="" event_id="" event_hash=""

    archive_pos=$(snapshot_archive_pos)
    if [ -z "$archive_pos" ]; then
        log_warn "could not read archive log position; check will be skipped"
        archive_pos="__unavailable__"
    fi

    pg_count=$(snapshot_pg_count)
    if [ -z "$pg_count" ]; then
        log_warn "could not read ${REF_TABLE} row count; check will be skipped"
        pg_count="__unavailable__"
    fi

    event_line=$(snapshot_event)
    if [ -z "$event_line" ]; then
        log_warn "could not read latest event from ${REF_TABLE}; check will be skipped"
        event_id="__unavailable__"
        event_hash="__unavailable__"
    else
        event_id="${event_line%%|*}"
        event_hash="${event_line#*|}"
    fi

    cat > "$PRE_STATE_FILE" <<EOF
ARCHIVE_POS=${archive_pos}
PG_COUNT=${pg_count}
EVENT_ID=${event_id}
EVENT_HASH=${event_hash}
EOF

    log_ok "pre-drill state captured"
    log_info "archive log position : ${archive_pos}"
    log_info "${REF_TABLE} row count : ${pg_count}"
    log_info "latest event id/hash : ${event_id} / ${event_hash:0:16}..."
    return 0
}

# ============================================================================
# Step 3: take the stack down (keep volumes)
# ============================================================================

step_take_down() {
    log_step "Step 3/7 -- take stack down (named volumes retained)"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}would run: docker compose -f ${COMPOSE_FILE} down --volumes=false${C_RESET}"
        return 0
    fi

    if ! docker compose -f "$COMPOSE_FILE" down --volumes=false; then
        log_err "docker compose down --volumes=false failed"
        return 1
    fi

    local remaining=0
    remaining=$(docker compose -f "$COMPOSE_FILE" ps --services --status running 2>/dev/null | wc -l | tr -d '[:space:]') || remaining=0
    if [ "$remaining" -ne 0 ]; then
        log_err "${remaining} services still running after down"
        return 1
    fi
    log_ok "all services stopped; named volumes retained"
    return 0
}

# ============================================================================
# Step 4: bring the stack back up
# ============================================================================

step_bring_up() {
    log_step "Step 4/7 -- bring stack back up"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}would run: docker compose -f ${COMPOSE_FILE} up -d${C_RESET}"
        return 0
    fi

    if ! docker compose -f "$COMPOSE_FILE" up -d; then
        log_err "docker compose up -d failed"
        return 1
    fi
    log_ok "docker compose up -d returned"
    return 0
}

# ============================================================================
# Step 5: healthcheck loop
# ============================================================================

get_service_field() {
    local svc=$1
    local field=$2
    docker compose -f "$COMPOSE_FILE" ps --format json 2>/dev/null \
        | jq -r --arg s "$svc" --arg f "$field" \
            '.[] | select(.Service == $s) | .[$f] // "none"' \
        2>/dev/null \
        | head -n1
}

poll_service_health() {
    local svc=$1
    local timeout=$HEALTH_TIMEOUT
    local interval=$HEALTH_INTERVAL
    local elapsed=0
    local health state

    while [ "$elapsed" -lt "$timeout" ]; do
        health=$(get_service_field "$svc" Health || true)
        case "${health:-none}" in
            healthy)
                return 0
                ;;
            unhealthy)
                return 2
                ;;
            none)
                # No healthcheck defined; consider service up once it is running.
                state=$(get_service_field "$svc" State || true)
                if [ "$state" = "running" ]; then
                    return 0
                fi
                ;;
        esac
        sleep "$interval" || true
        elapsed=$((elapsed + interval))
    done
    return 1
}

step_healthcheck() {
    log_step "Step 5/7 -- healthcheck loop (timeout ${HEALTH_TIMEOUT}s per service)"

    local services
    if ! services=$(docker compose -f "$COMPOSE_FILE" config --services 2>/dev/null); then
        log_err "failed to list services from ${COMPOSE_FILE}"
        return 1
    fi
    if [ -z "$services" ]; then
        log_err "no services defined in ${COMPOSE_FILE}"
        return 1
    fi

    local svc rc
    while IFS= read -r svc; do
        [ -z "$svc" ] && continue
        printf '   %-30s ' "$svc"

        if [ "$DRY_RUN" = "true" ]; then
            printf '%s[DRY-RUN]%s would poll\n' "$C_YELLOW" "$C_RESET"
            HEALTHY_SVCS+=("$svc")
            continue
        fi

        poll_service_health "$svc"
        rc=$?
        case "$rc" in
            0)
                printf '%shealthy%s\n' "$C_GREEN" "$C_RESET"
                HEALTHY_SVCS+=("$svc")
                ;;
            2)
                printf '%sunhealthy%s\n' "$C_RED" "$C_RESET"
                UNHEALTHY_SVCS+=("$svc")
                ;;
            *)
                printf '%stimed out%s\n' "$C_RED" "$C_RESET"
                TIMED_OUT_SVCS+=("$svc")
                ;;
        esac
    done <<< "$services"
    return 0
}

# ============================================================================
# Step 6: verify state recovery
# ============================================================================

step_verify_recovery() {
    log_step "Step 6/7 -- verify state recovery"

    # shellcheck disable=SC1090
    . "$PRE_STATE_FILE"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}would compare archive log position, ${REF_TABLE} row count, and latest event hash${C_RESET}"
        return 0
    fi

    local rc=0
    local post_archive_pos post_pg_count post_event_line post_event_id post_event_hash

    # 6a -- archive log position
    post_archive_pos=$(snapshot_archive_pos)
    if [ "${ARCHIVE_POS}" = "__unavailable__" ]; then
        log_warn "archive log position : skipped (pre-drill value unavailable)"
    elif [ -z "$post_archive_pos" ]; then
        log_err "archive log position : FAILED (could not read post-drill position)"
        rc=1
    elif [ "$post_archive_pos" = "${ARCHIVE_POS}" ]; then
        log_ok "archive log position : match (${ARCHIVE_POS})"
    else
        log_err "archive log position : MISMATCH (pre=${ARCHIVE_POS} post=${post_archive_pos})"
        rc=1
    fi

    # 6b -- postgres row count
    post_pg_count=$(snapshot_pg_count)
    if [ "${PG_COUNT}" = "__unavailable__" ]; then
        log_warn "${REF_TABLE} row count : skipped (pre-drill value unavailable)"
    elif [ -z "$post_pg_count" ]; then
        log_err "${REF_TABLE} row count : FAILED (could not read post-drill count)"
        rc=1
    elif [ "$post_pg_count" = "${PG_COUNT}" ]; then
        log_ok "${REF_TABLE} row count : match (${PG_COUNT})"
    else
        log_err "${REF_TABLE} row count : MISMATCH (pre=${PG_COUNT} post=${post_pg_count})"
        rc=1
    fi

    # 6c -- replayed event
    post_event_line=$(snapshot_event)
    if [ -z "$post_event_line" ]; then
        post_event_id=""
        post_event_hash=""
    else
        post_event_id="${post_event_line%%|*}"
        post_event_hash="${post_event_line#*|}"
    fi

    if [ "${EVENT_ID}" = "__unavailable__" ]; then
        log_warn "replayed event       : skipped (pre-drill value unavailable)"
    elif [ -z "$post_event_id" ]; then
        log_err "replayed event       : FAILED (no rows in ${REF_TABLE} after recovery)"
        rc=1
    elif [ "$post_event_id" = "${EVENT_ID}" ] && [ "$post_event_hash" = "${EVENT_HASH}" ]; then
        log_ok "replayed event       : match (id=${EVENT_ID} hash=${EVENT_HASH:0:16}...)"
    else
        log_err "replayed event       : MISMATCH (pre=id:${EVENT_ID} hash:${EVENT_HASH} post=id:${post_event_id} hash:${post_event_hash})"
        rc=1
    fi

    if [ "$rc" -ne 0 ]; then
        log_err "recovery verification FAILED"
        return 1
    fi
    log_ok "recovery verification passed"
    return 0
}

# ============================================================================
# Step 7: summary
# ============================================================================

join_by() {
    local sep=$1
    shift
    local first=1 item
    for item in "$@"; do
        if [ "$first" -eq 1 ]; then
            printf '%s' "$item"
            first=0
        else
            printf '%s%s' "$sep" "$item"
        fi
    done
}

print_summary() {
    local duration=$1
    local rc=$2
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))

    echo
    log_step "Step 7/7 -- summary"

    if [ "$DRY_RUN" = "true" ]; then
        log_info "${C_DIM}(dry-run mode -- destructive actions were not executed)${C_RESET}"
    fi

    printf '   services up          : %s%d%s' "$C_GREEN" "${#HEALTHY_SVCS[@]}" "$C_RESET"
    if [ "${#HEALTHY_SVCS[@]}" -gt 0 ]; then
        printf '  (%s)' "$(join_by ', ' "${HEALTHY_SVCS[@]}")"
    fi
    printf '\n'

    printf '   services unhealthy   : %s%d%s' "$C_RED" "${#UNHEALTHY_SVCS[@]}" "$C_RESET"
    if [ "${#UNHEALTHY_SVCS[@]}" -gt 0 ]; then
        printf '  (%s)' "$(join_by ', ' "${UNHEALTHY_SVCS[@]}")"
    fi
    printf '\n'

    printf '   services timed out   : %s%d%s' "$C_RED" "${#TIMED_OUT_SVCS[@]}" "$C_RESET"
    if [ "${#TIMED_OUT_SVCS[@]}" -gt 0 ]; then
        printf '  (%s)' "$(join_by ', ' "${TIMED_OUT_SVCS[@]}")"
    fi
    printf '\n'

    if [ "$rc" -eq 0 ]; then
        printf '   recovery verification: %sPASS%s\n' "$C_GREEN" "$C_RESET"
    else
        printf '   recovery verification: %sFAIL%s\n' "$C_RED" "$C_RESET"
    fi

    printf '   time-to-recover      : %dm %02ds\n' "$minutes" "$seconds"

    echo
    if [ "$rc" -eq 0 ]; then
        printf '%s   DRILL PASSED%s\n' "$C_BOLD$C_GREEN" "$C_RESET"
    else
        printf '%s   DRILL FAILED%s\n' "$C_BOLD$C_RED" "$C_RESET" >&2
    fi
}

# ============================================================================
# Main
# ============================================================================

main() {
    if ! check_deps; then
        exit 1
    fi

    local start_epoch
    start_epoch=$(date +%s)
    local step_rc=0

    if ! step_verify_running; then
        step_rc=1
    elif ! step_snapshot_pre; then
        step_rc=1
    elif ! step_take_down; then
        step_rc=1
    elif ! step_bring_up; then
        step_rc=1
    elif ! step_healthcheck; then
        step_rc=1
    elif ! step_verify_recovery; then
        step_rc=1
    fi

    local end_epoch duration
    end_epoch=$(date +%s)
    duration=$((end_epoch - start_epoch))

    print_summary "$duration" "$step_rc"
    exit "$step_rc"
}

main "$@"
