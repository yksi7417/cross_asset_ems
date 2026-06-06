---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Morgan Stanley (Equity Routing)

Bulge-bracket sell-side firm. **MSET (Morgan Stanley Electronic Trading)** is the buy-side-facing equity execution arm; **MS Pool** is the firm's broker-operated ATS.

> Morgan Stanley also operates as a major FI dealer and FX LP — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **BLINK** — opportunistic / liquidity-seeking.
- **NIGHT OWL** — close-targeted.
- **EDGE** — VWAP / TWAP / POV with anti-gaming overlays.
- **Stealth** — passive dark posting.
- Custom configurations via FIX strategy tags.

## DMA

- DMA pass-through to US exchanges + EU/APAC venues.
- Low-latency colocation across the major financial centers.

## Dark pool

- **MS Pool** — US broker-operated ATS, top-5 broker pool by share.
- **MS Pool Europe** (post-Brexit MTF).
- **MS Pool Asia** (HK/SG/JP venues).

## Capital commitment

- Block-trading desk active in high-touch and program trading.
- Strong franchise in equity-linked / convert blocks.

## Central Risk Book (CRB)

- Active CRB operation across cash, options, and ETFs — internalization material to execution outcome.

## ETF block / RFQ desk

- Strong ETF block desk; significant AP role across major ETF families.

## Connectivity

- **FIX 4.4 / 5.0** for order entry, ExecutionReports, allocations.
- Drop-copy via FIX.
- MSET destination codes documented per algo.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[memx]] (Morgan Stanley is a founding member)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
