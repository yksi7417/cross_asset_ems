---
type: venue
venue_kind: rfq
asset_classes: ["fixed_income"]
status: draft
tags: [venue/rfq]
---

# Neptune Networks

A **buy-side-focused pre-trade liquidity-data network** for bonds. Neptune itself is not an execution venue — it aggregates **dealer axes and inventory** from contributing dealers and streams them to buy-side EMS subscribers via standardised FIX.

## Asset classes

- USD / EUR / GBP IG and HY corporate bonds
- EM bonds
- Govt bonds
- ETFs

## Workflow mechanisms

- **Axe / inventory distribution** — dealers publish what they want to trade and where.
- **No matching engine** — execution happens off-platform on MarketAxess / Tradeweb / Bloomberg / dealer-direct after the axe surfaces a counterparty.

## Connectivity

- **FIX-based feed** with standardised security identification and axe semantics.
- Available to subscribing buy-side EMSs.

## Key facts

- Founded by buy-side firms to address signal-noise in dealer axe distribution.
- Significant industry adoption — a useful, vendor-neutral pre-trade data layer.
- IOIs from Neptune appear in [[arch-ioi]] alongside other networks.

## Related

- [[arch-ioi]] (IOI provenance from networks)
- [[marketaxess]] · [[tradeweb]] (where the actual execution happens)
- [[corp-bonds-ig]] · [[corp-bonds-hy]]
