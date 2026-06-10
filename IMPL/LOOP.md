# The /goal Loop

How [[PLAN]] gets consumed task-by-task until the whole thing is done. Designed to be **resumable** (every session picks up where the last left off) and **continuous** (the loop runs until the goal is complete or genuinely blocked — not until an arbitrary pacing cap).

> **Execution model (since 2026-06-10): single executor, no delegation.** Every task runs directly on **Fable (Claude Code)** in the loop session. No OpenCode, no model switching, no local-LLM or subagent tiers, no Paperclip runners. [[DELEGATION]] is suspended (see its banner). `OPENCODE_LOOP.md`, `OPENCODE_PROMPT.txt`, and `.opencode/ralph-loop.local.md` are legacy artifacts of the old multi-model loop — kept for history, not used.

## Can the model run continuously? (reviewed 2026-06-10)

**Yes — within a session, indefinitely in practice; across sessions, via cheap re-entry.** What that rests on, and the honest caveats:

- **In-session:** Claude Code auto-compacts the conversation when it grows long; work continues in the next context window with a summary plus recent context. There is no message cap or context ceiling the loop must defend against, so the old OpenCode-era brakes (3 commits / 100 messages / 80% context) are removed. The real cost of compaction is loss of conversational nuance — neutralized here because all loop state is **durable**: git history, [[PLAN]] checkboxes, [[CHECKPOINT]] cursor. Rule: **after any compaction, re-read CHECKPOINT.md and PLAN.md before writing code.**
- **Across sessions:** the previous version of this file claimed a Stop hook + persistent `/goal` — **no such hook is configured in this repo's Claude Code settings** (that machinery was OpenCode's ralph-loop). Until one is added, cross-session continuity is manual but trivial: start a session, paste the goal text below (or run `/loop` with it for scheduled re-invocation); step 1 of the loop re-anchors on CHECKPOINT.md. A real Stop hook in `.claude/settings.json` can automate re-entry later if wanted.
- **Hard bounds that remain:** plan/usage limits and machine uptime. The wrap-up protocol below makes these safe: the loop commits per task, so at most one task is ever in flight, and an interrupted session loses at most the uncommitted work on that single task.

## The goal text

Paste this as the goal of a Claude Code session (or as the prompt of `/loop`):

```text
Read IMPL/PLAN.md and IMPL/CHECKPOINT.md. Goal: complete the v1 build-out scope — all tasks that
remain [ ] in Phases 7, 8, 9, 10 and 14 as of 2026-06-10, plus 11.15 (FIX venue simulator), 15.2
(FIX-wire end-to-end smoke) and Phase 17 (usage documentation). Execute every task yourself in this
Claude Code session (Fable): no OpenCode, no model switching, no delegation to local models,
subagents, or Paperclip; treat the (gemma)/(minimax)/(sonnet)/(opus) tags in PLAN.md as historical
complexity hints, not routing. Work in order 7 → 8 → 9 → 10 → 11.15 → 15.2 → 14 → 17, at each step
picking the next [ ] task whose "← blocks:" prerequisites are all [x]; skip [~] and [!] tasks.
Per task: claim it (mark [~] in PLAN.md, commit "task: claim X.Y"); implement against the
architecture notes the task references; write tests in the same commit; run the tests; commit
"<type>(X.Y): <what>"; mark [x] (sha) in PLAN.md; advance IMPL/CHECKPOINT.md; push to origin main
(standing permission). When the last task of a phase goes [x], update the phase table in
CHECKPOINT.md and optionally post the Hermes phase-complete notification per IMPL/HERMES.md with a
10s timeout — never block on it. Run continuously: no commit cap, no message cap, no context cap.
After any context compaction, re-read IMPL/CHECKPOINT.md and IMPL/PLAN.md before writing code, then
continue. Keep main green: if tests fail at a commit point, fix them before moving on; never push a
partial task to main — partial work goes to a wip/<task-id> branch with the task left [~]. Mark a
task [!] in PLAN.md and record it in CHECKPOINT.md only on a genuine blocker (missing external
dependency, or a design decision that needs a human); then continue with the next unblocked task.
Stop only when: (a) every in-scope task is [x] — update CHECKPOINT.md, write a goal-complete
summary, stop; (b) no in-scope task is startable (all remaining are [!] or [~] claimed elsewhere) —
record why in CHECKPOINT.md, stop; or (c) the session must end (usage limit / shutdown) — wrap up
per LOOP.md. Do not start refactors or tasks outside the scope.
```

That's the literal goal. It references the other files; they're the spec.

## Loop steps in detail

The agent runs this loop, task after task, until a stop condition:

### 1. Initialize

- `cd ~/dvlp/cross_asset_ems`
- Read `IMPL/CHECKPOINT.md` for the cursor, then `IMPL/PLAN.md` for the next in-scope `[ ]` task with all blockers `[x]`.
- If the cursor points at a `[~]` task with a `wip/<task-id>` branch: assess the branch; resume it if finishable, otherwise reset the task to `[ ]` (a wip branch older than 48h is abandoned — reset and note it in CHECKPOINT.md).

### 2. Claim the task

- Mark it `[~]` in PLAN.md and commit immediately (`task: claim X.Y`).
- Cheap insurance against a second concurrent session picking the same task; also makes the cursor durable before any code is written.

### 3. Execute

- Implement against the architecture notes the task references.
- Write tests in the same commit. Run them.
- Red tests are debugged here — there is no tier to escalate to. If genuinely stuck on a missing dependency or a human-level design decision, mark `[!]` (see below) and move on.

### 4. Commit + mark done + push

- Conventional commit: `<type>(<phase>.<task>): <what>` (e.g. `feat(7.4): multi-leg package handling`).
- Mark `[x] (sha1234)` in PLAN.md; advance CHECKPOINT.md (cursor, counts).
- Push `main` (standing permission). **Completed tasks only** — never push a partial task to main.

### 5. Notify (optional)

- Phase boundary → post the Hermes phase-complete notification per [[HERMES]], wrapped in `timeout 10s`. If `hermes` is missing or fails, log and continue — notifications are a courtesy, never a gate.

### 6. Re-anchor after compaction

- If the harness compacted context since the last task: re-read CHECKPOINT.md and PLAN.md before touching code. Durable state wins over summarized memory.

### 7. Continue or stop

- Loop back to step 2 for the next task.
- Stop conditions (from the goal text): all in-scope tasks `[x]` (goal complete) · nothing startable (all `[!]`/`[~]`) · session must end → wrap up.

## Wrap-up (only when the session must end)

- If a task is mid-flight (`[~]`), commit the partial work to `wip/<task-id>`. Do **not** push partial work to main.
- Update CHECKPOINT.md with the cursor and a one-line session log entry.
- Next session resumes from CHECKPOINT.md (step 1).

## Recovery: how the next session resumes

The next session reads CHECKPOINT.md, sees the cursor, and:

- Cursor on a `[~]` task → investigate its `wip/<task-id>` branch. Finishable → resume. Not → reset to `[ ]` and pick next.
- Cursor on a `[x]` task → advance to the next in-scope `[ ]` task.
- Any wip branch older than 48h → assume abandoned, reset the task to `[ ]`, record it in CHECKPOINT.md's abandoned-branch table.

## What happens if a task is impossible

A task that can't be completed (missing external dependency, blocked upstream, needs a human design decision) gets:

- `[!]` in PLAN.md (instead of `[ ]` / `[~]` / `[x]`), with the reason recorded in CHECKPOINT.md's blocked-tasks table.
- The loop skips `[!]` tasks and continues with the next `[ ]`.

A human review then decides: unblock and reset to `[ ]`, re-decompose into smaller tasks, or defer to a later goal.

## See also

- [[PLAN]] (the queue + the current goal scope)
- [[CHECKPOINT]] (state cursor)
- [[HERMES]] (optional notifications)
- [[DELEGATION]] (suspended — historical tier rules)
