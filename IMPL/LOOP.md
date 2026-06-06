# The /goal Loop

How [[PLAN]] gets consumed task-by-task until the whole thing is done. Designed to be **resumable** (every session picks up where the last left off) and **token-paced** (graceful wrap-up before exhaustion, no half-applied commits).

## The /goal text

Paste this into `/goal` exactly. The Stop hook will block exit until the loop's wrap-up conditions trigger.

```
Read IMPL/PLAN.md. Find the next unchecked [ ] task whose ← blocks: prerequisites are all [x] complete (skip [~] tasks — they are in-progress in another session or locked). Apply IMPL/DELEGATION.md to select the tier. Implement, write tests, run tests, commit with conventional-commit message, mark [x] (sha1234) in PLAN.md, advance IMPL/CHECKPOINT.md. If a phase boundary completes (last task in a phase goes [x]), invoke Hermes notification per IMPL/HERMES.md. Continue to the next task. Stop conditions: (1) you have committed 3 or more substantive commits this session OR (2) the conversation has exceeded ~100 messages OR (3) /cost shows context above 80% — when any triggers, gracefully wrap up the current task (commit any partial work to a feature branch, mark task [~] not [x]), post the Hermes "session-ending" notification with the cursor, and stop. Do not push to main on a partial task. Do not start a new task with <20% expected token budget remaining.
```

That's the literal goal. It references the other files; they're the spec.

## Loop steps in detail

The agent runs this loop per session:

### 1. Initialize
- `cd ~/dvlp/cross_asset_ems`
- Read `IMPL/CHECKPOINT.md` to see the current cursor.
- Read `IMPL/PLAN.md` to find the next `[ ]` task with all prerequisites `[x]`.
- If nothing is unblocked (every `[ ]` task has a `[~]` or `[ ]` blocker), check whether all `[~]` tasks are stale (no commits in 24h). If so, reset them to `[ ]` and continue. Otherwise post "loop idle" Hermes notification and stop.

### 2. Pick the task
- Lock the task by editing PLAN.md to set `[~]` and committing immediately (`task: claim X.Y`).
- This prevents two parallel sessions from picking the same task.

### 3. Choose tier
- Look at the tag (`(local)`, `(local first draft, claude review)`, `(gemini)`, `(claude)`) per [[DELEGATION]].
- Set up the delegation if not Claude.

### 4. Execute
- Implement against the architecture notes referenced in the task description.
- Write tests in the same commit.
- Run tests locally — if green, proceed; if red, debug or escalate.

### 5. Commit + mark done
- Conventional commit: `<type>(<phase>.<task>): <what>` (e.g. `feat(1.1): FSM YAML schema with effects`).
- Reference the architecture notes the task depended on in the commit body.
- Mark `[x] (sha1234)` in PLAN.md.
- Advance CHECKPOINT.md.

### 6. Hermes notification (conditional)
- If this task completed a phase, post a `phase complete` notification per [[HERMES]].
- If the task is critical-path and significantly large (3+ commits in service of one task), post a `task complete` notification.
- Routine tasks: silent.

### 7. Token / session check
- After every commit, check estimated session usage.
- Hard wrap-up triggers:
  - 3+ substantive commits this session.
  - Conversation length > 100 messages.
  - Context window > 80%.
- Soft wrap-up triggers:
  - Last commit took >30 turns to land.
  - The next task in PLAN is tagged `(claude)` and looks large (3+ acceptance criteria).
- On any trigger, jump to step 8.

### 8. Wrap up
- If a task is in-progress (`[~]`), commit current partial work to a `wip/<task-id>` branch.
- Do NOT push to main on partial work.
- Update CHECKPOINT.md with the cursor.
- Post Hermes "session ending" notification.
- Exit.

### 9. Continue
- If wrap-up triggers haven't fired, return to step 2 and pick the next task.

## Recovery: how the next session resumes

The next /goal session reads CHECKPOINT.md, sees the cursor, and:

- If the cursor is on a `[~]` task: investigate the `wip/<task-id>` branch. If looks finishable, resume. If not, reset to `[ ]` and pick next.
- If the cursor is on a `[x]` task: advance to next `[ ]` task.

If a wip branch is older than 48h, the loop assumes it's abandoned, resets the task to `[ ]`, and posts a "abandoned wip detected" Hermes notification.

## Pacing — why these thresholds

- **3 commits per session**: empirically a typical Claude Code session produces 2-5 substantive commits before context fragmentation. Wrapping at 3 leaves headroom for one more if the next task is small.
- **100 messages**: roughly the point at which message-history compaction starts to harm new-task fidelity. Wrap before that.
- **80% context**: hard ceiling because /compact at this point loses important state.

If you want different pacing, edit these thresholds in the goal text and the CHECKPOINT seed.

## Loop continuity across sessions

The `/goal` command stays set across sessions until the goal condition is satisfied. The Stop hook blocks any session that tries to exit without completing the condition. Combined with the wrap-up triggers, this means:

- Each session works on PLAN.md.
- Hits wrap-up triggers → graceful exit with state preserved.
- Next session resumes from CHECKPOINT.md.
- Goal stays set; loop continues.

When ALL tasks are `[x]`, the loop posts a "v0 MVP complete" Hermes notification with a summary, and exits cleanly. The goal can then be cleared manually (`/goal clear`) or replaced with a v1 goal.

## What happens if a task is impossible

A task that can't be completed at any tier (missing external dependency, blocked by upstream, etc.) gets marked:

- `[!]` in PLAN.md (instead of `[ ]` / `[~]` / `[x]`).
- Posted to Hermes as `task blocked: X.Y - <reason>`.
- The loop skips `[!]` tasks and continues with the next `[ ]`.

A human review (you) determines whether to:
- Unblock and reset to `[ ]`.
- Re-decompose into smaller tasks that ARE possible.
- Defer to v1 (move below "Done criteria" line in PLAN.md).

## See also

- [[PLAN]] (the queue)
- [[DELEGATION]] (tier rules)
- [[HERMES]] (notifications)
- [[CHECKPOINT]] (state cursor)
