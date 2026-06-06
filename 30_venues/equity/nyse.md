---
type: venue
venue_kind: exchange
asset_classes: ["equity"]
status: draft
tags: [venue/exchange]
---

# NYSE (Intercontinental Exchange)

ICE-owned **New York Stock Exchange** — the primary US listing exchange for the largest issuers, plus three sister exchanges (NYSE Arca, NYSE American, NYSE Chicago) covering ETFs, options, and small-cap.

## Asset classes

- US-listed equities (NYSE-listed names)
- ETFs (NYSE Arca is the dominant ETF listing venue)
- Equity options (NYSE Arca Options + NYSE American Options)

## Workflow mechanisms

- **CLOB** with DMM (Designated Market Maker) liquidity provision at the open / close auctions.
- **Reg-NMS-protected** quotes participate in the US national NBBO.
- **MOC / LOC** (market-on-close / limit-on-close) auctions are material liquidity events.

## Connectivity

- **NYSE Pillar** — proprietary binary protocol for low-latency.
- **FIX 4.2 / 4.4** for slower / institutional access.
- **NYSE Integrated Feed / OpenBook** for market data.
- Drop-copy via FIX or Pillar.

## Key facts

- Largest US equity listing venue by market cap of listings.
- DMM model differs from Nasdaq's pure ECN structure.
- Closing auction is the largest single liquidity event of the US session.

## Related

- [[cash-equity]]
- [[nasdaq]] · [[cboe-bzx]] · [[iex]] · [[memx]] (US exchange peers)
- [[goldman-sachs]] · [[morgan-stanley]] (DMM firms; broker routing destinations)
- [[arch-smart-order-router]] · [[arch-realtime-analytics]] (NBBO)
