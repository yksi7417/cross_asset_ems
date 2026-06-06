---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Capital Commitment

**Capital commitment** is when a **broker takes the other side of a client trade onto its own balance sheet** rather than passing the trade through to an external venue (agency execution). The broker absorbs the risk and works out of the position over time.

Capital commitment is the **traditional block-trade mechanism**: a client wants to sell 500K shares at a guaranteed price, the broker pays for the whole block at a negotiated price (slightly worse than mid), and the broker carries the inventory risk while working out. The broker charges a **risk premium** baked into the price.

Modern broker offerings often combine capital commitment with a **Central Risk Book** ([[central-risk-book]]) — the broker's flow desk aggregates client risk against a centrally-managed book, allowing better economics than naked agency execution while still providing the size guarantee.

## Example

A buy-side trader wants to sell 800K shares of a mid-cap immediately at the close. Agency routing would take 30 minutes and move the price. Goldman commits capital at a 30 bp discount to last trade for the entire block; Goldman now owns 800K shares to work out of over the next session. The client got immediacy and certainty.

## Why it matters in an EMS

- The EMS must support principal vs agency tagging on every trade.
- Best-ex audit for capital-commitment trades benchmarks vs market mid + risk premium.
- Compliance / surveillance must distinguish principal from agency for [[arch-surveillance|insider/conflict detection]].

## Related

- [[agency-vs-principal]] · [[central-risk-book]]
- [[_brokers-overview]] · [[goldman-sachs]] · [[morgan-stanley]] · [[jpmorgan]] (capital-commitment franchises)
- [[arch-best-execution]] · [[arch-surveillance]]
