---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg TBILL<GO>

**TBILL<GO>** is a Bloomberg Terminal monitor screen for **US Treasury bills** — dealer-indicative bid/ask, yield, discount, and auction schedule by maturity.

> **This is a Bloomberg Terminal monitor screen, not a routable execution destination.** A trader views the T-bill complex here; execution routes to a real venue.

## What it shows

- Outstanding T-bill issues by maturity (4-week through 52-week).
- Dealer-streamed bid/ask, yield, discount rate.
- Upcoming auction schedule (announce / auction / settle dates).
- WI (when-issued) trading data.

## Where execution actually happens

| Stage | Routable destination |
|---|---|
| Primary auction | [[treasury-direct]] (direct / non-competitive); primary dealers via [[arch-venue-connectivity]] direct line to NY Fed FedTrade |
| Secondary on-the-run | [[brokertec]] (interdealer), [[tradeweb]] D2C |
| Secondary off-the-run | [[tradeweb]] D2C RFQ, [[marketaxess]] |

## Related

- [[bloomberg-btmm]] (parent money-markets monitor)
- [[treasury-direct]] · [[brokertec]] · [[tradeweb]] · [[marketaxess]] (real destinations)
- [[money-market-tbills]] · [[govt-bonds]] (asset classes)
