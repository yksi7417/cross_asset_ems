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
| ~~`wip/6.4-validation-rules`~~ | 6.4 | **EXTRACTED + RECONCILED 2026-06-12** — rules renumbered into EMS-ORD-6xxx blocks, catalog extended to 256 codes | done |
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
- [x] **6.4** Per-asset-class validation rules (gemma first drafts, sonnet review) — reconciled 2026-06-12: **catalog extended** (not remapped — 185 precise rules onto 26 generic codes would destroy the per-code feedback the desktop renders); per-asset-class blocks in the unused `EMS-ORD-6xxx` range (equity 60xx, bond 61xx, fx 62xx, derivative 63xx, commodity 64xx, crypto 65xx, abs 66xx, loan 67xx — also fixes the draft's triple reuse of EMS-ORD-1001), 185 catalog entries generated from the rules, consistency pinned by `RejectCodeCatalogConsistencyTest` (unique, in-block, catalog-resolving, metadata count)
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
- [ ] **11.18** RFQ workflow for RFQ-traded instruments (user request 2026-06-12) — ETFs (create/redeem-driven block liquidity), fixed income (corp/govt — already the dominant trading style on MKAX/TWEU-style venues), and other quote-driven instruments: (1) instrument-level `quoteStyle` flag in the security master (ORDER_BOOK / RFQ / BOTH) so the ticket knows which workflow to offer; (2) RFQ lifecycle on the OMS — request → dealer quotes (N respondents, countdown) → accept/decline/expire — distinct FSM from the order path, linked to the resulting execution; (3) desktop RFQ ticket panel: pick instrument + size + side, fire RFQ to selected dealers, live quote ladder with countdown, click-to-accept books the fill into the blotter like any execution; (4) demo edge: mock dealers quoting around the simulated feed for the ETF + bond instruments in DemoUniverse; E2E spec for the workflow ← blocks: 11.13, 18.2
- [ ] **11.14** RFQ-to-3 enforcement for [[mat|MAT]] swaps (sonnet) ← blocks: 11.13
- [x] **11.15** FIX venue simulator — venue-side FIX acceptor for end-to-end wire tests (sits alongside the in-process mock 11.2, which stays): session layer (Logon/Heartbeat/TestRequest/SequenceReset + ResendRequest recovery), NewOrderSingle/Cancel/Replace handling with Appendix-D-correct pending states, configurable execution model (ack → partial/full fills, rejects, busts), runs in-process for `ems-it` and standalone via Gradle (sonnet) ← blocks: 11.1 `(04adb49)`
- [x] **11.16** Broker algo support: FIX `StrategyParameters` / FIXatdl ingestion + algo ticket metadata so broker algos are routable with custom parameters (sonnet) ← blocks: 11.1
- [x] **11.17** FX ESP streaming executable quotes + last-look handling (complements RFQ; needed for click-to-trade) (sonnet) ← blocks: 9.1 (satisfied by 18.12 SPI per 2026-06-10 rescope), 11.1 `(db832b5)`

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
- [x] **12.12** [[finra|CAT]] submission — equities/options order-event reporting (16.1 already maps equity→CAT; this is the submission adapter, sibling of 12.6–12.9) (sonnet) ← blocks: 12.5
- [x] **12.13** Commissions / fees / accrued-interest engine — per-broker + per-asset-class schedules, applied at allocation and carried onto confirms (a buyer expects net-money confirms, not clean qty×price) (sonnet) ← blocks: 12.1
- [ ] **12.14** TCA per [[arch-tca]] — slippage vs arrival/VWAP/IS benchmarks, venue/broker league tables, exportable best-ex committee pack (sonnet) ← blocks: 9.5, 12.10
- [x] **12.15** Surveillance feed per [[arch-surveillance]] — order/exec event export + baseline alerts (layering, wash, spoof-pattern, fat-finger cluster) (sonnet) ← blocks: 3.3
- [x] **12.16** Client drop-copy service — real-time FIX drop of executions per client/desk/firm scope (sonnet) ← blocks: 8.2

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
- [x] **17.3** Developer guide refresh (`DEVELOPMENT.md` + `java/README.md`): module map, codegen pipeline, how to add an asset class / venue adapter / validator rule (sonnet) ← blocks: 17.1 `(3c4c873)`

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

- [x] **18.1** Trading blotter — live orders + routes + fills in **Perspective** (WASM) tables fed by the 8.10 WS stream (Arrow/JSON row deltas, not full refreshes), per the [[order-manager]] workflow note (the buyer's first screen; ui/ today has only ops UIs) (sonnet) ← blocks: 8.10 `(fefdc9a)`
- [x] **18.2** Order ticket + staging UI per `staging-via-ticket` — per-asset-class ticket layouts, stage/amend/route actions against the 8.4 API surface (sonnet) ← blocks: 18.1 `(bfdddba)`
- [x] **18.3** Basket / program trading — list load via 8.6, wave routing, aggregate monitoring (distinct from structurally-linked multi-leg packages 7.4) (sonnet) ← blocks: 7.5, 8.6, 18.1 `(3554682)`
- [x] **18.4** Firm-wide kill switch — firm/desk/venue-scoped mass-cancel + cancel-on-disconnect + new-order lockout; one audited action (opus — control path; a silent failure here is catastrophic) ← blocks: 7.2 `(6aa282c)`
- [x] **18.5** SEC 15c3-5 market-access pack — named mapping of controls (fat-finger 10.2, credit/capital limits 10.6, duplicate-order check, kill switch 18.4) + attestation evidence export (sonnet) ← blocks: 10.2 (deferred — carried as DEFERRED control in the pack), 10.6, 18.4 `(e702bb7)`
- [x] **18.6** Borrow / locate service per [[arch-borrow-service]] — Reg SHO short-sale gating wired into the validator/compliance path (sonnet) ← blocks: 10.1 `(9c3e89f)`
- [x] **18.7** Intraday P&L — realized/unrealized off positions (10.7) + pricing (10.8), FX-converted to firm base currency (sonnet) ← blocks: 10.7, 10.8 `(f007a19)`
- [x] **18.8** Notification service per [[arch-notification-service]] — fills/rejects/limit-breach alerts to desktop + email/mobile sinks (sonnet) ← blocks: 8.4 `(4a2e5e5)`
- [x] **18.9** Enterprise SSO — OIDC/SAML login + SCIM provisioning on the AAA layer (vendor due-diligence checklist item) (sonnet) ← blocks: 5.1 `(b217889)`
- [x] **18.10** Maker-checker (4-eyes) approvals on config / limit / restricted-list changes (sonnet) ← blocks: 3.7, 10.4 `(05e11e4)`
- [x] **18.11** Click-to-trade on streaming quotes (ESP) with slippage guard + last-look awareness (sonnet) ← blocks: 11.17, 18.1 `(d8ced96)`
- [x] **18.12** Pluggable market-data feed SPI — `MarketDataFeed` interface (subscribe/unsubscribe by FIGI + field set, tick/quote callbacks, feed health), provider-agnostic so Bloomberg (18.13) now and the internal quote server (9.1) later are drop-in implementations; ticks bridge into Perspective via the 8.4 subscription topics (sonnet) ← blocks: 8.4 `(068cbee)`
- [x] **18.13** Bloomberg market-data adapter — BLPAPI `//blp/mktdata` subscriptions via Desktop API (localhost:8194) or Server API per config, mapping security/fields to the 18.12 SPI; session resilience + entitlement failures surfaced as feed health; runtime requires the desk's Bloomberg terminal/SAPI subscription, CI uses a fake feed (sonnet) ← blocks: 18.12 `(8253d2f)`
- [x] **18.14** Market-data watchlist panel — Perspective grid streaming live ticks via 18.12 (sustained rapid-update path: row deltas into a Perspective table, no re-render), per-desk symbol lists (sonnet) ← blocks: 18.1, 18.12 `(715c3d7)`
- [x] **18.15** Runnable end-to-end demo (user request 2026-06-11, post-goal addendum) — one-command launcher for demo edge + desktop, sample basket list, and a guided walkthrough doc mapping every UI action to its backend call (logon → streams → ticket/preview → route → fills → baskets/waves → ESP click-to-trade → kill drill → resume semantics) (sonnet) ← blocks: 18.1–18.14 `(effd6cc)`
- [x] **18.16** Demo video + DEMO.md entry point (user request 2026-06-11) — Playwright-recorded browser session of the 18.15 walkthrough (video + key screenshots committed under docs/demo/), root `DEMO.md` linked from `README.md`; flushed out + fixed the Perspective WASM boot bug (explicit init_server/init_client — the desktop hung in any browser without it) and the chroma-js dev-mode interop (sonnet) ← blocks: 18.15 `(bd58d34)`
- [x] **18.17** Desktop UX round 2 (user feedback 2026-06-11) — (1) discoverable per-column sort/filter/group, (2) security NAME everywhere (FIGI demoted to optional column), (3) linked blotter: order click filters routes (toggleable), (4) fills hidden until a route is selected (fill volume = render cost), (5) right-click context menus with multi-select (ctrl+click) for batch ops incl. aggregate-selection-into-basket (sonnet) ← blocks: 18.16 `(f5d8908)`
- [x] **18.18** QA review round 1 (user request 2026-06-11) — systematic defect hunt over the desktop workflows + fixes: actionable error feedback (catalog codes/messages surfaced, not bare counts), state-aware context menus, amend prefill, request serialization (session-seq races), selection visibility, watchlist management UI; 16-finding report in docs/QA_REVIEW.md (sonnet) ← blocks: 18.17 `(013e7eb)`
- [x] **18.19** Workflow test automation + strategy — test-trophy suite: API-driven workflow contract tests (the bulk; fast, assert the exact codes the UI renders), a small Playwright E2E set (one spec per trader workflow) against a deterministic quiet-mode edge, mock-vs-real decision rules in docs/TESTING.md, CI wiring (sonnet) ← blocks: 18.18 `(6862f53)`
- [x] **18.20** Grid selection model round 2 (user feedback 2026-06-11) — right-click selects the row under the cursor (pointer truth; no stale-selection actions), visible row highlighting that survives streaming redraws, shift+click range select, keyboard selection (arrows / shift+arrows / page keys); selection re-reads live row state at menu-open so state-aware menus never act on stale snapshots (sonnet) ← blocks: 18.18

> **Desktop feedback round 3 (user, 2026-06-11).** Five further asks from hands-on demo use,
> captured as 18.21–18.25: the demo universe is equity-only despite the cross-asset back end;
> grouping needs trading-grade aggregation semantics (signed qty, weighted-average price,
> same-or-MULTI) rather than Perspective's defaults; columns need a discoverable picker and a
> single documented add-a-column path; the blotters should dock/rearrange VSCode-style; and
> orders/routes need a visible audit trail (the event history already exists on the server —
> surface it to the trader).

- [x] **18.21** Cross-asset demo universe (user feedback 2026-06-11) — `DemoUniverse.java`: 13 instruments, ≥2 per supported asset class (1 US + 1 international) — equity (Apple/Microsoft/Toyota), govt bonds (UST/Gilt), corp credit (Apple '29/VW '31), FX (EURUSD spot/USDJPY fwd), listed futures (ES/SX5E), IRS (USD SOFR/EUR €STR) — each with natural qty units + class-appropriate venue MICs; demo bot trades per-class conventions; non-USD P&L converts via demo FX rates; `assetClass` column on the order blotter (enriched client-side with the name cache); coverage rule pinned by `DemoUniverseTest` ← blocks: 18.15, 16.3
- [x] **18.22** Trading-grade grouping & aggregation semantics (user feedback 2026-06-11) — (1) **signedQty** expression (buy +, sell −) summed under grouping, plain qty stays unsigned-sum; (2) identity columns (px/side/state/subState/account/orderId/ts/tif/ordType/venue/name/assetClass) aggregate `unique` with a **MULTI badge** painted on mixed group cells (`aggregates.ts` style listener — under group_by every rendered row is an aggregate, the engine yields null for mixed `unique`); (3) **avgPx** = server-side per-order/route WAP (BlotterPublisher execution accumulators, recorded before delegation + rolled back on FSM reject) aggregating group-level as `["weighted mean", ["cumQty"]]` (Perspective 3.8 serde: weight column must be array-wrapped); (4) **TIF** (FIX tag-59 labels) + **ordType** (LMT/MKT) on the order blotter. Pinned by BlotterProjectionTest (WAP incl. terminal row, MKT/IOC labels, rejected-fill rollback) + headless probe ← blocks: 18.17
- [x] **18.23** Column picker & flexible column management (user feedback 2026-06-11) — discoverable show/hide-columns UX on every blotter (Perspective's settings panel exposes this but is hidden behind the config toggle — make it obvious or add a dedicated picker), per-user/desk layout persistence (save/restore), and one documented place to add a new column so server fields (e.g. 18.22's TIF/orderType) reach the grid without per-grid wiring; mind the standing rule that interaction key columns must stay configured ← blocks: 18.22
- [x] **18.24** Dockable blotters — VSCode-style layout (user feedback 2026-06-11) — evaluate **`@finos/perspective-workspace`** (drag/dock/split/tab around Perspective viewers, native fit) vs a generic dock layout (Golden Layout / Dockview) hosting all panels; pick one, migrate the desktop shell, persist layouts per user, keep non-grid panels (ticket, kill, notifications) dockable too ← blocks: 18.23
- [x] **18.25** Order/route audit trail viewer (user feedback 2026-06-11) — per-order and per-route message history visible to the trader: staged/amended/ready/routed/sent/acked/cancel-requested/cancelled/filled, each with timestamp, actor, venue, and catalog code where applicable; served from the event-sourced journal via `GET /api/v1/orders/{id}/history` (+ routes equivalent), rendered as a timeline detail panel opened from the context menu ("View history") — the trader-visible face of the audit spine ← blocks: 18.20
- [x] **18.26** Wire real telemetry from the demo edge (user feedback 2026-06-11: "Grafana/OpenSearch/Jaeger show nothing from the dev demo") — `EmsOpenTelemetry` (the OTLP traces/metrics/logs pipeline, 13.1) is only ever constructed by `OtelToyTrace`; **no service main, including `TraderDesktopEdgeMain`, initializes the SDK**, so the demo emits zero telemetry and the 13.4 dashboards were only validated against the toy generator. Fix: build `EmsOpenTelemetry` in the demo edge (honoring `OTEL_EXPORTER_OTLP_ENDPOINT`, no-op when the collector is absent so tests/CI stay clean), span per REST operation + order/route lifecycle transition carrying the internal trace ID as a span attribute, OTel log appender bridge, demo-bot activity visible as live traffic in Jaeger/Grafana/OpenSearch; document the demo+obs-stack bring-up in docs/DIAGNOSTICS.md ← blocks: 13.1, 18.15
- [x] **18.27** Multi-select blotter linking (user feedback 2026-06-11: "linking between multi-select of order blotter and route blotter always only selects 1") — blotter linking is single-row today (`onPrimary` fires per click); make it selection-aware: selecting N orders filters ROUTES to all N (`["orderId", "in", [...]]` filter), selecting N routes reveals FILLS for all N; keyboard/shift-click selections drive linking the same as clicks; link chips show "⛓ N orders"; E2E pin in linking.spec ← blocks: 18.20
- [x] **18.28** Notional column (user feedback 2026-06-11) — `notional = px × signedQty` on the order and route blotters as a Perspective expression column (signed: buys positive, sells negative), aggregated **sum** under grouping so a grouped book reads its net notional; visible by default next to signedQty; respect per-class price meaning (IRS "px" is a rate — label/format the column so a 1M-notional swap doesn't read as px×qty dollars; futures lack contract multipliers in the demo security master — note as known limitation or add multiplier to DemoUniverse) ← blocks: 18.22
- [x] **18.29** Issuer / underlying-company grouping (user feedback 2026-06-11: "a bond of MSFT could be grouped with stock of MSFT, or convertible bond of MSFT along with options — look at them as a group") — surface the issuer on every blotter row so group-by-issuer collapses one company's whole capital structure across asset classes: (1) populate `issuerLei` in `DemoUniverse` cores (it exists on `InstrumentCore`, currently null) + a demo LEI→issuer-name registry; (2) `GET /api/v1/instruments/{figi}` returns `issuer`; (3) UI caches issuer alongside name/assetClass (`instruments.ts`) and adds an `issuer` column (configured on orders/routes/fills, same-or-MULTI aggregate); (4) extend the demo universe so the story shows: add an MSFT convertible bond and an MSFT listed option, sharing the Microsoft issuer with the equity — grouping by issuer then shows stock + convert + option + (Apple already has equity + '29 corp bond); derivatives use the UNDERLYING's issuer (an option on MSFT groups under Microsoft); index/rates/FX products without a single issuer show blank, not a fake issuer ← blocks: 18.21, 18.22
- [x] **18.31** Backend-restart recovery (user feedback 2026-06-11: "when backend comes back, the front end has no idea — no updates until refreshed") — the streams already retry forever, but a RESTARTED backend has fresh in-memory sessions and topic seqs from 1, so the old sessionId is rejected on every retry and the old cursor points past the new world; chips stay orange until a manual refresh. Fix: (1) `recovery.ts` watchdog — while any stream is not live, poll a cheap REST endpoint; two consecutive healthy responses with streams still dead ⇒ server is back but our session/cursors are not ⇒ `location.reload()` (a brief network blip never trips it: WS reconnects at 500ms backoff long before the second 3s poll); (2) seamless re-image — the logon token persists in `sessionStorage` and the page auto-logs-on on load (with retry while the server is still booting), so the recovery reload lands on a live desktop, not a logon prompt ← blocks: 18.1
- [x] **18.30** Currency model: trading vs base vs settlement currency (user feedback 2026-06-11) — a single `currency` field cannot express how currency actually behaves per security type; model the distinctions and document them. **(a) Knowledge-base note** `70_concepts/currency-in-execution.md` ([[currency-in-execution]]): how currency drives execution & order management per security type — FX pairs (BASE/QUOTE convention, price = quote-per-base, qty in base units, settlement in BOTH legs; inverse pairs e.g. USD/JPY vs JPY/USD and market-convention direction), samurai/eurobond-style debt (foreign issuer, local-denomination: a Toyota USD bond vs a Microsoft JPY samurai — denomination ≠ issuer country), ADRs/GDRs (underlying listed in one ccy, depositary receipt TRADES and SETTLES in another, ratio conversion + FX exposure), dual-listed/multi-currency lines (same ISIN trading on XLON in GBp vs XETR in EUR — minor-unit pence trap), derivatives (price ccy vs settlement ccy vs underlying ccy; quanto note), and what each means for: limit-price validation, notional computation (18.28), P&L conversion (18.7), settlement instructions (50_clearing_settlement), and best-ex comparison across venues quoting in different ccys. **(b) Schema**: extend `InstrumentCore` (or a sidecar) with `tradingCurrency` (what px is quoted in, incl. minor-unit flag for GBp), `settlementCurrency` (what cash moves), and for FX `baseCurrency`/`quoteCurrency`; existing `currency` maps to tradingCurrency for back-compat. **(c) Wire-through**: instruments endpoint returns all of them; ticket shows trading ccy on the px field + settlement ccy on the preview; blotter gains configured `ccy` (trading) and `settleCcy` columns (same-or-MULTI); P&L converts via trading ccy (fix the 18.21 `CURRENCY_OF` to distinguish); notional (18.28) labels in trading ccy. **(d) Demo universe**: add a Microsoft JPY samurai bond, a Toyota ADR (USD-traded vs the XTKS JPY line), and ensure EUR/USD vs USD/JPY show base/quote correctly. Pin with unit tests per security type + a contract test on the instruments endpoint ← blocks: 18.21, 18.28, 18.29

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
