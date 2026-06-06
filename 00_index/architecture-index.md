# Architecture Index

The architectural spine the workflows and asset notes link back to. Captured in `80_architecture/` as atomic notes.

> Drives the entire EMS: **API-first** with FIX as a subset, batch-by-default, all internal traffic over **SBE on Aeron**, all state as an **append-only event log**, all permissions evaluated via **3-layer tag AND-gate**.

## Surface

- [[arch-api-first]] — batch-by-default API as the single operation surface
- [[arch-fix-api-bridge]] — FIX as a subset; mixed FIX+API client rule
- [[arch-sbe-aeron-transport]] — internal binary protocol on Aeron
- [[arch-sequence-recovery]] — session, sequence numbers, gap detection, heartbeats

## Persistence & Time

- [[arch-event-sourcing]] — append-only log; deterministic state derivation
- [[arch-time-replay-server]] — clock interface; deterministic replay

## OMS Core

- [[arch-order-staged]] — Staged Order Manager (Layer 1)
- [[arch-router-layer]] — Router (Layer 2)
- [[arch-automation-layer]] — rules between order and route

## Order Model Extensions

- [[arch-multileg]] — atomic / sequenced / independent multi-leg orders (FX swap, options spread, futures roll, portfolio trade)
- [[arch-aggregation]] — N parent orders → one execution unit, allocated back
- [[arch-fx-netting]] — value-date arithmetic, PB isolation, PAC constraints, swap-aware netting
- [[arch-order-route-lifecycle]] — **FIX-aligned canonical state machines** for orders and routes (Pending Replace `150=E`, Pending Cancel `150=6`, Replaced `150=5`, Canceled `150=4`, OrderCancelReject `35=9`, post-fill TradeCorrect `150=G` / TradeCancel `150=H`). Referenced by every workflow that touches amend/cancel semantics.
- [[arch-fix-appendix-d]] — **FIX Appendix D race conditions** that every production EMS must handle: Too-Late-to-Cancel (D4/D5), Fill-during-Pending-Replace (D7/D10), over-allocation prevention, PossResend / duplicate ClOrdID (D31), unsolicited cancel / restate, trade bust / correct cascade, concurrent cancel+replace, late-ack-after-local-terminal anomalies. Ends with an implementation contract checklist every venue adapter must pass.
- [[arch-fix-fsm-design]] — **Shared FIX-compliant FSM design.** The core load-bearing component: pure transition function `(state, event, context) -> (state', effects, events)` declared in versioned YAML definitions, codegen'd to all consuming languages, shared by Order / Route / Multileg / VenueSession / SOR. Covers definition format, composition, **lifecycle chaining + cascading cancellations** (the cancel-stuck-in-Pending problem), single-writer runtime, versioning, replay determinism, and the 5-layer testing strategy.

## Market Data

- [[arch-quote-server]] — PubSub with subscriber visibility, even over multicast

## Reference Data

- [[arch-symbology-figi]] — FIGI-first; SEDOL/CUSIP/ISIN as licensed/metered

## Validation & Identity

- [[arch-validator]] — single source of **hard reject** with standardized reject codes (no override)
- [[arch-firm-desk-user]] — three-level hierarchy + settings cascade
- [[arch-tag-permissions]] — 3-layer AND-gated permissions

## Pre-trade Auxiliary Components (overarching; tap into multiple layers)

- [[arch-compliance]] — **block-with-override** layer distinct from Validation. Pre-trade synchronous gates (fat-finger with price fallback, machine-gun rate + aggregation, allow/restricted/watch lists, account/KYC/ID-market checks, position-aware checks) **plus** continuous stream surveillance. Critical netted-vs-unnetted handling so a typo on a child that nets to zero on the parent still triggers fat-finger. Override mechanics with required tags, optional four-eyes, time-bound releases, mandatory rationale.
- [[arch-risk-engine]] — pre-trade and continuous **position-aware** risk: notional / VaR / DV01 / greeks / FX exposure / margin / stress. Independent of Compliance: a trade may pass Compliance but fail Risk, or vice versa.
- [[arch-position-service]] — read-only projection of current positions per account, derived continuously from fill events (and post-fill bust/correct reversals). Foundational for Compliance concentration checks and Risk position-aware caps.
- [[arch-surveillance]] — **post-trade pattern detection** for market abuse: spoofing / layering / wash / quote-stuffing / marking-close / front-running / cross-market manipulation. Outputs alerts to Compliance; severe alerts auto-freeze actor.

## Analytics

- [[arch-realtime-analytics]] — streaming benchmark service: VWAP, TWAP, **PWP** (participation-weighted price), arrival, NBBO/EBBO, mid, iNAV. Single source of truth consumed by Compliance (fat-finger ref price), SOR strategies, automation rules, TCA. Versioned formulas; replay-deterministic.
- [[arch-pretrade-analytics]] — **pluggable quant model registry** for execution strategy recommendation. Market-impact / liquidity-profile / spread-forecast / optimal-trajectory / strategy-recommender models. Output: cost estimate + ranked strategies. Consumed by SOR algo wheel as input; advisory, not blocking.
- [[arch-tca]] — **post-trade transaction cost analysis**: slippage vs arrival / VWAP / TWAP / PWP / close; impact / timing / opportunity / spread-capture decomposition; per-broker / per-algo / per-venue aggregation; MiFID II RTS 27/28 and US best-ex reporting; **feedback loop** to algo wheel `PERFORMANCE_TIER` scoring.

## Market Intelligence

- [[arch-ioi]] — **pluggable IOI network integration** (Autex, Bloomberg IOI, Liquidnet, Cantor, dealer-direct) with canonical IOI data model **differentiated** from orders and quotes. Preserves IOI qualifiers (NATURAL / SUPER_NATURAL / UNWOUND / IN_TOUCH_WITH / DELTA_HEDGED / PORTFOLIO_TRADE). Permission-scoped distribution respecting per-network client-segment restrictions.
- [[arch-rfq]] — **RFQ as first-class architectural component** spanning FX / FI / muni / govt / TBA / OTC derivatives / equity blocks and **equity ETF**. Canonical model with state machine (Requested → Active → Elected → Executed) handling last-look fades. Per-asset-class variants documented including ETF RFQ NAV-based pricing and AP-workflow specifics. Integrates with IOI provenance and SOR strategy dispatch.

## Bulk I/O

- [[arch-bulk-io]] — **first-class Excel / CSV import-export engine.** Versioned schemas per data domain (orders, fills, allocations, reference data, compliance lists). Format support (XLSX / XLSM / CSV / TSV / Parquet). Type coercion for the messy reality of spreadsheets. Per-row validation + compliance. Idempotent re-import. Multiple inbound sources (UI / FTP / SFTP / S3 / API). Replay-deterministic.

## Connectivity & Ops

- [[arch-venue-connectivity]] — outbound venue adapters (FIX / binary / REST)
- [[arch-smart-order-router]] — **pluggable layer between Route → Venue.** Appears to the router as a virtual venue; internally composes child routes per the selected strategy (algo wheel, slicer, dark-first, Reg-NMS-compliant, anti-gaming, cost optimizer). Plug-and-play: bypassed by default; activated by firm/desk setting. Algo wheel section covers WEIGHTED_RANDOM / ROUND_ROBIN / PERFORMANCE_TIER / COMMISSION_TIER selection with replay-stable seeding and best-ex audit chain.
- [[arch-jmx-introspection]] — per-component introspection & privileged injection

## Gaps Surfaced During Workflow Expansion

The first-draft workflow expansion (waves 1–6, ~46 notes) surfaced architecture concepts that workflows reference but that don't yet have dedicated `arch-*` notes. These are the **highest-priority architecture gaps** to close in a future round:

### High-priority gaps

- **`arch-allocation-service`** — Allocation engine semantics referenced by [[allocation-prime-broker]], [[arch-aggregation]], [[stp-summary]], [[arch-fx-netting]]. Needs: per-fill vs per-order allocation, rounding residual rules, multi-PB splits, deferred/late allocations ("block now, allocate later"), per-asset-class quirks.

- **`arch-stp-pipeline`** — Outbound post-trade pipeline ([[stp-summary]] documents the user-facing view, but the underlying adapter architecture beyond [[arch-venue-connectivity]] is implicit). Needs: stage ordering (allocation → confirmation → settlement instruction → reg-report → books-and-records), parallel vs sequential stages, retry / nack handling, anomaly queues.

- **`arch-confirmation-affirmation`** — Confirmation matching service referenced by [[route-to-cnf]] and [[stp-summary]]. Needs: affirmation matching algorithm, unmatched-trade queues, two-sided confirm semantics, dispute resolution, integration with MarkitSERV-style platforms.

- **`arch-markup-pricing-service`** — Pricing + markup service referenced by [[markup]] and [[fxel]]. Needs: reference-price sourcing, band-policy versioning, side-aware computation, disclosure metadata, Dodd-Frank / MiFID-style compliance hooks.

- **`arch-limit-state-projection`** — Limit usage projection referenced by [[trading-limits]] and the validator. Needs: how usage counters derive from the event log, intraday queries, soft-warn semantics, projection rebuild story.

### Medium-priority gaps

- **`arch-projection-engine`** — Generic read-side projection architecture. Referenced implicitly by [[order-manager]] blotter, [[trading-limits]] usage, [[counterparty-enablement]] enablement queries. Worth formalizing the rebuild semantics, snapshotting, idempotency in event_id.

- **`arch-reference-data-service`** — Beyond [[arch-symbology-figi|FIGI]]: instrument master, license metering protocols, batch resolution API performance characteristics, cache invalidation on corporate actions.

- **`arch-regulatory-reporting-service`** — Formalize the reporting service architecture referenced by [[regulatory-base]] and [[stp-summary]]. Per-regulator adapters, retry/backoff policies, late-report semantics, replay sandboxing.

### Lower-priority refinements

- **`arch-blotter-view`** — UI-side projection details surfaced in [[order-manager]] (live event subscription, permission-scoped views).
- **`arch-batch-service`** — Explicit batch lifecycle service from [[batch-creation]] (vs implicit `batch_name` everywhere else).
- **`arch-notes-schema-service`** — Per-firm custom-notes schema registry from [[notes-and-custom-notes]].

These gaps are intentionally not written yet — the workflow expansion is a more useful design review surface than another round of architecture notes would have been. Pick which to fill based on which workflows you find yourself referencing most in actual implementation work.
