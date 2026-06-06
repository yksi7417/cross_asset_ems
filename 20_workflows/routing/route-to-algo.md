---
type: workflow
category: routing
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/routing, workflow/algo]
---

# Route to Algo

Route a [[arch-order-staged|staged order]] to a broker- or venue-hosted execution algorithm (VWAP, TWAP, POV, IS, sweeper, etc.). The EMS emits the parent route and consumes child executions; the algo lives at the broker / venue.

## Purpose

Hand off working-the-order responsibility to a counterparty's algorithm under explicit parameters. The EMS retains visibility (parent fills, in-flight quantity, cancel/replace), but does not micromanage child orders.

## Trigger / Entry Point

- Trader calls `route_orders([{order_id, venue: <broker_algo>, mode: ALGO, strategy, algo_params}])`.
- [[arch-automation-layer|Automation]] rule with action `route_to_algo` — see [[fx-automation-tradebest]] / [[fx-automation-rbld]] for FX analogues; equity examples use a [[bloomberg-fit|FIT]]-like or EMSX-style route with `Strategy=VWAP` and parameter binding.

## Actors

- Trader / DMA user.
- Broker's algo engine (counterparty).
- [[arch-router-layer]] — manages the parent route.
- [[arch-venue-connectivity|venue adapter]] for the chosen broker — usually FIX, with broker-specific `Strategy` / parameter dialects.

## Steps (canonical flow)

1. Validator confirms instrument-broker-strategy support per [[arch-validator]]. Strategy-specific guards (e.g. POV requires a `participation_rate`, IS requires a `risk_aversion` band) are enforced via the asset/strategy extension.
2. Router materialises a `Route` with `mode=ALGO`, `strategy=VWAP|TWAP|POV|IS|...`, `algo_params={...}`. Event `RouteSent`.
3. Adapter encodes per broker's FIX dialect — vendor `Strategy` enum, vendor-specific `StrategyParameters` group, vendor-specific `ExecInst`.
4. Broker acks (`ExecutionReport 150=0`) → `RouteAcknowledged`. Subsequent broker child fills update `cum_qty` / `last_qty` on the route.
5. Optional in-flight controls:
   - `replace_routes([{route_id, fields}])` to change `participation_rate`, `end_time`, `limit_price`.
   - `cancel_routes([route_id])` to halt; broker confirms; remainder reverts to [[arch-order-staged|order]] for re-decision.
6. Terminal: `RouteFilled` on full execution, `RouteCanceled` on user/broker cancel, `RouteRejected` on strategy violation.

## Inputs

- `order_id` (`READY`).
- `venue` — the broker / algo provider connection.
- `strategy` — vendor-recognised name (the bridge owns the cross-vendor canonical-to-FIX-tag map).
- `algo_params` — strategy-specific (start, end, participation rate, limit, urgency, dark-mix preference, etc.).
- Asset-class extension where applicable (FX: `value_date`, `tenor`; FI: `min_increment`).

## Outputs / Side Effects

- `RouteSent`, `RouteAcknowledged`, repeated `RouteChildFill` events, terminal route state.
- Parent `OrderFilled` increments as child fills come in.
- FIX `ExecutionReport` mirrored to paired FIX client per [[arch-fix-api-bridge]].
- Possible `AllocationRequested` per attached allocation template.

## Edge Cases & Nuances

- **Strategy not supported by broker.** `EMS-RTE-1004 strategy_unsupported_by_venue`. Often a stale broker capability cache — see `Capability` in [[arch-venue-connectivity]].
- **Parameter range violations.** Each vendor advertises ranges (e.g. POV 1–30%); validator clamps or rejects per firm policy.
- **In-flight strategy change.** Some brokers reject mid-life `Strategy` swap and require cancel + re-route. The router models this by issuing cancel → new route, atomically tracked as a `RouteSuperseded` event.
- **Limit price interaction.** A limit may be the strategy's hard ceiling/floor. Some strategies (e.g. liquidity-seeking) treat it as a softer constraint. Capability metadata records which.
- **Algo end of day.** TWAP/VWAP ending past close → broker auto-cancels remainder. Adapter translates to `RouteExpired`.
- **Multi-leg algos** (e.g. delta-hedged options sweep, FX swap algos). Modelled with a multileg envelope on the order (see planned `arch-multileg`) and a single parent route to the algo; child fills carry leg refs.
- **Replay determinism.** Algo fills are external events; in [[arch-time-replay-server|replay mode]] they come from the event log, not the live broker. Adapters run in shadow mode.
- **Hand-off back to manual.** If trader cancels the algo and the order has `remaining > 0`, the order returns to `STAGED` (not `READY`), forcing re-validation of the now-stale routing decision.

## Common strategy parameter sets (illustrative)

| Strategy | Required | Optional |
|---|---|---|
| VWAP | start, end | limit, max_pov, dark_pct |
| TWAP | start, end | limit, slice_interval |
| POV | participation_rate | start, end, limit |
| IS (Implementation Shortfall) | urgency | start, end, limit |
| Liquidity seeker | — | limit, dark_pct, anti_gaming |
| FX TWAP (FXEM-style) | start, end, value_date | benchmark, max_clip_qty |

## API mapping

```
operation: route_orders
items: [{
  order_id,
  venue:    VenueRef,         // broker connection
  mode:     ALGO,
  strategy: "VWAP" | "TWAP" | "POV" | ...,
  algo_params: { ... },       // strategy-specific
  qty, limit_price?, tif
}]
```

## Validator codes touched

`EMS-RTE-1001` (venue not enabled), `EMS-RTE-1004` (strategy unsupported), `EMS-PRM-1001..1003` (algo-trader tag 3-layer), `EMS-ORD-3003` (strategy params out of range).

## Permissions

- `#algo-execution` (3-layer AND-gate per [[arch-tag-permissions]]).
- Per-broker tags (`#cpty-{broker}`).
- Some strategies are tagged separately (`#strategy-is`, `#strategy-dark`).

## Related

- [[arch-router-layer]] · [[arch-venue-connectivity]] · [[arch-automation-layer]] · [[arch-validator]]
- [[route-single]] · [[route-to-rfq]] · [[partial-routes]] · [[auto-route]]
- [[fx-automation-tradebest]] · [[fx-automation-rbld]]
