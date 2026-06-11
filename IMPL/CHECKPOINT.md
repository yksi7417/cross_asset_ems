# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **Active goal (set 2026-06-11): Phase 18 — trader desktop & buyer-readiness.** Order: 18.12 → 18.13 → 18.1 → 18.14 → 18.2 → 18.3 → 18.4 → 18.6 → 18.7 → 18.8 → 18.9 → 18.10 → 18.5 → 11.17 → 18.11 (11.17 pulled in as 18.11's blocker per the queued next-goal definition; 18.5 late because its 10.2 input is deferred — pack will carry fat-finger as a deferred control or go `[!]`). Fable executes every task directly, no delegation; LOOP protocol per IMPL/LOOP.md.
- **Last completed task:** 18.6 — Borrow/locate `(9c3e89f)`. BorrowService (availability/locate/borrow/recall/accrual/attestation) + ShortSaleLocateCheck in the 10.1 gate (naked-short BLOCK w/ override path, HTB tag gate, short-exempt WARN). 10 tests.
- **In-progress task:** _(none — next: 18.7 intraday P&L)_
- **WIP branch:** main
- **Last updated:** 2026-06-11
- **Phase 18 progress:** 18.12 `068cbee`, 18.13 `8253d2f`, 18.1 `fefdc9a`, 18.14 `715c3d7`, 18.2 `bfdddba`, 18.3 `3554682`, 18.4 `6aa282c`, 18.6 `9c3e89f`. Remaining: 18.5, 18.7–18.11 (+11.17 for 18.11).
- **Front-end decision (2026-06-10, user):** trader desktop on **Perspective** (WASM streaming-pivot grid, github.com/perspective-dev/perspective) for high-rate market-data/blotter updates; market data via the pluggable SPI (18.12) with Bloomberg Desktop/Server API (18.13) first.
- **🏁 Prior goals:** MVP v0 ✅ (2026-06-10, `MvpSmokeTest` corp-bond end-to-end, single trace + replay) · Phase 16 cross-asset ✅ (7 asset classes, `CrossAssetSmokeTest`) · **v1 BUILD-OUT ✅ (2026-06-11 ~00:30 EDT)** — Phases 7/8/10-in-scope/14/17 + 11.15 + 15.2 all `[x]`, full suite green at goal close. Details in the session log + git history.
- **Total progress:** **134 of 177 tasks [x]** (75.7%). MVP v0, Phase 16, v1 build-out complete; Phase 18 underway.
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
| 2026-06-10 | 2026-06-10 20:20 EDT | v1 build-out run (Fable, continuous); paused on user token budget, resume 22:15 EDT | ~25 (11 feat + claims/marks) | **Phase 7 complete** (7.4 multi-leg, 7.5 aggregation, 7.6 netting), **Phase 8 complete** (8.2 venue FIX gw, 8.3 tag-9700, 8.4 API surface, 8.5 batch, 8.6 import, 8.7 export, 8.8 idempotent re-import, 8.10 REST edge, 8.11 parity), 10.1 compliance gate, 10.3 machine-gun; catalog 44xx/22xx/5xxx/2510; mid-run rescope: Phase 9 deferred, Perspective+Bloomberg SPI (18.12–18.14) | next = 10.4 |
| 2026-06-10 22:15 | 2026-06-11 ~00:30 EDT | scheduled resume (22:15 one-shot); ran to GOAL COMPLETE | ~30 (15 feat + claims/marks/docs) | **Phase 10 in-scope** (10.4 lists, 10.5 overrides, 10.7 positions, 10.6 risk, 10.8 pricing, 10.9 analytics), **11.15** FIX simulator, **15.2** FIX-wire smoke, **Phase 14** (14.1 introspection, 14.2 admin console, 14.5 switchover, 14.6 lease, 14.7 fenced creds, 14.10 region drill), **Phase 17** (17.1/17.2/17.3 docs) — **v1 BUILD-OUT GOAL COMPLETE**; 05:30 resume cancelled | next goal = Phase 18 (Perspective desktop + Bloomberg SPI) |

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
| 8 — FIX / API Bridge | complete | 2026-06-10 |
| 9 — Market Data | **deferred** (2026-06-10 user decision; Bloomberg SPI 18.12/18.13 covers the desktop) | |
| 10 — Pre-Trade Auxiliaries | in-scope complete (10.2 deferred w/ 9.5) | 2026-06-10 |
| 11 — Venue Connectivity | partial (11.1, 11.2, 11.15 done; 11.3–11.14, 11.16–11.17 queued) | |
| 12 — Post-Trade | partial (12.1–12.3, 12.5, 12.6 done; 12.4, 12.7–12.16 queued) | |
| 13 — Observability | partial (13.1-13.3, 13.5 done; 13.4 [~], 13.6 pending) | |
| 14 — Operations | complete | 2026-06-10 |
| 15 — MVP Integration | complete | 2026-06-11 |
| 16 — Cross-asset coverage | complete | 2026-06-10 |
| 17 — Usage Documentation | complete | 2026-06-11 |
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
