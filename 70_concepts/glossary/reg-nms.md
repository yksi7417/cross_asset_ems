---
type: concept
status: draft
tags: [concept/glossary, glossary/equity, glossary/regulatory]
---

# Reg NMS

**Regulation NMS** (Regulation National Market System) is the SEC's 2005 rulemaking that **prevents trade-throughs across the US national market system**. Its **Rule 611 (Order Protection Rule)** mandates that brokers route or execute marketable orders against the **NBBO-protected quotes** at lit exchanges before trading through them.

Reg NMS established the modern fragmented US equity market structure: many lit venues compete on fee/rebate discipline, but all must contribute to the SIP-disseminated NBBO and honour each others' top-of-book quotes. **ATSs (dark pools)** are exempt from Rule 611 only when matching at the midpoint or better than the NBBO.

Other key components: Rule 605/606 (broker execution-quality reporting), Rule 610 (access fees capped at 30 mils), Rule 612 (sub-penny pricing rules). The "Tick Size Pilot" and ongoing Market Data Infrastructure rule are the modern extensions.

## Example

A broker receives a marketable buy order at $145.20 for AAPL. The NBBO offer is $145.14 (IEX, 100 shares). The broker must hit IEX's 100 at $145.14 first — they cannot route to a venue with an inferior offer (e.g. Nasdaq at $145.15) just because of fee/rebate considerations. Once IEX's protected 100 is consumed, the broker may route elsewhere.

## Why it matters in an EMS

- The SOR ([[arch-smart-order-router]]) must enforce Reg-NMS-compliant routing as a hard constraint.
- 605/606 broker execution reports are produced by the [[arch-best-execution]] component.
- Audit must demonstrate Reg-NMS compliance per trade.

## Related

- [[nbbo-ebbo]] · [[lit-vs-dark]] · [[ats-ecn-mtf]]
- [[arch-smart-order-router]] · [[arch-best-execution]] · [[arch-jurisdictional-compliance]]
- [[nyse]] · [[nasdaq]] · [[cboe-bzx]] · [[iex]] · [[memx]]
