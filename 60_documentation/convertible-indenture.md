---
type: documentation
status: draft
tags: [documentation]
---

# Convertible Bond Indenture

The **indenture** for a [[convertibles|convertible bond]] is the bond's foundational legal document — defines the conversion mechanics, call schedules, put dates, anti-dilution adjustments, change-of-control provisions, and ranking. Unique per issue (not standardized like ISDA / GMRA).

## Where it is required

- Every [[convertibles|convertible bond]] issue has its own bespoke indenture.

## Key terms

### Conversion mechanics
- **Conversion ratio**: how many shares per $1000 of par.
- **Conversion price** = $1000 / ratio.
- **Conversion premium**: how much above current stock price.
- **Mandatory conversion** vs **optional conversion** at maturity.

### Call schedule
- Issuer's right to call the bond.
- Soft call: triggered only above a stock price threshold.
- Hard call: unconditional.
- Call protection: period during which calls are forbidden.

### Put dates
- Holder's right to put the bond at par (or above) on specific dates.
- Put prices often define the bond's effective tenor.

### Anti-dilution adjustments
- Stock splits / reverse splits adjust conversion ratio.
- Dividend pass-through / special distribution adjustments.
- M&A / spin-offs trigger conversion adjustments.

### Change-of-control puts
- Holder can put if change-of-control occurs at a specified premium.

### Ranking
- Subordinated to senior debt typically.
- Pari passu with other unsecured.

## EMS implications

- Per-bond indenture data (conversion ratio, call/put schedule) is reference data in [[arch-reference-data-service]].
- Conversion option valuation depends on indenture terms — [[arch-pricing-service]].
- Mandatory events (calls, puts, conversions) drive [[arch-corporate-actions]] processing.

## Related

- [[convertibles]] · [[cash-equity]] (underlying)
- [[arch-pricing-service]] · [[arch-corporate-actions]] · [[arch-reference-data-service]]
- [[isda]] (sister doc for OTC equity derivatives that may hedge convertibles)
