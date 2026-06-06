---
type: venue
venue_kind: mtf
asset_classes: ["fx"]
status: draft
tags: [venue/mtf]
---

# Currenex (State Street)

State Street-owned **electronic FX trading platform** for institutional buy-side and prime-brokered participants. Mix of **disclosed RFQ, anonymous streaming, and ECN-style matching**.

## Asset classes

- FX spot (majors + many EM)
- FX outrights / forwards
- FX swaps
- FX NDFs (selected)

## Workflow mechanisms

- **Anonymous streaming pool** with click-to-trade.
- **Disclosed RFQ** to chosen dealer set.
- **Algorithmic execution** (TWAP, POV, dark seek).

## Connectivity

- **FIX 4.4 / 5.0** for order entry, executions.
- WebSocket streaming for live pricing.
- Drop-copy and post-trade APIs.

## Key facts

- State Street ownership ties into [[fx-connect]] (also State Street) — together they cover both ECN and multi-dealer RFQ workflows.
- Strong prime-broker integration.

## Related

- [[fx-spot]] · [[fx-forward]] · [[fx-swap]]
- [[fx-connect]] (State Street sibling) · [[refinitiv-fxall]] · [[hotspot-fx]] · [[360t]]
- [[arch-smart-order-router]] (anonymous pool routing)
