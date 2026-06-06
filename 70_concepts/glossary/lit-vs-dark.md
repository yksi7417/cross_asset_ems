---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Lit vs. Dark Markets

A **lit market** is a venue where **the order book is publicly visible** — bid, offer, depth all displayed pre-trade. A **dark market** (dark pool, dark venue) is one where **pre-trade quotes are not displayed**; orders rest hidden until they match.

Lit venues (NYSE, Nasdaq, Cboe, LSE, Xetra) contribute to NBBO / EBBO and bear price-discovery responsibility. Dark venues (ATSs, broker dark pools, MTFs' dark books) exist because **large institutional orders fear information leakage** — displaying a 500K-share sell order will move the market against the seller before they execute.

Dark venues typically **match at the lit NBBO midpoint** or better — that's the regulatory carveout that lets them not display quotes. In EU MiFID II, the **double volume cap** restricts the amount that can trade in dark pools at certain reference-price waivers, pushing some flow to **periodic auctions** as a workaround.

## Example

A buy-side trader has a 250K-share sell of AAPL. On lit venues (NBBO 145.12 / 145.14), displaying that size would crater the price. Instead, the trader posts at midpoint (145.13) in 5 dark pools simultaneously. As contras arrive, fills happen invisibly at 145.13. Total impact: minimal vs. ~50bp had they displayed.

## Why it matters in an EMS

- Lit vs dark routing is a strategic decision in [[arch-smart-order-router]].
- Broker dark pools (Sigma X, MS Pool, JPMX, etc. — see [[_brokers-overview]]) are part of the broker offering.
- TCA must distinguish lit and dark fills for benchmarking.

## Related

- [[nbbo-ebbo]] · [[reg-nms]] · [[ats-ecn-mtf]]
- [[midpoint-cross]] · [[systematic-internaliser]]
- [[goldman-sachs]] · [[morgan-stanley]] · [[ubs]] (broker dark pools)
- [[arch-smart-order-router]] · [[arch-best-execution]]
