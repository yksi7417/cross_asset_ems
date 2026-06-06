---
type: concept
status: draft
tags: [concept/glossary, glossary/settlement]
---

# Allocation / Affirmation / Confirmation

Three sequential post-trade steps in the institutional settlement workflow:

- **Allocation** — the buy-side **divides a block trade across the underlying accounts** that benefit from it. E.g. a 100K-share buy at 145.10 might allocate 30K to Fund A, 50K to Fund B, 20K to Fund C — at one (averaged) price or at specific prices per allocation policy. See [[arch-allocation-service]].
- **Affirmation** — the buy-side **confirms allocation details to the executing broker** (account IDs, quantities, settlement instructions). The broker validates the accounts are pre-authorised, settlement standing instructions exist, etc.
- **Confirmation** — the **two-sided trade match** — buy-side and broker agree on every economic detail (price, qty, settle date, account, fees). Often via a matching network (DTCC CTM, MarkitSERV, Bloomberg VCON). Mismatches generate "breaks" that operations teams must resolve before settlement.

CSDR penalties for late settlement make this pipeline **time-critical**. T+1 US equity settlement compresses this from a day-and-half to a few hours.

## Example

A buy-side block of 100K IBM at 145.10. Allocation: 40K Fund A, 60K Fund B (per pre-trade allocation template). Affirmation: buy-side sends allocation breakdown to broker via DTCC CTM. Confirmation: broker's record matches buy-side's record on all fields → "matched"; settlement instructions flow to DTC for T+1 RVP settlement. Mismatch → "break" → operations chases resolution before T+1 cutoff.

## Why it matters in an EMS

- These three stages are first-class components: [[arch-allocation-service]] and [[arch-confirmation-affirmation]].
- The [[arch-stp-pipeline]] orchestrates them post-execution.
- Breaks feed the [[arch-notification-service]] for operations attention.

## Related

- [[tplus-1-tplus-2]] · [[dvp-rvp-fop]]
- [[arch-allocation-service]] · [[arch-confirmation-affirmation]] · [[arch-stp-pipeline]]
- [[arch-notification-service]] · [[arch-jurisdictional-compliance]] (CSDR)
