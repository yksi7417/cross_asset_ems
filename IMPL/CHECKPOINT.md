# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **Last completed task:** 12.3 — Confirmation/Affirmation service (MVP Track C). Pure `MatchEngine` (keyed fields exact + price/qty/accrued tolerance; corp bond ½ tick); `ConfirmationService` state machine (Submitted→Matched/Unmatched→Disputed→Matched/Voided; affirmation request→received/rejected); pluggable `ConfirmationNetwork` SPI + `MockConfirmationNetwork` (mock MarketAxess Post-Trade); `ConfirmationStageHandler` bridges into 12.2's STP as the CONFIRMATION stage (match→COMPLETE / mismatch→ANOMALY). Event-sourced, deterministic. 12 tests green.
- **Last commit (main):** `feat(12.3): confirmation/affirmation service (MVP Track C)`
- **Last commit sha (main):** `b218e63`
- **Tasks merged/marked this session:** 8.1 `b3aa7ab`, 12.1 `31911bb`, 12.2 `bc92f1c`, 12.3 `b218e63` (Opus). Prior MVP session: 8.9 `981c33d`, 11.1 `1d95436`, 11.2 `7bb2739`; 7.7 `9e90812`.
- **In-progress task:** _(none)_
- **WIP branch:** main
- **Last updated:** 2026-06-10
- **MVP v0 track:** 11 [MVP] tasks. Done: **8.9, 11.1, 11.2, 8.1, 12.1, 12.2, 12.3**. Next buildable: **12.5** (reg reporting), **13.5** (distributed-trace verify ← 8.1+11.2). 12.6 (TRACE-mock) ← 12.5. 15.1 waits on 12.6/13.5.
- **Next task:** **12.5** Regulatory reporting → **12.6** TRACE-mock (plugs into STP as the `REGULATORY_REPORTING` handler); then **13.5** trace verify; **15.1** end-to-end smoke last.
- **Total progress:** **81 of 144 tasks [x]** (56.3%). MVP v0: 7 of 11 done (8.9, 11.1, 11.2, 8.1, 12.1, 12.2, 12.3).
- **Hold-pending-rework branches:** 4.11 (InstrumentCore byte mismatch), 6.4 (reject codes need catalog extension — field-format codes don't exist in catalog; design decision required before marking done), 13.4 (dashboards at 9/9/6 panels vs 24/12/12 targets), 11.2-11.10 (abandoned WIP branch — empty files, reset to `[ ]`).

## Open WIP branches

| Branch | Task | Status | Notes |
|---|---|---|---|
| `wip/6.4-validation-rules` | 6.4 | **needs rework** | 185 per-asset validation rules. Reject codes misaligned: uses sequential `EMS-ORD-1001/1002/…` for field-format checks, but catalog uses those codes for different semantics. Rules also cover concepts absent from catalog. Sonnet must decide: extend catalog with new codes, or remap to closest existing. Do not merge until reconciled. |

## Session log

The loop appends a one-line entry per session.

| Session start | Session end | Trigger | Commits | Completed tasks | Cursor at end |
|---|---|---|---|---|---|
| 2026-06-06 | 2026-06-06 | session-already-past-pacing-threshold (prior work) | 1 (`0e2fe6a`) | 0.1 | next = 0.2 |
| 2026-06-06 | 2026-06-06 | user out of Claude tokens; delegate to local Gemma / Gemini per DELEGATION.md | 11 (0.2-0.8 impl + docs refresh + checkpoint) | 0.2, 0.3, 0.4, 0.5, 0.6, 0.8 + README/DEVELOPMENT/KNOWLEDGE_BASE refresh | next = 0.7 (claude) OR local-tier tasks (0.9, 4.22-4.24, 6.1, instrument drafts) |
| 2026-06-06 | 2026-06-06 | 3-commit pacing trigger (loop wrap-up) | 4 substantive (4.23 feat + annotate, 4.24 cherry-pick + mark) | 4.23, 4.24 merged from wip/ branches | next = 4.5/4.6/4.9/4.13-15-16/4.22 reviews, then 0.7 (claude) |
| 2026-06-06 | 2026-06-06 | 3-commit pacing trigger (loop wrap-up) | 6 (3 feat + 3 task-annotate for 1.2/1.3/1.5) | 1.2, 1.3, 1.5 | next = 1.4 (MultiLeg FSM) or 1.6 (SOR FSM) |
| 2026-06-06 | 2026-06-06 | 3-commit pacing trigger (loop wrap-up) | 6 (3 feat + 3 task-annotate for 1.4/1.6/1.9) | 1.4, 1.6, 1.9 | next = 1.7 (codegen) or 1.10 (edge cases) |
| 2026-06-07 | 2026-06-07 | WIP branch audit + extraction | 3 substantive + 1 annotation | 13.2, 13.3 extracted; 6.5 regenerated (42 fixtures); 11.2-11.10 abandoned; 6.4 documented | next = 1.7 (opus) or 6.4 catalog extension (sonnet) |
| 2026-06-07 | 2026-06-07 | 3-commit pacing trigger (loop wrap-up) | 3 (aeron-cluster bump + claim, feat 0.7, task-annotate) | 0.7 done | next = 0.10 (sonnet) or 1.7/1.10 (opus) |
| 2026-06-07 | 2026-06-07 | 3-commit pacing trigger + Phase 0 complete | 4 (docs+obs, claim 0.10, feat 0.10, annotate) | 0.10 done; Phase 0 all [x] | next = 1.7 (opus) or 1.10 (opus) |
| 2026-06-07 | 2026-06-07 | context-resuming prior session | 3 (1.7b C++ inline FSMs + task annotation; 1.11 replay harness) | 1.7b `994d8fa`, 1.11 `32acdac` | next = 1.8 (gemma) or 1.10/1.12 |
| 2026-06-09 | 2026-06-09 | phase goal: complete Phase 5 | 5 (5.1–5.5 all committed) | 5.1 `66a79c7`, 5.2 `4316e34`, 5.3 `2aa7528`, 5.4 `dac894d`, 5.5 `ff8d71c` | Phase 5 complete; next = 4.12/4.14/4.17 |
| 2026-06-09 | 2026-06-09 | phase goal: complete Phase 6 | 4 (6.2 impl + annotate, 6.3 impl + annotate) | 6.2 `b099d4a`, 6.3 `b80b305` | Phase 6 partial (6.4 [~] locked); next = 4.12/4.14/4.17 or 7.1 |
| 2026-06-09 | 2026-06-09 | phase goal: complete Phase 7 | 1 (7.1 staged order manager) | 7.1 `8de5bab` | Phase 7 started; next = 7.2 Router Layer |
| 2026-06-10 | 2026-06-10 | phase goal: complete Phases 7/8/9 | 3 (7.2 router layer, 7.8 lifecycle e2e, 7.3 automation layer) | 7.2 `6b9163c`, 7.8 `bc1b34e`, 7.3 `0149917` | Phase 7 continued; next = 7.4/7.5 |
| 2026-06-10 | 2026-06-10 | user-directed jump to 7.7 (opus tier) | 1 (7.7 Appendix D race tests + Route FSM fill-race transitions) | 7.7 `9e90812` | Phase 7 partial (7.4–7.6 pending); next = 7.4/7.5 |
| 2026-06-10 | 2026-06-10 | MVP v0 scoping + first MVP task | 3 (api-first doc/plan `a9b8fba`, MVP plan `a938411`, 8.9 `981c33d`) | 8.9 done; MVP v0 track defined (11 tasks) | next = 8.1 / 11.1 / 12.1 / 12.5 (MVP, parallel) |
| 2026-06-10 | 2026-06-10 | MVP v0 build (3-commit pacing trigger) | 3 (8.9 prior; 11.1 `1d95436`, 11.2 `7bb2739`) | 11.1, 11.2 — MVP Track B (venue) complete | next = 8.1 (Track A) or 12.1/12.5 (Track C) |
| 2026-06-10 | 2026-06-10 | MVP v0 build (Opus, user-directed local execution); 3-commit pacing stop | 3 (8.1 `b3aa7ab`, 12.1 `31911bb`, 12.2 `bc92f1c`) | 8.1 — client FIX edge (Track A); 12.1 — allocation service; 12.2 — STP pipeline orchestrator | next = 12.3 confirmation (← 12.2) / 12.5→12.6 / 13.5 trace verify |

## Phase progress

Updated when a phase completes.

| Phase | Status | Completed at |
|---|---|---|
| 0 — Bootstrap | complete | 2026-06-07 |
| 1 — FSM Foundation | complete | 2026-06-07 |
| 2 — Transport | complete | 2026-06-08 |
| 3 — Event Sourcing | complete | 2026-06-09 |
| 4 — Reference Data | partial (4.12/4.14/4.17 pending) | |
| 5 — Identity & Permissions | complete | 2026-06-09 |
| 6 — Validator | partial (6.4 [~] locked; 6.1/6.2/6.3/6.5 done) | |
| 7 — OMS Core | partial (7.1 done; 7.2–7.8 pending) | |
| 8 — FIX / API Bridge | partial (8.1, 8.9 done; 8.2-8.8/8.10-8.11 pending) | |
| 9 — Market Data | not started | |
| 10 — Pre-Trade Auxiliaries | not started | |
| 11 — Venue Connectivity | not started | |
| 12 — Post-Trade | not started | |
| 13 — Observability | not started | |
| 14 — Operations | not started | |

## Blocked tasks

Loop adds `[!]` tasks here for human review.

| Task ID | Reason | First flagged |
|---|---|---|
| _(none)_ | | |

## Abandoned wip branches

Loop adds entries when a wip branch is older than 48h and gets reset.

| Branch | Task ID | Reset at | Reason |
|---|---|---|---|
| `wip/11.2-11.10-fix-adapters` | 11.2–11.10 | 2026-06-07 | All 10 Java files were empty (1 newline each). Commit message described 1910 lines that were never written. Tasks reset to `[ ]`. |

## See also

- [[PLAN]] (task queue)
- [[LOOP]] (consumer of this cursor)
- [[HERMES]] (notifications that reference this cursor)
