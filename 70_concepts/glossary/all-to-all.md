---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# All-to-All Trading

**All-to-all** describes a market structure where **buy-side firms can transact directly with each other** (not just with dealers), typically anonymously, through an intermediating venue. The venue (or its CCP) sits between matched parties so neither sees the other's identity.

Traditionally bond markets were **dealer-intermediated**: every trade involved a dealer principal. Post-2008, dealer balance sheet shrank and buy-side liquidity grew — all-to-all venues emerged so buy-side firms could provide liquidity to each other when dealer pricing was uncompetitive. MarketAxess **Open Trading**, Bloomberg **Bridge**, Trumid (anonymous) are the dominant examples in corporate credit.

Operationally: a buy-side firm posts a quote (or responds to a sent RFQ) anonymously. The venue matches with another buy-side or with a dealer. The intermediating party (the venue itself, often via a clearing leg through DTC / FICC) becomes the legal counterparty so neither side sees the other.

## Example

A buy-side sends an RFQ on a HY corp bond via [[marketaxess]] Open Trading. Three responses arrive: two from dealers, one from another buy-side. The buy-side elects the third response (best price). The trade settles with MarketAxess as the legal intermediary; the two buy-side firms never see each other's name.

## Why it matters in an EMS

- The EMS must handle anonymous counterparty fields and the venue-as-legal-cpty pattern.
- Best-ex selection rationale captures "all-to-all match vs dealer fill" as distinct types.
- [[arch-compliance]] surveillance must treat anonymous all-to-all flow differently from disclosed dealer flow.

## Related

- [[rfq]] · [[clob-vs-rfq]] · [[ioi-vs-rfq]]
- [[marketaxess]] · [[bloomberg-bridge]] · [[trumid]] (all-to-all venues)
- [[arch-rfq]] · [[arch-best-execution]] · [[arch-compliance]]
