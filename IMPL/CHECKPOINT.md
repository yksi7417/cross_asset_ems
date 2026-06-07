# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **Last completed task:** 1.5 — VenueSession FSM (conform Gemma draft to schema)
- **Last commit (main):** `task(1.5): annotate sha e903037`
- **Last commit sha (main):** `a72c1ef`
- **Tasks merged/marked this session:** 1.2 (Order FSM + validator), 1.3 (Route FSM), 1.5 (VenueSession FSM) — 3 substantive commits + 3 annotation commits
- **In-progress task:** _(none)_
- **WIP branch:** main
- **Last updated:** 2026-06-06
- **Next task for Claude (when budget resets):** **1.4** (Multi-leg FSM, blocks: 1.1 ✓) OR **1.6** (SOR FSM, blocks: 1.1 ✓) OR **0.7** (Aeron ping/pong, heavy infra). Recommended: 1.4 → 1.6 to complete the Phase 1 FSM spine before tackling Aeron infra.
- **Total progress:** **26 of 150 tasks [x]** (17.3%). Phases with at least one [x]: 0, 1 (partial: 1.1/1.2/1.3/1.5), 2, 3, 4, 6, 13, 14.
- **Hold-pending-rework branches:** 4.11 (InstrumentCore byte mismatch), 6.4/6.5 (depend on 6.1 prefix migration just landed), 13.2-13.4 (dashboard/port issues), 11.2-11.10 (need careful FIX review).

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
| `wip/6.4-validation-rules` | 6.4 | (1 commit) | ready | 185 per-asset validation rules across 8 YAML files; 155 distinct reject codes (some fabricated pending 6.1 main-merge) |
| `wip/6.5-validator-golden` | 6.5 | (1 commit) | ready | 47 JSON test fixtures across 10 categories (one file per category) |
| `wip/13.2-13.4-observability` | 13.2, 13.3, 13.4 | (1 commit) | ready | 13 observability files: OpenSearch pipeline, Logstash conf, OpenSearch template, Filebeat input, Prometheus scrape + 5 service exporter samples, 3 Grafana dashboards. Caveats: Grafana dashboards under-delivered on panel count (9/6/9 vs 12/24/12 target); prometheus.yml has wrong target ports and missing jobs — needs follow-up pass. |
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

| Branch | Task ID | Reset at |
|---|---|---|
| _(none)_ | | |

## See also

- [[PLAN]] (task queue)
- [[LOOP]] (consumer of this cursor)
- [[HERMES]] (notifications that reference this cursor)
