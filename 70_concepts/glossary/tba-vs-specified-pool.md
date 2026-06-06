---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# TBA vs. Specified Pool

**TBA (To-Be-Announced)** is the forward agency-MBS market where you trade by **coupon / agency / settlement month** without naming the specific underlying pools — the seller delivers any conforming pools on settlement date per SIFMA Good Delivery rules. **Specified pool** is the opposite: the trade names exact pool numbers with their specific characteristics (FICO, LTV, loan size, geography, prepay speed).

TBA is **massively liquid** (the second-largest fixed-income market after Treasuries) and **homogeneous** — it works because deliverable pools within a coupon/agency are reasonably interchangeable. Specified-pool trades exist because pools with **non-generic prepay profiles** (low loan balance, high FICO, NY/CA concentration) trade at a **pay-up** to TBA — the buyer pays extra for predictable prepay behavior.

Settlement dates follow SIFMA's monthly Class A/B/C/D schedule. **Dollar rolls** (selling near-month TBA and buying next-month TBA) are a financing transaction off the TBA framework — see [[dollar-roll]].

## Example

Selling "30-year Fannie 5.5% July settlement" is a TBA trade — settles via [[bloomberg-tba]] / [[tradeweb]] / dealer-direct, delivered against any conforming pool. Selling "FN MA4567" (a specific pool with 75 FICO 800+ loans in TX) is a specified-pool trade with a pay-up over TBA.

## Why it matters in an EMS

- Symbology differs: TBA tickers (FNCL 5.5 JUL) vs. specific pool CUSIPs.
- Settlement-date discipline is rigid (one Class A/B/C/D date per month per category).
- Dollar rolls are a two-legged trade and the EMS must model them paired.

## Related

- [[mbs]] · [[bloomberg-tba]] · [[tradeweb]] · [[sifma-tba-guidelines]]
- [[dollar-roll]] · [[wac-wam-wala]] (pool characteristics that drive pay-ups)
- [[arch-confirmation-affirmation]] · [[dtcc]] (MBS settlement)
