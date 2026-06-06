---
type: venue
venue_kind: broker_dealer
asset_classes: ["fixed_income", "equity"]
status: draft
tags: [venue/broker_dealer]
---

# Bloomberg Tradebook (US — BTBU)

**Bloomberg Tradebook LLC** is Bloomberg's **US-registered broker-dealer**, providing electronic execution across **fixed income, equities, and futures**. Distinct from the BMTF/SEF regulated venues — Tradebook acts as an executing broker on the buy-side's behalf.

## Asset classes

- US equities (cash + ADRs) with Bloomberg's algo suite
- US equity options
- US futures
- Fixed income (corp, govt, ETF cross-product)

## Workflow mechanisms

- **Agency execution** — Tradebook executes on behalf of the buy-side as broker of record.
- **Algorithmic execution** — Bloomberg-branded algos (TWAP, VWAP, IS, dark-seek, etc.).
- **DMA / smart order routing** through Tradebook's routing engine.
- **List trading** for portfolios.

## Connectivity

- **FIX 4.2 / 4.4** for order entry, ExecutionReports, allocations, broker-instruction strategies.
- Integrates with [[bloomberg-emsx]] as a destination among the 3,700+.

## Regulatory shape

- **SEC-registered broker-dealer**, FINRA member.
- Trades execute on the underlying exchanges / ATSs through Tradebook's routing.
- Subject to all US best-ex obligations ([[arch-best-execution]]).

## Related

- [[bloomberg-tradebook-sg]] (Singapore sibling)
- [[bloomberg-emsx]] (the EMS-side that often targets Tradebook)
- [[cash-equity]] · [[equity-options]] · [[govt-bonds]] · [[corp-bonds-ig]]
- [[arch-best-execution]] · [[arch-smart-order-router]]
