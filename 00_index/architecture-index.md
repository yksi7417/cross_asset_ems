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

## Post-trade Pipeline

- [[arch-stp-pipeline]] — orchestration contract for the post-trade pipeline. Stage ordering (allocation → {confirmation || SI || reg-report || B&R} in parallel). Per-trade pipeline state. Anomaly handling per stage with independent ops queues. Per-asset-class profiles.
- [[arch-allocation-service]] — per-fill allocation engine with policies (PRO_RATA / AVG_PRICE / SEQUENCED / CUSTOM), rounding strategies, multi-PB splits, deferred ("block now, allocate later"), 2-level aggregation allocation, bust/correct reversal cascade.
- [[arch-confirmation-affirmation]] — both-sided trade match for settlement. Pluggable network adapters (BBG VCON / MarkitSERV / OASYS / DTCC CTM / MarketAxess Post-Trade). Per-asset-class match keys with tolerance. Affirmation flow for allocations.
- [[arch-regulatory-reporting-service]] — per-trade + periodic reports to TRACE / MSRB / CFTC SDR / FINRA CAT / FICC / Fed / MIFIR / EMIR. Determination logic, per-regulator profiles, deadline tracking, ack/nack lifecycle, retry policy, amendment/void protocol.

## Foundational Services

- [[arch-projection-engine]] — generic read-side projection framework. Used by positions, blotter, limit usage, allocation history, surveillance windows, TCA aggregates, compliance counters, route state, cpty enablement, wheel perf metrics. Single-writer per partition; declared snapshot strategy; versioned rebuilds; cross-projection consistency.
- [[arch-reference-data-service]] — unified reference data registry for everything that isn't FIGI. Accounts, broker codes, counterparty enablement, allocation templates, compliance lists, calendars, market microstructure, strategy/wheel definitions, validator/compliance rule sets, FSM definitions, SBE schemas, per-firm settings. Effective-date supersession, change-approval workflows, distribution + cache invalidation.
- [[arch-pricing-service]] — valuation pricing distinct from quotes and benchmarks. Indicative / model-based / mark-to-market / NAV / iNAV / manual marks. Pluggable model registry. Fallback chain consumed by compliance fat-finger check. Curve/surface management. License metering for pricing data.
- [[arch-notification-service]] — unified alert/escalation routing to humans + systems. Multi-channel (UI / email / SMS / IB / mobile / webhook). Audience resolution, throttling/digest, ack tracking, escalation chains. Used by Compliance / Risk / Surveillance / STP anomalies / reporting deadlines / component health.
- [[arch-corporate-actions]] — splits, dividends, mergers, spin-offs, name changes, rights issues, redemptions. Lifecycle (Announced → Locked → Applied → Cancelled). Application to positions, symbology, pricing, open orders. Multi-source de-duplication.
- [[arch-borrow-service]] — equity stock borrow: locate, borrow execution, returns, recall handling, cost accrual, hard-to-borrow flags. Pre-trade short-sale eligibility for Compliance. Reg SHO compliance attestation.

## Connectivity & Ops

- [[arch-venue-connectivity]] — outbound venue adapters (FIX / binary / REST)
- [[arch-smart-order-router]] — **pluggable layer between Route → Venue.** Appears to the router as a virtual venue; internally composes child routes per the selected strategy (algo wheel, slicer, dark-first, Reg-NMS-compliant, anti-gaming, cost optimizer). Plug-and-play: bypassed by default; activated by firm/desk setting. Algo wheel section covers WEIGHTED_RANDOM / ROUND_ROBIN / PERFORMANCE_TIER / COMMISSION_TIER selection with replay-stable seeding and best-ex audit chain.
- [[arch-jmx-introspection]] — per-component introspection & privileged injection

## Gaps Surfaced (Revised after expansion rounds)

After three expansion rounds (workflows + analytics/IOI/RFQ/bulk-io + post-trade/foundational/corporate-actions/borrow), the original high-priority and medium-priority gaps are largely closed. The current open list is shorter; pick from it based on actual implementation work.

### Remaining open gaps (lower priority)

- **`arch-markup-pricing-service`** — partially covered by [[arch-pricing-service]] + the [[markup]] workflow note; the band-policy storage + Dodd-Frank disclosure metadata could be formalized as its own note when the markup workflow gets more concrete implementation requirements.
- **`arch-limit-state-projection`** — covered conceptually by [[arch-projection-engine]] (limit_usage is a concrete projection example); could be formalized as its own note documenting limit-specific concerns (rolling windows, soft-warn vs hard-cap, cross-currency conversion).
- **`arch-news-events`** — referenced by [[arch-surveillance]] for insider-pattern detection. Needs: feed integrations, news enrichment, MNPI-flag derivation.
- **`arch-account-master-kyc`** — conceptually covered by [[arch-reference-data-service]] (accounts is a domain there); could spin out if KYC complexity warrants.
- **`arch-blotter-view`** — UI-side projection details from [[order-manager]] workflow. Concrete implementation work would benefit; not blocking design review.
- **`arch-batch-service`** — explicit batch lifecycle ([[batch-creation]] workflow). Adequate for the workflow as written; formalize if batch-level operations grow.
- **`arch-notes-schema-service`** — per-firm custom-notes registry ([[notes-and-custom-notes]]). Conceptually covered by [[arch-reference-data-service]].

### Tooling (would be useful when implementation begins)

- **`arch-fsm-codegen-pipeline`** — the YAML → code + SBE + diagrams + tests pipeline from [[arch-fix-fsm-design]]. Sketched in the FSM design note; formal note useful when the pipeline gets built.
- **`arch-schema-registry`** — distinct from the FSM/SBE codegen pipeline; the runtime schema registry. Conceptually covered by [[arch-reference-data-service]] (schemas are a domain).

Pick the next round based on what's load-bearing for actual implementation work.
