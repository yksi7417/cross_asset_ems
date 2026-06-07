# Cross-Asset EMS — Implementation Plan

The build queue. **One source of truth** for what gets done, in what order, by which tier (local Gemma / Gemini-via-Hermes / Claude Code).

- Each task is one checklist line. `[ ]` = pending, `[~]` = in-progress (started but not committed), `[x]` = done.
- Tags after the task: `(local)` = delegate to local Gemma; `(gemini)` = delegate to Gemini via Hermes; `(claude)` = needs Claude Code. See [[DELEGATION]] for the rules.
- `← blocks: a.b` records prerequisites. The loop must not start a task until its blockers are `[x]`.
- When marking `[x]`, append a short commit SHA: `[x] (abc1234)`.

The loop protocol is in [[LOOP]]. Notifications in [[HERMES]]. Current cursor in [[CHECKPOINT]].

---

## Phase 0 — Bootstrap

Foundation for everything else. ~1-2 weeks of work.

- [x] **0.1** Monorepo layout (java/, rust/, schemas/, infra/, docs/) (local) ← blocks: none `(0e2fe6a)`
- [x] **0.2** Multi-module Gradle/Cargo workspace (local) ← blocks: 0.1 `(96ca57c)`
- [x] **0.3** Git hooks (pre-commit lint, conventional commits) (local) `(e6b411b)`
- [x] **0.4** GitHub Actions baseline (unit tests, lint, SBOM) (claude) ← blocks: 0.2 `(5283807)`
- [x] **0.5** Docker Compose dev environment (claude) ← blocks: 0.2 `(c74f4c4)`
- [x] **0.6** SBE codegen Gradle plugin wired up (claude) ← blocks: 0.2 `(ab1fd79)`
- [ ] **0.7** Aeron Cluster + Archive toy ping/pong (claude) ← blocks: 0.6
- [x] **0.8** OpenTelemetry SDK + collector + Jaeger toy trace (local) `(b86e7f8)`
- [x] **0.9** Conventional commit + changelog automation (local) `(27ce39f)`
- [ ] **0.10** Phase-0 smoke test job in CI (claude) ← blocks: 0.4, 0.7

## Phase 1 — Shared FIX-Compliant FSM

The core determinism guarantee. ~2-3 weeks.

- [ ] **1.1** FSM YAML schema (states, events, transitions, effects) (claude)
- [ ] **1.2** Order FSM definition per [[arch-order-route-lifecycle]] (claude) ← blocks: 1.1
- [ ] **1.3** Route FSM definition per [[arch-order-route-lifecycle]] (claude) ← blocks: 1.1
- [ ] **1.4** Multi-leg / Package FSM per [[arch-multileg]] (claude) ← blocks: 1.1
- [ ] **1.5** VenueSession FSM (use existing Gemma draft in fsm/) (claude) ← blocks: 1.1
- [ ] **1.6** SOR FSM (claude) ← blocks: 1.1
- [ ] **1.7** Codegen pipeline YAML → Java/Rust state structs (claude) ← blocks: 1.1
- [ ] **1.8** Unit test generator: YAML → ~5000 transition tests (local) ← blocks: 1.7
- [ ] **1.9** Lifecycle chaining (Order cancel cascades to Route) tests (claude) ← blocks: 1.2, 1.3
- [ ] **1.10** Pending Replace / Pending Cancel edge cases per [[arch-fix-appendix-d]] (claude) ← blocks: 1.9
- [ ] **1.11** Replay determinism test harness (single FSM, replay log slice) (claude) ← blocks: 1.7
- [ ] **1.12** Identity chaining stamping into FSM events per [[arch-identity-chaining]] (claude) ← blocks: 1.7

## Phase 2 — Transport (SBE + Aeron)

Wire protocol and clustering. ~1-2 weeks.

- [ ] **2.1** SBE base envelope schema (`MessageHeader`, `SessionHeader`) (claude)
- [ ] **2.2** SessionHeader extended with `trace_id`, `parent_span_id`, `initial_order_id`, `initial_route_id` per [[arch-sbe-aeron-transport]] (claude) ← blocks: 2.1
- [ ] **2.3** Aeron channel layout doc and code (claude) ← blocks: 2.1
- [ ] **2.4** Sequence recovery service skeleton (claude) ← blocks: 2.2
- [ ] **2.5** Aeron Cluster (Raft) 3-node bootstrap (claude) ← blocks: 0.7
- [ ] **2.6** Aeron Archive recording + replay APIs (claude) ← blocks: 2.5
- [ ] **2.7** Schema evolution test: old reader + new writer (claude) ← blocks: 2.1

## Phase 3 — Event Sourcing + Configuration

The auditable spine. ~2 weeks.

- [ ] **3.1** Event envelope SBE schema per [[arch-event-sourcing]] (claude) ← blocks: 2.1
- [ ] **3.2** Event log writer (append-only, fsync discipline) (local) ← blocks: 3.1
- [ ] **3.3** Stream-id partitioning (order.{id}, route.{id}, etc.) (claude) ← blocks: 3.1
- [ ] **3.4** Projection framework (idempotent, rebuild-from-scratch) (claude) ← blocks: 3.1
- [ ] **3.5** Replay engine (log slice → re-derive state) (claude) ← blocks: 3.4
- [ ] **3.6** Time/Replay server (sim-clock interface) per [[arch-time-replay-server]] (claude) ← blocks: 3.5
- [ ] **3.7** Configuration service per [[arch-configuration-service]] (claude) ← blocks: 3.1, 3.5
- [ ] **3.8** Local cache snapshot agent (atomic message-boundary swap) (claude) ← blocks: 3.7

## Phase 4 — Reference Data

The "what" layer. ~3-4 weeks.

- [ ] **4.1** Symbology service (FIGI + licensed secondaries) per [[arch-symbology-figi]] (claude)
- [ ] **4.2** License-metering and audit (claude) ← blocks: 4.1
- [ ] **4.3** SBE template registry for Instrument templates (claude) ← blocks: 2.1, 4.1
- [ ] **4.4** `InstrumentCore` SBE block per [[arch-security-master]] (claude) ← blocks: 4.3
- [x] **4.5** `EquityInstrument` template (local first draft, claude review) `(5752d7c)`
- [x] **4.6** `BondInstrument` template (local first draft, claude review) `(f58abcd)`
- [ ] **4.7** `IrsInstrument` template (claude — composition complexity)
- [ ] **4.8** `CdsInstrument` template (claude — reference entity)
- [x] **4.9** `FxSpotInstrument` / `FxForwardInstrument` / `FxSwapInstrument` / `FxNdfInstrument` (local drafts, claude review) `(1c8d297)`
- [ ] **4.10** `FxOptionInstrument` template (claude — exotic discriminator)
- [ ] **4.11** `ListedOptionInstrument` / `ListedFutureInstrument` (local drafts, claude review)
- [ ] **4.12** `TbaMbsInstrument` / `SpecifiedPoolInstrument` (claude — fungibility handling)
- [x] **4.13** `AbsInstrument` / `ConvertibleBondInstrument` / `LoanInstrument` (local drafts, claude review) `(49ceffd)`
- [ ] **4.14** `StructuredProductInstrument` (claude — flexibility for bespoke)
- [x] **4.15** `CommodityFutureInstrument` / `CommodityPhysicalInstrument` (local drafts, claude review) `(49ceffd)`
- [x] **4.16** `CryptoFungibleInstrument` / `NftInstrument` (local drafts, claude review) `(49ceffd)`
- [ ] **4.17** `EventContractInstrument` template (prediction markets) (claude)
- [ ] **4.18** Package entity + Leg group schema (claude) ← blocks: 4.4
- [ ] **4.19** Security master CRUD + supersession events per [[arch-security-master]] (claude) ← blocks: 4.4, 3.1
- [ ] **4.20** Corporate actions → supersession integration per [[arch-corporate-actions]] (claude) ← blocks: 4.19
- [ ] **4.21** Reference data service (calendars, day counts, tick sizes) per [[arch-reference-data-service]] (claude)
- [x] **4.22** Holiday calendars per currency (local — data ingest) `(d8eaf57)`
- [x] **4.23** Day count conventions table (local) `(1f67b28)`
- [x] **4.24** Counterparty / broker code / venue MIC tables (local) `(f3bfa4e)`
- [ ] **4.25** Internal-allocated identifier namespace for OTC (claude) ← blocks: 4.19

## Phase 5 — Identity & Permissions (AAA)

Trust boundary. ~1-2 weeks.

- [ ] **5.1** AAA service skeleton per [[entry-point-aaa]] (claude)
- [ ] **5.2** Firm/Desk/User hierarchy per [[arch-firm-desk-user]] (claude) ← blocks: 5.1
- [ ] **5.3** Tag permissions 3-layer AND-gate per [[arch-tag-permissions]] (claude) ← blocks: 5.2
- [ ] **5.4** Trace ID stamping at session-logon (claude) ← blocks: 5.1, 2.2
- [ ] **5.5** Session sequence recovery integrated (claude) ← blocks: 5.4, 2.4

## Phase 6 — Validator

Hard-reject path. ~1-2 weeks.

- [ ] **6.1** Reject code catalog (`EMS-<CAT>-<NNNN>`) per [[arch-validator]] (local)
- [ ] **6.2** Layered evaluation pipeline (session → identity → ref → perm → ...) (claude)
- [ ] **6.3** Permission denial messages with admin-hint pointers (claude) ← blocks: 6.1, 5.3
- [ ] **6.4** Per-asset-class validation rules (local first drafts, claude review)
- [ ] **6.5** Validator golden tests (one per code, full coverage) (local) ← blocks: 6.1

## Phase 7 — OMS Core

The order/route layers. ~3-4 weeks.

- [ ] **7.1** Staged Order Manager per [[arch-order-staged]] (claude) ← blocks: 1.2, 4.19, 6.2
- [ ] **7.2** Router Layer per [[arch-router-layer]] (claude) ← blocks: 1.3, 7.1
- [ ] **7.3** Automation Layer per [[arch-automation-layer]] (claude) ← blocks: 7.1, 7.2
- [ ] **7.4** Multi-leg / Package handling per [[arch-multileg]] (claude) ← blocks: 1.4, 7.2
- [ ] **7.5** Aggregation service per [[arch-aggregation]] (claude) ← blocks: 7.1
- [ ] **7.6** FX netting per [[arch-fx-netting]] (claude) ← blocks: 7.1
- [ ] **7.7** FIX Appendix D race-condition golden tests (claude) ← blocks: 7.2, 1.10
- [ ] **7.8** Lifecycle chaining (cancel cascades) end-to-end test (claude) ← blocks: 1.9

## Phase 8 — FIX / API Bridge + Bulk I/O

External edges. ~2-3 weeks.

- [ ] **8.1** FIX gateway in (inbound from buy-side clients) per [[arch-fix-api-bridge]] (claude)
- [ ] **8.2** FIX gateway out (outbound to venues) (claude) ← blocks: 8.1
- [ ] **8.3** Tag 9700 (TraceparentHex) propagation and fallback (claude) ← blocks: 8.1, 5.4
- [ ] **8.4** REST API surface (CRUD operations) per [[arch-api-first]] (local for CRUD scaffold) ← blocks: 7.1
- [ ] **8.5** Batch operation semantics (all-or-nothing, partial) (claude) ← blocks: 8.4
- [ ] **8.6** Excel/CSV bulk import per [[arch-bulk-io]] (local for parsers) ← blocks: 8.4
- [ ] **8.7** Excel/CSV bulk export with templates (local) ← blocks: 8.4
- [ ] **8.8** Idempotent re-import test (claude) ← blocks: 8.6

## Phase 9 — Market Data

Inputs. ~2 weeks.

- [ ] **9.1** Quote server per [[arch-quote-server]] (claude) ← blocks: 2.3
- [ ] **9.2** Subscriber-visibility registry (claude) ← blocks: 9.1
- [ ] **9.3** Quote multicast over Aeron (claude) ← blocks: 9.1, 2.3
- [ ] **9.4** IOI service per [[arch-ioi]] (claude)
- [ ] **9.5** Real-time analytics (VWAP, TWAP, PWP, arrival) per [[arch-realtime-analytics]] (local for math, claude for streaming)

## Phase 10 — Pre-Trade Auxiliaries

Block-with-override + position-aware. ~3-4 weeks.

- [ ] **10.1** Compliance service per [[arch-compliance]] (claude)
- [ ] **10.2** Fat-finger check (netted-vs-unnetted) (claude) ← blocks: 10.1, 9.5
- [ ] **10.3** Machine-gun rate limiter (claude) ← blocks: 10.1
- [ ] **10.4** Allow / restricted / watch list service (claude) ← blocks: 10.1
- [ ] **10.5** Override mechanics (tag-gated, time-bound) (claude) ← blocks: 10.1, 5.3
- [ ] **10.6** Risk engine per [[arch-risk-engine]] (claude) ← blocks: 10.7
- [ ] **10.7** Position service per [[arch-position-service]] (claude) ← blocks: 3.4
- [ ] **10.8** Pricing service per [[arch-pricing-service]] (claude) ← blocks: 4.19
- [ ] **10.9** Pre-trade analytics (pluggable models) per [[arch-pretrade-analytics]] (claude) ← blocks: 10.8

## Phase 11 — Venue Connectivity

Outbound to the market. ~4-6 weeks.

- [ ] **11.1** Venue adapter framework per [[arch-venue-connectivity]] (claude)
- [ ] **11.2** [[marketaxess]] FIX adapter (local for boilerplate, claude for nuances)
- [ ] **11.3** [[tradeweb]] FIX adapter (local for boilerplate, claude)
- [ ] **11.4** [[brokertec]] FIX adapter (local for boilerplate, claude)
- [ ] **11.5** [[ebs]] FIX adapter (local for boilerplate, claude)
- [ ] **11.6** [[refinitiv-fxall]] FIX adapter (local for boilerplate, claude)
- [ ] **11.7** [[bloomberg-emsx]] FIX adapter (local for boilerplate, claude)
- [ ] **11.8** [[bloomberg-sef]] FIX adapter (local for boilerplate, claude)
- [ ] **11.9** [[bloomberg-bridge]] FIX adapter (local for boilerplate, claude)
- [ ] **11.10** [[nyse]] / [[nasdaq]] / [[cboe-bzx]] FIX/Pillar/OUCH adapters (local for boilerplate, claude)
- [ ] **11.11** Smart Order Router per [[arch-smart-order-router]] (claude)
- [ ] **11.12** Algo wheel selection strategies (claude) ← blocks: 11.11
- [ ] **11.13** RFQ orchestration per [[arch-rfq]] (claude) ← blocks: 11.1
- [ ] **11.14** RFQ-to-3 enforcement for [[mat|MAT]] swaps (claude) ← blocks: 11.13

## Phase 12 — Post-Trade

STP and reporting. ~3-4 weeks.

- [ ] **12.1** Allocation service per [[arch-allocation-service]] (claude)
- [ ] **12.2** STP pipeline per [[arch-stp-pipeline]] (claude) ← blocks: 12.1
- [ ] **12.3** Confirmation/Affirmation per [[arch-confirmation-affirmation]] (claude) ← blocks: 12.2
- [ ] **12.4** [[markitserv]] integration (claude) ← blocks: 12.3
- [ ] **12.5** Regulatory reporting per [[arch-regulatory-reporting-service]] (claude)
- [ ] **12.6** [[trace]] submission (claude) ← blocks: 12.5
- [ ] **12.7** [[msrb-rtrs]] submission (claude) ← blocks: 12.5
- [ ] **12.8** [[cftc-sdr]] submission (claude) ← blocks: 12.5
- [ ] **12.9** [[rts-22-27-28|RTS 22]] submission (claude) ← blocks: 12.5
- [ ] **12.10** Best execution audit per [[arch-best-execution]] (claude)
- [ ] **12.11** Per-pod / per-firm jurisdiction routing per [[arch-jurisdictional-compliance]] (claude)

## Phase 13 — Observability

Three pillars. ~1-2 weeks.

- [x] **13.1** OTel SDK + collector configuration (local) `(9661ba3)`
- [ ] **13.2** ELK / OpenSearch ingest pipeline (local)
- [ ] **13.3** Prometheus exporters per service (local)
- [ ] **13.4** Grafana dashboards: golden signals + per-asset latency (local for templates, claude for design)
- [ ] **13.5** Distributed-trace verification: end-to-end trace from FIX in → venue out (claude)
- [ ] **13.6** Sampling strategy (1-5% routine, 100% errors) (claude)

## Phase 14 — Operations

Resilience + tooling. ~2-3 weeks.

- [ ] **14.1** JMX introspection per [[arch-jmx-introspection]] (claude)
- [ ] **14.2** Privileged event injection (security-gated) (claude) ← blocks: 14.1
- [x] **14.3** Time/Replay server UI (local for scaffold, claude for design) `(6b3e666)`
- [x] **14.4** Configuration service UI (local for scaffold) `(6b3e666)`
- [ ] **14.5** Blue/green switchover protocol per [[arch-deployment]] (claude)
- [ ] **14.6** Cluster-of-clusters active lease (claude) ← blocks: 14.5
- [ ] **14.7** Fence-token venue credential rotation (claude) ← blocks: 14.5
- [x] **14.8** Weekly leader-kill drill scripted (local) `(91ccfd7)`
- [x] **14.9** Monthly cold-start drill scripted (local) `(91ccfd7)`
- [ ] **14.10** Quarterly cross-region failover drill scripted (claude)

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
