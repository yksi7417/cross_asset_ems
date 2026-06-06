# Hermes Notifications

Discord notifications during the build loop, posted via the local `hermes` CLI. **No new dependencies** — `hermes` is already in PATH and configured with Discord targets.

## Channel routing

| Event | Channel | Severity |
|---|---|---|
| Phase complete | `discord:#fin-alerts` | medium |
| MVP complete | `discord:#fin-alerts` | high |
| Session ending (pacing wrap-up) | `discord:#ccc-software-paperclip` | low |
| Task escalated to Claude after Gemma failure | `discord:#ccc-software-paperclip` | low |
| Task blocked (`[!]`) | `discord:#fin-alerts` | high |
| Build / test breakage | `discord:#fin-alerts` | high |
| Abandoned wip branch detected | `discord:#ccc-software-paperclip` | medium |
| Daily summary at 18:00 NY | `discord:#the-long-game` | low |

Channels were verified via `hermes send --list` against the local Hermes config. If a channel changes name, update this table only — the rest of the loop is unaffected.

## Command shape

All notifications go through `hermes send`. The basic shape:

```bash
hermes send --to <channel> --subject "<short header>" --file <body-path>
```

Or for a single-line message:

```bash
echo "<message>" | hermes send --to <channel> --subject "<header>"
```

Use `--quiet` to suppress stdout success messages in scripts.

## Templates

### Phase complete

```bash
PHASE="$1"
LAST_TASK="$2"
COMMITS=$(git log --oneline --since="6 hours ago" | wc -l)

cat > /tmp/ems-phase-complete.md <<EOF
**Phase $PHASE complete.**

Last task: $LAST_TASK
Commits in this phase: $COMMITS
Cursor advanced to: $(grep -E "^- \[ \]" IMPL/PLAN.md | head -1 | sed 's/^- //')

Next phase queue:
$(grep -A 1 "^## Phase" IMPL/PLAN.md | grep "^## Phase" | head -3)
EOF

hermes send --to discord:#fin-alerts \
  --subject "[EMS] Phase $PHASE done" \
  --file /tmp/ems-phase-complete.md \
  --quiet
```

### Session ending (pacing wrap-up)

```bash
CURSOR=$(grep -E "^- \[[ ~]\]" IMPL/PLAN.md | head -1)
COMMITS=$(git log --oneline --since="1 hour ago" | wc -l)
TRIGGER="$1"  # e.g. "3-commit cap", "100-message cap", "80% context"

cat > /tmp/ems-session-end.md <<EOF
**Session ending: $TRIGGER**

Commits this session: $COMMITS
Cursor: $CURSOR
WIP branch: $(git branch --show-current)

Resume: run /goal again. Loop will read IMPL/CHECKPOINT.md and pick up here.
EOF

hermes send --to discord:#ccc-software-paperclip \
  --subject "[EMS] Session paused" \
  --file /tmp/ems-session-end.md \
  --quiet
```

### Task blocked

```bash
TASK="$1"      # e.g. "11.5"
REASON="$2"

cat > /tmp/ems-task-blocked.md <<EOF
**Task blocked: $TASK**

Reason: $REASON
Tier escalation attempted: $(grep "$TASK" IMPL/PLAN.md)

Needs human review:
- (a) unblock and reset to [ ]
- (b) re-decompose into smaller tasks
- (c) defer to v1 in PLAN.md
EOF

hermes send --to discord:#fin-alerts \
  --subject "[EMS] BLOCKED: task $TASK" \
  --file /tmp/ems-task-blocked.md \
  --quiet
```

### MVP complete

```bash
cat > /tmp/ems-mvp-complete.md <<EOF
**v0 MVP complete.**

All phases 0-14 marked [x].

End-to-end smoke: $(grep "End-to-end smoke" IMPL/PLAN.md | head -1)

Total commits: $(git log --oneline | wc -l)
Total time: <calculate from CHECKPOINT history>

v1 starts when ready. Set new /goal with phase 15+ scope.
EOF

hermes send --to discord:#fin-alerts \
  --subject "[EMS] v0 MVP READY 🚀" \
  --file /tmp/ems-mvp-complete.md
```

### Daily summary

A cron job (or Hermes' own scheduler) at 18:00 NY can post a daily roll-up. The loop itself does NOT post daily summaries — that's a separate concern.

```bash
COMPLETED_TODAY=$(grep "^- \[x\]" IMPL/PLAN.md | grep -c "$(date -I)")
IN_PROGRESS=$(grep "^- \[~\]" IMPL/PLAN.md | wc -l)
NEXT=$(grep "^- \[ \]" IMPL/PLAN.md | head -3 | sed 's/^- //')

cat > /tmp/ems-daily.md <<EOF
**EMS build daily summary**

Tasks completed today: $COMPLETED_TODAY
Tasks in progress: $IN_PROGRESS
Phase progress: $(grep -c "^- \[x\]" IMPL/PLAN.md) / $(grep -cE "^- \[[ x~]\]" IMPL/PLAN.md)

Next up:
$NEXT
EOF

hermes send --to discord:#the-long-game \
  --subject "[EMS] $(date +%Y-%m-%d) daily" \
  --file /tmp/ems-daily.md \
  --quiet
```

## Verification before first use

Before relying on this in the loop, run a smoke:

```bash
echo "EMS notification smoke test" | hermes send --to discord:#hermes --quiet
```

This goes to `#hermes` rather than `#fin-alerts` so a test ping isn't noisy in the real alert channel.

If the smoke fails, the loop falls back to **stdout logging only** — it does NOT block on notification failures. Notifications are a courtesy, not a correctness requirement.

## Rate limiting

Discord has rate limits (~5 messages/sec per channel for bots). The loop produces a handful of notifications per hour at most. Not a concern.

If a Hermes API call hangs (rare), the loop should `timeout 10s hermes send ...` so a stuck call doesn't block forever.

## See also

- [[LOOP]] (when each notification fires)
- [[PLAN]] (what cursor data each notification references)
- [[CHECKPOINT]] (state cursor referenced in session-ending notification)
- Hermes docs: `hermes send --help`, `hermes send --list`
