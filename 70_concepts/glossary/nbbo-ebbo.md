---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# NBBO / EBBO

The **NBBO** (National Best Bid and Offer) is the **best lit bid and best lit offer across all US regulated equity venues** at a moment in time, computed and disseminated by the SIPs (Securities Information Processors). The **EBBO** (European Best Bid and Offer) is the equivalent across European regulated venues, though Europe lacks a single mandatory consolidated tape so EBBO is constructed by data vendors.

Reg-NMS (US) requires that **protected quotes** at the NBBO be honoured — a broker can't trade through a better-priced quote on another lit venue without filling at least that quote first. This rule shapes US smart-order routing: every aggressive order must hit the NBBO-protected quote(s) before going elsewhere.

EBBO has no equivalent legal protection — MiFID II requires best execution but not NBBO-style trade-through prevention. As a result, EU SOR routing has more discretion.

## Example

Apple shows the following lit quotes: NYSE 145.10 / 145.15; Nasdaq 145.12 / 145.15; IEX 145.10 / 145.14. NBBO is 145.12 (Nasdaq) / 145.14 (IEX). A market sell of 1000 shares must first hit the Nasdaq 145.12 bid (NBBO bid) before sweeping lower venues.

## Why it matters in an EMS

- Reg-NMS-compliant routing is a first-class feature of the [[arch-smart-order-router]].
- The [[arch-realtime-analytics]] service publishes NBBO/EBBO as a tick feed for algos and compliance.
- TCA benchmarks against arrival NBBO are standard for US equity.

## Related

- [[reg-nms]] · [[lit-vs-dark]] · [[clob-vs-rfq]]
- [[nyse]] · [[nasdaq]] · [[cboe-bzx]] · [[iex]] · [[memx]]
- [[arch-smart-order-router]] · [[arch-realtime-analytics]] · [[arch-tca]]
