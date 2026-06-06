---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# ClOrdID / OrigClOrdID / OrderID / ExecID

Four FIX order identifiers that **every EMS engineer must distinguish** because they have distinct lifetimes and ownership:

- **`11 ClOrdID`** — the **client's order identifier**, chosen by the buy-side. Required on every new order, every cancel, every replace. Must be unique within the session.
- **`41 OrigClOrdID`** — the **previous ClOrdID** when sending a cancel or replace. Required on `35=F` Cancel and `35=G` Replace messages so the broker knows which prior order is being modified.
- **`37 OrderID`** — the **broker/venue order identifier**, assigned by the broker on first ack. Stays the same through the order's life (even as ClOrdID changes on each amend).
- **`17 ExecID`** — the **per-execution identifier** assigned by the broker for each ExecutionReport. New ExecID for every fill / status change / reject. Must be globally unique within the broker.

The critical confusion: **ClOrdID changes on every cancel/replace** (the new ClOrdID identifies the new order state); **OrderID is stable**. Best practice for the buy-side: track the entire chain by `OrderID`, but use ClOrdID for the current state. The EMS's [[arch-identity-chaining]] discipline goes further — preserves `initial_cl_ord_id` from the first ack so the entire amend chain is reconstructable.

## Example

Send new order: `ClOrdID=ABC001`. Broker acks with `OrderID=GS-7891234`. Trader amends quantity: send new `ClOrdID=ABC002` with `OrigClOrdID=ABC001`. Broker acks: still `OrderID=GS-7891234`, but now linked to `ClOrdID=ABC002`. Each fill arrives with its own `ExecID`.

## Why it matters in an EMS

- Misreading these IDs is the #1 source of FIX state machine bugs.
- The [[arch-fix-fsm-design|shared FSM]] tracks the (OrderID, current-ClOrdID) pair explicitly.
- [[arch-identity-chaining]] preserves the original IDs across amends for audit.

## Related

- [[fix-protocol-basics]] · [[ordstatus-exectype]] · [[pending-replace-pending-cancel]]
- [[arch-order-route-lifecycle]] · [[arch-fix-appendix-d]] · [[arch-identity-chaining]]
