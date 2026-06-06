---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# DV01 and Duration

**DV01** ("Dollar Value of 1 basis point") is the **dollar P&L change for a 1 bp parallel shift in the yield curve** on a bond, swap, or portfolio. **Duration** is the closely-related percentage measure (often "modified duration") — the percent price change for a 100 bp shift, by convention.

DV01 is the everyday risk metric on the fixed-income / rates desk because it's additive across positions, currencies, and instruments — a portfolio's DV01 is the sum of its parts. **Key-rate DV01** decomposes the total into per-tenor exposures (2y, 5y, 10y, 30y DV01) so hedgers know which bucket to neutralize.

For credit, **spread DV01** (sometimes "spread duration" or "CR01") is the analogue — P&L for a 1 bp spread move, holding the underlying rate curve constant. For CDS, **DV01** typically means the par-spread variant; for IRS, fixed-leg DV01.

## Example

A USD 10y UST 4% coupon at par has DV01 ≈ $818 per $1M face. A position of $100M has DV01 ≈ $81,800 — for a 1 bp rates rally, you lose $81,800.

## Why it matters in an EMS

- The risk engine ([[arch-risk-engine]]) caps positions by DV01, key-rate DV01, and spread DV01.
- Pre-trade risk checks compare proposed-trade DV01 against limits.
- TCA benchmarks for rates trades often use DV01-equivalent comparisons.

## Related

- [[arch-risk-engine]] · [[arch-position-service]]
- [[govt-bonds]] · [[corp-bonds-ig]] · [[interest-rate-swaps]] · [[credit-default-swaps]]
