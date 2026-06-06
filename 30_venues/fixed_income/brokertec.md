---
type: venue
venue_kind: interdealer
asset_classes: ["fixed_income"]
status: draft
tags: [venue/interdealer]
---

# BrokerTec

CME Group's electronic interdealer (D2D) platform for **US Treasuries**, **US repo (GCF + tri-party + bilateral)**, **EU govt bonds**, and **EU repo**. Operates a central limit order book — the closest thing to a "futures exchange" for cash treasuries.

## Asset classes

- US Treasury cash (on-the-run dominant; off-the-run growing)
- US Repo (GCF FICC-cleared; tri-party; bilateral)
- EU govt bonds (Bund, BTP, OAT, Bonos)
- EU Repo (GC and specials, EUR-denominated)

## Workflow mechanisms

- **CLOB** — order-driven matching with iceberg / hidden / pegged order types.
- Price-time priority with size-tier breakers for specific instruments.
- Interdealer-only counterparty model (primary dealers and major bank dealers).

## Connectivity

- **FIX 4.2 / 4.4** for order entry, modify, cancel, mass-cancel.
- **iLink (CME proprietary binary)** for low-latency access.
- Drop-copy via FIX.

## Key facts

- Dominant venue for on-the-run UST interdealer flow.
- GCF Repo on BrokerTec is FICC-cleared — important for capital efficiency.
- CME ownership integrates BrokerTec into the broader CME futures complex (basis trades).

## Related

- [[govt-bonds]] · [[money-market-repo]] · [[money-market-tbills]]
- [[treasury-direct]] (primary auction) · [[tradeweb]] (D2C parallel)
- [[arch-smart-order-router]] (CLOB strategies) · [[arch-realtime-analytics]] (NBBO from CLOB)
