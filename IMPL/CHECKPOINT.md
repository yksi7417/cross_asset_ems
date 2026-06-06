# Build Loop Checkpoint

State cursor for the [[LOOP]]. Updated automatically by the agent at the end of every commit.

**Do not edit by hand** while the loop is running — race conditions vs the agent.

---

## Current cursor

- **Last completed task:** 0.8 — OpenTelemetry SDK + Jaeger toy trace
- **Last commit (main):** `feat(0.8): OTel toy trace + ems-observability application config`
- **Last commit sha (main):** `b86e7f8`
- **In-progress task:** _(none)_
- **WIP branch:** main
- **Last updated:** 2026-06-06
- **Next task for Claude (when budget resets):** 0.7 — Aeron Cluster + Archive toy ping/pong (claude)
- **Next tasks for local-tier delegation (parallel subagents via openRouter paid + Google Gemini free):** 4.22 holiday calendars, then SBE template drafts (4.5/4.6/4.9/4.11/4.13/4.15/4.16). Code review of bootstrap 0.1–0.8 via Gemini.

## WIP branches awaiting Claude review-and-merge

Drafted by openRouter (paid `minimax/minimax-m3`) and Google Gemini free tier while
Claude tokens are out. Claude to review and merge when budget resets.

| Branch | Task | Commit | Status | Notes |
|---|---|---|---|---|
| `wip/0.9-changelog` | 0.9 | `f90a549` | ready | cliff.toml + scripts/release/gen-changelog.sh |
| `wip/4.23-day-counts` | 4.23 | `ec73df9` | ready | 13 ISDA 2006 conventions in schemas/reference-data/day-counts.yaml |
| `wip/4.24-mic-codes` | 4.24 | `569cc47` + `ed13df4` | ready | 72 MICs + 25 brokers; 3 LEI flags (BNP NULL, ML INACTIVE, DBAB name mismatch) |
| `wip/6.1-reject-codes` | 6.1 | `ee3ec32` | ready | 47 reject codes across 10 categories in schemas/reject-codes/catalog.yaml |

## Session log

The loop appends a one-line entry per session.

| Session start | Session end | Trigger | Commits | Completed tasks | Cursor at end |
|---|---|---|---|---|---|
| 2026-06-06 | 2026-06-06 | session-already-past-pacing-threshold (prior work) | 1 (`0e2fe6a`) | 0.1 | next = 0.2 |
| 2026-06-06 | 2026-06-06 | user out of Claude tokens; delegate to local Gemma / Gemini per DELEGATION.md | 11 (0.2-0.8 impl + docs refresh + checkpoint) | 0.2, 0.3, 0.4, 0.5, 0.6, 0.8 + README/DEVELOPMENT/KNOWLEDGE_BASE refresh | next = 0.7 (claude) OR local-tier tasks (0.9, 4.22-4.24, 6.1, instrument drafts) |

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
