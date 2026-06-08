---
active: true
iteration: 1
maxIterations: 100
sessionId: ses_15aa4658affeFL5ye5NKxqZv10
---

once previous task is complete, Read IMPL/PLAN.md and IMPL/CHECKPOINT.md. Find the next unchecked [ ] task whose ← blocks: prerequisites are all [x] complete. Skip [~] tasks (in-progress elsewhere), [!] tasks (blocked), and any (opus) tasks — leave opus as [ ], do not claim them. Apply IMPL/DELEGATION.md to select tier 1–3 model and switch the OpenCode model to the matching provider (gemma → Google, minimax → OpenCode Zen, sonnet → GitHub Copilot). Implement, write tests, run tests, commit with conventional-commit message, mark [x] (sha) in PLAN.md, advance IMPL/CHECKPOINT.md. If a phase boundary completes (last task in phase goes [x]), post Hermes notification per IMPL/HERMES.md. Continue to the next task. Stop conditions: (1) 3 or more substantive commits this session, (2) conversation exceeds ~100 messages, (3) context above 80% — when any triggers, gracefully wrap up the current task (commit partial work to wip/<task-id> branch, mark task [~] not [x]), post Hermes "session-ending" notification with cursor, and stop. Do not push to main on partial work. If ALL remaining unblocked non-opus tasks are exhausted, post "OpenCode idle — Claude Code session needed" to Hermes and stop.