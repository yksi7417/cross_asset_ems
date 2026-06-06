---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# OrdStatus / ExecType (FIX)

Two FIX tags on every ExecutionReport (`35=8`) that **together describe the order's state**:

- **`39 OrdStatus`** — the current **state of the order** as seen by the broker/venue: `0` New, `1` Partially Filled, `2` Filled, `4` Canceled, `5` Replaced, `6` Pending Cancel, `8` Rejected, `A` Pending New, `E` Pending Replace, `C` Expired.
- **`150 ExecType`** — the **reason this ExecutionReport was sent**: `0` New, `4` Canceled, `5` Replaced, `6` Pending Cancel, `8` Rejected, `E` Pending Replace, `F` Trade (a fill), `G` Trade Correct, `H` Trade Cancel (a fill bust).

The pair distinguishes "the order was just replaced" (ExecType=5) from "the order is now in Replaced status" (OrdStatus=5). Some FIX dialects use the same letter for both with different semantics — read carefully. Modern FIX 4.4 strongly prefers ExecType to carry the **change-of-state event** while OrdStatus carries the **current state**.

Critical for the EMS: **OrderCancelReject (`35=9`) is NOT an ExecutionReport** — it has its own OrdStatus field reflecting the order's state before/after the failed cancel-replace. A `35=9` does NOT terminate the order; it leaves the order in its prior live state.

## Example

Send NewOrder. ExecReport arrives: `ExecType=0` (just acknowledged), `OrdStatus=0` (state: New). 100 shares fill: `ExecType=F` (trade), `OrdStatus=1` (Partially Filled), `LastQty=100`. Send Cancel. ExecReport: `ExecType=6` (Pending Cancel), `OrdStatus=6`. Cancel succeeds: `ExecType=4` (Canceled), `OrdStatus=4`.

## Why it matters in an EMS

- The shared FSM ([[arch-fix-fsm-design]]) is driven by the (ExecType, OrdStatus) pair.
- Misinterpreting these is a common cause of order-state bugs in EMS code.
- Appendix D race conditions ([[arch-fix-appendix-d]]) frequently turn on subtle ExecType / OrdStatus differences.

## Related

- [[fix-protocol-basics]] · [[clordid-origclordid]] · [[pending-replace-pending-cancel]]
- [[arch-order-route-lifecycle]] · [[arch-fix-fsm-design]] · [[arch-fix-appendix-d]]
