---
type: workflow
category: routing
applies_to: ["fx"]
status: draft
tags: [workflow/routing, workflow/sequencing]
---

# Spot-First

A **sequencing policy** for orders with multiple legs where the spot leg must execute (and confirm) before the forward leg is sent. Common in FX swap-and-hedge workflows and corporate-treasury value-date-sensitive flows.

## Purpose

Eliminate the leg-risk window: if the forward leg goes first and the spot leg fails (or moves materially against), the position is exposed. Spot-first locks in the spot first, then conditions the forward on the spot's outcome.

## Trigger / Entry Point

- A multileg ([[arch-multileg]]) order with `execution_mode = SEQUENCED` and `sequence_policy = spot_first`.
- A pair of single orders (spot + forward) tied by `group_id` with a "spot before forward" automation rule bound.
- FX execution workflows (including corporate-treasury flows via [[fxel]]) with the desk setting `enforce_spot_first = true` for swap-shaped orders.

## Actors

- Trader / automation initiating the sequenced order.
- [[arch-order-staged|Staged order layer]] — manages multileg state with leg dependencies.
- [[arch-router-layer]] — emits only the spot route first; forward route gated until spot fills.
- [[arch-validator]] — checks pre-flight sequencing eligibility.

## Steps

```mermaid
sequenceDiagram
  participant T as Trader / Rule
  participant O as Order Layer<br/>[[arch-order-staged]]
  participant R as Router<br/>[[arch-router-layer]]
  participant V as Venue

  T->>O: stage_orders(multileg, mode=SEQUENCED, policy=spot_first)
  O->>O: validate, persist; leg[forward]=BLOCKED
  O->>R: LegReady(leg=spot)
  R->>V: route spot leg
  V-->>R: ExecutionReport filled
  R-->>O: LegFilled(leg=spot)
  alt spot fully filled
    O->>R: LegReady(leg=forward)
    R->>V: route forward leg
    V-->>R: ExecutionReport filled
    R-->>O: LegFilled(leg=forward)
    O-->>T: OrderFilled
  else spot partial / fail
    O->>O: re-evaluate; either hold forward, reduce forward qty, or cancel
    O-->>T: SequencedDecisionRequired
  end
```

1. Validator confirms the order is multileg-shaped with at least one spot and one forward leg, both on the same currency pair (or compatible cross-pairs).
2. Order persisted; `leg[forward].state = BLOCKED` with `blocked_by: leg[spot].leg_id`.
3. Router receives `LegReady(spot)` only; forward stays out of the routing surface.
4. Spot fills → router emits `LegFilled(spot)` → order layer unblocks forward leg → `LegReady(forward)`.
5. Forward routes per its own mode/venue.

## Inputs

- A multileg envelope with `execution_mode=SEQUENCED` and `sequence_policy=spot_first` OR a pair of orders linked by `group_id` with the `spot_first_pairs` rule bound.
- Per-leg routing modes (typically spot to a CLOB / RFQ venue; forward to a swap-capable venue or another spot+points pair).

## Outputs / Side Effects

- `LegReady`, `LegFilled` events on the parent.
- One `OrderFilled` on the parent when both legs are terminal-filled.
- Possible `SequencedDecisionRequired` event if partial fill on spot needs trader input.

## Edge Cases & Nuances

- **Spot partial fill.** Forward leg's notional may need to shrink to match the actual filled spot quantity. Policy:
  - `STRICT`: cancel forward leg if spot doesn't fully fill.
  - `SCALE`: reduce forward qty to match cum_spot_qty; trader notified.
  - `WAIT`: hold forward until trader decides (`SequencedDecisionRequired`).
- **Spot rejected / no fill.** Forward leg never sent; order remains in `LEGS_WORKING` until trader acts. Audit captures.
- **Spot-first with internal cross.** Spot can fill internally via [[route-to-local]]; the forward leg is still gated on spot completion.
- **Multi-leg-spot.** Some constructs have multiple spot legs (e.g. NDF + hedging spot). Sequence policy lists the dependency order: `[spot_hedge, ndf]`.
- **Cancel mid-sequence.** Cancelling the parent mid-sequence cancels the forward leg trivially (it never went out). If spot is mid-fill, cancel propagates to the venue but late fills can still arrive — reconciled per [[arch-venue-connectivity]] anomaly rules.
- **Replay determinism.** Sequence transitions are deterministic given the spot fills; replay reproduces the same forward dispatch decisions.

## Relationship to multileg

`spot_first` is one concrete `SequencePolicy`. Other policies in the same shape:

- `fixing_after_observation` — for fixing orders ([[auto-route-fixing-aim]]).
- `hedge_before_principal` — for delta-hedged options listed-leg workflows.
- `near_before_far` — for futures rolls.

Each follows the same gating mechanism on a different dependency.

## API mapping

```
operation: stage_orders
items: [{
  multileg_kind: SWAP | DELTA_HEDGE | CUSTOM,
  execution_mode: SEQUENCED,
  sequence_policy: { kind: spot_first, partial_policy: STRICT | SCALE | WAIT },
  legs: [
    { leg_id: spot_id, side: BUY, qty, instrument: { ccy_pair, value_date: T+2 } },
    { leg_id: fwd_id,  side: SELL, qty, instrument: { ccy_pair, value_date: T+30 } }
  ]
}]
```

## Validator codes touched

`EMS-ORD-4004` (sequence_policy invalid for multileg kind), `EMS-ORD-4005` (no eligible spot leg in sequenced order), `EMS-ORD-4006` (partial_policy required for SEQUENCED), `EMS-PRM-1001..1003` per leg's venue.

## Permissions

- `#fx-trade` (3-layer per [[arch-tag-permissions]]).
- `#multileg-sequenced` for sequence-policy authoring.

## Related

- [[arch-multileg]] · [[arch-order-staged]] · [[arch-router-layer]] · [[arch-automation-layer]]
- [[route-single]] · [[route-to-local]] · [[partial-routes]]
- [[what-are-swaps]] · [[auto-route-fixing-aim]]
