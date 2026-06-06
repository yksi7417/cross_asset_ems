---
type: workflow
category: staging
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/staging, workflow/bulk]
---

# Bulk Order Update / Route

Apply the same operation — amend, set field, route, cancel — to **many orders at once**, identified either by explicit `order_id` list or by a selector (batch_name, group_id, tag, asset class, etc.). The [[arch-api-first|batch-by-default API]] makes this natural; the workflow note covers UX, validation, and partial-success handling.

## Purpose

Treat fleet operations as first-class: "amend all my staged orders' broker to GS_US", "route all of batch TREAS-20260605 to RFQ", "cancel everything tagged #panic". Without this, traders fall back to repetitive single-order operations or scripts that bypass audit.

## Trigger / Entry Point

- Trader selects multiple orders on the blotter and chooses a bulk action ("Set Broker..." / "Route to..." / "Cancel selected").
- API `bulk_amend([selector, fields])`, `bulk_route([selector, route_template])`, `bulk_cancel([selector])`.
- Automation rule (e.g. EOD cancel-all-DAY-orders).

## Actors

- Trader / operator / rule.
- [[arch-validator]] — per-order validation, but with a bulk-aware partial-success policy.
- [[arch-order-staged|order layer]] — multi-target.
- [[arch-router-layer]] — receives bulk route fanout.

## Selector shapes

```
Selector =
    { order_ids: [UUID] }                          # explicit
  | { batch_name: string }                          # all in batch
  | { group_id: string }                            # all in group
  | { tag: string }                                 # all with tag
  | { filter: { asset_class?, side?, state?, ... } } # general predicate
```

Selectors can be combined with `AND`. The selector is **resolved server-side** at execution time; client doesn't pre-expand to UUIDs.

## Steps

```mermaid
sequenceDiagram
  participant U as Trader / Rule
  participant API as Edge API
  participant V as Validator
  participant O as Order Layer
  participant R as Router

  U->>API: bulk_amend(selector, fields, options:{partial_ok})
  API->>O: resolve selector to N order_ids
  par per-order in parallel
    O->>V: validate replace for order_i
    V-->>O: pass/reject
    alt pass
      O->>O: persist OrderReplaceRequested_i then OrderReplaced_i (150=E -> 150=5)
    else fail
      O->>O: persist OrderReplaceRejected_i (35=9; order stays in prior state)
    end
  end
  O-->>API: ItemResult[N]
  API-->>U: summary {ok, rejected, deferred}
  alt bulk_route follow-up
    U->>API: bulk_route(selector, route_template)
    API->>O: resolve & per-order route
    O->>R: route_orders fanout
  end
```

1. Selector resolved to a concrete set of orders.
2. **Per-order validation** runs (full validator); failures recorded.
3. **Partial-success policy:** `partial_ok=true` (default) commits successful items, rejects others; `partial_ok=false` rolls back the entire bulk on any failure (rare for bulk; expensive).
4. Returns one `ItemResult` per resolved order.

## Inputs

- `selector` per shape above.
- For bulk_amend: `fields: { ... }`.
- For bulk_route: `route_template: { venue, mode, dealers?, ... }`.
- For bulk_cancel: typically just the selector.
- `options`: `partial_ok`, `dry_run`, `max_items_cap` (safety).

## Outputs / Side Effects

- Per-order events per the [[arch-order-route-lifecycle|FIX-aligned lifecycle]]: `OrderReplaceRequested` → `OrderReplaced` / `OrderReplaceRejected` (for `bulk_amend`), `RouteSent` / `RouteAcknowledged` (for `bulk_route`), `OrderCancelRequested` → `OrderCanceled` / `OrderCancelRejected` (for `bulk_cancel`).
- One `BulkOperationStarted` and `BulkOperationCompleted` envelope event with counts.
- Summary returned to client.

## Edge Cases & Nuances

- **Selector resolves to nothing.** Returns success with zero items. UI surfaces "no matching orders".
- **Selector resolves to too many.** `max_items_cap` (firm-policy, e.g. 5000) triggers reject `EMS-ORD-1090 bulk_selection_too_large` to prevent accidental cancel-all events.
- **Mid-operation order state change.** An order is cancelled by someone else while bulk_amend is processing — that item reports `EMS-ORD-2105 cannot_amend_in_state`; others continue.
- **Permission heterogeneity.** Selector may cover orders the user can't amend (e.g. different desks). Per-order tag check filters per [[arch-tag-permissions]]; rejected items report the standard permission denial with admin hint.
- **Dry run.** `dry_run=true` returns the would-be results without persisting. Crucial before destructive bulk-cancels.
- **Bulk on routed orders.** `bulk_amend` on routed orders triggers per-order route replace/cancel logic (see [[amend-order]] per-field policy). Effects can cascade significantly.
- **Bulk route conflict.** `bulk_route` against orders that aren't `READY` → those items skipped with `EMS-ORD-2110 not_ready_to_route`.
- **Audit.** Bulk operations generate a parent `BulkOperationStarted` event linking all child amend/route/cancel events via `caused_by`, making it easy to reconstruct who-clicked-what in audits.

## API mapping

```
operation: bulk_amend
items: [{ selector, fields, options? }]

operation: bulk_route
items: [{ selector, route_template, options? }]

operation: bulk_cancel
items: [{ selector, options? }]
```

Note: each top-level operation is still batch-by-default. Items within the batch may target overlapping selectors; deduplication is server-side.

## Validator codes touched

`EMS-ORD-1090` (selection too large), `EMS-ORD-2105` (state-incompatible), `EMS-ORD-2110` (not ready to route), all per-order codes that the underlying operations can return.

## Permissions

- `#bulk-amend`, `#bulk-route`, `#bulk-cancel` (per operation).
- Plus all permissions the per-order amend/route/cancel would require, per-item.

## Related

- [[arch-api-first]] · [[arch-order-staged]] · [[arch-validator]] · [[arch-event-sourcing]]
- [[amend-order]] · [[batchname-column]] · [[group-id]]
- [[staging-via-excel]] · [[netting-auto-via-excel]]
