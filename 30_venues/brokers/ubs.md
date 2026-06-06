---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# UBS (Equity Routing)

European bulge-bracket sell-side firm (now incorporating Credit Suisse post-2023 acquisition). **UBS Equities Direct Execution** is the electronic execution arm; **UBS PIN ATS** (now UBS Cross) is the firm's broker-operated ATS.

> UBS also operates as a major FI dealer and FX LP — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **UBS Tap** — opportunistic / dark-seek.
- **UBS Hunter** — liquidity-detection.
- **VWAP / TWAP / POV** with EU MiFID II RTS 6 algo flagging.
- **Close** — close-targeted.
- Credit Suisse legacy algos (AES family — Crossfinder, etc.) being integrated post-merger.

## DMA

- DMA to US, EU, UK, APAC exchanges.
- Colocation across major centers.

## Dark pool

- **UBS PIN (Cross)** — US broker-operated ATS, historically top-3 by share.
- **UBS MTF** (EU/UK MiFID II venue).

## Capital commitment

- Block-trading desk active in EU and Swiss markets particularly.
- Strong franchise in Swiss listings.

## Central Risk Book (CRB)

- Active CRB across cash and options.

## ETF block / RFQ desk

- Significant EU and UK ETF block presence.

## Connectivity

- **FIX 4.4 / 5.0** for order entry, executions, allocations.
- Drop-copy via FIX.
- UBS destination codes documented per algo.

## Key facts

- 2023 Credit Suisse acquisition consolidated UBS's franchise; AES (CS) algos being merged into UBS strategy suite.
- Strong in EU/Swiss market structure.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[memx]] (UBS is a founding member)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
