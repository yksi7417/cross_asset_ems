---
type: workflow
category: routing
applies_to: ["fx", "fixed_income"]
status: draft
tags: [workflow/routing, workflow/confirmation]
---

# Route to CNF (Confirmation)

Route a [[arch-order-staged|staged order]] into a **confirmation** workflow where the trade is materialized against a counterparty that the desk already agreed price with (typically by voice, IB chat, or another out-of-band channel). The CNF venue is effectively a booking surface — no price discovery; just a structured way to record and report the trade.

## Purpose

Capture an off-platform agreement as a first-class trade in the EMS: full audit, allocation, regulatory reporting, and downstream STP — without losing the fact that the price was agreed externally.

## Trigger / Entry Point

- Sales-trader has agreed a price with a counterparty by voice or IB ([[bloomberg-ib]]) and wants to book it.
- API: `route_orders([{order_id, venue: CNF, mode: CONFIRMATION, counterparty, agreed_price, agreed_qty, agreement_ref}])`.
- A trader UI or dealer-facing confirmation screen exposes a "Confirm" action that calls the same API.

## Actors

- Sales-trader (originator of the off-platform agreement).
- Counterparty (already aligned; the confirmation is sent for their FIX/IB confirmation systems to acknowledge).
- [[arch-router-layer]] — owns the route lifecycle.
- The CNF venue adapter — usually FIX with a specific subscription convention, or proprietary BBG IB confirmation flow.

## Steps

```mermaid
sequenceDiagram
  participant S as Sales-Trader
  participant O as Order Layer
  participant R as Router
  participant A as CNF Adapter
  participant C as Counterparty

  Note over S,C: Price agreed off-platform (voice / IB)
  S->>O: route_orders(mode=CONFIRMATION, cpty, price, qty, agreement_ref)
  O->>O: validate (cpty enabled, agreement_ref unique)
  O->>R: create Route(state=Pending, mode=CONFIRMATION) — see [[arch-order-route-lifecycle]]
  R->>A: send confirmation
  A->>C: wire confirmation message
  C-->>A: ack / affirm
  A-->>R: RouteFilled (immediate; no price discovery)
  R-->>O: OrderFilled
  Note over A,C: Some venues require a 2-sided affirmation before fill is final
```

1. Sales-trader provides the agreed `counterparty`, `agreed_price`, `agreed_qty`, and an `agreement_ref` (free-form sales reference, e.g. a chat ID or voice ticket #).
2. Validator checks the counterparty is enabled and the `agreement_ref` hasn't been used in the current trade-date window.
3. Router creates the route, adapter transmits to the counterparty's confirmation endpoint.
4. Some CNF venues use **affirmation/confirmation matching** — both sides post their version, the venue matches. Mismatch → unmatched-trade workflow ([[stp-summary]] tracks).
5. On match / single-sided ack: route immediately `FILLED` at agreed price; order fills accordingly.

## Inputs

- `order_id` (`READY`).
- `venue: CNF` plus a sub-type if the firm has multiple confirmation venues (BBG, MarkitSERV affirmation, internal).
- `counterparty` — the dealer code or LEI.
- `agreed_price`, `agreed_qty` — required.
- `agreement_ref` — free-form unique within trade date; sales captures voice ticket or chat ref.
- Optional `trade_date`, `value_date` for FX, `settle_date` for FI.

## Outputs / Side Effects

- `RouteSent` (with `mode=CONFIRMATION`), `RouteFilled` events.
- Trade booking event downstream (allocation, regulatory reporting).
- TRACE / MSRB / CFTC SDR reporting fired immediately for relevant instrument types — see `40_regulatory/`.

## Edge Cases & Nuances

- **Counterparty disputes the price.** Counterparty responds with a different price → unmatched. Sales must amend the CNF route (`replace_routes`) with corrected fields, or cancel and re-issue.
- **Duplicate `agreement_ref`.** `EMS-RTE-1010 duplicate_agreement_ref`. Forces sales to verify they aren't double-booking.
- **No counterparty FIX session.** Some counterparties don't accept electronic confirmations. The "venue" then is internal-only — the trade is booked and reported, but no outbound confirmation; counterparty confirms via their post-trade process.
- **Asset-class semantics:**
  - **FX**: `value_date` typically required; PB info passes through.
  - **FI**: `settle_date`, `accrued_interest`, `yield` calculations all derived from agreed price.
  - **Repo**: Two cashflows (open and close) confirmed under one agreement; CNF is structurally similar to multileg with two settlements.
- **Self-trade prevention.** A CNF route where the counterparty matches an internal account triggers `EMS-RTE-2010 self_trade_blocked`.
- **Two-step approval.** Some firms gate large CNF trades behind [[two-step-approval]] — the approver typically holds `#cnf-approver`.

## API mapping

```
operation: route_orders
items: [{
  order_id,
  venue:           CNF_VenueRef,
  mode:            CONFIRMATION,
  counterparty:    CounterpartyRef,
  agreed_price:    decimal,
  agreed_qty:      decimal,
  agreement_ref:   string,
  value_date?:     date,
  settle_date?:    date
}]
```

## Validator codes touched

`EMS-RTE-1001` (venue not enabled), `EMS-RTE-1010` (duplicate agreement_ref), `EMS-RTE-2010` (self-trade blocked), `EMS-PRM-1001..1003` (cpty tag 3-layer), `EMS-RTE-3002` (counterparty disputes — async, recorded on receipt).

## Permissions

- `#trade-{asset_class}` (3-layer).
- `#cnf-confirm` (some firms restrict the CNF mode to sales/desk leadership only).
- `#cpty-{counterparty}` per dealer.

## Related

- [[arch-router-layer]] · [[arch-venue-connectivity]] · [[arch-validator]]
- [[bloomberg-ib]] · [[stp-summary]] · [[two-step-approval]]
- [[route-single]] · [[route-to-rfq]] · [[auto-route]]
