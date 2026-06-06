---
type: regulatory
regulator: FINRA
status: draft
tags: [regulatory]
---

# TRACE (FINRA)

FINRA's **Trade Reporting and Compliance Engine** — the post-trade reporting facility for **US corporate, agency, MBS, ABS, and select Treasury debt**. Mandatory for broker-dealers within the reporting deadline. See the [[trace|glossary entry]] for the quick definition.

## Scope (asset classes)

- US IG and HY [[corp-bonds-ig|corporate bonds]] (since 2002).
- Agency debenture debt (since 2010).
- Agency MBS (since 2011) — see [[mbs]].
- Specified-pool MBS (since 2013).
- ABS / CMBS / CDO (since 2018) — see [[abs]].
- US Treasuries (since 2017) — but regulator-only, not public dissemination.

## Reporting fields / timing

- **Timing**: **15 minutes** from execution for most categories; **1 minute** for some equity-linked debt; T+1 for some structured products.
- **Fields**: ~40 fields including CUSIP, side, quantity, price, dirty/clean indicator, settlement date, capacity (agency vs principal), commission, ATS / non-ATS flag, when-issued flag.
- **Block-size dissemination caps** — HY blocks $5M+ reported at $5M with delayed actual disclosure; same idea for IG blocks. Designed to preserve dealer ability to work positions out.
- **Submission channel**: TRACE web portal + FIX (some dealers); FINRA's REPORT processing.

## Touchpoints in the EMS

- [[arch-regulatory-reporting-service]] handles TRACE submission for trades the firm is the obligated reporter on.
- The reportable-determination logic uses asset-class and venue metadata from [[arch-reference-data-service]].
- Submission deadlines are tracked and ack/nack lifecycle managed; missed deadlines escalate via [[arch-notification-service]].
- TRACE dissemination feed (public TRACE tape) is consumed back as a market-data input — see [[composite-price]].

## Related

- [[trace|TRACE glossary]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[mbs]] · [[abs]]
- [[msrb-rtrs]] · [[cftc-sdr]] · [[finra-cat]] (sibling US reporting regimes)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
