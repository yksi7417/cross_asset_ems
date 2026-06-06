---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# CFTC SDR

**CFTC SDR** ("Swap Data Repository") is the **post-trade reporting destination for swaps subject to CFTC jurisdiction** under Dodd-Frank Title VII. Every swap trade by a US person (or with a US counterparty) must be reported to a registered SDR — **real-time reporting** (typically within minutes) for public dissemination, plus **regulatory reporting** with full counterparty details to the CFTC.

Multiple SDRs are CFTC-registered: **DTCC DDR** (the dominant credit/rates/equity SDR), **CME SDR** (rates / commodities), **ICE Trade Vault** (credit / commodities). Each reporting party / counterparty chooses an SDR and submits per the SDR's API.

Reporting fields are extensive: USI/UTI, asset class, product class, parties, notional, price, dates, lifecycle events, clearing flag, FCM, MAT flag. Lifecycle events (resets, payments, novations, terminations) trigger amendment reports.

## Example

A buy-side firm executes a USD 10y IRS on Bloomberg SEF with Goldman. Bloomberg SEF submits the real-time report to DTCC DDR within minutes (publicly disseminated with masked counterparty). On novation to LCH, LCH submits an amendment report. Throughout the trade's life, lifecycle events feed back through DDR.

## Why it matters in an EMS

- The EMS submits SDR reports for swaps the firm is the obligated reporter on (often the SEF or dealer, but the buy-side has obligations too in some cases).
- Real-time deadline discipline is operationally critical.
- See [[40_regulatory/cftc-sdr]] for the deeper note.

## Related

- [[mat]] · [[novation]] · [[fcm]] · [[usi-uti]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]]
- [[bloomberg-sef]] · [[sef-platforms]]
- [[trace]] · [[finra-cat]] · [[emir-sftr-csdr]] (sibling reporting regimes)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
