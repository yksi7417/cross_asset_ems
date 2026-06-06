---
type: venue
venue_kind: interdealer
asset_classes: ["fx"]
status: draft
tags: [venue/interdealer]
---

# EBS (CME EBS Market)

CME Group's **electronic interdealer FX CLOB**, originally the dominant venue for **EUR/USD, USD/JPY, EUR/JPY** D2D flow. Now part of CME's FX complex alongside FX futures on Globex.

## Asset classes

- FX spot — the major pairs (EUR/USD, USD/JPY, EUR/JPY, AUD/USD, GBP/USD, USD/CHF, etc.)
- Selected EM pairs (USD/CNH, USD/MXN, USD/RUB historically)
- Spot precious metals (XAU/USD, XAG/USD)

## Workflow mechanisms

- **CLOB** with price-time priority and minimum quote life (MQL) discipline.
- **Tagged liquidity** — different participant tiers see different liquidity to reduce gaming.
- **NDF segment** (EBS Direct NDF) for select EM NDFs.

## Connectivity

- **EBS Ai** — proprietary API for low-latency CLOB access.
- **FIX** for slower / institutional access.
- **iLink (CME binary)** for the CME-style integration.

## Key facts

- Long-standing dominance in EUR/USD interdealer, alongside Refinitiv Matching.
- CME ownership integrates EBS with FX futures (basis / arbitrage).
- MQL discipline addresses last-look / flickery-quote concerns.

## Related

- [[fx-spot]] · [[fx-ndf]]
- [[refinitiv-fxall]] · [[hotspot-fx]] · [[360t]] · [[currenex]] (D2C ECN siblings; D2D peer is Refinitiv Matching)
- [[arch-realtime-analytics]] (NBBO / EBBO across FX ECNs)
