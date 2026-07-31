# component-04-validation-layers

**Covers:** the layered validation pipeline — SESSION → IDENTITY → REFERENCE → PERMISSION — and,
above all, that the layers run **in that order and short-circuit**.

**Why it exists:** the earlier cases each exercise one refusal in isolation. This one puts two
faults in the same order and asserts which one is reported. That is the property most likely to
drift between three implementations, because nothing about a single-fault test constrains it.

| Input line | What it proves |
|---|---|
| 1–2 | `InstrumentCreated` populates the security master from the journal: one `ACTIVE`, one `SUSPENDED` |
| 4 | everything valid → `OrderAccepted` with `ORD-0000000001` |
| 5 | unknown FIGI → `EMS-REF-2001` at `REFERENCE` |
| 6 | known but suspended → `EMS-REF-2002`, quoting the lifecycle status |
| 7 | **bad session *and* bad FIGI → `SESSION` wins.** Layer 1 short-circuits before REFERENCE is consulted |
| 8 | valid instrument, ungranted tag → `EMS-PRM-1003` at `PERMISSION`, the last layer |

**Line 7 is the point of this case.** Telling a trader "FIGI not present in security master" when
their session had expired would send them to symbology onboarding to fix a login. The reject names
the outermost thing that was wrong, and the `layer` field on the journal event says which layer
decided.

**Why `layer` is on the wire at all:** two rejections can share a code and differ in the layer that
produced them once layers 5–8 land. A journal that omitted it would make those indistinguishable on
replay.

**Seed:** 0.

**Reviewed against:** `schemas/reject-codes/catalog.yaml`.

- `EMS-REF-2001` — "Unknown FIGI: FIGI not present in security master" (line 5).
- `EMS-REF-2002` — the inactive-instrument entry (line 6).
- `EMS-SES-1002` — "Session not found or expired" (line 7).
- `EMS-PRM-1003` — "Firm {firm} is not granted tag #{tag}" (line 8).

The admin hints are the pipeline's own (`Talk to session admin.`, `Verify symbology onboarding for
this instrument.`, `Talk to FIRM1 admin.`), not invented for the corpus.

**Scope, stated plainly:** layers 5–8 (ASSET_CLASS, LIMITS, MARKET, ROUTE) are stubs in the Java
pipeline and absent from the ports. The journal carries only the two instrument fields the
REFERENCE layer actually consults — `InstrumentCore` has twenty-two, and putting them all on the
wire would imply the slice validates against them. There is still no FSM, routing or venue.
