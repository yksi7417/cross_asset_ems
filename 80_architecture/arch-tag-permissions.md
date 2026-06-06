---
type: architecture
layer: identity
status: draft
tags: [architecture/identity]
---

# Tag Permissions — Three-Layer AND-Gate

Permissions in the EMS are granted via **tags** evaluated against the [[arch-firm-desk-user|three-level hierarchy]]. For a user `U` to perform an action `A` requiring tag `T`, **all three** of the following must be true:

```
1. U.firm    is granted T
2. U.desk    is granted T
3. U        is granted T
```

If **any** of the three is missing, the action is denied — with a denial message that names the missing level and the admin who can grant it.

## Why three-layer

- **Firm-level grant** captures licensing and contractual entitlements (e.g. "this firm has paid for the algo strategy").
- **Desk-level grant** captures organisational policy (e.g. "credit desk does not trade FX").
- **User-level grant** captures individual delegation (e.g. "this user is not a senior trader yet").

ANY of these can revoke without disturbing the others. A user demoted off a tag still leaves the firm and desk grants intact for their colleagues.

## Evaluation

```
def authorize(identity, tag):
    if tag not in firm_grants[identity.firm_id]:
        return DENY("EMS-PRM-1003",
                    f"Firm `{identity.firm_id}` is not granted tag `#{tag}`.",
                    admin=firm_admin)
    if tag not in desk_grants[identity.desk_id]:
        return DENY("EMS-PRM-1002",
                    f"User `{identity.user_id}` has tag `#{tag}` "
                    f"but desk `{identity.desk_id}` is not granted.",
                    admin=desk_admin)
    if tag not in user_grants[identity.user_id]:
        return DENY("EMS-PRM-1001",
                    f"User `{identity.user_id}` does not have permission tag `#{tag}`.",
                    admin=tag_admin_for(tag))
    return ALLOW
```

The order matters: failing levels are reported **outermost first** because outer denials are usually load-bearing (a firm without the grant means resolving the user-level grant alone is insufficient).

## Worked example

> User Anne sits at Firm A, Desk D1, with tag `#algo-execution`.
> A permission for `#algo-execution` is granted to Firm A, **Desk D2**, and Anne.

Anne attempts to bind an algo rule. The validator runs:

- Firm A is granted `#algo-execution` → ✓
- Anne's desk D1 is **not** granted `#algo-execution` → ✗

Denial:

> `EMS-PRM-1002`: "User `anne` has tag `#algo-execution` but desk `D1` is not granted. Talk to desk admin `dave.lee`."

If Anne moves to D2, evaluation passes.

## Tag categories

| Tag class | Example tags | Typical scope of grant |
|---|---|---|
| Asset enablement | `#trade-fx-spot`, `#trade-corp-hy` | Firm + Desk |
| Workflow capability | `#two-step-approval-approver`, `#auto-route-binder` | Desk + User |
| Data sensitivity | `#lvl2-equity-na`, `#cusip-license` | Firm |
| Counterparty | `#cpty-marketaxess-rfq`, `#cpty-bbg-tba` | Firm + Desk |
| Special operations | `#superuser-inject` | User only (rare, audited) |

## Audit

Every permission **denial** is logged as a `PermissionDenied` event in [[arch-event-sourcing]]:

```
PermissionDenied {
  request_id, identity, tag, missing_level: FIRM|DESK|USER,
  admin_hint, original_operation
}
```

Permission **grants and revocations** are also events on the admin stream (`TagGranted`, `TagRevoked`) and are replayable like any other state change.

## Tag-scoped automation

[[arch-automation-layer]] rules can be `scope: TAG, scope_ref: "#algo-execution"`. Such a rule's actor (the binder) must be authorized for the tag, **and** the rule will only fire for orders whose owner is also authorized for the tag — re-evaluating both at firing time.

## Tags as baskets/lists

Beyond permissions, hashtag-style tags on orders ([[arch-order-staged]]) serve as **flexible baskets** — a single order can carry many tags (`#rebalance`, `#client-omega`, `#vwap`), and tags are queryable for grouping, automation triggers, and reporting. Permission tags and grouping tags share the same string space but distinct semantics: a permission tag has explicit grants; a grouping tag is free-form metadata.

## What this is not

- Not a full RBAC engine. Tags are flat strings; there is no role hierarchy.
- Not attribute-based access control with arbitrary boolean policies. Three-layer AND is the only composition.
- Not silent. Every denial is verbose and points at an admin.

## See also

- [[arch-firm-desk-user]]
- [[arch-validator]]
- [[arch-automation-layer]]
- [[arch-order-staged]]
- [[arch-event-sourcing]]
