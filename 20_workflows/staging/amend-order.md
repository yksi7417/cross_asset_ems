---
type: workflow
category: staging
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/staging]
---

# Amend an Order

Modify fields of a [[arch-order-staged|staged order]] before (or in some cases during) routing. Amend is the single mechanism for any field change after stage — there is no out-of-band edit path.

## Purpose

Allow user / automation to adjust an order in response to new information (price moved, qty needs adjustment, broker changed mind, allocation template wrong) without losing the order's identity. Every amend is a separate event in [[arch-event-sourcing|the log]] — the order's history is a derivable replay.

## Trigger / Entry Point

- Trader edits a ticket field after stage.
- Sales-trader updates allocation template post-stage.
- [[arch-automation-layer|automation rule]] amends fields (e.g. RBLD adjusts qty after preceding fills).
- [[arch-fix-api-bridge|FIX]] `OrderCancelReplace` (`G`) → API `amend_orders` — but only from non-mixed-client FIX sessions.

## Actors

- Trader / sales / automation.
- [[arch-validator]] — runs full validation on the amended state, not on the delta.
- [[arch-order-staged|order layer]] — persists.
- Any [[two-step-approval|approver]] queue is recomputed if approval is reset.

## Steps

```mermaid
sequenceDiagram
  participant U as User / Rule
  participant API as Edge API
  participant V as Validator
  participant O as Order Layer
  participant R as Router

  U->>API: amend_orders([{order_id, fields}])
  API->>V: full re-validate (post-amend state)
  alt order is STAGED
    V-->>API: pass
    API->>O: persist OrderAmended event
    O-->>U: ack
  else order is ROUTING / WORKING
    V-->>API: check amend-during-routing rules
    alt allowed
      O->>R: emit RouteAmendRequested
      R->>R: replace_routes if amendable; cancel+re-stage if not
    else not allowed
      API-->>U: reject (EMS-ORD-2105 cannot_amend_in_state)
    end
  end
```

1. User issues `amend_orders` with the fields to change.
2. Validator runs **the full rule set** against the post-amend state — not a delta-only check. Cheaper field changes still go through every layer (per [[arch-validator]] discipline).
3. If order in `STAGED`: simple persist. `OrderAmended` event with field-diff payload.
4. If order in `ROUTING` / `LEGS_WORKING`: amend may translate to route-level `replace_routes` or, if structural, cancel + re-stage. Per-field policy table governs.
5. Side effects: [[two-step-approval]] reset if firm policy says the amended field is "material".

## Inputs

- `order_id`.
- `fields: map<FieldKey, NewValue>` — only the fields being changed.
- `expected_version?` — optional optimistic concurrency token (see edge cases).

## Outputs / Side Effects

- `OrderAmended` event with `before/after` diff.
- Possible `ApprovalReset` event (if a material field changed and two-step approval was pending).
- For routed orders: `RouteAmendRequested`, then `RouteReplaced` or `RouteCancelledForReamend`.
- FIX mirror echo as `ExecutionReport` (`150=5` Replace) per [[arch-fix-api-bridge]].

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
- **Optimistic concurrency.** Two users amend concurrently. Last-writer-wins by default; firms may opt into `expected_version`-guarded amends → second writer gets `EMS-ORD-2106 stale_version`.
- **Cancel-during-amend.** A cancel arrives while an amend is mid-flight. Cancel wins (terminal state takes precedence); the amend rejects with `EMS-ORD-2107 order_cancelled_during_amend`.
- **FIX mixed-client.** A mixed FIX+API client cannot amend via FIX — see [[arch-fix-api-bridge]]. API amend echoes back as `ExecutionReport` to the FIX side.
- **Audit reconstruction.** Order's current state is `replay(events)`. Amend events carry full diff so audit can render any historical moment without snapshots.
- **Amend chains.** Five amends in a row produce five events; the blotter renders the cumulative state. UI typically lets the user click an order to see the amend history.

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

- [[arch-order-staged]] · [[arch-validator]] · [[arch-event-sourcing]] · [[arch-fix-api-bridge]]
- [[two-step-approval]] · [[order-ownership]] · [[staging-via-ticket]] · [[staging-via-fix]]
- [[notes-and-custom-notes]] · [[bulk-order-update-route]]
