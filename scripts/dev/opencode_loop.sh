#!/usr/bin/env bash
# opencode_loop.sh — restart OpenCode sessions until all tier 1-3 tasks are done.
#
# Stops when:
#   - No [ ] tasks tagged (gemma), (minimax), or (sonnet) remain in PLAN.md
#   - OpenCode exits non-zero
#   - No new git commits in 2 consecutive sessions (all available tasks blocked by opus prereqs)
#   - MAX_SESSIONS reached
#
# Usage: ./scripts/dev/opencode_loop.sh [--dry-run]

set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────
MAX_SESSIONS=50
SLEEP_BETWEEN=15          # seconds between sessions (rate-limit buffer)
MAX_STALL_SESSIONS=2      # stop after this many consecutive no-commit sessions
PLAN=IMPL/PLAN.md
LOG=logs/opencode_loop.log
LOCK=/tmp/opencode_ems.lock
DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

mkdir -p logs

# ── Lock: prevent two instances running at once ───────────────────────────
if [ -e "$LOCK" ]; then
  echo "ERROR: $LOCK exists (PID $(cat "$LOCK")). Another session may be running. Remove to force." >&2
  exit 1
fi
echo $$ > "$LOCK"
trap 'rm -f "$LOCK"' EXIT INT TERM

# ── Helpers ───────────────────────────────────────────────────────────────
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG"; }

# [ ] tasks OpenCode can work on (gemma / minimax / sonnet)
oc_tasks_remaining() {
  grep -cE '^\- \[ \].*\((gemma|minimax|sonnet)' "$PLAN" || true
}

# [ ] tasks only Claude Code can do
opus_tasks_remaining() {
  grep -cE '^\- \[ \].*\(opus' "$PLAN" || true
}

# ── Main loop ─────────────────────────────────────────────────────────────
log "===== opencode_loop starting (max $MAX_SESSIONS sessions, dry_run=$DRY_RUN) ====="

session=0
stall=0

while [ $session -lt $MAX_SESSIONS ]; do
  session=$((session + 1))

  oc_left=$(oc_tasks_remaining)
  log "Session $session/$MAX_SESSIONS — $oc_left OpenCode-eligible [ ] tasks in $PLAN"

  # ── Stop condition: nothing left for OpenCode ──────────────────────────
  if [ "$oc_left" -eq 0 ]; then
    opus_left=$(opus_tasks_remaining)
    if [ "$opus_left" -gt 0 ]; then
      log "No (gemma/minimax/sonnet) tasks remain. $opus_left (opus) task(s) need a Claude Code session."
    else
      log "ALL tasks complete — EMS v0 done!"
    fi
    exit 0
  fi

  # ── Snapshot HEAD before running ──────────────────────────────────────
  sha_before=$(git rev-parse HEAD)

  # ── Run OpenCode ──────────────────────────────────────────────────────
  prompt=$(cat IMPL/OPENCODE_PROMPT.txt)

  if $DRY_RUN; then
    log "[dry-run] would run: opencode --prompt \"$(head -c 80 IMPL/OPENCODE_PROMPT.txt)...\""
  else
    opencode --prompt "$prompt" || {
      log "ERROR: opencode exited non-zero (exit code $?). Stopping loop."
      exit 1
    }
  fi

  # ── Stall detection: did we make any commits? ──────────────────────────
  sha_after=$(git rev-parse HEAD)
  if [ "$sha_before" = "$sha_after" ]; then
    stall=$((stall + 1))
    log "WARNING: no new commits this session (stall $stall/$MAX_STALL_SESSIONS)."
    if [ $stall -ge $MAX_STALL_SESSIONS ]; then
      log "Stall limit reached — all remaining non-opus tasks are likely blocked by unfinished opus prereqs."
      log "Start a Claude Code session to unblock them."
      exit 0
    fi
  else
    stall=0
    new_commits=$(git log --oneline "${sha_before}..HEAD" | wc -l)
    log "Session $session produced $new_commits new commit(s)."
  fi

  [ $session -lt $MAX_SESSIONS ] && sleep "$SLEEP_BETWEEN"
done

log "Reached MAX_SESSIONS=$MAX_SESSIONS without finishing. Check $PLAN and IMPL/CHECKPOINT.md."
exit 1
