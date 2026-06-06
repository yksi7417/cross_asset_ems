---
type: concept
status: draft
tags: [concept/terminal_screen, concept/analytics]
---

# Bloomberg SWPM<GO>

**SWPM** ("Swap Manager") is a Bloomberg Terminal **valuation and analytics screen** for interest-rate, cross-currency, inflation, and equity swaps. It builds curves, prices swaps, runs scenarios, and supports trade entry for booking.

> **This is a Bloomberg Terminal analytics/calculator screen, not a routable execution destination.** SWPM prices swaps and supports book-entry — it does not execute them. Swap execution flows through a SEF, MTF, or dealer-direct.

## What it does

- Curve construction from deposit / FRA / future / swap inputs (OIS, SOFR, ESTR, SONIA, TONA).
- Pricing, PV, DV01, key-rate sensitivities, scenario analysis.
- Trade-entry templates for booking IRS / XCCY / inflation / equity swaps after execution.

## Where swap execution actually happens

Cleared IRS in the US/EU regulated regime flows through a **SEF/MTF**:

- [[bloomberg-sef]] — IRS, OIS, FRA, basis.
- [[tradeweb]] — major IRS SEF.
- [[bloomberg-bmtf]] — EU MTF for EUR rates.
- Dealer-direct bilateral for exempt non-cleared trades.

## Related

- [[bloomberg-sef]] · [[bloomberg-bmtf]] · [[tradeweb]] (real IRS execution destinations)
- [[bloomberg-cdsw]] (sister calculator for CDS)
- [[interest-rate-swaps]] · [[equity-swaps]] (asset classes)
