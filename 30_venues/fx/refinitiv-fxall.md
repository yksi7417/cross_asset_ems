---
type: venue
venue_kind: mtf
asset_classes: ["fx"]
status: draft
tags: [venue/mtf]
---

# Refinitiv FXall (LSEG)

LSEG-owned **multi-dealer FX trading platform** for institutional buy-side — formerly Reuters FXall. Strong in spot, forwards, swaps, NDFs, options across all major and EM pairs.

## Asset classes

- FX spot (all majors + most EM)
- FX outrights / forwards
- FX swaps
- FX NDFs (across EM)
- FX options (vanilla + barriers)
- Precious metals (XAU/XAG)
- Money markets / deposits (selected)

## Workflow mechanisms

- **RFQ** disclosed to a chosen dealer set (typical 3-10 LPs).
- **RFS (Request-for-Stream)** — short-lived dealer stream upon request.
- **QuickTrade** — one-click against a streaming price.
- **Settlement Center** for post-trade netting and instruction.

## Connectivity

- **FIX 4.4 / 5.0** for order entry, RFQ, ExecutionReports, allocations.
- REST + WebSocket APIs for streaming pricing and reference.
- Refinitiv ITP (Refinitiv Trading Protocol) for legacy integrations.

## Key facts

- Long-standing buy-side platform — one of the most widely used institutional FX venues.
- LSEG ownership integrates with Refinitiv data and Tradeweb (LSEG).
- Strong NDF and options offering distinguishes it from spot-only ECNs.

## Related

- [[fx-spot]] · [[fx-forward]] · [[fx-swap]] · [[fx-ndf]] · [[fx-options]]
- [[ebs]] (D2D peer) · [[360t]] · [[currenex]] · [[hotspot-fx]] (D2C peers)
- [[arch-rfq]] · [[arch-realtime-analytics]]
