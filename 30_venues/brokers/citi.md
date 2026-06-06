---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Citi (Equity Routing)

Bulge-bracket sell-side firm. **Citi Velocity Equities** is the electronic execution arm; **Citi Match** is the firm's broker-operated ATS.

> Citi also operates as a major FI dealer, FX LP, and prime broker — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **Dagger** — liquidity-detection with anti-gaming.
- **Smart Click** — opportunistic dark/lit routing.
- **VWAP / TWAP / POV** — standard benchmarks.
- **Liquidity-Seeking** — dark-first opportunistic.
- **Volume Inline** — POV with adaptive participation.

## DMA

- DMA across US/EU/UK/APAC.
- Colocation across the majors.

## Dark pool

- **Citi Match** — US broker-operated ATS.
- **Citi LX (CitiCross)** — EU dark venue (MTF).

## Capital commitment

- Active block desk, particularly strong in EM and global program trading.

## Central Risk Book (CRB)

- Active CRB across cash and derivatives.

## ETF block / RFQ desk

- ETF block presence; AP across major US/EU ETF families.

## Connectivity

- **FIX 4.4 / 5.0**.
- Drop-copy.
- Citi destination codes per algo.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
