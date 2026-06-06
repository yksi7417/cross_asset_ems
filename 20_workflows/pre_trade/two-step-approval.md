---
type: workflow
category: pre_trade
applies_to: ["fx", "equity", "fixed_income"]
status: draft
tags: [workflow/pre_trade]
---

# Two-Step Approval

A firm-policy gate that requires a **second user** (beyond the order originator) to approve before an order can leave the [[arch-order-staged|staged state]] and become routable. Common in FX corporate-treasury workflows (FXEM), sensitive cross-asset blocks, and certain regulated entities.

## Purpose

Reduce single-point-of-error risk on material orders. The originator stages and supplies content; the approver validates intent and unlocks routing. Behaviour interacts directly with **amend** and **solicited-trading** flows.

## Trigger / Entry Point

Two-step approval is invoked when **all** are true on a staged order:

- The desk-level `two_step_approval` setting is `enabled` (see [[arch-firm-desk-user|settings cascade]]).
- The order matches the firm's approval predicate — typically a notional/asset-class/tag filter.
- The originator is not also a member of the `#two-step-approval-approver` tag (otherwise they could self-approve, which firm policy may permit or forbid).

When invoked, the order's `pending_actions` includes `NeedApproval2`. It cannot transition to `READY` until cleared.

## Actors

- **Originator** — stages the order (FIX, API, UI).
- **Approver** — a second user holding `#two-step-approval-approver` evaluated under the [[arch-tag-permissions|3-layer AND-gate]].
- **Validator** — enforces invariants (originator ≠ approver, scope match).
- **OMS staged order layer** — owner of state.
- **FXPV (FX Trader Pre-trade Validation) / FXPF (preferences) consumer** — in FXEM-style workflows the approver acts inside the **solicited trading screen** (FXPV), which calls the same API.

## Steps (canonical flow)

1. Originator stages an order. Validator marks `pending_actions: [NeedApproval2]`. Event `ApprovalRequested` is logged.
2. Order surfaces in approvers' queue (any UI / FXPV / TSOX-equivalent calling `list_pending_approvals`).
3. Approver inspects, optionally amends (subject to amend rules below), then calls `approve_orders([order_id, {comment?}])`.
4. Validator re-runs full validation against the order **as it stands at approval time**. Same code path as routing-time validation — see [[arch-validator]].
5. On pass: `pending_actions` clears `NeedApproval2`. If no other pending actions remain, order auto-transitions to `READY`. Event `OrderApproved` is logged.
6. On reject by approver: `reject_approval([order_id, {reason}])` → terminal `REJECTED`, with reason audited.
7. Mixed-client mirror: any state change is reflected to a paired FIX session as `ExecutionReport` (`8`) with appropriate `OrdStatus`. See [[arch-fix-api-bridge]].

## Effects on amend

Amending an order in `pending_actions: [NeedApproval2]` is **gated**:

| Amend source | Behavior |
|---|---|
| Originator amends an unapproved field (qty, price, account) | Allowed; **re-triggers** approval (resets `NeedApproval2`). Counters reset. Event `ApprovalReset` logged. |
| Approver amends during review | Allowed if approver holds amend-capable tags; **does not** self-approve — must still call `approve_orders` afterward. Originator notified via event. |
| API third party | Disallowed unless they hold both originator-amend and approver tags, which is rare by policy. |

Firms typically configure "**material field amends reset approval**" — e.g. qty change > 5% resets; trader notes do not. Predicate is per-firm config.

## Solicited trading interaction (FXPV-style)

Solicited trading screens (where sales quotes a client interactively, then a trader approves the resulting order) collapse the approval step into the same screen: the FXPV user sees `pending_actions: [NeedApproval2]` inline and approves with one operation. Behavior is identical at the API level — same event, same validator pass.

## Inputs

- `order_id` (target)
- `approve_orders([{order_id, comment?}])` or `reject_approval([{order_id, reason}])`
- Identity (must hold `#two-step-approval-approver` per [[arch-tag-permissions]])

## Outputs / Side Effects

- `ApprovalRequested`, `ApprovalReset`, `OrderApproved`, `OrderApprovalRejected` events.
- If approved and no other pending actions, automatic transition `STAGED → READY` (which may trigger downstream `auto_route_*` rules — see [[arch-automation-layer]]).
- Possible FIX echo `8` (`150=8` Rejected) or `8` (`150=0` New equivalent on approval-readiness).

## Edge Cases & Nuances

- **Originator = approver.** Default: disallowed (`EMS-PRM-1201 self_approval_not_allowed`). Some firms permit for low-notional with explicit setting override; setting is desk-level only — never user-level.
- **Approval predicate change mid-life.** If firm policy widens to include an already-staged order, `NeedApproval2` is added retroactively. If it narrows, `ApprovalNoLongerRequired` event clears the action.
- **Approver loses tag.** A queued approver loses `#two-step-approval-approver`; pending items vanish from their queue (visibility is permission-gated). Other approvers are unaffected.
- **Concurrent approval / reject.** First approval wins; any second attempt returns `EMS-AUT-2010 already_approved`. Race resolved by [[arch-event-sourcing|event log]] ordering.
- **Bulk approval.** The API operation is batch by default — see [[arch-api-first]]. Approvers can `approve_orders([...])` for many orders; validation runs per-item, partial success allowed.
- **Replay.** Replay through [[arch-time-replay-server]] re-derives approval state from the event sequence; no separate snapshot.

## API mapping

```
operation: approve_orders
items: [{ order_id, comment? }]

operation: reject_approval
items: [{ order_id, reason }]
```

## Validator codes touched

`EMS-PRM-1001..1003` (3-layer tag), `EMS-PRM-1201` (self-approval blocked), `EMS-AUT-2010` (already approved), `EMS-ORD-2101` (re-validation fail at approval time).

## Permissions

- Approver must hold `#two-step-approval-approver` under the 3-layer AND-gate.
- The approver's desk membership must overlap with the originator's desk **or** the approver must hold a `#cross-desk-approver` tag (firm-policy-dependent).

## Related

- [[arch-order-staged]] · [[arch-tag-permissions]] · [[arch-validator]] · [[arch-event-sourcing]]
- [[order-ownership]] · [[amend-order]] · [[batch-creation]]
- [[fxel]] (corp-treasury approval pairing) · [[auto-route]]
