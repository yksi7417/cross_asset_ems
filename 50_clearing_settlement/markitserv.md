---
type: clearing_settlement
kind: affirmation
status: draft
tags: [clearing/affirmation]
---

# MarkitSERV (S&P Global)

**MarkitSERV** is the **dominant post-trade middleware service for OTC derivatives** — operates **MarkitWire** for IRS / CDS affirmation, **MarkitServ for FX**, and **Pulse** (allocation / affirmation for cash bonds). Now part of S&P Global after the IHS Markit acquisition.

## What it does

Standardised post-trade processing:

- **Trade affirmation** — buy-side and dealer confirm trade economics post-execution.
- **Clearing routing** — submits affirmed trades to the relevant CCP ([[lch]] / [[cme-clear]] / [[ice-clear]]) for clearing.
- **Allocation handling** — buy-side allocates a block trade across underlying accounts; MarkitSERV routes the allocations to dealer and CCP.
- **Lifecycle event processing** — resets, payments, partial novations, amendments flow through MarkitSERV.

## Scope (asset classes)

- [[interest-rate-swaps|IRS]] / OIS / basis (via MarkitWire).
- [[credit-default-swaps|CDS]] (via MarkitServ Credit).
- FX swaps / forwards / NDFs ([[fx-ndf]]) — via MarkitServ FX.
- Equity swaps and TRS ([[equity-swaps]]).
- Cash bonds — via Pulse for selected workflows.

## Settlement / timing

- Real-time affirmation flow — trades confirmed within minutes of execution.
- Clearing submission immediately after affirmation.
- Lifecycle events as they occur.

## EMS touchpoints

- [[arch-confirmation-affirmation]] integrates with MarkitSERV as a primary network.
- Allocation flows via [[arch-allocation-service]] route through MarkitSERV for OTC.
- USI/UTI ([[usi-uti]]) preserved through the affirmation pipeline.

## Related

- [[allocation-affirmation-confirmation]] (the workflow)
- [[arch-confirmation-affirmation]] (the architectural integration)
- [[lch]] · [[cme-clear]] · [[ice-clear]] (CCPs downstream)
- [[bloomberg-toms]] (sell-side trade-capture integration)
- [[arch-stp-pipeline]]
