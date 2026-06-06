---
type: workflow
category: routing
applies_to: ["fx", "fixed_income"]
status: draft
tags: [workflow/routing, workflow/rfq]
---

# Route to RFQ

Route a [[arch-order-staged|staged order]] to one or more dealers and collect competitive responses before electing an execution. Predominant in fixed income (Bloomberg ALLQ/FIT, MarketAxess, Tradeweb) and FX (FXConnect, BBG ALLQ, dealer-direct platforms).

## Purpose

Discover liquidity by sending an enquiry to a curated set of counterparties and receiving timed quote responses on which the trader (or [[arch-automation-layer|automation]]) can act. Unlike a working order at a CLOB, the EMS owns no resting state at the venue between the request and the trade.

## Trigger / Entry Point

- Trader / sales calls `route_orders([{order_id, venue: <rfq_venue>, mode: RFQ, dealers: [...]}])`.
- Or [[auto-route]] / [[multi-route-rfq]] rule fires the same API call.
- For solicited-trading flows, the route operation is implicitly created by accepting a price on the pre-trade validation screen — semantically identical at the API level (see [[two-step-approval]] for the approval interaction).

## Actors

- Trader / sales-trader at the EMS desk.
- Dealers (counterparties).
- [[arch-router-layer]] — owner of route lifecycle.
- [[arch-venue-connectivity|venue adapter]] for the chosen RFQ platform.
- [[arch-quote-server]] — distributes received quote responses to subscribers.

## Steps (canonical flow)

1. Pre-flight validation per [[arch-validator]]: venue-instrument support, dealer-list validity, per-dealer enablement tags ([[arch-tag-permissions]]).
2. Router materialises a `Route` with `mode=RFQ`, `dealers=[...]`, `expire_in=Δt`. Event `RouteSent` is logged.
3. Venue adapter encodes the RFQ in the venue's dialect (FIX `R` / `AH`, or proprietary REST / binary) and dispatches.
4. Responses stream in: each becomes a `QuoteResponse` event tagged to the `route_id`, fanned out via [[arch-quote-server]] on a per-route topic.
5. Trader (or automation) **elects** a response:
   - `execute_quote([{route_id, dealer, accepted_price, accepted_qty}])` → adapter sends the venue's accept message.
6. Venue confirms execution (`ExecutionReport` from the venue) → adapter emits `RouteFilled` → router emits `OrderFilled`.
7. Unsold quotes expire at `expire_in` or upon election; expirations are logged.

## Inputs

- `order_id` (must be in `READY` state — see [[arch-order-staged]]).
- `venue` (e.g. `MARKETAXESS_RFQ`, `BBG_ALLQ`, `FXCONNECT_RFQ`, `TRADEWEB_RFQ`).
- `dealers` — explicit list or `default_dealer_list` from [[arch-firm-desk-user|firm/desk settings]].
- `mode` — single-dealer vs multi-dealer vs **anonymous all-to-all** where the venue supports it (MarketAxess A2A).
- `expire_in` — quote window (typical: 30s–5m by asset class).
- `min_dealers_responding` — optional gate before allowing election.

## Outputs / Side Effects

- `RouteSent`, `QuoteResponseReceived`, `RouteFilled`/`RouteRejected`/`RouteExpired` events.
- Quote topic on [[arch-quote-server]]: `quote.{figi}.rfq.{route_id}` — UIs / rules subscribe.
- On fill: `OrderFilled` (parent order), FIX `ExecutionReport` to any paired FIX client.
- Possible `AllocationRequested` event if allocation template attached.

## Edge Cases & Nuances

- **No responses.** Route reaches `expire_in` with zero quotes → `RouteExpired`. Order returns to `READY` with `pending_actions: []` — trader decides next action.
- **Partial dealer set blocked by permissions.** If 4 dealers requested but the user lacks `#cpty-{x}` tags for 2, the validator returns `EMS-PRM-1003 desk_not_granted_cpty` for those, and the request continues with the remaining 2 **only** if `partial_ok=true`. Otherwise the whole route is rejected.
- **Aggressive accept while quote is stale.** Many venues will reject an `execute_quote` referencing a price older than the venue's "last good" — adapter translates to `EMS-RTE-3003 quote_no_longer_executable` with the venue's underlying code preserved.
- **Multi-route RFQ.** [[multi-route-rfq]] sends parallel RFQs across venues (e.g. simultaneously to BBG ALLQ and MarketAxess). Election is across the union; venues not elected receive cancel/expire.
- **Hit-rate-driven dealer list.** Some firms set rules that prune dealers from the default list based on recent hit rate — modelled as an [[arch-automation-layer|automation rule]] that rewrites `dealers` before dispatch.
- **Solicited single-dealer.** For an inbound bid from sales, the EMS may form a "route" of size 1 with the soliciting dealer as the only counterparty — same machinery.
- **TBA / pool-specified FI.** RFQs to BBG TBA route to the [[bloomberg-tba|TBA pool]]; the instrument extension carries pool conventions.
- **Asset-class semantics:**
  - **FX**: response carries `value_date`, `forward_points`, possibly `swap_points`; election must agree on the value date.
  - **Corp HY**: smaller dealer panel typical; voice fallback path may run in parallel — captured as a `voice_route_link` metadata pointer.
  - **Muni**: BWIC / OWIC ([[bloomberg-bwic-owic]]) is structurally an RFQ-like list; the same route shape models it with `mode=BWIC`.

## Relevant venues (see also `30_venues/`)

[[marketaxess]], [[tradeweb]], [[bloomberg-bridge]] (EM all-to-all), [[bloomberg-bmtf]] (EU MTF), [[trumid]], [[neptune]] (axes/IOIs pre-trade — not an execution venue), [[ice-bondpoint]], [[municenter]], [[bloomberg-bwic-owic]], [[bloomberg-tba]] (TBA RFQ), [[sef-platforms]] (mandatory for cleared OTC swaps), [[refinitiv-fxall]] · [[360t]] · [[currenex]] · [[fxspotstream]] · [[fx-connect]] (FX RFQ).

> Terminal monitor screens like [[bloomberg-allq]] / [[bloomberg-fit]] are price-discovery surfaces, **not** routable destinations — see [[_venue-index]].

## API mapping

```
operation: route_orders
items: [{
  order_id,
  venue:        VenueRef,
  mode:         RFQ | MULTI_RFQ | BWIC | A2A,
  dealers:      [DealerRef] | "default",
  qty:          decimal,            // can be < order.remaining
  expire_in:    duration,
  min_responding: int?,
  partial_ok:   bool
}]

operation: execute_quote
items: [{ route_id, dealer, accepted_price, accepted_qty }]
```

## Validator codes touched

`EMS-RTE-1001` (venue not enabled for instrument), `EMS-RTE-1003` (capability unsupported), `EMS-PRM-1001..1003` (cpty tag 3-layer), `EMS-RTE-3003` (stale quote at execute), `EMS-RTE-1005` (insufficient dealers responding).

## Permissions

- `#trade-{asset_class}` (3-layer).
- `#cpty-{venue}` and per-dealer tag.
- For BWIC/OWIC ([[bloomberg-bwic-owic]]): `#muni-bwic` typical.

## Related

- [[arch-router-layer]] · [[arch-venue-connectivity]] · [[arch-quote-server]] · [[arch-automation-layer]]
- [[route-single]] · [[multi-route-rfq]] · [[auto-route]] · [[partial-routes]] · [[spot-first]]
- [[allocation-prime-broker]] · [[marketaxess]] · [[tradeweb]] · [[bloomberg-bridge]] · [[trumid]]
- [[bloomberg-allq]] · [[bloomberg-fit]] (price-discovery screens — observed, not routed to)
