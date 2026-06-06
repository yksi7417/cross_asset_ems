---
type: concept
status: draft
tags: [concept/terminal_screen, concept/analytics]
---

# Bloomberg CDSW<GO>

**CDSW** ("CDS Workstation") is a Bloomberg Terminal **valuation calculator and analytics screen** for credit default swaps. It supports CDS pricing, spread / upfront conversion, hazard rate bootstrapping, MTM, and what-if scenarios against the ISDA standard model.

> **This is a Bloomberg Terminal analytics/calculator screen, not a routable execution destination.** CDSW prices CDS — it does not execute them. CDS execution flows through a SEF or dealer-direct.

## What it does

- ISDA standard-model CDS pricing (PV, spread, upfront, hazard rate).
- Curve bootstrapping from market quotes.
- Trade entry for booking purposes (post-execution).
- What-if scenarios for spread / recovery / hazard sensitivities.

## Where CDS execution actually happens

Single-name and index CDS execution in the US/EU regulated regime flows through a **SEF**:

- [[bloomberg-sef]] — multi-asset SEF including credit indices and single-names.
- [[tradeweb]] — credit SEF.
- ICE Swap Trade — credit SEF.
- Dealer-direct bilateral for exempt non-cleared trades.

## Related

- [[bloomberg-sef]] · [[tradeweb]] (real CDS execution destinations)
- [[bloomberg-swpm]] (sister calculator for IRS)
- [[credit-default-swaps]] · [[interest-rate-swaps]] (asset classes)
- [[arch-rfq]] (RFQ workflow including CDS index)
