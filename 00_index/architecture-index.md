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

## Market Data

- [[arch-quote-server]] — PubSub with subscriber visibility, even over multicast

## Reference Data

- [[arch-symbology-figi]] — FIGI-first; SEDOL/CUSIP/ISIN as licensed/metered

## Validation & Identity

- [[arch-validator]] — single source of "no" with standardized reject codes
- [[arch-firm-desk-user]] — three-level hierarchy + settings cascade
- [[arch-tag-permissions]] — 3-layer AND-gated permissions

## Connectivity & Ops

- [[arch-venue-connectivity]] — outbound venue adapters (FIX / binary / REST)
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
