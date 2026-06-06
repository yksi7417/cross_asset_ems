---
type: venue
venue_kind: ats
asset_classes: ["fx"]
status: draft
tags: [venue/ats]
---

# Hotspot FX (Cboe FX)

Cboe Global Markets' **electronic FX ECN**, formerly Hotspot Markets. Operates a **central limit order book** for spot FX in major and selected EM pairs — closest equivalent to an "exchange-style" FX matching venue alongside [[ebs]] and Refinitiv Matching.

## Asset classes

- FX spot — major pairs and selected EM
- Limited NDF support

## Workflow mechanisms

- **CLOB** with price-time priority.
- **Anonymous matching** (counterparty disclosure only post-trade via PB).
- **Tiered participant model** with minimum quote life.

## Connectivity

- **FIX 4.2 / 4.4** for order entry, executions, drop-copy.
- **Cboe binary** for low-latency CLOB access.
- Market data via Cboe FX feed.

## Key facts

- Cboe acquired Hotspot from KCG (formerly Knight) in 2015.
- Significant share of EUR/USD anonymous spot.
- Tier discipline addresses last-look / flickery-quote concerns.

## Related

- [[fx-spot]]
- [[ebs]] (CME interdealer CLOB peer) · [[fxspotstream]]
- [[refinitiv-fxall]] · [[currenex]] · [[360t]] (D2C RFQ alternatives)
- [[arch-realtime-analytics]] (CLOB-derived NBBO/EBBO)
