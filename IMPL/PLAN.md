# Cross-Asset EMS — Implementation Plan

The build queue. **One source of truth** for what gets done, in what order.

> **Execution model (since 2026-06-10): no delegation.** Every task is executed directly by **Fable
> (Claude Code)** in the [[LOOP]] session — no OpenCode, no model switching, no local-LLM or
> subagent tiers. The per-task tags (`(gemma)`, `(minimax)`, `(sonnet)`, `(opus)`) are retained as
> **complexity hints only** — they no longer route work to different models. [[DELEGATION]] is
> suspended.

- Each task is one checklist line. `[ ]` = pending, `[~]` = in-progress (started, draft on a branch, or not yet committed), `[x]` = done, `[!]` = blocked (needs human input).
- `← blocks: a.b` records prerequisites. The loop must not start a task until its blockers are `[x]`.
- When marking `[x]`, append a short commit SHA: `[x] (abc1234)`.

The loop protocol is in [[LOOP]]. Current cursor + open WIP branches in [[CHECKPOINT]]. (Hermes Discord notifications are optional/legacy.)

---

## Current goal — v1 build-out (set 2026-06-10)

The active [[LOOP]] scope, executed entirely by Fable. In order:

1. **Phase 7 remainder** — ✅ 7.4 multi-leg, 7.5 aggregation, 7.6 FX netting (complete 2026-06-10)
2. **Phase 8 remainder** — 8.2 ✅, 8.3 ✅, 8.4 ✅ → 8.5/8.6/8.7 → 8.8, 8.10, 8.11
3. **Phase 10** — 10.1 → 10.3–10.5; 10.7 → 10.6; 10.8 → 10.9. (**10.2 skipped**: its blocker 9.5
   is deferred with Phase 9 — fat-finger lands when real-time analytics return.)
4. **FIX simulator track** — **11.15** venue-side FIX simulator → **15.2** FIX-wire end-to-end
   smoke (the in-process mock venue 11.2 stays green alongside)
5. **Phase 14 remainder** — 14.1 → 14.2; 14.5 → 14.6/14.7; 14.10
6. **Phase 17 (docs)** — 17.1 user guide, 17.2 operator guide, 17.3 developer guide refresh

> **Scope change (2026-06-10, user decision): Phase 9 deferred.** The internal Market Data Quote
> Server is not a priority. Market data reaches the trader desktop through the **pluggable feed
> SPI (18.12)** backed by **Bloomberg Desktop/Server API (18.13)** for now; Phase 9 returns when
> SOR/venue work demands an internal quote fabric.

Scope is otherwise frozen to the tasks that were `[ ]` in these phases on 2026-06-10 plus the
tracks named above. Later additions (11.16+, 12.12+, Phase 18) are **queued for the next goal**,
not this one. Out of scope and unchanged: 4.12/4.14/4.17, 6.4 reconciliation, 11.3–11.14 real
venue adapters, 12.4/12.7–12.11, 13.4/13.6.

---

## MVP v0 critical path

The minimal set to hit the [v0 done-criteria](#done-criteria-for-v0-mvp): a FIX order through
validate → stage → route (**1 mock venue**) → fill → allocate → confirm → TRACE-mock, with a single
trace ID end-to-end, replay-deterministic. The core (Phases 0–7) is already done; only the two edges
(client-FIX, one mock venue) and the post-trade tail remain. Tasks on this path are tagged **[MVP]**
inline below; everything else is post-MVP.

Ordered, with parallel tracks (✅ = buildable now, blockers met):

- **A — Client edge (FIX + session):** **8.9** ✅ → **8.1**
- **B — One mock venue:** **11.1** ✅ → **11.2** (implement as in-process mock for v0)
- **C — Post-trade tail:** **12.1** ✅ → **12.2** → **12.3** ; **12.5** ✅ → **12.6**
- **D — Verification:** **13.5** (← A+B) → **15.1** (end-to-end smoke + 1-day replay slice)

A/B/C run in parallel; D ties them together. **Deferred for v0** (per scoping decision 2026-06-09):
8.2, 8.3, 8.4–8.8, 8.10–8.11; all of Phase 9 & 10; venues 11.3–11.14; 12.4, 12.7–12.11; 7.4–7.6;
exotic instrument templates 4.12/4.14/4.17; and 6.4 per-asset bond rules (core validator suffices
for the smoke order).

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

**No-delegation note (2026-06-10):** the open queue is consumed solely by the [[LOOP]] session
(Fable). The tier carry-forward notes above describe *what the rework needs*, not who does it.

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
- [x] **7.2** Router Layer per [[arch-router-layer]] (sonnet) ← blocks: 1.3, 7.1 (6b9163c)
- [x] **7.3** Automation Layer per [[arch-automation-layer]] (sonnet) ← blocks: 7.1, 7.2 (0149917)
- [x] **7.4** Multi-leg / Package handling per [[arch-multileg]] (sonnet) ← blocks: 1.4, 7.2 `(535b37c)`
- [x] **7.5** Aggregation service per [[arch-aggregation]] (sonnet) ← blocks: 7.1 `(e980553)`
- [x] **7.6** FX netting per [[arch-fx-netting]] (sonnet) ← blocks: 7.1 `(898bbcb)`
- [x] **7.7** FIX Appendix D race-condition golden tests (opus — silent-race correctness) ← blocks: 7.2, 1.10 (9e90812)
- [x] **7.8** Lifecycle chaining (cancel cascades) end-to-end test (sonnet) ← blocks: 1.9 (bc1b34e)

## Phase 8 — FIX / API Bridge + Bulk I/O

External edges. ~2-3 weeks.

- [x] **[MVP] 8.1** FIX gateway (client-facing: inbound NewOrderSingle + outbound ExecutionReports to buy-side clients) per [[arch-fix-api-bridge]] — rides the same resumable session channel as the API surface (one session layer, both surfaces) (sonnet) ← blocks: 8.9 `(b3aa7ab)`
  - **8.9 consumer contracts** (from 8.9 review): (1) reconnect must call **both** `logon` (inbound gap reconcile) **and** `resumeOutbound` (outbound replay) — `logon` alone leaves the client missing server-sent messages; (2) if `resumeOutbound`'s first returned seq > requested `fromSeq`, the buffer evicted the hole → issue **RESET** (EMS-SES catastrophic mismatch), do not silently resume over the gap; (3) `checkLiveness` re-returns `SEND_TEST_REQUEST` every poll in the `[interval, 2·interval)` window — the heartbeat driver must send **one** TEST_REQUEST then wait, not poll-and-send.
- [x] **8.2** FIX gateway out (outbound to venues) (sonnet) ← blocks: 8.1 `(7014bea)`
- [x] **8.3** Tag 9700 (TraceparentHex) propagation and fallback (sonnet) ← blocks: 8.1, 5.4 `(6aca67e)`
- [x] **8.4** API session surface — request/response + publish/subscribe operation API (Session/Service/Request/Subscription/Event) over the AAA-authenticated session channel; native client handshake. **Not REST-specific** — REST/WS is one edge binding (8.10). Per [[arch-api-first]] (sonnet) ← blocks: 7.1, 5.1, 8.9 `(f1777fd)`
- [x] **8.5** Batch operation semantics (all-or-nothing, partial) (sonnet) ← blocks: 8.4 `(7220f45)`
- [x] **8.6** Excel/CSV bulk import per [[arch-bulk-io]] (gemma for parsers) ← blocks: 8.4 `(41e74b4)`
- [x] **8.7** Excel/CSV bulk export with templates (gemma) ← blocks: 8.4 `(903589b)`
- [x] **8.8** Idempotent re-import test (sonnet) ← blocks: 8.6 `(6cc135a)`
- [x] **[MVP] 8.9** Resumable session channel — heartbeat/TEST_REQUEST, outbound resend buffer, resume-from-seq, per-hop seq dedup; completes the `SequenceRecoveryService` skeleton per [[arch-sequence-recovery]] (sonnet) ← blocks: 5.1, 2.3 (981c33d)
- [x] **8.10** REST/WebSocket edge binding for browser UI — maps HTTP/WS onto the API session surface; WS carries the resumable subscription stream (`Last-Event-ID`/seq resume) per [[arch-api-first]] (gemma for REST scaffold, sonnet for WS resume) ← blocks: 8.4 `(d6e42f3)`
- [x] **8.11** Multi-surface consistent-view parity test — same operation via FIX / native API / REST-WS yields byte-identical events + identical projection; anchored to the [[arch-fix-api-bridge]] mixed-client rule (sonnet) ← blocks: 8.1, 8.4, 8.10 `(f1eae20)`

## Phase 9 — Market Data

Inputs. ~2 weeks.

> **⏸ DEFERRED (2026-06-10, user decision).** Not a priority yet. The trader desktop gets market
> data via the pluggable feed SPI (**18.12**) with a Bloomberg Desktop/Server API adapter
> (**18.13**) — quick to stand up against a terminal subscription. The internal quote server /
> Aeron multicast fabric below resumes when SOR (11.11) or venue work needs it. Consequence:
> **10.2** (fat-finger, ← 9.5) and **12.14** (TCA, ← 9.5) wait with it.

- [ ] **9.1** Quote server per [[arch-quote-server]] (sonnet) ← blocks: 2.3
- [ ] **9.2** Subscriber-visibility registry (sonnet) ← blocks: 9.1
- [ ] **9.3** Quote multicast over Aeron (sonnet) ← blocks: 9.1, 2.3
- [ ] **9.4** IOI service per [[arch-ioi]] (sonnet)
- [ ] **9.5** Real-time analytics (VWAP, TWAP, PWP, arrival) per [[arch-realtime-analytics]] (gemma for math, sonnet for streaming)

## Phase 10 — Pre-Trade Auxiliaries

Block-with-override + position-aware. ~3-4 weeks.

- [x] **10.1** Compliance service per [[arch-compliance]] (sonnet) `(668a978)`
- [ ] **10.2** Fat-finger check (netted-vs-unnetted) (sonnet) ← blocks: 10.1, 9.5
- [x] **10.3** Machine-gun rate limiter (sonnet) ← blocks: 10.1 `(fb4db51)`
- [x] **10.4** Allow / restricted / watch list service (sonnet) ← blocks: 10.1 `(ea6f860)`
- [x] **10.5** Override mechanics (tag-gated, time-bound) (sonnet) ← blocks: 10.1, 5.3 `(3dd4020)`
- [x] **10.6** Risk engine per [[arch-risk-engine]] (sonnet) ← blocks: 10.7 `(05c2c0b)`
- [x] **10.7** Position service per [[arch-position-service]] (sonnet) ← blocks: 3.4 `(29e5ce1)`
- [x] **10.8** Pricing service per [[arch-pricing-service]] (sonnet) ← blocks: 4.19 `(8dc51fe)`
- [x] **10.9** Pre-trade analytics (pluggable models) per [[arch-pretrade-analytics]] (sonnet) ← blocks: 10.8 `(02356cc)`

## Phase 11 — Venue Connectivity

Outbound to the market. ~4-6 weeks.

- [x] **[MVP] 11.1** Venue adapter framework per [[arch-venue-connectivity]] (sonnet) `(1d95436)`
- [x] **[MVP] 11.2** [[marketaxess]] FIX adapter — **v0: in-process mock** (accepts routes, emits ack+fills; no real wire) (gemma for boilerplate, sonnet for nuances) ← blocks: 11.1 `(7bb2739)`
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
- [x] **11.15** FIX venue simulator — venue-side FIX acceptor for end-to-end wire tests (sits alongside the in-process mock 11.2, which stays): session layer (Logon/Heartbeat/TestRequest/SequenceReset + ResendRequest recovery), NewOrderSingle/Cancel/Replace handling with Appendix-D-correct pending states, configurable execution model (ack → partial/full fills, rejects, busts), runs in-process for `ems-it` and standalone via Gradle (sonnet) ← blocks: 11.1 `(04adb49)`
- [ ] **11.16** Broker algo support: FIX `StrategyParameters` / FIXatdl ingestion + algo ticket metadata so broker algos are routable with custom parameters (sonnet) ← blocks: 11.1
- [ ] **11.17** FX ESP streaming executable quotes + last-look handling (complements RFQ; needed for click-to-trade) (sonnet) ← blocks: 9.1, 11.1

## Phase 12 — Post-Trade

STP and reporting. ~3-4 weeks.

- [x] **[MVP] 12.1** Allocation service per [[arch-allocation-service]] (sonnet) `(31911bb)`
- [x] **[MVP] 12.2** STP pipeline per [[arch-stp-pipeline]] (sonnet) ← blocks: 12.1 `(bc92f1c)`
- [x] **[MVP] 12.3** Confirmation/Affirmation per [[arch-confirmation-affirmation]] (sonnet) ← blocks: 12.2 `(b218e63)`
- [ ] **12.4** [[markitserv]] integration (sonnet) ← blocks: 12.3
- [x] **[MVP] 12.5** Regulatory reporting per [[arch-regulatory-reporting-service]] (sonnet) `(51e1713)`
- [x] **[MVP] 12.6** [[trace]] submission — **v0: mock submission** (sonnet) ← blocks: 12.5 `(39a9a88)`
- [ ] **12.7** [[msrb-rtrs]] submission (sonnet) ← blocks: 12.5
- [ ] **12.8** [[cftc-sdr]] submission (sonnet) ← blocks: 12.5
- [ ] **12.9** [[rts-22-27-28|RTS 22]] submission (sonnet) ← blocks: 12.5
- [ ] **12.10** Best execution audit per [[arch-best-execution]] (sonnet)
- [ ] **12.11** Per-pod / per-firm jurisdiction routing per [[arch-jurisdictional-compliance]] (sonnet)
- [ ] **12.12** [[finra|CAT]] submission — equities/options order-event reporting (16.1 already maps equity→CAT; this is the submission adapter, sibling of 12.6–12.9) (sonnet) ← blocks: 12.5
- [ ] **12.13** Commissions / fees / accrued-interest engine — per-broker + per-asset-class schedules, applied at allocation and carried onto confirms (a buyer expects net-money confirms, not clean qty×price) (sonnet) ← blocks: 12.1
- [ ] **12.14** TCA per [[arch-tca]] — slippage vs arrival/VWAP/IS benchmarks, venue/broker league tables, exportable best-ex committee pack (sonnet) ← blocks: 9.5, 12.10
- [ ] **12.15** Surveillance feed per [[arch-surveillance]] — order/exec event export + baseline alerts (layering, wash, spoof-pattern, fat-finger cluster) (sonnet) ← blocks: 3.3
- [ ] **12.16** Client drop-copy service — real-time FIX drop of executions per client/desk/firm scope (sonnet) ← blocks: 8.2

## Phase 13 — Observability

Three pillars. ~1-2 weeks.

- [x] **13.1** OTel SDK + collector configuration (gemma) `(9661ba3)`
- [x] **13.2** ELK / OpenSearch ingest pipeline (gemma) `(6c9601c)`
- [x] **13.3** Prometheus exporters per service (gemma) `(6c9601c)`
- [~] **13.4** Grafana dashboards: golden signals + per-asset latency (gemma for templates, sonnet for design) — scaffold at 9/9/6 panels; targets 24/12/12; needs sonnet follow-up pass
- [x] **[MVP] 13.5** Distributed-trace verification: end-to-end trace from FIX in → venue out, single trace ID through the whole chain (sonnet) ← blocks: 8.1, 11.2 `(6953c3c)`
- [ ] **13.6** Sampling strategy (1-5% routine, 100% errors) (sonnet)

## Phase 14 — Operations

Resilience + tooling. ~2-3 weeks.

- [x] **14.1** JMX introspection per [[arch-jmx-introspection]] (sonnet) `(f2ffe6d)`
- [x] **14.2** Privileged event injection (security-gated) (sonnet) ← blocks: 14.1 `(024c5ac)`
- [x] **14.3** Time/Replay server UI (gemma for scaffold, sonnet for design) `(6b3e666)`
- [x] **14.4** Configuration service UI (gemma for scaffold) `(6b3e666)`
- [x] **14.5** Blue/green switchover protocol per [[arch-deployment]] (sonnet) `(874130c)`
- [x] **14.6** Cluster-of-clusters active lease (sonnet) ← blocks: 14.5 `(b159a50)`
- [x] **14.7** Fence-token venue credential rotation (sonnet) ← blocks: 14.5 `(809b31d)`
- [x] **14.8** Weekly leader-kill drill scripted (gemma) `(91ccfd7)`
- [x] **14.9** Monthly cold-start drill scripted (gemma) `(91ccfd7)`
- [x] **14.10** Quarterly cross-region failover drill scripted (sonnet) `(fe3bbc8)`

## Phase 15 — MVP Integration

The v0 done-criteria, made executable. ~1 week.

- [x] **[MVP] 15.1** End-to-end MVP smoke test + replay-determinism on a 1-day mock log slice: FIX NewOrderSingle (US IG corp) → validator → staged → routed via mock venue → fill ack → allocation → confirmation → TRACE-mock, asserting a single trace ID through the whole chain and byte-identical replay (sonnet) ← blocks: 8.1, 11.2, 12.3, 12.6, 13.5 `(26f29d8)`
- [x] **15.2** FIX-wire end-to-end smoke — client FIX NewOrderSingle → validator → staged → routed → venue-facing FIX gateway (8.2) over a **real FIX session** to the venue simulator (11.15) → ack/fills back → allocation → confirmation → TRACE-mock; single trace ID + byte-identical replay; the 11.2 in-process-mock path stays green alongside (sonnet) ← blocks: 8.2, 11.15, 15.1 `(1d59f81)`

> **🚀 MVP v0 COMPLETE (2026-06-10).** All 11 [MVP]-tagged tasks are `[x]`. The end-to-end smoke (`MvpSmokeTest` in `ems-it`) drives FIX NewOrderSingle → validator → staged → mock MarketAxess → fill → allocation → confirmation → TRACE-mock with a single trace ID and byte-identical replay. See [v1 scope](#what-v1-looks-like) for what's next.

## Phase 16 — Cross-asset coverage (v1)

Enrich coverage beyond the single v0 corp-bond path: drive **US equity, preferred shares,
treasury, listed futures & options, FX spot, and FX forward** end-to-end through the existing
post-trade services. Each asset class gets a post-trade profile (STP stage set, confirmation
tolerance, applicable regulators, allocation lot size) keyed on the canonical
`InstrumentCore.AssetClass`, plus an end-to-end smoke proving it flows with a single trace ID and
byte-identical replay. Builds directly on 12.1–12.6, 13.5, 15.1.

- [x] **16.1** Cross-asset post-trade profile registry: per-`AssetClass` `StageProfile` + confirmation `MatchTolerance` + `RegulatorDeterminer.crossAssetUs()` (equity→CAT, treasury→TRACE, fut/opt→CFTC, FX-fwd→CFTC SDR, FX-spot→none) (sonnet) ← blocks: 12.2, 12.3, 12.5 `(e3dde05)`
- [x] **16.2** Asset-class allocation precision (lot sizing: equity/options whole units, FI $1k denomination, FX 10k min-notional) (sonnet) ← blocks: 12.1, 16.1 `(dcd250c)`
- [x] **16.3** Cross-asset end-to-end smoke: US equity, preferred, treasury, listed fut/opt, FX spot, FX forward each through allocation→STP→confirmation→reporting, asserting single trace ID + byte-identical replay (sonnet) ← blocks: 16.1, 16.2, 15.1 `(e944197)`

> **Phase 16 complete (2026-06-10).** Cross-asset post-trade coverage: 7 asset classes (US IG corp, treasury, US equity, preferred, listed fut/opt, FX spot, FX forward) flow end-to-end through allocation → STP → confirmation → reporting per their `AssetClassProfile`, each with a single trace ID and byte-identical replay (`CrossAssetSmokeTest`).

## Phase 17 — Usage Documentation (v1)

How to use the system — written for someone who didn't build it. Lives in `docs/`, kept current as
the v1 build-out lands. An initial `docs/USER_GUIDE.md` covering the as-built system was written
2026-06-10; these tasks extend and finalize it. (Named `USER_GUIDE` because `[[USAGE]]` is the
vault how-to in `00_index/`.)

- [x] **17.1** User guide (`docs/USER_GUIDE.md`): build/run/test, dev-stack bring-up, submitting orders (FIX + API + bulk), order lifecycle walk-through, mock venue vs FIX simulator — extend the 2026-06-10 initial version to cover everything the v1 build-out added (sonnet) ← blocks: 15.2 `(52557d8)`
- [x] **17.2** Operator guide (`docs/OPERATIONS.md`): config service, time/replay server, observability (dashboards/traces/logs), JMX introspection, drills, blue/green switchover (sonnet) ← blocks: 14.2, 14.5 `(0c517d5)`
- [~] **17.3** Developer guide refresh (`DEVELOPMENT.md` + `java/README.md`): module map, codegen pipeline, how to add an asset class / venue adapter / validator rule (sonnet) ← blocks: 17.1

## Phase 18 — Trader Desktop & Buyer-Readiness (v1.1)

Output of the 2026-06-10 buyer's-lens gap review ("would a head trader buy this?"). The spine
(determinism, full audit, cross-asset breadth, replay) is genuinely differentiated — but a desk
buys what it can see and the controls it must attest to. The trader-facing surface is described in
the workflow vault (`20_workflows/common/order-manager.md`, `staging-via-ticket`,
`staging-via-excel`) yet had **no build tasks**, and four architecture notes were designed but
never planned ([[arch-tca]], [[arch-surveillance]], [[arch-borrow-service]],
[[arch-notification-service]]) — closed below plus Phase 12 additions 12.12–12.16 and Phase 11
additions 11.16–11.17. **Not in the current [[LOOP]] goal; queue as the next goal.**

> **Front-end decision (2026-06-10, user):** the trader desktop is built on **Perspective**
> (<https://github.com/perspective-dev/perspective>) — the WebAssembly streaming-pivot data grid —
> for high-rate, responsive updates of rapid market-data and blotter streams. Market data arrives
> through a **pluggable feed SPI (18.12)**; the first implementation is **Bloomberg Desktop
> API / Server API (18.13)** so a desk with a terminal subscription lights up immediately. The
> internal quote server (Phase 9, deferred) becomes just another SPI implementation later.
> Suggested order within this phase: 18.12 → 18.13 → 18.1 → 18.14 → the rest.

- [ ] **18.1** Trading blotter — live orders + routes + fills in **Perspective** (WASM) tables fed by the 8.10 WS stream (Arrow/JSON row deltas, not full refreshes), per the [[order-manager]] workflow note (the buyer's first screen; ui/ today has only ops UIs) (sonnet) ← blocks: 8.10
- [ ] **18.2** Order ticket + staging UI per `staging-via-ticket` — per-asset-class ticket layouts, stage/amend/route actions against the 8.4 API surface (sonnet) ← blocks: 18.1
- [ ] **18.3** Basket / program trading — list load via 8.6, wave routing, aggregate monitoring (distinct from structurally-linked multi-leg packages 7.4) (sonnet) ← blocks: 7.5, 8.6, 18.1
- [ ] **18.4** Firm-wide kill switch — firm/desk/venue-scoped mass-cancel + cancel-on-disconnect + new-order lockout; one audited action (opus — control path; a silent failure here is catastrophic) ← blocks: 7.2
- [ ] **18.5** SEC 15c3-5 market-access pack — named mapping of controls (fat-finger 10.2, credit/capital limits 10.6, duplicate-order check, kill switch 18.4) + attestation evidence export (sonnet) ← blocks: 10.2, 10.6, 18.4
- [ ] **18.6** Borrow / locate service per [[arch-borrow-service]] — Reg SHO short-sale gating wired into the validator/compliance path (sonnet) ← blocks: 10.1
- [ ] **18.7** Intraday P&L — realized/unrealized off positions (10.7) + pricing (10.8), FX-converted to firm base currency (sonnet) ← blocks: 10.7, 10.8
- [ ] **18.8** Notification service per [[arch-notification-service]] — fills/rejects/limit-breach alerts to desktop + email/mobile sinks (sonnet) ← blocks: 8.4
- [ ] **18.9** Enterprise SSO — OIDC/SAML login + SCIM provisioning on the AAA layer (vendor due-diligence checklist item) (sonnet) ← blocks: 5.1
- [ ] **18.10** Maker-checker (4-eyes) approvals on config / limit / restricted-list changes (sonnet) ← blocks: 3.7, 10.4
- [ ] **18.11** Click-to-trade on streaming quotes (ESP) with slippage guard + last-look awareness (sonnet) ← blocks: 11.17, 18.1
- [ ] **18.12** Pluggable market-data feed SPI — `MarketDataFeed` interface (subscribe/unsubscribe by FIGI + field set, tick/quote callbacks, feed health), provider-agnostic so Bloomberg (18.13) now and the internal quote server (9.1) later are drop-in implementations; ticks bridge into Perspective via the 8.4 subscription topics (sonnet) ← blocks: 8.4
- [ ] **18.13** Bloomberg market-data adapter — BLPAPI `//blp/mktdata` subscriptions via Desktop API (localhost:8194) or Server API per config, mapping security/fields to the 18.12 SPI; session resilience + entitlement failures surfaced as feed health; runtime requires the desk's Bloomberg terminal/SAPI subscription, CI uses a fake feed (sonnet) ← blocks: 18.12
- [ ] **18.14** Market-data watchlist panel — Perspective grid streaming live ticks via 18.12 (sustained rapid-update path: row deltas into a Perspective table, no re-render), per-desk symbol lists (sonnet) ← blocks: 18.1, 18.12

## Done criteria for v0 (MVP)

All **[MVP]**-tagged tasks marked `[x]` (the [MVP v0 critical path](#mvp-v0-critical-path) — **not** all of phases 0–14; the rest is post-MVP). Concretely:

- End-to-end smoke: a FIX inbound NewOrderSingle → validator → staged → routed via venue adapter (mock) → fill ack → allocation → confirmation → TRACE-mock submission. Single trace ID through the whole chain.

- One asset class fully wired (start: US IG corp via mocked MarketAxess) end-to-end with full audit trail.

- Replay determinism passes on a 1-day mock log slice.

## What v1 looks like

- Three asset classes wired end-to-end (US IG corp, US equity, USD IRS).
- All venue adapters in 11.x talking to UAT venue endpoints.
- UAT-1 / UAT-2 blue/green deployments healthy.
- Compliance + Risk + Best-Ex full feature parity.
- Buyer-readiness: trader desktop + attestable controls per **Phase 18** (the 2026-06-10 gap
  analysis), with TCA/CAT/drop-copy from 12.12–12.16.

The plan beyond v1 is post-MVP. Don't pre-plan it.
