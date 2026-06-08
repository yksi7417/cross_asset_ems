---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# iNAV (Intraday NAV)

**iNAV** is the **continuously-recalculated estimate of an ETF's underlying net asset value** based on the live prices of its constituent securities. Distinct from the official end-of-day NAV, which is a single number per day; iNAV is a tick stream throughout the session.

iNAV is the natural reference price for ETF trading: an ETF's market price should track its iNAV within a small spread; significant deviations signal arbitrage opportunities for [[authorized-participant|APs]]. The ETF issuer or a data vendor publishes the iNAV (often the issuer outsources to ICE / Refinitiv / Bloomberg).

For an EMS, iNAV serves as:

- The **best-ex benchmark** for ETF executions (ETF NBBO isn't a meaningful reference for thinly-traded ETFs).
- The **fat-finger reference** in pre-trade compliance.
- The **pre-trade fair-price estimate** for ETF block RFQs.

## Example

A buy-side trader RFQs a 500K-share block of an EM equity ETF that trades thinly on lit venues. The iNAV at the RFQ moment is $48.20. Dealer responses come in at $48.18 / $48.15 / $48.12 (bid). The buy-side elects $48.18 — within 4 bp of iNAV, a tight execution given the ETF's secondary-market spread might be 30 bp.

## Why it matters in an EMS

- iNAV is consumed by [[arch-realtime-analytics]] as a benchmark feed.
- The [[arch-compliance]] fat-finger check uses iNAV as the reference for thin-secondary ETFs.
- [[arch-best-execution]] ETF audits benchmark against iNAV, not just NBBO.

## Related

- [[authorized-participant]] · [[bloomberg-rfqe]]
- [[cash-equity]] (ETFs as a sub-class)
- [[arch-realtime-analytics]] · [[arch-pricing-service]] · [[arch-best-execution]] · [[arch-compliance]]
