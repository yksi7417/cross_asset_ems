---
type: concept
status: draft
tags: [concept/glossary, glossary/settlement]
---

# T+1 / T+2 — Settlement Cycles

A **settlement cycle** is the number of business days between trade execution and cash + security exchange. **T+2** has been the historical industry standard (FX spot, US equity until 2024, EU equity). **T+1** is the current US equity standard (since 28 May 2024), several APAC equity markets, and historically some FI products.

The convention is jurisdiction- and asset-specific:

| Asset | Cycle |
|---|---|
| US equities (post-2024) | T+1 |
| EU / UK equities | T+2 |
| FX spot (most pairs) | T+2 |
| FX spot USD/CAD | T+1 |
| US Treasuries | T+1 |
| US corporate bonds | T+2 |
| US munis | T+2 |
| TBA-MBS | per SIFMA Class A/B/C/D monthly schedule |
| Cleared swaps | per CCP rules |

The shorter cycles squeeze the post-trade pipeline: confirmation, allocation, FX (for non-USD cash), funding, instruction-to-CSD all happen in less time. The US T+1 transition forced material upgrades to STP and FX-funding workflows.

## Example

A US equity trader buys 10K AAPL on Monday 2026-06-08. Settlement on Tuesday 2026-06-09 (T+1). The buy-side must have USD cash on Tuesday; if the cash came from selling EU equity (T+2 settling Wednesday) there's a 1-day funding gap requiring overdraft or pre-funding.

## Why it matters in an EMS

- T+N is a per-instrument reference data field.
- [[arch-stp-pipeline]] timing windows are set by T+N.
- FX-for-funding becomes a first-class derived workflow in T+1 regimes — see [[arch-fx-netting]].

## Related

- [[spot-date-value-date]] · [[dvp-rvp-fop]] · [[allocation-affirmation-confirmation]]
- [[arch-stp-pipeline]] · [[arch-confirmation-affirmation]] · [[arch-fx-netting]]
- [[arch-reference-data-service]] (per-instrument settlement convention)
