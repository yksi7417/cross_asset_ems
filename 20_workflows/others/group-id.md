---
type: workflow
category: others
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/others]
---

# Group ID (vs. BatchName)

`group_id` is an explicit per-order field used to **correlate orders across batches**, programs, or sessions. Frequently confused with `batch_name` ([[batchname-column]]) and with display-tag groupings — this note resolves the confusion.

## Purpose

Provide a correlation key that survives across batch boundaries and across time. Example: a portfolio rebalance ("program 42") may be uploaded in three Excel batches across one morning; each batch has its own `batch_name`, but all orders carry `group_id=program-42` so the firm can:

- Run cross-batch reporting on the whole program.
- Optionally widen netting across batches (with the right tag).
- Correlate post-trade allocations across sessions.

## Distinction from other identifiers

| Identifier | Source | Semantic | Lifecycle |
|---|---|---|---|
| `order_id` | EMS-internal | Unique per order. | Per order. |
| `batch_name` | Excel header or batch envelope ([[batchname-column]]) | Bag-of-orders submitted together; netting scope by default. | Per upload session. |
| `group_id` | Explicit per-order field | Cross-batch correlation. | Multi-batch / multi-session. |
| Hashtag (`tags: set<string>`) | Free-form per-order | Display / list grouping; automation triggers. | Free-form. |

## Steps

```mermaid
flowchart LR
  U1[Upload batch 1<br/>batch_name=X1<br/>group_id=program-42 on each row] --> O1[Orders staged]
  U2[Upload batch 2<br/>batch_name=X2<br/>group_id=program-42] --> O2[Orders staged]
  U3[Upload batch 3<br/>batch_name=X3<br/>group_id=program-42] --> O3[Orders staged]
  O1 --> R[Report by group_id]
  O2 --> R
  O3 --> R
  O1 -. cross-batch netting? .-> N{requires<br/>#net-cross-batch}
  O2 -. .-> N
  O3 -. .-> N
  N -- yes --> NN[Wider net group]
  N -- no --> SB[Intra-batch only]
```

## Inputs

- `group_id: string` on the order envelope.

## Outputs / Side Effects

- Persisted on order.
- Cross-batch queries can filter by `group_id`.
- If `#net-cross-batch` granted to the requesting user, netting scope can widen to include all orders sharing `group_id`.

## Edge Cases & Nuances

- **Collision.** `group_id` is firm-scoped; two desks using the same string isn't blocked but isn't recommended. Firms typically namespace (`desk_id:program-42`).
- **No semantic enforcement.** `group_id` doesn't imply any structural relationship — orders sharing a group_id may be totally unrelated economically. Use other fields (`multileg`, `parent_id`) for actual structural relationships.
- **Netting widening.** `#net-cross-batch` is the gate; without it, default netting respects `batch_name` only.
- **Reporting / TCA.** Cross-batch program reporting (e.g. tracking error for the whole rebalance) uses `group_id` as the grouping key.
- **Cancel-by-group.** [[bulk-order-update-route|bulk_cancel]] can target by `group_id` selector, cancelling everything across batches. Powerful; gated by `#bulk-cancel-by-group`.

## API mapping

```
order.group_id: string

# Selector usage:
operation: bulk_cancel
items: [{ selector: { group_id: "program-42" } }]

operation: list_orders(selector: { group_id: "..." })
```

## Validator codes touched

`EMS-PRM-1502` (cross-batch netting requires tag), `EMS-PRM-2300` (bulk-cancel-by-group requires tag).

## Permissions

- Free-form set on stage; no permission required to populate.
- `#net-cross-batch` to net across `group_id`.
- `#bulk-cancel-by-group` for bulk ops by group.

## Related

- [[arch-order-staged]] · [[arch-fx-netting]] · [[arch-tag-permissions]]
- [[batchname-column]] · [[netting-auto-via-excel]] · [[bulk-order-update-route]]
- [[batch-creation]] · [[staging-via-excel]]
