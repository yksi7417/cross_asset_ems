---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# Two-Way vs. One-Way Markets

A **two-way market** has a continuously-quoted bid AND offer — anyone can lift the offer or hit the bid at any moment. A **one-way market** is one where dealers only show **one side** at a given time (often the side away from their current axe), and the other side requires asking.

Examples of two-way markets: lit equity exchanges, on-the-run UST CLOB, EUR/USD spot ECN. Examples of one-way markets: most off-the-run corporate bonds (dealers show offers but not bids on hard-to-source bonds), illiquid munis, distressed credit.

The buy-side EMS workflow differs accordingly. In two-way markets you can route to a CLOB and expect either side to fill. In one-way markets you typically send an **RFQ** because there's no continuously-quoted contra. Some workflows are **two-way RFQ** — the buy-side asks for both sides without revealing direction, so the dealer can't mark up the side they think you want.

## Example

In US IG corp: ALLQ shows offers from dealers because dealers are long the bond and want to sell. To buy, you click an offer. To sell, you must RFQ — no continuous bid exists. In on-the-run UST: BrokerTec shows both bid and offer continuously.

## Why it matters in an EMS

- Order types available depend: market-orders only make sense in two-way markets.
- The validator (see [[arch-validator]]) rejects market orders for one-way instruments.
- TCA benchmarks differ — arrival mid is well-defined in two-way; in one-way it's an estimation.

## Related

- [[clob-vs-rfq]] · [[rfq]]
- [[govt-bonds]] (two-way) · [[corp-bonds-hy]] · [[whole-loans]] (one-way)
- [[arch-validator]] · [[arch-realtime-analytics]] (mid estimation)
