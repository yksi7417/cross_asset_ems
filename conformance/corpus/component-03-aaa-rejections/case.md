# component-03-aaa-rejections

**Covers:** the three-layer tag-permission AND-gate and session lookup — the first case in the
corpus where the slice *refuses* something, and the first that proves rejection is byte-identical
across three languages too.

**Why it exists:** every earlier case is a happy path. An implementation that agreed on success and
diverged on failure would pass the whole corpus. Rejections are also where three languages have the
most room to disagree: an exception in one, a sentinel in another, an error value in the third.

This case runs the **production** evaluator (`io.crossasset.ems.aaa.permission.TagPermissionEvaluator`),
not a slice-local simplification of it. The Rust and C++ ports mirror that evaluator, which is the
whole point of the exercise.

| Input line | What it proves |
|---|---|
| 1 | an `OrderNew` **before** any logon is `EMS-SES-1002` — session state is built from the journal, so ordering matters |
| 2–3 | firm grant missing → **`EMS-PRM-1003`**. `firmTags` defaults to the user's tags, so a user-only grant leaves the firm layer empty |
| 4–5 | firm grants, desk does not → **`EMS-PRM-1002`**, naming both the user and the desk |
| 6–7 | firm and desk grant, user does not hold it → **`EMS-PRM-1001`** |
| 8 | an order requiring *no* tag on that same session is accepted — the deny is specific to the entitlement, not the session |
| 9–10 | a re-logon on session 9 widens the user's tags and the previously refused order now succeeds |
| 11 | a non-numeric `sessionId` is a **rejection, not a crash**: malformed data on the wire is data |

**Denial order is part of the contract.** The gate reports the *outermost* missing layer and stops.
A firm denial is emitted without consulting the desk, because a missing firm grant makes the inner
resolution irrelevant — and telling a user "you lack the tag" when their whole firm was never
granted it sends them to the wrong administrator.

**The identifier property this case pins:** the two accepted orders take `ORD-0000000001` and
`ORD-0000000002`. The four rejections consume no identifier. If they did, ids in every journal would
depend on how many orders had failed earlier, and any case downstream of a rejection would shift the
moment a validation rule changed.

**Seed:** 0.

**Reviewed against:** `schemas/reject-codes/catalog.yaml`.

- `EMS-SES-1002` — "Session not found or expired: per-request session lookup failed" (lines 1, 11).
- `EMS-PRM-1003` — "Firm {firm} is not granted tag #{tag}" (line 3).
- `EMS-PRM-1002` — "User has tag #{tag} but desk {desk} is not granted" (line 5).
- `EMS-PRM-1001` — "User does not have permission tag #{tag}" (line 7).

Each emitted message follows its catalog template. No code was invented for this case.

**Scope, stated plainly:** the AAA *decision* only — no logon credentials, no SSO, no SCIM, no
session sequence recovery (ADR 0002). There is still no validation pipeline, FSM, routing or venue;
an authorized order goes straight to `OrderAccepted`.
