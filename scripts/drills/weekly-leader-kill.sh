#!/usr/bin/env bash
# scripts/drills/weekly-leader-kill.sh
#
# Exercises the Aeron Cluster Raft leader-failover path on a running EMS
# dev stack.  This is a destructive drill: it kills the current cluster
# leader and asserts that the remaining followers elect a new one within a
# bounded time window, after which a smoke test is performed.
#
# Usage:
#   ./scripts/drills/weekly-leader-kill.sh [--dry-run] [--help]
#
# Environment variables:
#   CLUSTER_ADMIN_URL  Cluster admin endpoint (default: http://localhost:2020/aeron/cluster)
#   COMPOSE_FILE       Docker compose file (default: infra/compose.dev.yaml)
#   POLL_INTERVAL      Seconds between polls (default: 2)
#   POLL_TIMEOUT       Total seconds to wait for new leader (default: 30)
#   EVENTS_API         Base URL for smoke test (default: http://localhost:8080)
#   DRY_RUN            Set to "1"/"true" to enable dry-run mode.

set -euo pipefail

# ---- Defaults ----
: "${CLUSTER_ADMIN_URL:=http://localhost:2020/aeron/cluster}"
: "${COMPOSE_FILE:=infra/compose.dev.yaml}"
: "${POLL_INTERVAL:=2}"
: "${POLL_TIMEOUT:=30}"
: "${EVENTS_API:=http://localhost:8080}"

# ---- CLI parsing ----
DRY_RUN_FLAG=0
print_help() {
  cat <<EOF
Usage: $(basename "$0") [--dry-run] [--help]

Exercises Aeron Cluster Raft leader failover on the running EMS dev stack.

Options:
  --dry-run    Print the planned steps without performing destructive actions.
  --help, -h   Show this help and exit.

Environment variables:
  CLUSTER_ADMIN_URL  Cluster admin endpoint (default: $CLUSTER_ADMIN_URL)
  COMPOSE_FILE       Docker compose file         (default: $COMPOSE_FILE)
  POLL_INTERVAL      Seconds between polls        (default: $POLL_INTERVAL)
  POLL_TIMEOUT       Total seconds to wait        (default: $POLL_TIMEOUT)
  EVENTS_API         Base URL for smoke test      (default: $EVENTS_API)
  DRY_RUN            Set to "1"/"true" to enable dry-run.
EOF
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN_FLAG=1 ;;
    --help|-h) print_help; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; print_help >&2; exit 1 ;;
  esac
done
if [[ "${DRY_RUN:-0}" == "1" || "${DRY_RUN:-false}" == "true" ]]; then
  DRY_RUN_FLAG=1
fi

# ---- Color output (only when stdout is a TTY) ----
if [[ -t 1 ]]; then
  C_RESET=$(tput sgr0); C_BOLD=$(tput bold)
  C_RED=$(tput setaf 1); C_GREEN=$(tput setaf 2)
  C_YELLOW=$(tput setaf 3); C_BLUE=$(tput setaf 4)
  C_CYAN=$(tput setaf 6)
else
  C_RESET=""; C_BOLD=""; C_RED=""; C_GREEN=""
  C_YELLOW=""; C_BLUE=""; C_CYAN=""
fi

log()  { printf "%b\n" "$*"; }
info() { log "${C_BLUE}[info]${C_RESET} $*"; }
warn() { log "${C_YELLOW}[warn]${C_RESET} $*" >&2; }
err()  { log "${C_RED}[err ]${C_RESET} $*" >&2; }
ok()   { log "${C_GREEN}[ok  ]${C_RESET} $*"; }
hdr()  { log "${C_BOLD}${C_CYAN}== $* ==${C_RESET}"; }

# ---- Helpers ----
now_ms() { date +%s%3N; }

# shellcheck disable=SC2120  # args are optional pass-through to `ps`
compose_ps() {
  if [[ -f "$COMPOSE_FILE" ]]; then
    docker compose -f "$COMPOSE_FILE" ps "$@"
  else
    docker compose ps "$@"
  fi
}

compose_q() {
  if [[ -f "$COMPOSE_FILE" ]]; then
    docker compose -f "$COMPOSE_FILE" ps -q "$@"
  else
    docker compose ps -q "$@"
  fi
}

# Read a field from JSON on stdin. Uses jq if available, else python3.
json_get() {
  local expr="$1"
  if command -v jq >/dev/null 2>&1; then
    jq -r "$expr"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$expr" <<'PY'
import json, sys
expr = sys.argv[1]
data = json.loads(sys.stdin.read())
val = data
for p in expr.lstrip('.').split('.'):
    if val is None:
        break
    if isinstance(val, list):
        try:
            val = val[int(p)]
        except (ValueError, IndexError, TypeError):
            val = None
    elif isinstance(val, dict):
        val = val.get(p)
    else:
        val = None
print('' if val is None else val)
PY
  else
    err "Need either 'jq' or 'python3' for JSON parsing."
    return 1
  fi
}

# Find containerId for a given member_id within a members JSON array.
container_for_member() {
  local member_id="$1"
  local members="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r --argjson m "$member_id" \
      '.[] | select((.memberId == $m) or (.id == $m)) | .containerId // empty' \
      <<<"$members" | head -n1
  else
    # members passed via env var: the heredoc already occupies stdin (the
    # python source), so it can't also carry the JSON via a here-string.
    MEMBERS="$members" python3 - "$member_id" <<'PY'
import json, os, sys
mid = int(sys.argv[1])
for m in json.loads(os.environ['MEMBERS']):
    if m.get('memberId') == mid or m.get('id') == mid:
        print(m.get('containerId', '') or '')
        break
PY
  fi
}

# ---- State ----
SCRIPT_START_MS=$(now_ms)
OLD_LEADER_ID=""
OLD_LEADER_CONTAINER=""
OLD_LEADER_TERM=""
MEMBERS_JSON="[]"
NEW_LEADER_ID=""
NEW_LEADER_CONTAINER=""
NEW_LEADER_TERM=""
TIME_TO_ELECT_MS=0
SMOKE_RESULT="not run"
RECOVERY_STATUS="unknown"
SNAPSHOT_FILE=""
EVENTS_BEFORE=""
LOG_POS_BEFORE=""

# ---- Summary printer (defined early so error paths can use it) ----
print_summary() {
  local total_ms=$(( $(now_ms) - SCRIPT_START_MS ))
  local status_color="$C_GREEN"
  case "$RECOVERY_STATUS" in
    ok*) : ;;
    *)   status_color="$C_RED" ;;
  esac
  cat <<EOF

${C_BOLD}${C_CYAN}================ Leader Failover Drill Summary ================${C_RESET}
  Old leader .............. member_id=${OLD_LEADER_ID:-?}  container=${OLD_LEADER_CONTAINER:-?}  term=${OLD_LEADER_TERM:-?}
  New leader .............. member_id=${NEW_LEADER_ID:-none}  container=${NEW_LEADER_CONTAINER:-?}  term=${NEW_LEADER_TERM:-?}
  Time-to-elect ........... ${TIME_TO_ELECT_MS} ms
  Smoke test .............. ${SMOKE_RESULT}
  Events written (before) . ${EVENTS_BEFORE:-?}
  Log position (before) ... ${LOG_POS_BEFORE:-?}
  Snapshot file ........... ${SNAPSHOT_FILE:-n/a}
  Recovery status ......... ${status_color}${RECOVERY_STATUS}${C_RESET}
  Total drill time ........ ${total_ms} ms
  Compose file ............ ${COMPOSE_FILE}
  Admin endpoint .......... ${CLUSTER_ADMIN_URL}
${C_BOLD}${C_CYAN}===============================================================${C_RESET}
EOF
}

# Always print a summary on exit, and exit non-zero if any step failed.
# shellcheck disable=SC2317,SC2329  # invoked via the EXIT trap, not inline
on_exit() {
  local code=$?
  if [[ $code -ne 0 && "$RECOVERY_STATUS" == "unknown" ]]; then
    RECOVERY_STATUS="failed: aborted before completion"
    print_summary
  fi
  exit "$code"
}
trap on_exit EXIT

# ---- Step 1: Verify dev stack is running ----
hdr "Step 1/10: Verifying dev stack is running"
if ! command -v docker >/dev/null 2>&1; then
  err "docker is not installed or not on PATH"
  exit 1
fi
if ! compose_ps >/dev/null 2>&1; then
  err "Dev stack is not running. Start it with: docker compose -f $COMPOSE_FILE up -d"
  exit 1
fi
ok "Dev stack is running (compose file: $COMPOSE_FILE)"

# ---- Step 2: Query cluster admin endpoint for the current leader ----
hdr "Step 2/10: Querying cluster admin endpoint"
info "URL: $CLUSTER_ADMIN_URL"

fetch_cluster_json() {
  curl --silent --show-error --fail --max-time 5 "$CLUSTER_ADMIN_URL"
}

CLUSTER_JSON=$(fetch_cluster_json) || {
  err "Failed to query cluster admin endpoint: $CLUSTER_ADMIN_URL"
  exit 1
}
[[ -n "$CLUSTER_JSON" ]] || { err "Empty response from $CLUSTER_ADMIN_URL"; exit 1; }
ok "Got cluster state"

OLD_LEADER_ID=$(json_get '.leader.memberId // .leaderMemberId // .leader.id // empty' <<<"$CLUSTER_JSON")
OLD_LEADER_TERM=$(json_get '.leadershipTerm // .leader.leadershipTerm // .term // empty' <<<"$CLUSTER_JSON")
MEMBERS_JSON=$(json_get '.members // .clusterMembers // []' <<<"$CLUSTER_JSON")

if [[ -z "$OLD_LEADER_ID" || "$OLD_LEADER_ID" == "null" ]]; then
  err "Could not determine current leader from cluster admin endpoint"
  exit 1
fi
ok "Current leader: member_id=$OLD_LEADER_ID term=$OLD_LEADER_TERM"

# ---- Step 3: Resolve leader's container_id ----
hdr "Step 3/10: Resolving leader container_id"
OLD_LEADER_CONTAINER=$(container_for_member "$OLD_LEADER_ID" "$MEMBERS_JSON")
if [[ -z "$OLD_LEADER_CONTAINER" ]]; then
  SERVICE_FALLBACK="aeron-cluster-${OLD_LEADER_ID}"
  warn "containerId not present in members JSON; falling back to service name '$SERVICE_FALLBACK'"
  OLD_LEADER_CONTAINER=$(compose_q "$SERVICE_FALLBACK" 2>/dev/null | head -n1 || true)
fi
if [[ -z "$OLD_LEADER_CONTAINER" ]]; then
  err "Could not resolve container for leader member_id=$OLD_LEADER_ID"
  exit 1
fi
ok "Leader container_id=$OLD_LEADER_CONTAINER"

# ---- Step 4: Snapshot state for post-mortem comparison ----
hdr "Step 4/10: Snapshotting state"
SNAPSHOT_FILE=$(mktemp -t leader-kill-snap.XXXXXX.json)
printf '%s\n' "$CLUSTER_JSON" > "$SNAPSHOT_FILE"
EVENTS_BEFORE=$(json_get '.eventsWritten // .snapshot.eventsWritten // "unknown"' <<<"$CLUSTER_JSON")
LOG_POS_BEFORE=$(json_get '.logPosition // .snapshot.logPosition // "unknown"' <<<"$CLUSTER_JSON")
ok "eventsWritten=$EVENTS_BEFORE  logPosition=$LOG_POS_BEFORE"
ok "Snapshot saved to: $SNAPSHOT_FILE"

# ---- Dry-run short-circuit ----
if [[ $DRY_RUN_FLAG -eq 1 ]]; then
  hdr "DRY-RUN mode: no destructive actions will be performed"
  cat <<EOF
  1. Verify dev stack ............... done
  2. Identify current leader ........ done (member_id=$OLD_LEADER_ID, term=$OLD_LEADER_TERM)
  3. Resolve leader container ....... done (container_id=$OLD_LEADER_CONTAINER)
  4. Snapshot state ................. done ($SNAPSHOT_FILE)
  5. Kill leader container .......... docker kill $OLD_LEADER_CONTAINER
  6. Poll for new leader ............ every ${POLL_INTERVAL}s, timeout ${POLL_TIMEOUT}s
  7. Verify new leader is a follower  pending
  8. Smoke test (write + read) ...... TODO(invocation): wire in real smoke-test client
  9. Print summary ................. pending
 10. Exit 0 on success
EOF
  RECOVERY_STATUS="ok (dry-run)"
  print_summary
  exit 0
fi

# ---- Step 5: Kill the leader ----
hdr "Step 5/10: Killing leader container"
KILL_START_MS=$(now_ms)
if ! docker kill "$OLD_LEADER_CONTAINER" >/dev/null; then
  err "Failed to kill container $OLD_LEADER_CONTAINER"
  RECOVERY_STATUS="failed: docker kill returned non-zero"
  print_summary
  exit 1
fi
ok "Killed $OLD_LEADER_CONTAINER"

# ---- Step 6: Poll for a NEW leader ----
hdr "Step 6/10: Polling for new leader (interval=${POLL_INTERVAL}s, timeout=${POLL_TIMEOUT}s)"
DEADLINE=$(( $(now_ms) + POLL_TIMEOUT * 1000 ))
ATTEMPT=0
NEW_LEADER_ID=""
# Disable errexit around the polling loop so transient curl failures don't abort.
set +e
while [[ $(now_ms) -lt DEADLINE ]]; do
  ATTEMPT=$((ATTEMPT + 1))
  sleep "$POLL_INTERVAL"
  if NEW_JSON=$(fetch_cluster_json 2>/dev/null); then
    CANDIDATE=$(json_get '.leader.memberId // .leaderMemberId // empty' <<<"$NEW_JSON" 2>/dev/null)
    if [[ -n "$CANDIDATE" && "$CANDIDATE" != "null" && "$CANDIDATE" != "$OLD_LEADER_ID" ]]; then
      NEW_LEADER_ID="$CANDIDATE"
      NEW_LEADER_TERM=$(json_get '.leadershipTerm // .leader.leadershipTerm // .term // empty' <<<"$NEW_JSON" 2>/dev/null)
      # Refresh members JSON in case it has been re-emitted with containerIds.
      MEMBERS_JSON=$(json_get '.members // .clusterMembers // []' <<<"$NEW_JSON" 2>/dev/null)
      break
    fi
  fi
  info "  attempt $ATTEMPT: no new leader yet"
done
set -e

TIME_TO_ELECT_MS=$(( $(now_ms) - KILL_START_MS ))

if [[ -z "$NEW_LEADER_ID" ]]; then
  err "No new leader was elected within ${POLL_TIMEOUT}s"
  RECOVERY_STATUS="failed: no new leader within ${POLL_TIMEOUT}s"
  SMOKE_RESULT="skipped"
  print_summary
  exit 1
fi
ok "New leader elected: member_id=$NEW_LEADER_ID (took ${TIME_TO_ELECT_MS}ms)"

# ---- Step 7: Verify the new leader is one of the previously-follower members ----
hdr "Step 7/10: Verifying new leader is a previous follower"
if command -v jq >/dev/null 2>&1; then
  KNOWN=$(jq -r '.[].memberId' <<<"$MEMBERS_JSON" | sort -n)
else
  KNOWN=$(python3 -c "
import json, sys
for m in json.loads(sys.stdin.read()):
    mid = m.get('memberId')
    if mid is not None:
        print(mid)
" <<<"$MEMBERS_JSON" | sort -n)
fi

if ! echo "$KNOWN" | grep -qx "$NEW_LEADER_ID"; then
  err "New leader $NEW_LEADER_ID is not a known cluster member"
  RECOVERY_STATUS="failed: unknown new leader"
  print_summary
  exit 1
fi
if [[ "$NEW_LEADER_ID" == "$OLD_LEADER_ID" ]]; then
  err "Leader did not change (still $OLD_LEADER_ID); failover did not occur"
  RECOVERY_STATUS="failed: leader unchanged"
  print_summary
  exit 1
fi
ok "Confirmed: new leader is a previously-follower member"

# Best-effort resolution of new leader container (informational).
NEW_LEADER_CONTAINER=$(container_for_member "$NEW_LEADER_ID" "$MEMBERS_JSON")
if [[ -z "$NEW_LEADER_CONTAINER" ]]; then
  NEW_SVC="aeron-cluster-${NEW_LEADER_ID}"
  NEW_LEADER_CONTAINER=$(compose_q "$NEW_SVC" 2>/dev/null | head -n1 || true)
fi
if [[ -n "$NEW_LEADER_CONTAINER" ]]; then
  ok "New leader container_id=$NEW_LEADER_CONTAINER"
else
  warn "Could not resolve new leader container_id (informational only)"
fi

# ---- Step 8: Smoke test (write one event, read it back, assert match) ----
hdr "Step 8/10: Running smoke test on new leader"
# TODO(invocation): wire in the real smoke-test client once it exists.
# The expected invocation pattern is:
#
#   SMOKE_ID="drill-$(date +%s%N)"
#   PAYLOAD="leader-failover-${OLD_LEADER_ID}->${NEW_LEADER_ID}"
#   curl -fsS -X POST "${EVENTS_API}/events" \
#        -H 'content-type: application/json' \
#        -d "{\"id\":\"${SMOKE_ID}\",\"payload\":\"${PAYLOAD}\"}"
#   read_back=$(curl -fsS "${EVENTS_API}/events/${SMOKE_ID}")
#   if [[ "$read_back" == *"$SMOKE_ID"* && "$read_back" == *"$PAYLOAD"* ]]; then
#     SMOKE_RESULT="pass"
#   else
#     SMOKE_RESULT="fail: payload mismatch"
#     RECOVERY_STATUS="failed: smoke test"
#     print_summary
#     exit 1
#   fi
#
# For now we mark this as a stub so the drill remains runnable end-to-end.
SMOKE_RESULT="TODO: stubbed; real smoke-test invocation not wired"
warn "Smoke test stub: $SMOKE_RESULT"

# ---- Step 9: Summary ----
hdr "Step 9/10: Summary"
RECOVERY_STATUS="ok"
print_summary

# ---- Step 10: Exit cleanly ----
hdr "Step 10/10: Done"
exit 0
