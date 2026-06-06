---
type: regulatory
regulator: CFTC
status: draft
tags: [regulatory]
---

# SEF Reporting

CFTC-mandated reporting obligations specific to **Swap Execution Facilities** ([[sef-platforms]]). SEFs report executed trades to a chosen SDR ([[cftc-sdr]] / [[dtcc-sdr]]) and to the CFTC directly; they also operate audit and rule-book obligations.

## What SEFs report

- **Real-time public report** of every executed trade (anonymized counterparties, masked notional above caps).
- **Regulator report** to CFTC with full counterparty detail.
- **Rule-book compliance** — RFQ-to-3 ([[rfq-to-3]]) for MAT D2C, audit of order book activity, position limits enforcement.
- **Quarterly SEF activity reports** to CFTC under Part 37.

## Major SEFs

See [[sef-platforms]] for the list — Bloomberg SEF, Tradeweb SEF, ICE Swap Trade, BGC, GFI, TP-ICAP, 360T SEF.

## Touchpoints in the EMS

- For sell-side EMS implementations, integration with the SEF's submission interfaces is direct.
- Buy-side EMS receives execution acks that include SEF identifiers and USI/UTI.
- [[arch-best-execution]] audit captures SEF interaction (RFQ count, response rate, cover info).

## Related

- [[sef-platforms]] · [[bloomberg-sef]] (specific SEFs)
- [[mat]] · [[rfq-to-3]] (SEF regulatory mechanics)
- [[cftc-sdr]] · [[dtcc-sdr]] (SDR reporting destinations)
- [[arch-best-execution]] · [[arch-regulatory-reporting-service]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]]
