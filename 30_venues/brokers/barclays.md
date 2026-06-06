---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Barclays (Equity Routing)

European bulge-bracket sell-side firm. **Barclays Electronic Trading** is the buy-side-facing execution arm; **Barclays LX** is the firm's broker-operated ATS.

> Barclays also operates as a major FI dealer and FX LP — see the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **Power** family — VWAP, TWAP, POV, Close, IS.
- **Hidden** — passive dark posting.
- **Liquidity-Seeker** — opportunistic dark/lit.
- **Stealth** — anti-gaming with adaptive participation.

## DMA

- DMA across US/EU/UK/APAC.
- Colocation across the majors.

## Dark pool

- **Barclays LX** — US broker-operated ATS (subject to past regulatory scrutiny — Barclays settled an SEC/NYAG case in 2016 related to LX representations).
- **Barclays MTF** (EU dark venue).

## Capital commitment

- Block desk active in equity, particularly UK and EU large-cap.

## Central Risk Book (CRB)

- Active CRB across cash and ETF.

## ETF block / RFQ desk

- AP and block-trading presence across EU ETF families.

## Connectivity

- **FIX 4.4 / 5.0**.
- Drop-copy.
- Barclays destination codes per algo.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]]
- [[bloomberg-emsx]] (reachable via EMSX)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints)
