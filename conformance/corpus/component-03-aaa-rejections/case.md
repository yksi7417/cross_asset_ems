# component-03-aaa-rejections

**Covers:** the AAA entitlement decision — the first case in the corpus where the slice *refuses*
something, and the first that proves rejection is byte-identical across three languages too.

**Why it exists:** every earlier case is a happy path. An implementation that agreed on success and
diverged on failure would pass the whole corpus. Rejections are also where the three languages have
the most room to disagree: an exception in one, a sentinel in another, an error value in the third.

| Input line | What it proves |
|---|---|
| 1 | an `OrderNew` **before** any logon is `EMS-SES-1002` — session state is built from the journal, so ordering matters |
| 2 | `SessionLogon` grants `market-data` only |
| 3 | an order requiring `#order-entry` is `EMS-PRM-1001`, naming the user and the tag |
| 4 | an order requiring *no* tag on the same session is accepted — the deny is specific to the entitlement, not to the session |
| 5 | a re-logon on the same session id replaces the identity (`trader1` → `trader2`) with wider tags |
| 6 | the same order that failed at line 3 now succeeds — the decision follows the current session, not the first one |
| 7 | a non-numeric `sessionId` is a **rejection, not a crash**: malformed data on the wire is data, and the slice must not die on it |

**The identifier property this case pins:** accepted orders take `ORD-0000000001` and
`ORD-0000000002`. The three rejections consume no identifier. If they did, ids in every journal
would depend on how many orders had failed earlier, and any corpus case downstream of a rejection
would shift the moment a validation rule changed.

**Seed:** 0.

**Reviewed against:** `schemas/reject-codes/catalog.yaml`. `EMS-SES-1002` is "Session not found or
expired — per-request session lookup failed: session ID unknown or already logged out", which is
what lines 1 and 7 do. `EMS-PRM-1001` is "User missing tag — User does not have permission tag
#{tag}", and the emitted reason follows that template. Neither code was invented for this case.

**Scope, stated plainly:** this is the AAA *decision* only — no logon credentials, no SSO, no SCIM,
no session sequence recovery (ADR 0002). There is still no validation pipeline, FSM, routing or
venue; an accepted order goes straight to `OrderAccepted`.
