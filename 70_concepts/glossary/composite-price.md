---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# Composite Price (CBBT / BVAL / CP+ etc.)

A **composite price** is a synthetic mid (or bid/offer) constructed from **multiple dealer-streamed quotes** for a single fixed-income instrument — used as the indicative reference price where no single venue carries continuous two-way liquidity.

Major composites: **CBBT** (Composite Bloomberg Bond Trader — uses dealer-contributed quotes), **BVAL** (Bloomberg Valuation Service — model + observable trades), **CP+** (MarketAxess composite from its dealer stream + Open Trading prints), **ICE Continuous Evaluated Pricing**, IDC. Each provider has its own methodology and timing.

Composites serve three roles in an EMS: (1) the **reference price** for fat-finger and pre-trade compliance — see [[arch-compliance]]; (2) the **benchmark** for TCA in markets without a tradeable NBBO; (3) the **mid** for limit-vs-mid validation in the [[arch-validator]].

## Example

A buy-side trader staging a sell of a BBB-rated 7y corp at 98.00 — the CBBT shows 98.20 / 98.10 / 98.00 from three dealers, mid composite 98.10. The validator passes (within 1% of mid); compliance fat-finger passes (within band). Without a composite, neither check has a reference.

## Why it matters in an EMS

- Composite-pricing dependencies are first-class — the system has a fallback chain (CBBT → BVAL → last-trade → manual) per [[arch-pricing-service]].
- Per-bond licensing of CBBT / BVAL is metered — see [[arch-symbology-figi]] for the licensed-data-access pattern.
- TCA against composite mid is the only feasible benchmark in many FI markets.

## Related

- [[arch-pricing-service]] · [[arch-realtime-analytics]] · [[arch-tca]]
- [[bloomberg-allq]] (the terminal screen that displays CBBT) · [[bloomberg-fit]]
- [[arch-compliance]] (fat-finger ref price) · [[arch-validator]]
