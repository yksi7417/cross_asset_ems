---
type: venue
venue_kind: exchange
asset_classes: ["equity"]
status: draft
tags: [venue/exchange]
---

# Nasdaq

**Nasdaq Stock Market** — the primary US listing venue for tech, growth, and high-volume issuers. Pure CLOB with no DMM (in contrast to NYSE). Also operates Nasdaq BX and Nasdaq PSX as sister exchanges with different tier discipline.

## Asset classes

- US-listed equities (Nasdaq-listed names)
- ETFs (some listings)
- Equity options (Nasdaq Options Market, Nasdaq PHLX, Nasdaq BX Options, Nasdaq ISE)

## Workflow mechanisms

- **CLOB** with price-time priority.
- **Opening / Closing crosses** (auctions) — the closing cross is one of the larger US liquidity events.
- **Imbalance feed** during cross periods is heavily consumed by execution algos.

## Connectivity

- **OUCH** — proprietary low-latency binary order entry protocol.
- **ITCH** — proprietary market data multicast.
- **FIX 4.2 / 4.4** for slower / institutional.
- Drop-copy and post-trade via FIX.

## Key facts

- Largest US options-exchange operator (six sister venues).
- Closing cross discipline differs from NYSE Arca / NYSE in ways algos must model.
- Ownership of multiple venues lets Nasdaq run different tier discipline experiments.

## Related

- [[cash-equity]] · [[equity-options]]
- [[nyse]] · [[cboe-bzx]] · [[iex]] · [[memx]] (US peers)
- [[arch-smart-order-router]] (Reg-NMS routing) · [[arch-realtime-analytics]]
