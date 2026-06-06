---
type: workflow
category: routing
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/routing, workflow/resting]
---

# Route to Resting

Place a passive limit order at a venue and **let it rest** in the order book until filled, cancelled, or expired. Contrasts with aggressive marketable routing ([[route-single]]) and with quote-discovery flows ([[route-to-rfq]]).

## Purpose

Earn the spread (or improve average price) by adding liquidity rather than taking it. The route sits at the venue's CLOB; the EMS holds an obligation to track its lifecycle and manage replaces / cancels.

## Trigger / Entry Point

- Trader sends a passive limit explicitly via API `route_orders([{order_id, venue, mode: RESTING, limit_price, tif}])`.
- [[auto-route]] rule that prefers passive when the order has a non-aggressive limit price (e.g. inside-bid for a buy order is aggressive; below-bid is passive).
- An [[arch-automation-layer|automation rule]] like [[fx-automation-tradebest|TradeBest]] reposting at chasing prices.

## Actors

- Trader / DMA user / automation actor.
- Venue's order book.
- [[arch-router-layer]] — tracks the resting route's lifecycle including replaces.
- [[arch-quote-server]] — used by automation to decide when to reprice.

## Steps

```mermaid
sequenceDiagram
  participant U as User / Rule
  participant R as Router
  participant V as Venue
  participant Q as Quote Server

  U->>R: route_orders(mode=RESTING, price, tif)
  R->>V: 35=D NewOrderSingle
  V-->>R: 35=8 ExecType=0 New<br/>(RouteAcknowledged, state=Working)
  loop until terminal
    Q-->>R: quote update
    alt automation rule fires reprice
      U->>R: replace_routes(new price)
      R->>V: 35=G OrderCancelReplaceRequest<br/>(new ClOrdID, OrigClOrdID=current)
      V-->>R: 35=8 ExecType=E Pending Replace<br/>(RouteReplacePendingAtVenue)
      Note over V: original price still workable until venue confirms
      alt accepted
        V-->>R: 35=8 ExecType=5 Replaced<br/>(RouteReplaced; may lose queue priority)
      else rejected
        V-->>R: 35=9 OrderCancelReject<br/>(RouteReplaceRejected; route stays in prior Working state)
      end
    else cancel
      U->>R: cancel_routes
      R->>V: 35=F OrderCancelRequest
      V-->>R: 35=8 ExecType=6 Pending Cancel
      alt accepted
        V-->>R: 35=8 ExecType=4 Canceled (RouteCanceled, terminal)
      else rejected
        V-->>R: 35=9 OrderCancelReject (route stays Working)
      end
    else venue match
      V-->>R: 35=8 ExecType=F (RouteFilled / RoutePartiallyFilled)
    else TIF expiry
      V-->>R: 35=8 ExecType=C (RouteExpired)
    end
  end
```

The full FIX state-machine reference is in [[arch-order-route-lifecycle]].

1. Validation: passive eligibility (some venues restrict who can rest), tick alignment, lot size, tif compatibility.
2. Router creates `Route { mode=RESTING, state=Pending }`; adapter sends `35=D`.
3. Venue ack → `Working` (`35=8 ExecType=0`). The order sits in the book.
4. Lifecycle:
   - **Filled** by an aggressive contra → `35=8 ExecType=F` (`RouteFilled` full or `RoutePartiallyFilled`).
   - **Replaced** by trader / rule — adapter issues `35=G` with **new ClOrdID** and `OrigClOrdID` per FIX convention; passes through `Pending Replace` (`150=E/39=E`) before resolving to `Replaced` (`150=5/39=5`) or `OrderCancelReject` (`35=9`, route stays in prior state).
   - **Canceled** explicitly via `35=F` → `Pending Cancel` (`150=6/39=6`) → `Canceled` (`150=4/39=4`) or `OrderCancelReject` (route stays Working).
   - **Expired** on TIF — `DAY` at close, `GTD` at supplied date (`150=C/39=C`); `IOC`/`FOK` rejected as resting requests.

> **Key FIX rule for resting orders:** during `Pending Replace` the **original price/qty is still working at the venue** and may fill. A replace's `Qty` ≤ `CumQty` is rejected (venue-side `35=9` or EMS pre-flight `EMS-RTE-2030`). Qty *decrease* typically preserves queue priority; price change or qty *increase* typically loses it — venue-dependent.

> **Appendix D race conditions apply.** Resting orders are the most race-prone surface in the system — cancels and replaces frequently race against fills. See [[arch-fix-appendix-d]] for the full catalogue (Too-Late-to-Cancel D4/D5, Fill-during-Pending-Replace D7/D10, over-allocation prevention, PossResend D31, unsolicited venue cancels, trade busts/corrects). The implementation contract checklist at the bottom of that note is what every resting-order adapter must satisfy.

## Inputs

- `order_id` (`READY`).
- `venue` — a CLOB-capable venue.
- `limit_price` (required; resting without a limit is undefined).
- `tif` — `DAY`, `GTC`, `GTD`, `GTX` (good-till-extended).
- `display_qty` / `min_qty` — for iceberg orders where supported.
- `peg_offset` — for pegged-to-bid/offer orders where supported.

## Outputs / Side Effects

- `RouteSent`, `RouteAcknowledged` (`Working`), `RouteReplaceRequested` / `RouteReplacePendingAtVenue` / `RouteReplaced` per replace, `RouteCancelRequested` / `RouteCancelPendingAtVenue` / `RouteCanceled` per cancel, `RoutePartiallyFilled` per fill, terminal `RouteFilled` / `RouteCanceled` / `RouteRejected` / `RouteExpired`. See [[arch-order-route-lifecycle]] for the FIX-name mapping.
- `RouteReplaceRejected` / `RouteCancelRejected` when the venue returns `35=9` — these do **not** terminate the route.
- FIX `35=8` ExecutionReports mirrored to paired FIX clients; outbound `35=9 OrderCancelReject` on a rejected amend/cancel.

## Edge Cases & Nuances

- **Reprice cadence.** Aggressive reprice loops (replace every quote tick) can violate venue throttles. The [[arch-automation-layer|automation layer]] rules implementing TradeBest-style logic must respect rate limits or get `EMS-RTE-3008 venue_replace_throttled`.
- **In-flight replace race.** Replace issued while a fill is in-flight; venue may fill the old price. Reconciliation per [[arch-venue-connectivity]] failure-mode rules.
- **Late cancel after fill.** Cancel sent after venue has already filled → adapter receives both ack messages; `RouteAnomaly` event for ops triage; book state unaffected.
- **Iceberg/hidden quantity.** Venue may report only the `display_qty` portion as visible; fills can exceed `display_qty` per refresh cycle. Adapter aggregates fills correctly against the parent route.
- **GTC across trade dates.** A `GTC` resting order traverses the [[tradedate-roll|trade-date roll]]; the route's logical identity persists but venue may require a fresh ID per session.
- **Asset-class semantics:**
  - **FX**: most FX RFQ venues are not CLOBs; resting applies to FX CLOB venues (e.g. EBS, FX Spot+).
  - **FI**: resting on bond CLOBs is rare; mostly used on listed UST futures via the futures adapter.
  - **Equity**: standard. Display quantity, MPID handling, ELP / iceberg.

## API mapping

```
operation: route_orders
items: [{
  order_id,
  venue:         VenueRef,
  mode:          RESTING,
  limit_price:   decimal,
  qty:           decimal,
  tif:           DAY | GTC | GTD | GTX,
  display_qty?:  decimal,
  min_qty?:      decimal,
  peg?:          { kind: BID | OFFER | MID, offset: decimal }
}]

operation: replace_routes
items: [{ route_id, fields: { limit_price? | qty? | display_qty? } }]

operation: cancel_routes
items: [{ route_id }]
```

## Validator codes touched

`EMS-RTE-1012` (resting not supported at venue), `EMS-RTE-3008` (replace throttled), `EMS-RTE-3009` (peg parameters out of range), `EMS-RTE-1013` (TIF not supported), `EMS-PRM-1001..1003` (cpty tag 3-layer).

## Permissions

- `#trade-{asset_class}` (3-layer).
- `#cpty-{venue}`.
- `#iceberg` / `#peg` where the firm restricts those order types.

## Related

- [[arch-order-route-lifecycle]] · [[arch-router-layer]] · [[arch-venue-connectivity]] · [[arch-quote-server]] · [[arch-automation-layer]]
- [[route-single]] · [[route-to-rfq]] · [[auto-route]] · [[fx-automation-tradebest]] · [[amend-order]]
- [[tradedate-roll]] · [[partial-routes]]
