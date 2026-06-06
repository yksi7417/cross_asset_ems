---
type: venue
venue_kind: ats
asset_classes: ["fx"]
status: draft
tags: [venue/ats]
---

# FXSpotStream

**Bank-owned consortium FX aggregation service** providing streaming pricing from ~15 major banks via a single connection. Operates as a multi-LP price aggregator and trade-routing utility — not a matching engine.

## Asset classes

- FX spot (majors and most EM)
- FX forwards / swaps (growing)
- FX NDFs (selected, growing)
- Precious metals

## Workflow mechanisms

- **Disclosed streaming pricing** from each LP bank — buy-side sees who is making the price.
- **Click-to-trade** against the disclosed stream.
- **No anonymous matching** — execution is bilateral against the chosen LP.

## Connectivity

- **FIX 4.4** for order entry and executions.
- WebSocket / binary feed for streaming pricing.
- LP-specific tags for last-look behavior, hold times.

## Key facts

- LP banks include Goldman Sachs, Morgan Stanley, JPM, Citi, BAML, UBS, BNP, Deutsche, Standard Chartered, HSBC, MUFG, Nomura, etc. — the same firms that appear under [[brokers/]] for equity.
- Differentiated from ECNs (EBS, Hotspot) by being fully disclosed.
- Low operating cost makes it attractive to small/mid buy-side that can't afford bilateral LP integrations.

## Related

- [[fx-spot]] · [[fx-forward]] · [[fx-swap]] · [[fx-ndf]]
- [[refinitiv-fxall]] · [[360t]] · [[currenex]] (multi-dealer alternatives)
- [[ebs]] · [[hotspot-fx]] (anonymous ECN alternatives)
- [[goldman-sachs]] · [[morgan-stanley]] · [[ubs]] (the same firms as FX LPs — dual-role disclaimer applies)
