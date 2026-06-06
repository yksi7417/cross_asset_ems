---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# FIX Protocol Basics

**FIX** (Financial Information eXchange) is the **dominant electronic trading wire protocol** for order entry, executions, allocations, and post-trade. Tag-value text format (`35=D|55=AAPL|54=1|...`); each tag is a numbered field with a defined semantic.

Common message types (`35=`): `D` NewOrderSingle, `G` OrderCancelReplaceRequest, `F` OrderCancelRequest, `8` ExecutionReport, `9` OrderCancelReject, `j` BusinessMessageReject, `3` Reject, `0` Heartbeat, `1` TestRequest, `2` ResendRequest, `J` AllocationInstruction.

Common tags: `11 ClOrdID` (client order id), `41 OrigClOrdID` (the prior id when amending), `37 OrderID` (exchange/broker id), `17 ExecID` (execution id), `39 OrdStatus`, `150 ExecType`, `54 Side`, `55 Symbol`, `38 OrderQty`, `40 OrdType`, `44 Price`, `59 TimeInForce`, `60 TransactTime`, `526 SecondaryClOrdID`, `583 ClOrdLinkID`. Versions: 4.2 (older but still common), 4.4 (most common today), 5.0 SP2 (newest).

## Example

A new order: `8=FIX.4.4|9=...|35=D|49=BUY|56=BROKER|11=ABC123|55=IBM|54=1|38=10000|40=2|44=145.50|59=0|60=20260606-14:30:00|10=...`

## Why it matters in an EMS

- FIX is the **default external protocol** even though we use SBE internally (see [[arch-fix-api-bridge]]).
- FIX OrdStatus / ExecType semantics drive the order lifecycle FSM (see [[arch-order-route-lifecycle]] and [[arch-fix-appendix-d]]).
- Custom-tag namespaces are politically expensive — we keep SBE for internal use and translate only at the edge.

## Related

- [[clordid-origclordid]] · [[ordstatus-exectype]] · [[pending-replace-pending-cancel]]
- [[arch-fix-api-bridge]] · [[arch-order-route-lifecycle]] · [[arch-fix-appendix-d]]
- [[arch-sbe-aeron-transport]] (the internal counterpart)
