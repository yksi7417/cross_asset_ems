---
type: architecture
layer: validation
status: draft
tags: [architecture/validation]
---

# Validator & Standardized Reject Codes

Every operation in the EMS — staging, amending, routing, cancelling, subscribing, rule-binding — runs through the **validator**. The validator is the single source of "no" with a stable, documented code list.

## Properties

- **Cross-surface.** Same rules apply to API, FIX, and automation traffic — see [[arch-fix-api-bridge]] and [[arch-automation-layer]].
- **Composable.** Rules layer per asset class, per firm, per desk, per user, per tag.
- **Idempotent.** Re-running validation on the same input + state produces the same decision.
- **Cheap.** Validation is hot path — must complete in microseconds for routing decisions.

## Reject envelope

```
ValidatorReject {
  code:        ValidatorCode      // e.g. "EMS-2014"
  category:    SESSION|REFDATA|PERMISSION|ORDER|ROUTE|AUTOMATION
  message:     string             // human-readable, deterministic from code+context
  admin_hint:  string?            // "talk to {admin} for {permission}", see [[arch-tag-permissions]]
  field:       FieldKey?          // when a specific field caused the reject
  caused_by:   request_id         // ties to the API call
}
```

Outbound:

- **API client** receives this as the `ItemResult.error` in the [[arch-api-first|batch response]].
- **FIX client** receives this as `BusinessMessageReject` (`j`) for order/route rejects, or `Reject` (`3`) for session-level — translated via [[arch-fix-api-bridge]].

## Reject code namespace

The code is `EMS-<CAT><NNNN>` where `CAT` is a short category prefix:

| Category | Prefix | Examples |
|---|---|---|
| Session | `SES`  | `EMS-SES-1001` invalid credentials, `EMS-SES-2001` gap detected |
| Reference data | `REF` | `EMS-REF-1001` license denied, `EMS-REF-2001` unknown FIGI |
| Permission | `PRM` | `EMS-PRM-1001` user missing tag, `EMS-PRM-1002` desk not enabled |
| Order | `ORD` | `EMS-ORD-1014` missing limit, `EMS-ORD-2003` qty exceeds max |
| Route | `RTE` | `EMS-RTE-1001` venue not enabled for instrument, `EMS-RTE-2005` cl_ord_id collision |
| Automation | `AUT` | `EMS-AUT-1001` rule scope mismatch, `EMS-AUT-2001` action not permitted for actor |

The full code list is its own document, linked from the validator service's [[arch-jmx-introspection|introspection endpoint]] (planned).

## Layered evaluation

Validation runs in **fixed order** so failures are deterministic:

```
1. Session     — sequence number, auth, heartbeat liveness
2. Identity    — user/desk/firm membership active
3. Reference   — FIGI resolves, license covers requested identifier
4. Permission  — [[arch-tag-permissions]] AND-gate per category
5. Asset-class — typed extension's rules (e.g. FX value date business-day calc)
6. Limits      — per-desk, per-user, per-counterparty notional / count caps
7. Market      — limit-vs-last sanity (see [[arch-quote-server]] integration)
8. Route       — venue/dialect compatibility, account enablement
```

If layer N fails, layers N+1..8 are not evaluated. The reject reports the first failing layer.

## Permission denial wording

For a permission failure, `admin_hint` must point to **the relevant admin** so the user can self-serve resolution. Examples:

| Code | Message | Admin hint |
|---|---|---|
| `EMS-PRM-1001` | "User {u} does not have permission tag `#{tag}`." | "Talk to tag admin `{tag_admin}`." |
| `EMS-PRM-1002` | "User {u} has tag `#{tag}` but desk `{desk}` is not granted." | "Talk to desk admin `{desk_admin}`." |
| `EMS-PRM-1003` | "Firm `{firm}` is not granted tag `#{tag}`." | "Talk to firm admin `{firm_admin}`." |

This is the **3-layer rule from [[arch-tag-permissions]]** made user-facing.

## Where validation hooks in

- [[arch-order-staged]] — on every `stage`, `amend`, `mark_ready`.
- [[arch-router-layer]] — on every `route`, `replace`, `cancel`.
- [[arch-automation-layer]] — on every rule firing, before the action is dispatched.
- Session layer — on every inbound, before deserialisation completes.
- Subscription — on every `subscribe` against [[arch-quote-server]].

## Tests

Every reject code has at least one **golden test** — input event stream + expected reject envelope. These run on every change to the rule sets.

## Sibling concepts

The validator is the **hard reject** path with no override. Two adjacent concerns sit alongside:

- [[arch-compliance]] — **block-with-override** for soft-fail policy (fat-finger, machine-gun, restricted lists, KYC). Compliance can block what the validator passes; humans with the right tag can override.
- [[arch-risk-engine]] — **position-aware caps** (VaR, DV01, exposure). Like compliance, can block what the validator passes; overridable.

A typical pre-trade pipeline runs them in order: **Validator → Compliance → Risk**. All three must pass (or be overridden where allowed) for the operation to proceed.

## See also

- [[arch-api-first]] · [[arch-fix-api-bridge]] · [[arch-tag-permissions]]
- [[arch-compliance]] · [[arch-risk-engine]] · [[arch-position-service]] · [[arch-surveillance]]
- [[arch-order-staged]] · [[arch-router-layer]] · [[arch-event-sourcing]]
