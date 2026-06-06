---
type: venue
venue_kind: exchange
asset_classes: ["equity"]
status: draft
tags: [venue/exchange]
---

# HKEX (Hong Kong Exchanges)

**Hong Kong Exchanges and Clearing** — primary cash-equity venue for Hong Kong listings (Hang Seng, H-shares, red chips). Also operates HK derivatives and the **Stock Connect** linkages with Shanghai and Shenzhen for cross-border China-A-share access.

## Asset classes

- HK-listed equities (Main Board, GEM)
- China-A shares (via Stock Connect)
- ETFs and inverse / leveraged products
- ADRs / REITs (HK-listed)
- HK derivatives (separate platform — HKEX Derivatives Market)

## Workflow mechanisms

- **CLOB** with opening / closing auctions.
- **Stock Connect Northbound** for foreign access to Shanghai / Shenzhen.
- **Stock Connect Southbound** for Mainland access to HK.
- **Closing Auction Session** with random end time (anti-gaming).

## Connectivity

- **OCG (Orion Central Gateway)** — proprietary order entry.
- **FIX 4.2 / 4.4** for institutional.
- Market data via HKEX OMD feed.

## Key facts

- Stock Connect makes HKEX the de-facto routing hub for foreign access to A-shares.
- Closing-auction random-end time is a model-relevant detail for execution algos.
- Major listing venue for Mainland China issuers (H-share dual listings).

## Related

- [[cash-equity]]
- [[jpx-tse]] · [[sgx]] · [[asx]] (APAC peers)
- [[bloomberg-tradebook-sg]] (APAC broker-dealer destination)
- [[arch-jurisdictional-compliance]] (HK SFC, Stock Connect rules)
