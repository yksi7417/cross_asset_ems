---
type: workflow
category: staging
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/staging]
---

# Amend an Order (FIX OrderCancelReplaceRequest 35=G)

Modify fields of a [[arch-order-staged|staged order]] or a working [[arch-router-layer|route]] without losing the order's identity. "Amend" is the EMS-side name for the FIX **OrderCancelReplaceRequest** (`35=G`) operation — also widely called **Modify**. The full lifecycle (Pending Replace → Replaced / OrderCancelReject) is defined in [[arch-order-route-lifecycle]] and obeyed end-to-end.

## Purpose

Allow user / automation to adjust an order in response to new information (price moved, qty needs adjustment, broker changed mind, allocation template wrong) without losing the order's identity, **without losing queue priority** where the venue's rules permit (typically: qty decrease preserves priority; price change or qty increase loses it). Every amend is a separate event in [[arch-event-sourcing|the log]] — the order's history is a derivable replay.

## Trigger / Entry Point

- Trader edits a ticket field after stage.
- Sales-trader updates allocation template post-stage.
- [[arch-automation-layer|automation rule]] amends fields (e.g. RBLD adjusts qty after preceding fills).
- [[arch-fix-api-bridge|FIX]] `OrderCancelReplaceRequest` (`35=G`) → API `amend_orders` — but only from non-mixed-client FIX sessions per the mixed-client rule in [[arch-fix-api-bridge]].

## Actors

- Trader / sales / automation.
- [[arch-validator]] — runs full validation on the amended state, not on the delta.
- [[arch-order-staged|order layer]] — persists.
- Any [[two-step-approval|approver]] queue is recomputed if approval is reset.

## Steps (FIX-aligned)

### Pre-route amend (order is `STAGED`, not yet at a venue)

```mermaid
sequenceDiagram
  participant U as User / Rule
  participant API as Edge API
  participant V as Validator
  participant O as Order Layer

  U->>API: amend_orders([{order_id, fields}])
  API->>O: emit OrderReplaceRequested (35=8 ExecType=E Pending Replace)
  Note over O: FIX-paired client sees Pending Replace echo
  API->>V: full re-validate (post-amend state)
  alt validation pass
    V-->>API: pass
    API->>O: persist OrderReplaced (35=8 ExecType=5 Replaced)
    Note over O: order now reflects new fields; FIX-paired client sees Replaced echo
    O-->>U: ack
  else validation fail
    V-->>API: reject (code)
    API->>O: emit OrderReplaceRejected (35=9 OrderCancelReject)
    Note over O: order stays in prior state — replace failed, order did not
    O-->>U: reject with code + admin hint
  end
```

### Working-route amend (order has live routes at a venue)

```mermaid
sequenceDiagram
  participant U as User
  participant API as API
  participant V as Validator
  participant R as Router<br/>[[arch-router-layer]]
  participant A as Venue Adapter
  participant X as Venue

  U->>API: amend_orders([{order_id, fields}])
  API->>V: validate post-amend state + per-field amend-during-routing policy
  V-->>API: pass / reject
  alt route is amendable in-place at the venue
    API->>R: replace_routes (new ClOrdID, OrigClOrdID=current)
    R->>A: send 35=G
    A->>X: wire
    X-->>A: 35=8 ExecType=E Pending Replace
    A-->>R: RouteReplacePendingAtVenue
    Note over R: original parameters still workable; fills may still print until venue confirms
    alt venue accepts
      X-->>A: 35=8 ExecType=5 Replaced
      A-->>R: RouteReplaced
      R-->>API: order reflects new fields
    else venue rejects
      X-->>A: 35=9 OrderCancelReject (CxlRejReason)
      A-->>R: RouteReplaceRejected
      Note over R: route stays in prior state — NOT terminated
    end
  else venue requires cancel-and-resubmit
    Note over R: e.g. structural field change like instrument
    R->>A: 35=F cancel original
    A->>X: send
    X-->>A: 35=8 ExecType=4 Canceled
    R->>A: 35=D new order with amended params
    A->>X: send
    Note over R: RouteSuperseded event records the supersession
  end
```

### Step-by-step

1. User issues `amend_orders` with the fields to change.
2. EMS emits **`OrderReplaceRequested`** (the internal name for FIX `35=8 ExecType=E Pending Replace`) immediately and echoes to any FIX-paired client.
3. Validator runs **the full rule set** against the post-amend state — not a delta-only check (per [[arch-validator]] discipline).
4. **If order in `New`/`Staged`** (pre-route): on validation pass, EMS records `OrderReplaced` (`35=8 ExecType=5 Replaced`); on fail, `OrderReplaceRejected` (`35=9 OrderCancelReject`) and the order **stays in its prior state**.
5. **If order has live routes**: amend may translate to a `replace_routes` call on the affected routes. Each route's lifecycle is independent (see [[arch-router-layer]] / [[arch-order-route-lifecycle]]):
   - In-place replace at venue: standard 35=G flow, queue priority may be lost per venue rules.
   - Structural field change (instrument, side): cancel + new-order pattern; `RouteSuperseded` event recorded.
6. Side effects: [[two-step-approval]] reset if firm policy says the amended field is "material".

> **FIX rule the EMS enforces**: an `OrderCancelReject` (`35=9`) **does not terminate** the original order or route. The order/route stays in its prior state. See [[arch-order-route-lifecycle]] § "Cancel/replace semantics".

## Inputs

- `order_id`.
- `fields: map<FieldKey, NewValue>` — only the fields being changed.
- `expected_version?` — optional optimistic concurrency token (see edge cases).

## Outputs / Side Effects

- `OrderReplaceRequested` (`150=E Pending Replace`) immediately on receipt.
- `OrderReplaced` (`150=5 Replaced`) on validator pass — with `before/after` field diff in the event payload.
- `OrderReplaceRejected` (`35=9 OrderCancelReject` with `CxlRejReason` 102) on validator fail — **order stays in prior state**.
- Possible `ApprovalReset` event (if a material field changed and two-step approval was pending).
- For routed orders: `RouteReplaceRequested` → `RouteReplacePendingAtVenue` → `RouteReplaced` or `RouteReplaceRejected`, or `RouteSuperseded` if the venue requires cancel-and-resubmit. See [[arch-order-route-lifecycle]].
- FIX mirror echoes as `35=8` ExecutionReports per [[arch-fix-api-bridge]].

## Per-field amend policy (illustrative)

| Field | Policy when STAGED | Policy when ROUTING |
|---|---|---|
| `limit_price` | Allowed; revalidate. | Allowed via `replace_routes` if venue supports; otherwise cancel+re-route. |
| `qty` | Allowed; recompute `available_to_route`. | Allowed via venue replace; many venues restrict qty decreases to ≤ `cum_qty` not allowed. |
| `tif` | Allowed. | Sometimes; venue-dependent. |
| `account` / `allocation_template` | Allowed. | Disallowed in most venues; cancel + re-stage. |
| `broker` | Allowed (if not yet routed to that broker). | Implies route cancel + new route. |
| `instrument` | Disallowed — must cancel and re-stage. | Disallowed. |
| `side` | Disallowed. | Disallowed. |
| `notes` / `tags` | Always allowed; never resets approval. | Always allowed. |

## Edge Cases & Nuances

- **Material field detection.** Each firm configures which fields are "material" for [[two-step-approval]] reset. Default: `qty`, `limit_price`, `account`. Notes and tags never reset.
- **Optimistic concurrency.** Two users amend concurrently. Last-writer-wins by default; firms may opt into `expected_version`-guarded amends → second writer gets `EMS-ORD-2106 stale_version` (mapped outbound to `35=9 CxlRejReason=3` "order already pending cancel/replace").
- **Cancel-during-amend.** A cancel arrives while an amend is mid-flight. The cancel is queued at the EMS until the replace resolves, then issued against the resulting state. FIX convention is one-pending-replace-per-order; the EMS enforces this on outbound to prevent venue `35=9 CxlRejReason=3`. See [[arch-fix-appendix-d]] § "Concurrent cancel + replace".
- **Fill-during-amend (Appendix D7/D10).** A fill arrives while a replace is in `PendingReplaceAtVenue`. The fill applies to the prior (un-replaced) parameters. After the venue confirms `Replaced`, the resulting `LeavesQty = new_OrderQty - CumQty` (where `CumQty` includes the in-flight fill). Never compute `LeavesQty` from the client's request alone — always derive from the venue's reported state. See [[arch-fix-appendix-d]] § "Fill during Pending Replace" for the full sequence.
- **Replace below `CumQty` (Appendix D over-allocation).** If the replace's new `Qty` ≤ current `CumQty`, the validator pre-empts with `EMS-RTE-2030 replace_qty_below_cum_qty` when it can; otherwise the venue rejects with `35=9`. **The order is not terminated** — it stays Working at the original `OrderQty` with `LeavesQty = original_OrderQty - CumQty`.
- **Too late to cancel/replace (Appendix D4/D5).** A `35=8 ExecType=F Filled` may arrive immediately before or after the venue's `35=9 CxlRejReason=0 (Too late to cancel)`. The order is terminal `Filled`; the 35=9 is informational. Do not block waiting for `Canceled`. See [[arch-fix-appendix-d]] § "Too Late to Cancel".
- **Queue priority loss.** Venues typically: qty *decrease* preserves time priority; qty *increase* or price change loses it. The EMS surfaces what the venue confirms — it does not pre-warn.
- **FIX mixed-client.** A mixed FIX+API client cannot amend via FIX — see [[arch-fix-api-bridge]]. API amend still echoes back as `35=8 ExecType=E/5` to the FIX side.
- **Audit reconstruction.** Order's current state is `replay(events)`. Replace events carry full field diff so audit can render any historical moment without snapshots.
- **Amend chains.** Five amends in a row produce five replace-request / replace events; the blotter renders the cumulative state. The UI typically lets the user click an order to see the full ClOrdID / OrigClOrdID chain.
- **ClOrdID minting.** Per FIX convention each `35=G` carries a **new ClOrdID** (tag 11) with `OrigClOrdID` (tag 41) pointing to the prior one. The EMS owns ClOrdID generation for outbound routes; inbound FIX amends honour the client's submitted ClOrdID + OrigClOrdID chain.

## API mapping

```
operation: amend_orders
items: [{
  order_id,
  fields: { limit_price?, qty?, tif?, account?, allocation_template?, broker?, notes?, tags?, ... },
  expected_version?: int       # optional optimistic concurrency
}]
```

## Validator codes touched

`EMS-ORD-2101..2107` (amend-specific), all standard `EMS-ORD-*` re-evaluated post-amend, `EMS-PRM-1001..1003` for permission-gated amend fields.

## Permissions

- `#amend-{field}` for sensitive fields (some firms gate `amend-account`).
- `#amend-during-routing` for in-flight amends — typically senior traders only.

## Related

- [[arch-order-route-lifecycle]] (FIX state-machine reference) · [[arch-order-staged]] · [[arch-router-layer]] · [[arch-validator]] · [[arch-event-sourcing]] · [[arch-fix-api-bridge]]
- [[two-step-approval]] · [[order-ownership]] · [[staging-via-ticket]] · [[staging-via-fix]]
- [[notes-and-custom-notes]] · [[bulk-order-update-route]] · [[route-to-resting]]
