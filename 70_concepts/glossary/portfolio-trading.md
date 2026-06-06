---
type: concept
status: draft
tags: [concept/glossary, glossary/fi, glossary/equity]
---

# Portfolio Trading

**Portfolio Trading (PT)** — execution of a **basket of securities as a single ticket** with a single price (or risk-pricing) negotiated against a dealer. The dealer commits capital to the entire basket and unwinds the residual risk afterward.

Heavily used in **US IG corporate credit** (where line-by-line execution would be slow and information-leaky) and in **equity program trading**. The dealer prices the basket against a risk model (correlation, sector exposure, residual delta), often referencing the largest ETF in that segment (e.g. LQD for IG) as an implicit hedge.

Mechanics: the buy-side sends the full basket to one or several dealers (often via a portfolio-trading platform — Tradeweb PT, MarketAxess PT, Trumid PT for credit; broker programs for equity); each dealer prices the whole list; buy-side elects the winner. Allocation back to the underlying accounts happens after the parent fills.

## Example

A buy-side asset manager rebalancing into a new IG benchmark sends a 350-line basket worth $250M to three dealers via Tradeweb PT. Each dealer prices the basket as a single number (e.g. "lift +2.5bp vs CBBT composite"). The asset manager elects, then the dealer hedges with LQD shorts and works out of the residual over a day.

## Why it matters in an EMS

- The aggregation layer ([[arch-aggregation]]) is the architectural primitive — N child orders execute as one parent.
- ETF reference pricing must be available to dealers for credit PT — see [[arch-pricing-service]].
- Best-ex audit is per-basket, not per-line.

## Related

- [[arch-aggregation]] · [[arch-bulk-io]] (basket ingest)
- [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[cash-equity]] (program trading)
- [[tradeweb]] · [[marketaxess]] · [[trumid]] (PT venues) · [[_brokers-overview|Brokers]] (equity program desks)
