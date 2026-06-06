---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives, glossary/regulatory]
---

# MAT (Made-Available-to-Trade)

**MAT** is a CFTC determination that a specific OTC swap product is **liquid enough to mandate SEF execution and central clearing**. Once an instrument is MAT, US persons can no longer trade it bilaterally — it must execute on a SEF (D2C in RFQ-to-3 mode or CLOB) and clear at a CCP.

A SEF determines an instrument is MAT, files with the CFTC, and after a 30-day review the MAT obligation is binding industry-wide. As of recent regimes, MAT covers: most USD IRS at standard tenors, the major CDS indices (CDX IG, CDX HY, iTraxx Main, iTraxx Crossover), and selected OIS / basis. Non-MAT swaps (bespoke tenors, exotic structures, single-name CDS outside major reference entities) remain eligible for bilateral execution.

The MAT regime is the **execution-venue mandate** that complements the **clearing mandate** introduced under Dodd-Frank Title VII. Together they brought standardized OTC swaps onto regulated electronic venues.

## Example

A USD 10y vanilla IRS at par swap: MAT — must execute on a SEF, clear at LCH or CME. A USD 12.5y IRS with a custom amortization schedule: not MAT — can execute bilaterally if both parties have ISDA infrastructure.

## Why it matters in an EMS

- The validator must enforce the MAT determination — reject bilateral attempts on MAT products.
- The MAT list is reference data that ships with the [[arch-reference-data-service]].
- See [[rfq-to-3]] for the SEF execution discipline for D2C MAT trades.

## Related

- [[ccp-vs-bilateral]] · [[novation]] · [[fcm]] · [[rfq-to-3]]
- [[bloomberg-sef]] · [[sef-platforms]]
- [[interest-rate-swaps]] · [[credit-default-swaps]]
- [[arch-jurisdictional-compliance]] (Dodd-Frank set)
