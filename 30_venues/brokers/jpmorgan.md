---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# JPMorgan (Equity Routing)

Bulge-bracket sell-side firm. **JPMorgan Electronic Client Solutions (JPM ECS)** is the buy-side-facing execution arm; **JPMX** is the firm's broker-operated ATS.

> JPMorgan also operates as a major FI dealer, FX LP, prime broker, and tri-party repo agent — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **AQUA** — liquidity-seeking with anti-gaming.
- **JET** — fast-execution / IS-style.
- **Open Cross** — close-targeted with full-day book-building.
- **VWAP / TWAP / POV / Arrival** — standard benchmarks.
- **Dark Sweep** — broker-dark targeting.

## DMA

- DMA across US/EU/UK/APAC.
- Colocation across major financial centers.

## Dark pool

- **JPMX** — US broker-operated ATS, top-5 by share.
- **JPM EU Dark** (MiFID II venue).

## Capital commitment

- Active block trading; one of the larger equity capital franchises post-2008 consolidation.

## Central Risk Book (CRB)

- Material CRB operation across cash and listed derivatives.

## ETF block / RFQ desk

- Top-tier ETF block presence as AP for major ETF families.

## Connectivity

- **FIX 4.4 / 5.0**.
- Drop-copy.
- JPM destination codes per algo.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[memx]] (JPM is a founding member)
- [[triparty-bnym-jpm]] (JPM as tri-party agent — distinct from equity broker role)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
