---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# Clean vs. Dirty Price

The **clean price** of a bond is the quoted price **excluding accrued interest** — the convention price you see on screens and in negotiation. The **dirty price** is the clean price PLUS accrued interest — the actual cash amount that changes hands at settlement.

Accrued interest accumulates **linearly between coupon dates** (per the bond's day-count convention — Actual/Actual, 30/360, etc.). On a coupon date, accrued resets to zero. The day-after-coupon dirty price equals the clean price; halfway between coupons the dirty exceeds the clean by half-a-coupon worth.

The convention exists because **clean prices are stable across coupon resets** — without it, a bond's screen price would jump down by the coupon amount on every coupon date, masking actual price moves.

## Example

A 5% UST trading at clean price 100.00 with 60 days of accrued (on a 180-day Actual/Actual semi-annual coupon basis) has accrued of 5.00 × (60/360) = 0.833. Dirty price = 100.833. Settlement cash on a 1M face position = 1,008,333.

## Why it matters in an EMS

- The validator must distinguish clean vs dirty when comparing trade prices.
- Settlement instructions and cash projections use dirty (actual cash).
- Accrued interest formulas vary by jurisdiction and instrument — see [[arch-reference-data-service]] for day-count conventions.

## Related

- [[govt-bonds]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[municipal-bonds]]
- [[arch-pricing-service]] · [[arch-reference-data-service]] (day-count)
- [[arch-stp-pipeline]] (post-trade cash projection)
