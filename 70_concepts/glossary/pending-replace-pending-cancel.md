---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# Pending Replace / Pending Cancel

Two FIX OrdStatus values that mark **the in-flight period** between when the buy-side sends a cancel or replace request and when the broker/venue confirms its outcome:

- **`OrdStatus=E ExecType=E` Pending Replace** — broker received the OrderCancelReplaceRequest (`35=G`) and is processing it. The order's prior fields are still active until either `OrdStatus=5 Replaced` (success) or `35=9 OrderCancelReject` (failure, order stays alive at prior fields).
- **`OrdStatus=6 ExecType=6` Pending Cancel** — broker received the OrderCancelRequest (`35=F`) and is processing it. The order is still alive until either `OrdStatus=4 Canceled` (success) or `35=9 OrderCancelReject` (failure).

The pending states matter for two reasons. **First**, the order is still **live and can fill** during pending — the FIX Appendix D race conditions (fill-during-pending-replace, too-late-to-cancel) all hinge on this. **Second**, the buy-side cannot send a second amend on top of a pending one — most brokers reject "amend during pending" with `35=9`.

A common pitfall: `35=9 OrderCancelReject` does **NOT terminate the order** — the order returns to whatever pre-pending state it was in (New, Partially Filled, etc.). EMS state machines that wrongly terminate on `35=9` create orphan executions that don't match any active order.

## Example

Send Replace to change price 145.00 → 145.10. Broker acks: `OrdStatus=E Pending Replace`. While pending, a fill arrives at 145.00 (the pre-replace price was still active): `ExecType=F, OrdStatus=1 Partially Filled` at 145.00 — fill is valid. Replace then succeeds for the remaining qty: `ExecType=5, OrdStatus=5 Replaced` at 145.10. The fill at 145.00 is real and correct.

## Why it matters in an EMS

- The shared FSM ([[arch-fix-fsm-design]]) explicitly models pending states as live states that can receive fills.
- Appendix D race conditions ([[arch-fix-appendix-d]]) are required production tests.
- Cancel-reject handling must NOT terminate the order.

## Related

- [[ordstatus-exectype]] · [[clordid-origclordid]] · [[fix-protocol-basics]]
- [[arch-order-route-lifecycle]] · [[arch-fix-fsm-design]] · [[arch-fix-appendix-d]]
