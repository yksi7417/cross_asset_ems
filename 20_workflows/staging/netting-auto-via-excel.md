---
type: workflow
category: staging
applies_to: ["fx"]
status: draft
tags: [workflow/staging, workflow/netting]
---

# Netting — Auto via Excel / `35=E` (`58=Netted`) / Limits

Buy-side desks (especially corporate treasuries) frequently stage many small same-currency-pair orders from spreadsheets; the EMS nets these into fewer market-facing orders before routing. Auto-netting can be requested **explicitly** (FIX `35=E` with `58=Netted`) or **implicitly** by firm policy when an Excel batch arrives.

## Purpose

Reduce market footprint, simplify counterparty interaction, and lower transaction costs by combining offsetting or same-direction child orders into a single (or fewer) routable parent(s), while preserving per-child accountability for allocation.

## Trigger / Entry Point

- Excel staging ([[staging-via-excel]]) with `BatchName` set and the desk's `auto_net` policy enabled (see [[arch-firm-desk-user|settings cascade]]).
- FIX `NewOrderList` (`35=E`) with `58=Netted` in the batch envelope.
- API `stage_orders([...], options: { net_within_batch: true })`.
- Automation rule that periodically re-evaluates an open batch and nets matured-for-netting orders.

## Actors

- Treasury operator / sales-trader uploading the batch.
- The [[arch-validator|validator]] — runs pre- and post-netting limit checks.
- The [[arch-order-staged|staged order manager]] — owner of child + netted-parent state.
- The [[arch-automation-layer|automation layer]] — when policy-driven netting fires.

## Steps (canonical flow)

1. **Batch ingested.** Each child order enters [[arch-order-staged|staged state]] with `batch_name=X`, `pending_actions: [PendingNet]` if policy mandates netting before release.
2. **Netting domain identified.** A netting key is computed per child — typically `(ccy_pair, value_date, account_group, side_independent=true)` for FX, asset-class-specific elsewhere.
3. **Net computation.** Children sharing a netting key are summed signed; the result is one or more **netted-parent** orders with `child_orders: [order_ids]` back-references.
4. **Limits re-applied.** Counterparty limits, single-order caps, and per-currency exposure are re-checked **post-netting**. A pre-net pass that succeeded can fail post-net for the netted notional, and vice versa.
5. **Children frozen.** Each child transitions to `STAGED_NET_CHILD` — they can no longer be amended individually unless their parent is reopened (un-netted).
6. **Parent routed.** The netted parent becomes routable; downstream [[arch-router-layer|routing]] proceeds as usual.
7. **Fills allocated back.** Parent fills are pro-rata allocated back to children for downstream allocation / booking. Allocation result is per-child events for audit.

## Inputs

- Batch-level: `batch_name`, `net_within_batch: bool`, optional `net_against_other_batches: bool` (rare, requires `#net-cross-batch` tag).
- Child-level: standard staged-order envelope (`instrument`, `side`, `qty`, `value_date`, `account`).
- Asset-class extension defines the netting key. For FX: `(ccy_pair, value_date, account_or_pb_group)`.

## Outputs / Side Effects

- `NetGroupFormed { netted_parent_id, child_order_ids[] }` event.
- `OrderStaged` for the netted parent (it is itself a staged order, with its own [[arch-validator|validator]] pass).
- Children's `OrderState` becomes `STAGED_NET_CHILD` — visible in listings but not directly routable.
- Pre-net and post-net `LimitCheck` events for audit.
- Per-child `AllocationFromParent` events on fill.

## `BatchName` vs `GroupID` vs `Group` distinction

The user-facing tooling has three overlapping concepts; the EMS treats them as **separate metadata fields with distinct semantics**:

| Field | Source | Semantic | Netting role |
|---|---|---|---|
| `batch_name` | Excel batch envelope | A bag of orders submitted together. | Default scope of auto-netting. |
| `group_id` | Explicit field on the order envelope ([[group-id]]) | Cross-batch correlation key. | Optional netting expansion (requires `#net-cross-batch`). |
| Hashtag groups (`tags`) | Free-form on each order | Display / list grouping; **not** netting. | None — they do not affect netting. |

This is the source of the often-asked "BatchName vs grouping" confusion — see [[batchname-column]].

## `35=E` with `58=Netted` semantics

FIX `NewOrderList` (`35=E`) carries `NoOrders` children; `58=Text` set to `Netted` (or a firm-defined sentinel) is the requesting hint. The EMS:

- Treats the hint as a **request**, not a directive — firm policy still gates.
- Logs the hint on the parent so audit can show "client asked for net".
- Sends the netted parent back through FIX as a separate `ExecutionReport` to the client; children are echoed individually as well, so the client sees the full mapping.

## Edge Cases & Nuances

- **Mixed sides cancelling.** Children with opposite sides may net to zero residual on the parent (e.g. +10M / −10M EURUSD same value date). The parent is **fully cancelled** with `OrderCancelled { reason: net_to_zero }`; children's allocation events still fire to reflect the booked legs internally.
- **Limit failure post-net.** Counterparty exposure post-net exceeds limit. Resolution: validator rejects with `EMS-ORD-2201 post_net_limit_breach`; children revert to `STAGED` un-netted, awaiting trader action.
- **Partial net.** Some children disqualify (different value date, or a hold flag). They stay `STAGED`; the rest net. Audit clearly enumerates per-child decision.
- **Un-netting.** A trader may force `un_net_group([netted_parent_id])` to release children if the parent has not yet routed. Routed → `EMS-ORD-2210 cannot_unnet_routed_parent`.
- **Auto-net trigger boundary.** Some firms net on batch close; others on a scheduled cron through the [[arch-automation-layer|automation layer]]. The choice is a desk setting.
- **Swap netting** (FX swap legs cancelling other swaps in same date / pair) — modelled as a multileg-aware netting key; see [[what-are-swaps]] and the planned `arch-fx-netting`.

## API mapping

```
operation: stage_orders
options: { net_within_batch: true, batch_name: "TREAS-20260605-001" }
items: [ ... child orders ... ]

# Or, post-stage:
operation: net_orders
items: [{ batch_name: "TREAS-20260605-001", policy: AUTO|MANUAL_REVIEW }]

operation: un_net_group
items: [ netted_parent_id ]
```

## Validator codes touched

`EMS-ORD-2201` (post-net limit breach), `EMS-ORD-2202` (heterogeneous value date), `EMS-ORD-2203` (net_to_zero result blocked by policy), `EMS-PRM-1502` (cross-batch netting requires tag), `EMS-ORD-2210` (cannot un-net routed parent).

## Permissions

- `#fx-trade` 3-layer for the standard auto-net path.
- `#net-cross-batch` tag required if cross-batch netting is requested via `group_id`.
- Treasury sub-flow (FXEL) has its own `#corp-treasury` tag with different default policies — see [[fxel]].

## Related

- [[arch-order-staged]] · [[arch-validator]] · [[arch-automation-layer]] · [[arch-event-sourcing]]
- [[staging-via-excel]] · [[batchname-column]] · [[group-id]] · [[netting-swap-net]] · [[what-are-swaps]]
- [[partial-routes]] · [[allocation-prime-broker]] · [[fxel]] · [[trading-limits]]
