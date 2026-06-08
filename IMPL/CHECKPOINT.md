# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **Last completed task:** 1.12 — Identity chaining stamping into FSM events (OrderFsm updated with initial_cl_ord_id, chain_id, and order_version; stamping effect added to OrderAccepted).
- **Last commit (main):** `feat(1.12): stamp identity chaining and versioning in OrderFsm`
- **Last commit sha (main):** `2faef35`
- **Tasks merged/marked this session:** 1.12 done `2faef35`
- **In-progress task:** _(none)_
- **WIP branch:** main
- **Last updated:** 2026-06-07
- **Next task:** **2.3** (Aeron channel layout, sonnet, unblocked by 2.1).
- **Total progress:** **45 of 144 tasks [x]** (31.3%). Phase 0 all [x]: 0.1–0.10.
- **Hold-pending-rework branches:** 4.11 (InstrumentCore byte mismatch), 6.4 (reject codes need catalog extension — field-format codes don't exist in catalog; design decision required before marking done), 13.4 (dashboards at 9/9/6 panels vs 24/12/12 targets), 11.2-11.10 (abandoned WIP branch — empty files, reset to `[ ]`).

## WIP branches awaiting Claude review-and-merge

Drafted by openRouter (paid `minimax/minimax-m3`) and Google Gemini free tier while
Claude tokens are out. Claude to review and merge when budget resets.

| Branch | Task | Commit(s) | Status | Notes |
|---|---|---|---|---|
| `wip/0.9-changelog` | 0.9 | `f90a549` | ready | cliff.toml + scripts/release/gen-changelog.sh |
| `wip/4.5-equity-instrument` | 4.5 | `5752d7c` | ready | EquityInstrument SBE template (template_id=0x2001) |
| `wip/4.6-bond-instrument` | 4.6 | `4159279` | ready | BondInstrument SBE template (template_id=0x2002) |
| `wip/4.9-fx-instruments` | 4.9 | (4 commits) | ready | 4 Fx templates (0x2040-0x2043): FxSpot, FxForward, FxSwap, FxNdf |
| `wip/4.11-listed-instruments` | 4.11 | (2 commits) | ready | 2 Listed templates (0x2030, 0x2031). Caveat: InstrumentCore is 22 fields/328 bytes (non-canonical); needs sync to 20/315 |
| `wip/4.13-4.15-4.16-sbe-batch` | 4.13, 4.15, 4.16 | (7 commits) | ready | 7 SBE templates: ABS, ConvertibleBond, Loan, CommodityFuture, CommodityPhysical, CryptoFungible, NFT. All use canonical 20/315 InstrumentCore. |
| `wip/4.22-calendars` | 4.22 | `56052aa`, `2322aad` | ready | 298 holiday entries + Gemini code review of bootstrap 0.1-0.8 |
| `wip/4.23-day-counts` | 4.23 | `ec73df9` | **merged** `1f67b28` | merged with isin_id→fpml_id fixup |
| `wip/4.24-mic-codes` | 4.24 | `569cc47`, `ed13df4` | **merged** `0001738` | 72 MICs + 25 brokers; follow-up: XCHI/MEMX naming nit |
| `wip/6.1-reject-codes` | 6.1 | `ee3ec32` | ready | 47 reject codes across 10 categories |
| `wip/6.4-validation-rules` | 6.4 | (1 commit) | **needs rework** | 185 per-asset validation rules, real YAML content. Reject codes misaligned: uses sequential `EMS-ORD-1001/1002/…` for field-format checks, but catalog uses those codes for different semantics (e.g. ORD-1001 = "Required field missing"). Rules also cover concepts absent from catalog. Sonnet must decide: extend catalog with new codes, or remap to closest existing code. |
| ~~`wip/6.5-validator-golden`~~ | 6.5 | `546d5ff` | **DONE** | Regenerated from scratch: 42 fixtures, 7 files, all catalog codes covered. Branch no longer needed. |
| ~~`wip/13.2-13.4-observability`~~ | 13.2, 13.3, 13.4 | `6c9601c` | **extracted** | 13.2/13.3 merged as `6c9601c`. 13.4 scaffold only (9/9/6 panels, targets 24/12/12). Branch no longer needed. |
| `wip/14.3-14.4-ui-scaffolds` | 14.3, 14.4 | (1 commit) | ready | 2 Next.js 14 / React / TypeScript scaffolds (34 files each): time-replay console + config-service admin console. Mock-data backed; runnable as-is. |
| `wip/14.8-14.9-drills` | 14.8, 14.9 | (1 commit) | ready | 2 drill scripts: weekly-leader-kill.sh (401 lines) + monthly-cold-start.sh (624 lines). Both follow set -euo pipefail, --dry-run + --help flags, configurable via env vars. |

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

## Phase progress

Updated when a phase completes.

| Phase | Status | Completed at |
|---|---|---|
| 0 — Bootstrap | not started | |
| 1 — FSM Foundation | not started | |
| 2 — Transport | not started | |
| 3 — Event Sourcing | not started | |
| 4 — Reference Data | not started | |
| 5 — Identity & Permissions | not started | |
| 6 — Validator | not started | |
| 7 — OMS Core | not started | |
| 8 — FIX / API Bridge | not started | |
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
