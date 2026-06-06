---
type: venue
venue_kind: ats
asset_classes: ["equity"]
status: draft
tags: [venue/ats]
---

# IEX (Investors Exchange)

**IEX** is a US national securities exchange differentiated by a **350-microsecond speed bump (Magic Shoebox)** at its access points, designed to neutralize latency-arbitrage strategies. SEC-registered as a national securities exchange.

## Asset classes

- US-listed equities (all NMS securities)
- Selected ETFs

## Workflow mechanisms

- **CLOB** with the IEX speed bump on both inbound and outbound.
- **D-Peg** — proprietary discretionary peg order type that uses IEX's signal to avoid trading against adverse selection.
- **CQS / UTP NBBO participation** with the speed-bump-adjusted view.

## Connectivity

- **FIX 4.2 / 4.4** for order entry.
- **IEX-specific binary** for low-latency.
- Market data via IEX TOPS / DEEP feeds.

## Key facts

- Speed bump is a structural anti-HFT-adverse-selection mechanism.
- D-Peg is the most-cited proprietary order type in modern algo design.
- Founded post-2010 in response to flash-boy concerns.

## Related

- [[cash-equity]]
- [[nyse]] · [[nasdaq]] · [[cboe-bzx]] · [[memx]] (US peers)
- [[arch-smart-order-router]] (IEX requires speed-bump-aware routing logic)
