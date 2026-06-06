---
type: venue
venue_kind: exchange
asset_classes: ["equity"]
status: draft
tags: [venue/exchange]
---

# JPX / TSE (Tokyo Stock Exchange)

**Japan Exchange Group (JPX)**'s primary cash-equity venue — Tokyo Stock Exchange. Three market segments (Prime, Standard, Growth) following the 2022 reorganisation. Sister venue: Osaka (futures via OSE), Tokyo Commodity (TOCOM).

## Asset classes

- Japanese-listed equities (Nikkei 225, TOPIX, all Prime/Standard/Growth)
- ETFs (TSE has the largest APAC ETF franchise)
- REITs (J-REITs)

## Workflow mechanisms

- **CLOB** with opening (Itayose) and closing auctions.
- **ToSTNeT** off-exchange crossing for blocks.
- **Continuous trading** with session breaks (morning + afternoon).

## Connectivity

- **arrowhead** — JPX's proprietary low-latency platform.
- **FIX 4.2 / 4.4** for institutional access.
- Market data via JPX feed.

## Key facts

- The largest cash-equity venue in APAC by market cap.
- Session structure (lunch break) differs from US/EU continuous trading and must be modelled in execution algos.
- 2022 market segment reorganization changed liquidity tiers (Prime ≈ old TSE 1st Section).

## Related

- [[cash-equity]]
- [[hkex]] · [[asx]] · [[ksrx]] · [[sgx]] (APAC peers)
- [[bloomberg-tradebook-sg]] (APAC broker-dealer destination) · [[arch-jurisdictional-compliance]] (JFSA)
