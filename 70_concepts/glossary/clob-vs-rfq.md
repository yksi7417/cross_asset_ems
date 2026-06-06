---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# CLOB vs. RFQ

A **CLOB (Central Limit Order Book)** is a continuous order-driven market — buyers and sellers post resting bids/offers; matches happen on price-time priority. An **RFQ (Request-for-Quote)** is a quote-driven workflow — the trader asks a chosen dealer set for a price on a specific size and elects the best response.

CLOBs dominate where the underlying is **homogeneous and continuously traded** (equities, futures, on-the-run Treasuries, EUR/USD spot). RFQs dominate where the underlying is **heterogeneous, illiquid, or block-sized** (most corporate bonds, swaps, FX outrights, ETF blocks, MBS). Many venues run both — Tradeweb has both CLOB (rates) and RFQ (credit), MarketAxess has Live Markets (CLOB) and traditional RFQ.

The buy-side EMS must support both interaction models cleanly — see [[arch-router-layer]] for routing modes and [[arch-rfq]] for the RFQ-as-first-class architecture.

## Example

A trader sells 10M USD 10y on-the-run UST: orders into [[brokertec]] CLOB or aggressively crosses the spread. A trader sells $5M of an off-the-run BBB-rated 7y corp: sends RFQ-to-5-dealers via [[marketaxess]], waits 30 seconds, elects best response.

## Why it matters in an EMS

- Order-type semantics differ (limit order in a CLOB; quote-response in an RFQ).
- Best-ex audit differs (NBBO comparison in CLOB; cover-info comparison in RFQ).
- The router treats them as different `route_mode` values.

## Related

- [[rfq]] · [[rfq-rfs-rfm]] · [[arch-rfq]]
- [[brokertec]] · [[tradeweb]] · [[marketaxess]] · [[nyse]] · [[nasdaq]] (CLOB examples)
- [[bloomberg-bridge]] · [[trumid]] (RFQ + all-to-all hybrids)
