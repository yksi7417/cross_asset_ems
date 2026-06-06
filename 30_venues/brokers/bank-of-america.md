---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Bank of America (Equity Routing)

Bulge-bracket sell-side firm. **BofA Merrill Lynch Global Markets** is the broker-of-record for equity execution; **Instinct X** is the firm's broker-operated ATS.

> Bank of America also operates as a major FI dealer and FX LP — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **Quant Strategies** family — VWAP, TWAP, POV, IS, Close-targeted.
- **Implementation Shortfall (IS)** with proprietary impact model.
- **Dark Aggregator** — broker-dark + external-dark routing.
- **Hunt** — opportunistic / liquidity-detection.

## DMA

- DMA across US/EU/UK/APAC.
- Colocation across the majors.

## Dark pool

- **Instinct X** — US broker-operated ATS.

## Capital commitment

- Block desk active across cash and ETF; strong in US large-cap program trading.

## Central Risk Book (CRB)

- Active CRB across cash equities.

## ETF block / RFQ desk

- AP presence across the major US ETF families.

## Connectivity

- **FIX 4.4 / 5.0**.
- Drop-copy.
- BAML destination codes per algo.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[memx]] (BAML is a founding member)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
