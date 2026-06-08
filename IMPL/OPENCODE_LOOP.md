# OpenCode Overnight Loop

Companion to [[LOOP]]. Same goal, same rules — except this session runs **tiers 1–3 only** (Gemma, MiniMax, Sonnet). Opus tasks are skipped with a note and picked up in a Claude Code session later.

---

## The prompt

Paste this into OpenCode's `/goal` (or system prompt field):

```text
Read IMPL/PLAN.md and IMPL/CHECKPOINT.md. Find the next unchecked [ ] task whose ← blocks: prerequisites are all [x] complete. Skip [~] tasks (in-progress elsewhere), [!] tasks (blocked), and any (opus) tasks — leave opus as [ ], do not claim them. Apply IMPL/DELEGATION.md to select tier 1–3 model and switch the OpenCode model to the matching provider (gemma → Google, minimax → OpenCode Zen, sonnet → GitHub Copilot). Implement, write tests, run tests, commit with conventional-commit message, mark [x] (sha) in PLAN.md, advance IMPL/CHECKPOINT.md. If a phase boundary completes (last task in phase goes [x]), post Hermes notification per IMPL/HERMES.md. Continue to the next task. Stop conditions: (1) 3 or more substantive commits this session, (2) conversation exceeds ~100 messages, (3) context above 80% — when any triggers, gracefully wrap up the current task (commit partial work to wip/<task-id> branch, mark task [~] not [x]), post Hermes "session-ending" notification with cursor, and stop. Do not push to main on partial work. If ALL remaining unblocked non-opus tasks are exhausted, post "OpenCode idle — Claude Code session needed" to Hermes and stop.
```

---

## Model switching in OpenCode

Switch before each task using the task's tier tag:

| Tag | Provider | Model |
|-----|----------|-------|
| `(gemma)` | Google | Gemma 4 31B |
| `(minimax)` | OpenCode Zen | MiniMax M3 |
| `(sonnet)` | GitHub Copilot | Sonnet 4.6 |

Switch with OpenCode's `/models` picker, or:

```bash
opencode run -m google/gemma-4-31b       # gemma tasks
opencode run -m openrouter/minimax-m3    # minimax tasks
opencode run -m copilot/claude-sonnet-4-6  # sonnet tasks
```

Do NOT attempt `(opus)` tasks. See the skip rule below.

---

## Loop steps

### 1. Initialize

```bash
cd ~/dvlp/cross_asset_ems
```

Read `IMPL/CHECKPOINT.md` for the current cursor. Read `IMPL/PLAN.md` for the task queue.

### 2. Find the next task

Scan `IMPL/PLAN.md` for the first `[ ]` task where:

- All `← blocks:` prerequisites are `[x]`
- The task is **not** tagged `(opus)`
- The task is **not** `[~]` (in-progress elsewhere) or `[!]` (blocked)

If the next unblocked task is `(opus)`, skip it and look at the task after it. Opus tasks don't get claimed — leave them `[ ]` for Claude Code.

**Priority hint:** tasks with no prerequisites unblock the most downstream work and should be picked first when multiple options are available. Currently many "no prereqs" tasks exist in phases 4–14.

### 3. Claim the task

Edit `PLAN.md`: change `[ ]` to `[~]`. Commit immediately:

```bash
git add IMPL/PLAN.md && git commit -m "task: claim X.Y"
git push origin main
```

This prevents two sessions from racing on the same task.

### 4. Select model and implement

Look at the tier tag, switch models (step above), then implement the task:

- Write the code
- Write tests in the same commit (or immediately after if the task is large)
- Run tests: `./gradlew test` (Java) or `cmake --build build && ctest` (C++)
- If tests fail and you're on Gemma → escalate to MiniMax. If still failing → escalate to Sonnet.
- Never loop at the same tier more than once.

### 5. Commit and mark done

Conventional commit format: `feat(<phase>.<task>): <what>`

Example: `feat(4.1): symbology service — FIGI lookup + licensed secondaries`

Then:

```bash
git add <changed files>
git commit -m "feat(X.Y): ..."
git push origin main
```

Edit `IMPL/PLAN.md`: change `[~]` to `[x] (sha)`.
Advance `IMPL/CHECKPOINT.md`: update last-completed, last-commit-sha, next-task, total-progress.

Commit the plan/checkpoint update:

```bash
git add IMPL/PLAN.md IMPL/CHECKPOINT.md
git commit -m "task(X.Y): mark [x] <sha>"
git push origin main
```

### 6. Hermes notification (conditional)

Only post if the task completed a phase (last `[ ]` in the phase goes `[x]`). See `IMPL/HERMES.md` for the command.

Routine tasks: silent.

### 7. Check stop conditions

After every commit, check:

1. **3+ substantive commits this session** → wrap up
2. **~100 messages exchanged** → wrap up
3. **Context window above 80%** → wrap up

If none triggered, return to step 2 and pick the next task.

### 8. Wrap up

If a task is mid-flight (`[~]`), commit partial work to `wip/<task-id>` branch — do NOT leave it in `main`. Reset PLAN.md to `[ ]` if nothing was committed.

Post a session-ending Hermes notification:

```bash
echo "OpenCode overnight session ending. Cursor: $(grep 'Last completed' IMPL/CHECKPOINT.md). Trigger: $TRIGGER" \
  | hermes send --to discord:#ccc-software-paperclip --subject "[EMS] OpenCode session paused" --quiet
```

Update `IMPL/CHECKPOINT.md` with the cursor and session log entry.

---

## Opus-task skip rule

When the next unblocked task is tagged `(opus)`:

1. Leave it as `[ ]` — do not claim it.
2. Skip to the next non-opus unblocked task.
3. If ALL remaining unblocked tasks are opus, post to Hermes:

   ```bash
   echo "All unblocked tasks are (opus). OpenCode loop idle — Claude Code session needed." \
     | hermes send --to discord:#ccc-software-paperclip --subject "[EMS] OpenCode idle"
   ```

4. Stop.

Current opus tasks that are unblocked: **1.10**, **2.5**, **3.4**.

---

## Escalation rule

| Situation | Action |
|-----------|--------|
| Gemma draft → tests red | Escalate to MiniMax with draft + errors as input |
| MiniMax → tests red | Escalate to Sonnet with draft + errors as input |
| Sonnet → tests red after 2 attempts | Mark task `[~]`, commit to `wip/<task-id>`, note in CHECKPOINT. Do not mark `[x]`. |
| Any task needs file-system tools + complex reasoning | Sonnet handles it (it has full tool use in OpenCode) |

---

## Priority queue (unblocked right now, non-opus)

Highest downstream leverage first:

```text
# Phase 4 — no prereqs, unlock 4.2/4.3/4.4/4.18/4.19 chain
4.1  (sonnet)  Symbology service (FIGI + licensed secondaries)
4.21 (sonnet)  Reference data service (calendars, day counts, tick sizes)
4.12 (sonnet)  TbaMbsInstrument / SpecifiedPoolInstrument SBE template
4.14 (sonnet)  StructuredProductInstrument SBE template
4.17 (sonnet)  EventContractInstrument template (prediction markets)

# Phase 5 — no prereqs, unlocks 5.2/5.3/5.4 chain
5.1  (sonnet)  AAA service skeleton

# Phase 6 — no prereqs, unlocks 6.3 and 7.1 chain
6.2  (sonnet)  Layered evaluation pipeline

# Phase 8 — no prereqs, unlocks 8.2/8.3 chain
8.1  (sonnet)  FIX gateway in (inbound from buy-side)

# Phase 11 — no prereqs, unlocks 11.2–11.14 chain
11.1 (sonnet)  Venue adapter framework
11.11(sonnet)  Smart Order Router

# Phase 12 — no prereqs
12.1 (sonnet)  Allocation service
12.5 (sonnet)  Regulatory reporting skeleton
12.10(sonnet)  Best execution audit
12.11(sonnet)  Per-pod/per-firm jurisdiction routing

# Phase 9/10 — no prereqs
9.4  (sonnet)  IOI service
9.5  (sonnet+gemma)  Real-time analytics (VWAP, TWAP, PWP, arrival)
10.1 (sonnet)  Compliance service

# Phase 13/14 — no prereqs
13.5 (sonnet)  Distributed-trace verification
13.6 (sonnet)  Sampling strategy
14.1 (sonnet)  JMX introspection
14.5 (sonnet)  Blue/green switchover protocol
14.10(sonnet)  Quarterly cross-region failover drill

# Phase 2/3 — prereqs met
2.7  (sonnet)  Schema evolution test                  (← 2.1 ✓)
3.2  (gemma)   Event log writer                       (← 3.1 ✓)
3.3  (sonnet)  Stream-id partitioning                 (← 3.1 ✓)

# Phase 1 — prereqs met
7.8  (sonnet)  Lifecycle chaining e2e test            (← 1.9 ✓)
```

---

## See also

- [[LOOP]] — full loop spec (shared rules)
- [[DELEGATION]] — tier definitions and escalation
- [[PLAN]] — the task queue
- [[CHECKPOINT]] — state cursor
- [[HERMES]] — notification commands
