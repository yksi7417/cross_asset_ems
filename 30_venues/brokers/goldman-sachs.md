---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Goldman Sachs (Equity Routing)

Bulge-bracket sell-side firm. **Goldman Sachs Electronic Trading (GSET)** is the buy-side-facing equity execution arm; **Sigma X / Sigma X2** is the firm's broker-operated ATS.

> Goldman also operates as a major FI dealer (reached via [[marketaxess]], [[tradeweb]], dealer-direct RFQ) and FX LP (reached via the FX ECNs). See the [[_brokers-overview|dual-role disclaimer]].

## Algorithmic suite

- **Atlas** family — VWAP, TWAP, POV, IS, close-targeted.
- **4-CAST** — opportunistic / dark-seek with adverse-selection avoidance.
- **Sonar** — passive/dark posting with anti-gaming.
- **Smart Pegged** — peg variants with liquidity-detection logic.
- Custom Atlas configurations on FIX instruction tags (`StrategyName` + parameter map).

## DMA

- DMA pass-through to all US national securities exchanges plus major EU/APAC.
- Low-latency colocation with NYSE Mahwah, Nasdaq Carteret, LSE Slough, Xetra Frankfurt, Tokyo CoLo.

## Dark pool

- **Sigma X / Sigma X2** — US broker-operated ATS, one of the largest broker-internalized pools by share.
- **Sigma X Europe** (UK MiFID II MTF post-Brexit).
- **Sigma X Japan** (TSE PTS).

## Capital commitment

- Active block-trading desk with willingness to take risk on large prints.
- Pre-trade commitment available via RFQ to the desk.

## Central Risk Book (CRB)

- Goldman runs one of the larger CRBs in the industry — internalizes client flow against a centrally managed inventory, frequently improving client price vs naked agency.

## ETF block / RFQ desk

- Strong ETF block presence as authorized participant for many ETFs.
- AP creation / redemption for primary-market large blocks.

## Connectivity

- **FIX 4.4 / 5.0** for order entry, ExecutionReports, allocations, allocation-after-give-up.
- Drop-copy via FIX.
- GSET destination codes are documented per algo / strategy.

## Related

- [[arch-smart-order-router]] (routing decision) · [[arch-best-execution]] (selection rationale)
- [[bloomberg-emsx]] (reachable via EMSX broker routing)
- [[memx]] (Goldman is a founding member)
- [[marketaxess]] · [[tradeweb]] · [[refinitiv-fxall]] (FI / FX touchpoints — dual-role)
