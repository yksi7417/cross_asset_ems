---
type: venue
venue_kind: mtf
asset_classes: ["fx"]
status: draft
tags: [venue/mtf]
---

# FX Connect (State Street)

State Street's **multi-dealer FX RFQ platform**, focused on the **buy-side asset-management** segment with deep integration into custody, NAV, and allocation workflows. Sister to [[currenex]] (the State Street ECN).

## Asset classes

- FX spot (majors and EM)
- FX outrights / forwards
- FX swaps
- FX NDFs (selected)

## Workflow mechanisms

- **RFQ** (typically 3-5 LPs) — disclosed.
- **NAV-rate execution** for fund-flow hedging tied to NAV cuts.
- **Allocation-aware** routing for multi-fund allocations.
- **Block-and-allocate** workflow common in asset management.

## Connectivity

- **FIX 4.4** for order entry, RFQ, executions, allocations.
- Tight integration with State Street custody and Charles River EMS.

## Key facts

- Asset-management focused — different design center from corp-treasury or hedge-fund FX.
- NAV-rate execution is a distinguishing feature.
- Frequently paired with [[currenex]] for split workflows (RFQ on FX Connect, anonymous on Currenex).

## Related

- [[fx-spot]] · [[fx-forward]] · [[fx-swap]]
- [[currenex]] (State Street sibling) · [[refinitiv-fxall]] · [[360t]]
- [[arch-allocation-service]] (block-and-allocate fits FX Connect's design)
