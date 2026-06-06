---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# Accrued Interest

**Accrued interest** is the **portion of the next coupon a bondholder has "earned" but not yet been paid**, accumulated linearly from the last coupon date to the settlement date. The seller of a bond receives the accrued from the buyer at settlement to compensate for the holding period.

Accrual rules are governed by the bond's **day-count convention**: Actual/Actual (most US Treasuries), 30/360 (most US corporates), Actual/360 (most money-market), 30E/360, Actual/365 (some Gilt). Each combines with the coupon frequency (semi-annual, annual, quarterly) to give a specific formula.

A bond traded at clean price 100, with accrued of 1.25, settles for dirty price 101.25 per 100 face. The accrued goes to the seller; the buyer "buys" the right to the full upcoming coupon and is even.

## Example

A 5% semi-annual UST 30/360, sold 60 days into a 180-day coupon period: accrued = 5.00 × (60/180)/2 = 0.833 per 100 face. On 1M face, settlement cash includes the 8,333 of accrued on top of the clean-price principal.

## Why it matters in an EMS

- Cash projections in [[arch-stp-pipeline]] must include accrued.
- Day-count conventions per instrument are reference data — see [[arch-reference-data-service]].
- The validator distinguishes price vs price+accrued when comparing trade levels.

## Related

- [[clean-vs-dirty-price]] · [[govt-bonds]] · [[corp-bonds-ig]] · [[municipal-bonds]]
- [[arch-reference-data-service]] (day-count) · [[arch-stp-pipeline]]
