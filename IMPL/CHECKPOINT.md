# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **🚀 MVP v0 COMPLETE + Phase 16 cross-asset coverage COMPLETE (2026-06-10).** MVP smoke (`MvpSmokeTest`) wires corp bond end-to-end with single trace ID + byte-identical replay. Phase 16 extends coverage to **7 asset classes** (US IG corp, treasury, US equity, preferred, listed fut/opt, FX spot, FX forward), each flowing allocation→STP→confirmation→reporting per its `AssetClassProfile` with single trace + replay (`CrossAssetSmokeTest`).
- **Last completed task:** 16.3 — Cross-asset end-to-end smoke. Parametrized over all `Coverage` labels; asserts per-asset stage sets (FX-spot confirms-not-reports, equity reports-not-confirms, corp/treasury both, FX-fwd/listed CFTC), single trace, full lot-sized allocation, byte-identical replay. Added `MockRegulatorAdapter` + `ReportingProfile.mock()`. 2 tests (7 asset classes each).
- **Last commit (main):** `feat(16.3): cross-asset end-to-end smoke (v1)`
- **Last commit sha (main):** `e944197`
- **Tasks merged/marked this session:** 16.1 `e3dde05`, 16.2 `dcd250c`, 16.3 `e944197` (cross-asset, Opus). Earlier this session: 8.1, 12.1, 12.2, 12.3, 12.5, 12.6, 13.5, 15.1 (MVP v0).
- **In-progress task:** _(none)_
- **WIP branch:** main
- **Last updated:** 2026-06-10
- **MVP v0 track:** **11 of 11 [MVP] tasks [x]** ✅. **Phase 16 (cross-asset): 3 of 3 [x]** ✅.
- **Active goal (set 2026-06-10, amended same day): v1 build-out, no delegation.** Complete the tasks that were `[ ]` in Phases 7 ✅, 8, 10, 14 — **Phase 9 deferred** (user decision: market data via the pluggable feed SPI 18.12 + Bloomberg adapter 18.13; 10.2 and 12.14 wait with 9.5) — plus **11.15** FIX venue simulator → **15.2** FIX-wire end-to-end smoke, then **Phase 17** usage docs. Fable (Claude Code) executes every task directly — goal text in [[LOOP]]. Carry-over intent folded in: 15.2 should drive the **real router + AAA-backed validator** path (replace the smokes' direct venue submit / permissive validator) and pull post-trade services off in-memory stubs where the flow demands it.
- **Front-end decision (2026-06-10, user):** trader desktop on **Perspective** (WASM streaming-pivot grid, github.com/perspective-dev/perspective) for high-rate market-data/blotter updates; market data via the pluggable SPI (18.12) with Bloomberg Desktop/Server API (18.13) first.
- **Next task:** **8.5** Batch operation semantics (← 8.4 `[x]`).
- **v1 build-out progress:** **Phase 7 COMPLETE (2026-06-10)** — 7.4 `535b37c` multi-leg manager (22 tests), 7.5 `e980553` aggregation (19 tests), 7.6 `898bbcb` FX netting (17 tests); catalog 44xx/5xxx/22xx blocks added, multileg codes realigned. **Phase 8:** 8.2 `7014bea` venue-facing FIX gateway (19 tests), 8.3 `6aca67e` tag-9700 trace propagation (7 tests), 8.4 `f1777fd` API session surface (13 tests).
- **Total progress:** **101 of 177 tasks [x]** (57.1%). MVP v0 + Phase 16 cross-asset + Phase 7 complete. (Count history: 95/174 after the 2026-06-10 loop-rework added 23 tasks; +3 more for the Perspective/Bloomberg desktop tasks 18.12–18.14; +6 done since: 7.4–7.6, 8.2–8.4.)
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
| 2026-06-10 | 2026-06-10 | MVP v0 completion run (Opus, /goal 10-commit cap) | 5 (12.3 `b218e63`, 12.5 `51e1713`, 12.6 `39a9a88`, 13.5 `6953c3c`, 15.1 `26f29d8`) | 12.3 confirmation, 12.5 reg reporting, 12.6 TRACE-mock, 13.5 trace verify, 15.1 end-to-end smoke — **MVP v0 COMPLETE** | next = v1 (US equity + USD IRS; real venues) |
| 2026-06-10 | 2026-06-10 | planning: loop rework + buyer's-lens gap analysis (Fable; no build tasks) | 1 (docs) | LOOP.md rewritten — no delegation, continuous run; goal reset to v1 build-out (Phases 7/8/9/10/14 + 11.15/15.2 + 17); PLAN +23 tasks (11.15–11.17, 12.12–12.16, 15.2, 17.1–17.3, 18.1–18.11); DELEGATION suspended; initial docs/USER_GUIDE.md | next = 7.4 |

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
| 7 — OMS Core | complete | 2026-06-10 |
| 8 — FIX / API Bridge | partial (8.1, 8.9 done; 8.2-8.8/8.10-8.11 pending — in active goal) | |
| 9 — Market Data | **deferred** (2026-06-10 user decision; Bloomberg SPI 18.12/18.13 covers the desktop) | |
| 10 — Pre-Trade Auxiliaries | not started — in active goal | |
| 11 — Venue Connectivity | partial (11.1, 11.2 done; 11.15 in active goal; 11.3–11.14, 11.16–11.17 queued) | |
| 12 — Post-Trade | partial (12.1–12.3, 12.5, 12.6 done; 12.4, 12.7–12.16 queued) | |
| 13 — Observability | partial (13.1-13.3, 13.5 done; 13.4 [~], 13.6 pending) | |
| 14 — Operations | partial (14.3, 14.4, 14.8, 14.9 done; rest in active goal) | |
| 15 — MVP Integration | partial (15.1 done 2026-06-10; 15.2 FIX-wire smoke in active goal) | |
| 16 — Cross-asset coverage | complete | 2026-06-10 |
| 17 — Usage Documentation | not started — in active goal (final leg) | |
| 18 — Trader Desktop & Buyer-Readiness | not started — queued as next goal | |

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
