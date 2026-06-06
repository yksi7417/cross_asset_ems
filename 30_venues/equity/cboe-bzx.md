---
type: venue
venue_kind: exchange
asset_classes: ["equity"]
status: draft
tags: [venue/exchange]
---

# Cboe BZX (and Cboe family)

**Cboe Global Markets** operates four US equity exchanges — **BZX, BYX, EDGX, EDGA** — each with different fee/rebate discipline targeting different participant strategies. Plus Cboe Global Markets' equity options venues (Cboe Options, C2, EDGX Options).

## Asset classes

- US-listed equities (trading; minimal listings)
- ETFs (significant share of ETF lit-market volume)
- Equity options (Cboe is the largest US options venue family)
- Index options (SPX, VIX — Cboe-proprietary)

## Workflow mechanisms

- **CLOB** with venue-specific maker-taker / taker-maker discipline.
- **Cboe Volume** discount tier structures incentivize specific participant types.
- **Closing cross** participation via Cboe Auctions.

## Connectivity

- **BOE (Binary Order Entry)** — Cboe's proprietary low-latency protocol.
- **FIX 4.2 / 4.4** for institutional.
- Market data via Cboe One feed.

## Key facts

- Four equity venues let Cboe segment flow by participant type without changing legal venue.
- Dominant US options franchise (SPX / VIX are exclusive).
- ETF listings increasingly migrating to Cboe BZX.

## Related

- [[cash-equity]] · [[equity-options]]
- [[nyse]] · [[nasdaq]] · [[iex]] · [[memx]] (US peers)
- [[arch-smart-order-router]] (Reg-NMS routing across the four Cboe venues + NYSE/Nasdaq)
