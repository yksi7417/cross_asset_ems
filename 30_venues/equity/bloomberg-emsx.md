---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker_dealer]
---

# Bloomberg EMSX

Bloomberg's **execution management service for equities, equity options, and futures**. EMSX is not itself a matching venue — it is a **buy-side EMS gateway with 3,700+ broker destinations**, providing FIX-based routing to brokers' algorithmic, DMA, dark-pool, and capital-commitment offerings.

## Asset classes

- Cash equities (global)
- Equity options
- Futures
- ETFs (linked to [[bloomberg-rfqe]])
- ADRs

## Workflow mechanisms

- **Broker routing** — orders routed to any of 3,700+ broker destinations.
- **Algo selection** — broker algos selectable (VWAP, TWAP, POV, IS, dark, etc.) with per-broker parameter sets.
- **DMA** — direct market access via the broker's connection.
- **List trading** — basket workflow.

## Connectivity

- **EMSX API (FIX-based)** — buy-side EMS can route inbound from FIX clients to EMSX, then outbound to brokers. **No additional connectivity fees** beyond Bloomberg's EMSX subscription.
- **BLPAPI** for reference and post-trade.
- Standard FIX 4.2 / 4.4 for order entry, ExecutionReports, allocations.

## Key facts

- EMSX is a real purchasable Bloomberg product, distinct from terminal monitor screens.
- The 3,700+ destination footprint is the primary reason firms adopt it.
- Brokers maintain their per-firm EMSX presets (algos exposed, capacity).

## Related

- [[arch-smart-order-router]] (in-house equivalent that may bypass EMSX for direct routes)
- [[arch-fix-api-bridge]] · [[arch-venue-connectivity]]
- [[goldman-sachs]] · [[morgan-stanley]] · [[ubs]] (brokers reachable through EMSX)
- [[cash-equity]] · [[equity-options]] (asset classes)
