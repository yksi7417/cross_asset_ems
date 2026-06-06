---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# TRACE

**TRACE** ("Trade Reporting and Compliance Engine") is FINRA's **post-trade reporting facility for US corporate bonds, agencies, MBS, and selected ABS**. Broker-dealers must report eligible TRACE-reportable trades **within 15 minutes** of execution (since 2015) and within 1 minute for some categories. The reported trades are then **disseminated publicly** in near-real-time (subject to size and dissemination delay rules for less-liquid issues).

TRACE coverage expanded over time: corporates (2002), agency debentures (2010), Agency MBS (2011), specified-pool MBS (2013), ABS / CMBS (2018), Treasuries (added 2017, but kept regulator-only at first). Each asset class has its own dissemination rules — large block trades in HY can be capped at $5M reported, with the actual size released after a delay to avoid market disruption.

For the EMS, TRACE is one of the dominant **post-trade reporting destinations** the [[arch-regulatory-reporting-service]] must integrate.

## Example

A buy-side firm executes a 5M IG corporate bond block with Goldman as principal. Goldman (as the executing broker-dealer) submits the trade to TRACE within 15 minutes. TRACE disseminates the trade (with the full size or capped per rules) to the public TRACE tape, which feeds MarketAxess CP+, Bloomberg BVAL/CBBT, and trader screens.

## Why it matters in an EMS

- The EMS submits TRACE reports for trades the firm is the obligated reporter on.
- Timing discipline (15 min, 1 min for some) is operationally important.
- See [[40_regulatory/trace]] for the deeper note; [[arch-regulatory-reporting-service]] for the architectural integration.

## Related

- [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[mbs]] · [[abs]] · [[govt-bonds]]
- [[msrb-rtrs]] · [[cftc-sdr]] · [[finra-cat]] (sibling US reporting destinations)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
