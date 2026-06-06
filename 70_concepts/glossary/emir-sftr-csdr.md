---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# EMIR / SFTR / CSDR — EU Post-Trade Trio

Three EU regulations covering distinct slices of post-trade obligations:

- **EMIR** ("European Market Infrastructure Regulation") — the EU swap-reporting and central-clearing regime. EU equivalent of CFTC SDR + Title VII. Every EU-touching derivative trade is reported to an EU TR (Trade Repository — DTCC EU, REGIS-TR, UnaVista) by T+1, plus mandatory CCP clearing for designated products. **UK EMIR** is the post-Brexit UK regime, almost identical.
- **SFTR** ("Securities Financing Transactions Regulation") — covers **repo, securities lending, buy-sell-backs, margin lending**. EU/UK firms report every SFT to an EU TR by T+1 — entire lifecycle: execution, collateral updates, reuses, modifications, termination.
- **CSDR** ("Central Securities Depository Regulation") — covers **settlement discipline**. Failed settlements (post-T+2) trigger mandatory cash penalties; persistent failures trigger mandatory buy-ins. Drives [[arch-confirmation-affirmation]] and [[arch-stp-pipeline]] design — fails are real-money costly.

Together they constitute the EU's post-trade equivalent of the US Dodd-Frank + Title VII + TRACE / CFTC SDR / settlement-rule complex.

## Example

A UK asset manager executes: a EUR IRS (reported to EMIR TR), a USD/EUR FX swap (no EMIR for non-derivative-status FX swaps, but reportable under the SI / market-data regimes), a repo to fund the IRS margin (reported under SFTR), and an equity buy in Germany (CSDR penalises if not settled T+2). Each leg generates a different reporting / lifecycle obligation.

## Why it matters in an EMS

- EMIR / SFTR reporting integrations are first-class destinations for [[arch-regulatory-reporting-service]].
- CSDR is a driver of [[arch-confirmation-affirmation]] timing and exception handling.
- See [[arch-jurisdictional-compliance]] for the full EU stack.

## Related

- [[cftc-sdr]] · [[trace]] · [[finra-cat]] (US counterparts)
- [[rts-22-27-28]] · [[mar-stor]] (sibling EU regimes)
- [[arch-regulatory-reporting-service]] · [[arch-confirmation-affirmation]] · [[arch-stp-pipeline]]
- [[arch-jurisdictional-compliance]]
