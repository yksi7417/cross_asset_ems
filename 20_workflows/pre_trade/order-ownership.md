---
type: workflow
category: pre_trade
applies_to: ["equity", "fx", "fixed_income"]
status: draft
tags: [workflow/pre_trade]
---

# Order Ownership

Every order has an **owner** — the user / identity responsible for it. Ownership drives blotter visibility, who can amend / cancel / route, and how state transitions are audited. Ownership can transfer (manual handoff, end-of-day rolls, sales→trader workflows).

## Purpose

Establish a single accountable identity per order at any point in time, while supporting legitimate handoffs without losing audit. Critical for both compliance (who decided this?) and operations (who is working this?).

## Trigger / Entry Point

- On stage: owner = the staging identity by default (`staged_by`).
- Manual transfer: `transfer_ownership([{order_id, new_owner, reason}])`.
- Automated transfer: shift end (e.g. NY desk hands off to LDN), policy-driven.
- Sales→trader workflow: sales stages on behalf of a client; trader takes ownership for working.

## Actors

- Originator (often sales).
- Current owner.
- New owner.
- [[arch-validator]] — permission checks.
- [[arch-event-sourcing|log]] — `OwnershipTransferred` events.

## Steps

```mermaid
sequenceDiagram
  participant S as Sales (originator)
  participant T as Trader (intended owner)
  participant API as API
  participant V as Validator
  participant O as Order Layer

  S->>API: stage_orders(staged_by=S, owner=S)
  S->>API: transfer_ownership([{order_id, new_owner=T, reason}])
  API->>V: verify S can transfer + T can receive
  V-->>API: pass
  API->>O: OwnershipTransferred event
  O-->>T: order appears on T's blotter
  Note over O: original_owner preserved in history; audit trail intact
```

1. Order staged with `owner=staged_by` by default.
2. Transfer requires:
   - Current owner authorizes (or admin with `#owner-override`).
   - New owner is eligible (same desk or holds `#cross-desk-receive`).
3. Event recorded; blotter views update.

## Ownership-derived behaviors

| Capability | Default scope |
|---|---|
| View on blotter | Owner + same-desk colleagues + supervisors |
| Amend | Owner (and any user with `#amend-anyone-on-desk` plus same-desk membership) |
| Cancel | Owner + supervisors |
| Approve (for [[two-step-approval]]) | Anyone with the approver tag who is NOT the originator (firm policy) |
| Reroute / replace routes | Owner |
| Add notes | Same-desk anyone (notes-author tag) |

## Edge Cases & Nuances

- **Cross-desk transfer.** A NY equity order handed to LDN for working overnight. Both desks need `#cross-desk-handoff`. Firm policy may auto-revert ownership when NY opens again.
- **Mass shift handoff.** End-of-day, all "active" orders on NY desk transfer to LDN. Rule-driven via [[arch-automation-layer]]; events for each transfer.
- **Originator vs owner.** Originator is preserved in `staged_by`; owner can change. Both visible in audit.
- **Refused transfer.** A transfer can be refused by the proposed new owner if firm policy enables; results in `OwnershipTransferRejected` event; order stays with current owner.
- **Order locked.** Some orders carry `ownership_locked=true` (e.g. compliance hold) and cannot be transferred until lock cleared.
- **Cancel during transfer.** A cancel issued mid-transfer: cancel wins (terminal); transfer aborts with audit.
- **Ownership and FIX paired-client.** Mixed FIX+API client orders: ownership is on the FIX-source identity by default; transfers to internal traders is normal.

## API mapping

```
operation: transfer_ownership
items: [{ order_id, new_owner, reason, require_acceptance?: bool }]

operation: accept_ownership_transfer
items: [{ order_id }]

operation: refuse_ownership_transfer
items: [{ order_id, reason }]

operation: lock_ownership
items: [{ order_id, reason }]

operation: unlock_ownership
items: [{ order_id }]
```

## Validator codes touched

`EMS-PRM-1801` (current owner cannot transfer), `EMS-PRM-1802` (new owner not eligible), `EMS-ORD-2301` (transfer to self), `EMS-ORD-2302` (ownership locked), `EMS-ORD-2303` (transfer pending acceptance).

## Permissions

- `#owner-transfer-out` and `#owner-receive` (3-layer).
- `#cross-desk-handoff` for cross-desk.
- `#owner-override` for admin-forced transfers.
- `#ownership-lock` for compliance-driven locks.

## Related

- [[arch-order-staged]] · [[arch-validator]] · [[arch-event-sourcing]] · [[arch-tag-permissions]] · [[arch-firm-desk-user]]
- [[two-step-approval]] · [[tradedate-roll]] · [[amend-order]] · [[batch-creation]]
