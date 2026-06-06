---
type: architecture
layer: identity
status: draft
tags: [architecture/identity]
---

# Firm → Desk → User Hierarchy

The identity and settings model is a **three-level hierarchy** with cascading overrides:

```
Firm  (e.g. ACME Capital)
 └── Desk  (e.g. EQ Cash, FX G10, FI Credit)
      └── User
```

Plus a free-form **tag** dimension that cuts across all three levels.

## Properties

- **Membership is exclusive at desk level.** A user belongs to one desk at a time. (Multi-desk users are modelled as separate user records with shared external identity.)
- **Settings cascade.** A user inherits firm defaults, then desk overrides, then user-level overrides.
- **Permissions AND-gate.** Some permissions apply at all three levels — see [[arch-tag-permissions]].
- **Admins per level.** Each level has an `admin` user. The admin name appears in [[arch-validator|reject messages]] so users know who to ask.

## Identity envelope

```
Identity {
  firm_id          string
  desk_id          string
  user_id          string
  auth_token       opaque             // session-bound, see [[arch-sequence-recovery]]
  tags             set<string>        // user-granted tags
  effective_tags   set<string>        // tags AND-gated against firm+desk grants
}
```

`effective_tags` is computed by intersecting the user's granted tags with the desk-granted and firm-granted sets. Operations evaluate permissions against `effective_tags`.

## Settings model

Settings (preferences, defaults, behaviours) live in a layered store:

```
Setting<T> {
  key:          string
  firm_value:   T?
  desk_value:   T?
  user_value:   T?
  resolved():   T?       // user_value ?? desk_value ?? firm_value
}
```

Examples:

| Key | Typical scope |
|---|---|
| `default_account` | user |
| `default_tif` | desk |
| `auto_route_to_rfq` | desk |
| `markup_bps` (corp treasury) | firm or desk |
| `pre_authorized_counterparties` | desk (with user override) |
| `max_single_order_notional` | desk (cap), user (lower) |

Override discipline: a desk-level cap **cannot be widened** by a user-level setting. Caps narrow downward only — enforced by [[arch-validator]].

## Admin model

Each level has a designated admin identity referenced by name:

| Level | Admin role | Used for |
|---|---|---|
| Firm | `firm_admin` | License grants ([[arch-symbology-figi]]), firm-level tag grants. |
| Desk | `desk_admin` | Desk membership, desk-level tag grants, desk overrides. |
| User | `user_admin` (often same as desk admin) | User onboarding, individual tag grants. |
| Tag | `tag_admin` (per-tag) | Granting a specific tag. |

Admin identifiers appear in [[arch-validator|reject messages]] so a failed user can self-route to the right person.

## How this is referenced

- [[arch-tag-permissions]] — three-layer AND-gate uses this hierarchy.
- [[arch-order-staged]] — `staged_by` is an `Identity`. Orders are visible to the desk and firm by default.
- [[arch-automation-layer]] — rules are scoped to `firm | desk | user | tag` levels.
- [[arch-quote-server]] — subscription permissions resolved per-user against tags.

## Cross-firm and external identity

- A user can have an external identity (SSO subject) mapped to multiple internal `user_id`s (one per firm context for service users).
- Inter-firm visibility is **off by default**. A user from firm A cannot see firm B's orders even with the same auth subject.

## See also

- [[arch-tag-permissions]]
- [[arch-validator]]
- [[arch-api-first]]
- [[arch-order-staged]]
