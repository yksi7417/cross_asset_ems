# Cross-Asset EMS — Implementation Plan

The build queue. **One source of truth** for what gets done, in what order, by which model tier. Work is driven through **OpenCode**, one model pinned per connected provider:

| Tag | Tier | OpenCode provider | Model | When |
|---|---|---|---|---|
| `(gemma)` | 1 — cheapest | Google | Gemma 4 31B | boilerplate, fixtures, config tables, first drafts |
| `(minimax)` | 2 — mid | OpenCode Zen | MiniMax 2.7 / 3 | review, research, docs, cross-language ports |
| `(sonnet)` | 3 — strong | GitHub Copilot | Sonnet 4.6 | most design + correctness work, orchestration |
| `(opus)` | 4 — apex | GitHub Copilot (Opus model) | Opus 4.x | crown jewels: replay determinism, consensus, FIX races (see below). Falls back to Sonnet if Opus isn't available. |

- Each task is one checklist line. `[ ]` = pending, `[~]` = in-progress (started, draft on a branch, or not yet committed), `[x]` = done.
- The tag after a task names its **tier**, not a hard-coded model — re-point a provider in OpenCode and the tiers still hold. See [[DELEGATION]] for what each tier may/may not do.
- `← blocks: a.b` records prerequisites. The loop must not start a task until its blockers are `[x]`.
- When marking `[x]`, append a short commit SHA: `[x] (abc1234)`.

**`(opus)` — reserved for the apex tier.** Use Opus only where a subtle error is *silent and catastrophic*: event-sourcing replay determinism, distributed consensus, and FIX cancel/replace race conditions. Everything else that needs strong reasoning is `(sonnet)`. Don't dilute the apex tier — if you're unsure, it's `(sonnet)`.

The loop protocol is in [[LOOP]]. Current cursor + open WIP branches in [[CHECKPOINT]]. (Hermes Discord notifications are optional/legacy under the OpenCode workflow.)

---

## Open WIP branches (weaker-model drafts in flight)

Four local branches hold draft work for the `[~]` tasks below. They predate the
current `main` and are **divergent** (branched off early FSM commits, before the
wrapper-jar / hybrid-CI / observability work landed). None are merged. They're a
starting point for the cheaper tiers, **not** merge-ready.

| Branch | Tasks | Contents | Carry-forward |
|---|---|---|---|
| `wip/6.4-validation-rules` | 6.4 | 185 per-asset validation rules, 8 files | `(sonnet)` reject-code reconciliation needed (see note below) |
| `wip/6.5-validator-golden` | 6.5 | 47 golden fixtures (wrong category/code scheme) | `(sonnet)` regen against actual catalog (see note below) |
| ~~`wip/11.2-11.10-fix-adapters`~~ | 11.2–11.10 | **ABANDONED** — all 10 files were empty (1 newline each). Reset to `[ ]`. | Fresh `(gemma)` boilerplate → `(sonnet)` review |
| ~~`wip/13.2-13.4-observability`~~  | 13.2–13.4 | **EXTRACTED** `6c9601c` — 13.2/13.3 done; 13.4 scaffolded (dashboards at 9/9/6 panels, targets 24/12/12) | 13.4 needs `(sonnet)` panel-count follow-up |

**6.4 reconciliation note:** rules use sequential `EMS-ORD-1001/1002/1003…` codes,
but those map to *different* concepts in `schemas/reject-codes/catalog.yaml` (e.g.
ORD-1001 = "Required field missing", not "ticker format invalid"). The rules also
cover concepts (field format, checksum, business-rule violations) that have no
catalog entry yet. A `(sonnet)` pass must decide: extend the catalog with new codes,
or remap rules to the closest existing code. Do not mark 6.4 `[x]` with the current codes.

**6.5 reconciliation note:** fixtures use a 10-category scheme (`SESS`, `IDENT`,
`PERM`, `REF`, `VAL`, `ROUT`, `CONF`, `AUTH`, `MKT`, `INFRA`) with sequential
0001-indexed codes, but the catalog has 7 categories (`SES`, `REF`, `PRM`, `ORD`,
`RTE`, `AUT`, `CFG`) with non-sequential codes. The fixture category names, code
strings, and numbering scheme are all misaligned. Regenerate from the actual catalog
entries rather than trying to patch-fix the existing files.

**Before continuing any branch:** rebase onto current `main` or cherry-pick the
useful commits onto a fresh branch — they diverged early and will conflict. Then
the carry-forward tier above picks up the draft.

**What the weaker tiers can pull from the open queue** (no branch yet): any
`[ ]`/`[~]` task tagged `(gemma)` or `(minimax)` whose blockers are all `[x]`.
Today that includes **1.8** (test generator, blocked on 1.7), **3.2** (event-log
writer), **8.4/8.6/8.7** (REST scaffold + bulk I/O), **9.5** (analytics math).
See [[DELEGATION]] for the per-task-type tier cheatsheet.

---

## Phase 0 — Bootstrap

Foundation for everything else. ~1-2 weeks of work.

- [x] **0.1** Monorepo layout (java/, cpp/, schemas/, infra/, docs/) (gemma) ← blocks: none `(0e2fe6a)`
- [x] **0.2** Multi-module Gradle/CMake workspace (gemma) ← blocks: 0.1 `(96ca57c)`
- [x] **0.3** Git hooks (pre-commit lint, conventional commits) (gemma) `(e6b411b)`
- [x] **0.4** GitHub Actions baseline (unit tests, lint, SBOM) (sonnet) ← blocks: 0.2 `(5283807)`
- [x] **0.5** Docker Compose dev environment (sonnet) ← blocks: 0.2 `(c74f4c4)`
- [x] **0.6** SBE codegen Gradle plugin wired up (sonnet) ← blocks: 0.2 `(ab1fd79)`
- [x] **0.7** Aeron Cluster + Archive toy ping/pong (sonnet) ← blocks: 0.6 `(d2bfd66)`
- [x] **0.8** OpenTelemetry SDK + collector + Jaeger toy trace (gemma) `(b86e7f8)`
- [x] **0.9** Conventional commit + changelog automation (gemma) `(27ce39f)`
- [x] **0.10** Phase-0 smoke test job in CI (sonnet) ← blocks: 0.4, 0.7 `(83b694f)`

## Phase 1 — Shared FIX-Compliant FSM

The core determinism guarantee. ~2-3 weeks.

- [x] **1.1** FSM YAML schema (states, events, transitions, effects) (sonnet) `(39169f8)`
- [x] **1.2** Order FSM definition per [[arch-order-route-lifecycle]] (sonnet) ← blocks: 1.1 `(ec378a8)`
- [x] **1.3** Route FSM definition per [[arch-order-route-lifecycle]] (sonnet) ← blocks: 1.1 `(1a8de92)`
- [x] **1.4** Multi-leg / Package FSM per [[arch-multileg]] (sonnet) ← blocks: 1.1 `(4708714)`
- [x] **1.5** VenueSession FSM (use existing Gemma draft in fsm/) (sonnet) ← blocks: 1.1 `(e903037)`
- [x] **1.6** SOR FSM (sonnet) ← blocks: 1.1 `(0917b01)`
- [x] **1.7** Codegen pipeline YAML → Java/C++ state structs (opus — foundational; all FSM code derives from it) ← blocks: 1.1 `(0f4a5b9)` **Note: Java complete; C++ headers committed as TODO stubs — transition impl deferred to 1.7b**
- [x] **1.7b** C++ FSM transition impl: fill `transition()` stubs in `cpp/fsm/generated/*.hpp`; compile-verify via CMake (sonnet) ← blocks: C++ simulator path `(994d8fa)`
- [x] (40deac8) **1.8** Unit test generator: YAML → ~5000 transition tests (gemma) ← blocks: 1.7
- [x] **1.9** Lifecycle chaining (Order cancel cascades to Route) tests (sonnet) ← blocks: 1.2, 1.3 `(b6cd1e4)`
- [x] **1.10** Pending Replace / Pending Cancel edge cases per [[arch-fix-appendix-d]] (opus — silent-race correctness) ← blocks: 1.9 `(6ce061e)`
- [x] **1.11** Replay determinism test harness (single FSM, replay log slice) (sonnet) ← blocks: 1.7 `(32acdac)`
- [x] **1.12** Identity chaining stamping into FSM events per [[arch-identity-chaining]] (sonnet) ← blocks: 1.7 `(2faef35)`

## Phase 2 — Transport (SBE + Aeron)

Wire protocol and clustering. ~1-2 weeks.

- [x] **2.1** SBE base envelope schema (`MessageHeader`, `SessionHeader`) (sonnet) `(1802a07)`
- [x] **2.2** SessionHeader extended with `trace_id`, `parent_span_id`, `initial_order_id`, `initial_route_id` per [[arch-sbe-aeron-transport]] (sonnet) ← blocks: 2.1 `(937904f)`
- [x] **2.3** Aeron channel layout doc and code (sonnet) ← blocks: 2.1 `(b9ef33c)`
- [x] **2.4** Sequence recovery service skeleton (sonnet) ← blocks: 2.2 `(5b05c48)`
- [x] **2.5** Aeron Cluster (Raft) 3-node bootstrap (opus — distributed consensus) ← blocks: 0.7 `(e5bd144)`
- [x] **2.6** Aeron Archive recording + replay APIs (sonnet) ← blocks: 2.5 `(91bc0d7)`
- [x] **2.7** Schema evolution test: old reader + new writer (sonnet) ← blocks: 2.1 `(b3a4c33)`

## Phase 3 — Event Sourcing + Configuration

The auditable spine. ~2 weeks.

- [x] **3.1** Event envelope SBE schema per [[arch-event-sourcing]] (sonnet) ← blocks: 2.1 `(eca3725)`
- [x] **3.2** Event log writer (append-only, fsync discipline) (gemma) ← blocks: 3.1 `(19a6eb9)`
- [x] **3.3** Stream-id partitioning (order.{id}, route.{id}, etc.) (sonnet) ← blocks: 3.1 `(ddfa937)`
- [x] **3.4** Projection framework (idempotent, rebuild-from-scratch) (opus — determinism spine) ← blocks: 3.1 `(48c2305)`
- [x] **3.5** Replay engine (log slice → re-derive state) (opus — determinism spine) ← blocks: 3.4 `(b0cc12b)`
- [x] **3.6** Time/Replay server (sim-clock interface) per [[arch-time-replay-server]] (sonnet) ← blocks: 3.5 `(5733929)`
- [x] **3.7** Configuration service per [[arch-configuration-service]] (sonnet) ← blocks: 3.1, 3.5 `(a47adfa)`
- [x] **3.8** Local cache snapshot agent (atomic message-boundary swap) (sonnet) ← blocks: 3.7 `(1d3c380)`

## Phase 4 — Reference Data

The "what" layer. ~3-4 weeks.

- [x] **4.1** Symbology service (FIGI + licensed secondaries) per [[arch-symbology-figi]] (sonnet) `(4ee2074)`
- [x] **4.2** License-metering and audit (sonnet) ← blocks: 4.1 `(2cad5e5)`
- [x] **4.3** SBE template registry for Instrument templates (sonnet) ← blocks: 2.1, 4.1 `(5564f71)`
- [x] **4.4** `InstrumentCore` SBE block per [[arch-security-master]] (sonnet) ← blocks: 4.3 `(dba3903)`
- [x] **4.5** `EquityInstrument` template (gemma first draft, sonnet review) `(5752d7c)`
- [x] **4.6** `BondInstrument` template (gemma first draft, sonnet review) `(f58abcd)`
- [x] **4.7** `IrsInstrument` template (sonnet — composition complexity) `(72cda34)`
- [x] **4.8** `CdsInstrument` template (sonnet — reference entity) `(4b8d756)`
- [x] **4.9** `FxSpotInstrument` / `FxForwardInstrument` / `FxSwapInstrument` / `FxNdfInstrument` (gemma drafts, sonnet review) `(1c8d297)`
- [x] **4.10** `FxOptionInstrument` template (sonnet — exotic discriminator) `(cfc4f05)`
- [x] **4.11** `ListedOptionInstrument` / `ListedFutureInstrument` (gemma drafts, sonnet review) `(7bf9ad5)`
- [ ] **4.12** `TbaMbsInstrument` / `SpecifiedPoolInstrument` (sonnet — fungibility handling)
- [x] **4.13** `AbsInstrument` / `ConvertibleBondInstrument` / `LoanInstrument` (gemma drafts, sonnet review) `(49ceffd)`
- [ ] **4.14** `StructuredProductInstrument` (sonnet — flexibility for bespoke)
- [x] **4.15** `CommodityFutureInstrument` / `CommodityPhysicalInstrument` (gemma drafts, sonnet review) `(49ceffd)`
- [x] **4.16** `CryptoFungibleInstrument` / `NftInstrument` (gemma drafts, sonnet review) `(49ceffd)`
- [ ] **4.17** `EventContractInstrument` template (prediction markets) (sonnet)
- [x] **4.18** Package entity + Leg group schema (sonnet) ← blocks: 4.4 `(b3ec442)`
- [x] **4.19** Security master CRUD + supersession events per [[arch-security-master]] (sonnet) ← blocks: 4.4, 3.1 `(c8eaddb)`
- [x] **4.20** Corporate actions → supersession integration per [[arch-corporate-actions]] (sonnet) ← blocks: 4.19 `(8a514fc)`
- [x] **4.21** Reference data service (calendars, day counts, tick sizes) per [[arch-reference-data-service]] (sonnet) `(8f47358)`
- [x] **4.22** Holiday calendars per currency (gemma — data ingest) `(d8eaf57)`
- [x] **4.23** Day count conventions table (gemma) `(1f67b28)`
- [x] **4.24** Counterparty / broker code / venue MIC tables (gemma) `(f3bfa4e)`
- [x] **4.25** Internal-allocated identifier namespace for OTC (sonnet) ← blocks: 4.19 `(ef7f24b)`

## Phase 5 — Identity & Permissions (AAA)

Trust boundary. ~1-2 weeks.

- [x] **5.1** AAA service skeleton per [[entry-point-aaa]] (sonnet) `(66a79c7)`
- [x] **5.2** Firm/Desk/User hierarchy per [[arch-firm-desk-user]] (sonnet) ← blocks: 5.1 `(4316e34)`
- [x] **5.3** Tag permissions 3-layer AND-gate per [[arch-tag-permissions]] (sonnet) ← blocks: 5.2 `(2aa7528)`
- [x] **5.4** Trace ID stamping at session-logon (sonnet) ← blocks: 5.1, 2.2 `(dac894d)`
- [x] **5.5** Session sequence recovery integrated (sonnet) ← blocks: 5.4, 2.4 `(ff8d71c)`

## Phase 6 — Validator

Hard-reject path. ~1-2 weeks.

- [x] **6.1** Reject code catalog (`EMS-<CAT>-<NNNN>`) per [[arch-validator]] (gemma) `(eaa7c8f)`
- [x] **6.2** Layered evaluation pipeline (session → identity → ref → perm → ...) (sonnet) `(b099d4a)`
- [x] **6.3** Permission denial messages with admin-hint pointers (sonnet) ← blocks: 6.1, 5.3 `(b80b305)`
- [~] **6.4** Per-asset-class validation rules (gemma first drafts, sonnet review)
- [x] **6.5** Validator golden tests (one per code, full coverage) (gemma) ← blocks: 6.1

## Phase 7 — OMS Core

The order/route layers. ~3-4 weeks.

- [x] **7.1** Staged Order Manager per [[arch-order-staged]] (sonnet) ← blocks: 1.2, 4.19, 6.2 `(8de5bab)`
- [ ] **7.2** Router Layer per [[arch-router-layer]] (sonnet) ← blocks: 1.3, 7.1
- [ ] **7.3** Automation Layer per [[arch-automation-layer]] (sonnet) ← blocks: 7.1, 7.2
- [ ] **7.4** Multi-leg / Package handling per [[arch-multileg]] (sonnet) ← blocks: 1.4, 7.2
- [ ] **7.5** Aggregation service per [[arch-aggregation]] (sonnet) ← blocks: 7.1
- [ ] **7.6** FX netting per [[arch-fx-netting]] (sonnet) ← blocks: 7.1
- [ ] **7.7** FIX Appendix D race-condition golden tests (opus — silent-race correctness) ← blocks: 7.2, 1.10
- [ ] **7.8** Lifecycle chaining (cancel cascades) end-to-end test (sonnet) ← blocks: 1.9

## Phase 8 — FIX / API Bridge + Bulk I/O

External edges. ~2-3 weeks.

- [ ] **8.1** FIX gateway in (inbound from buy-side clients) per [[arch-fix-api-bridge]] (sonnet)
- [ ] **8.2** FIX gateway out (outbound to venues) (sonnet) ← blocks: 8.1
- [ ] **8.3** Tag 9700 (TraceparentHex) propagation and fallback (sonnet) ← blocks: 8.1, 5.4
- [ ] **8.4** REST API surface (CRUD operations) per [[arch-api-first]] (gemma for CRUD scaffold) ← blocks: 7.1
- [ ] **8.5** Batch operation semantics (all-or-nothing, partial) (sonnet) ← blocks: 8.4
- [ ] **8.6** Excel/CSV bulk import per [[arch-bulk-io]] (gemma for parsers) ← blocks: 8.4
- [ ] **8.7** Excel/CSV bulk export with templates (gemma) ← blocks: 8.4
- [ ] **8.8** Idempotent re-import test (sonnet) ← blocks: 8.6

## Phase 9 — Market Data

Inputs. ~2 weeks.

- [ ] **9.1** Quote server per [[arch-quote-server]] (sonnet) ← blocks: 2.3
- [ ] **9.2** Subscriber-visibility registry (sonnet) ← blocks: 9.1
- [ ] **9.3** Quote multicast over Aeron (sonnet) ← blocks: 9.1, 2.3
- [ ] **9.4** IOI service per [[arch-ioi]] (sonnet)
- [ ] **9.5** Real-time analytics (VWAP, TWAP, PWP, arrival) per [[arch-realtime-analytics]] (gemma for math, sonnet for streaming)

## Phase 10 — Pre-Trade Auxiliaries

Block-with-override + position-aware. ~3-4 weeks.

- [ ] **10.1** Compliance service per [[arch-compliance]] (sonnet)
- [ ] **10.2** Fat-finger check (netted-vs-unnetted) (sonnet) ← blocks: 10.1, 9.5
- [ ] **10.3** Machine-gun rate limiter (sonnet) ← blocks: 10.1
- [ ] **10.4** Allow / restricted / watch list service (sonnet) ← blocks: 10.1
- [ ] **10.5** Override mechanics (tag-gated, time-bound) (sonnet) ← blocks: 10.1, 5.3
- [ ] **10.6** Risk engine per [[arch-risk-engine]] (sonnet) ← blocks: 10.7
- [ ] **10.7** Position service per [[arch-position-service]] (sonnet) ← blocks: 3.4
- [ ] **10.8** Pricing service per [[arch-pricing-service]] (sonnet) ← blocks: 4.19
- [ ] **10.9** Pre-trade analytics (pluggable models) per [[arch-pretrade-analytics]] (sonnet) ← blocks: 10.8

## Phase 11 — Venue Connectivity

Outbound to the market. ~4-6 weeks.

- [ ] **11.1** Venue adapter framework per [[arch-venue-connectivity]] (sonnet)
- [ ] **11.2** [[marketaxess]] FIX adapter (gemma for boilerplate, sonnet for nuances)
- [ ] **11.3** [[tradeweb]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.4** [[brokertec]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.5** [[ebs]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.6** [[refinitiv-fxall]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.7** [[bloomberg-emsx]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.8** [[bloomberg-sef]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.9** [[bloomberg-bridge]] FIX adapter (gemma for boilerplate, sonnet)
- [ ] **11.10** [[nyse]] / [[nasdaq]] / [[cboe-bzx]] FIX/Pillar/OUCH adapters (gemma for boilerplate, sonnet)
- [ ] **11.11** Smart Order Router per [[arch-smart-order-router]] (sonnet)
- [ ] **11.12** Algo wheel selection strategies (sonnet) ← blocks: 11.11
- [ ] **11.13** RFQ orchestration per [[arch-rfq]] (sonnet) ← blocks: 11.1
- [ ] **11.14** RFQ-to-3 enforcement for [[mat|MAT]] swaps (sonnet) ← blocks: 11.13

## Phase 12 — Post-Trade

STP and reporting. ~3-4 weeks.

- [ ] **12.1** Allocation service per [[arch-allocation-service]] (sonnet)
- [ ] **12.2** STP pipeline per [[arch-stp-pipeline]] (sonnet) ← blocks: 12.1
- [ ] **12.3** Confirmation/Affirmation per [[arch-confirmation-affirmation]] (sonnet) ← blocks: 12.2
- [ ] **12.4** [[markitserv]] integration (sonnet) ← blocks: 12.3
- [ ] **12.5** Regulatory reporting per [[arch-regulatory-reporting-service]] (sonnet)
- [ ] **12.6** [[trace]] submission (sonnet) ← blocks: 12.5
- [ ] **12.7** [[msrb-rtrs]] submission (sonnet) ← blocks: 12.5
- [ ] **12.8** [[cftc-sdr]] submission (sonnet) ← blocks: 12.5
- [ ] **12.9** [[rts-22-27-28|RTS 22]] submission (sonnet) ← blocks: 12.5
- [ ] **12.10** Best execution audit per [[arch-best-execution]] (sonnet)
- [ ] **12.11** Per-pod / per-firm jurisdiction routing per [[arch-jurisdictional-compliance]] (sonnet)

## Phase 13 — Observability

Three pillars. ~1-2 weeks.

- [x] **13.1** OTel SDK + collector configuration (gemma) `(9661ba3)`
- [x] **13.2** ELK / OpenSearch ingest pipeline (gemma) `(6c9601c)`
- [x] **13.3** Prometheus exporters per service (gemma) `(6c9601c)`
- [~] **13.4** Grafana dashboards: golden signals + per-asset latency (gemma for templates, sonnet for design) — scaffold at 9/9/6 panels; targets 24/12/12; needs sonnet follow-up pass
- [ ] **13.5** Distributed-trace verification: end-to-end trace from FIX in → venue out (sonnet)
- [ ] **13.6** Sampling strategy (1-5% routine, 100% errors) (sonnet)

## Phase 14 — Operations

Resilience + tooling. ~2-3 weeks.

- [ ] **14.1** JMX introspection per [[arch-jmx-introspection]] (sonnet)
- [ ] **14.2** Privileged event injection (security-gated) (sonnet) ← blocks: 14.1
- [x] **14.3** Time/Replay server UI (gemma for scaffold, sonnet for design) `(6b3e666)`
- [x] **14.4** Configuration service UI (gemma for scaffold) `(6b3e666)`
- [ ] **14.5** Blue/green switchover protocol per [[arch-deployment]] (sonnet)
- [ ] **14.6** Cluster-of-clusters active lease (sonnet) ← blocks: 14.5
- [ ] **14.7** Fence-token venue credential rotation (sonnet) ← blocks: 14.5
- [x] **14.8** Weekly leader-kill drill scripted (gemma) `(91ccfd7)`
- [x] **14.9** Monthly cold-start drill scripted (gemma) `(91ccfd7)`
- [ ] **14.10** Quarterly cross-region failover drill scripted (sonnet)

## Done criteria for v0 (MVP)

All phases 0-14 marked `[x]`. Plus:

- End-to-end smoke: a FIX inbound NewOrderSingle → validator → staged → routed via venue adapter (mock) → fill ack → allocation → confirmation → TRACE-mock submission. Single trace ID through the whole chain.

- One asset class fully wired (start: US IG corp via mocked MarketAxess) end-to-end with full audit trail.

- Replay determinism passes on a 1-day mock log slice.

## What v1 looks like

- Three asset classes wired end-to-end (US IG corp, US equity, USD IRS).
- All venue adapters in 11.x talking to UAT venue endpoints.
- UAT-1 / UAT-2 blue/green deployments healthy.
- Compliance + Risk + Best-Ex full feature parity.

The plan beyond v1 is post-MVP. Don't pre-plan it.
