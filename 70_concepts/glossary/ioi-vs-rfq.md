---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# IOI vs. RFQ

Two adjacent but distinct pre-trade signals:

- **IOI** (**Indication of Interest**) — a **dealer-published advertisement** of liquidity. The dealer signals "I am willing to trade X size at this approximate level" without yet committing. IOIs are **dealer-initiated**, distributed via Bloomberg IOI, Autex, Liquidnet, dealer-direct chat, [[neptune]]. Recipients can respond by negotiating.
- **RFQ** (**Request for Quote**) — a **client-initiated request** for a firm price. The client asks N dealers for a quote on a specific instrument and size; dealers respond with firm prices within a deadline; the client elects (or doesn't).

The directional difference is critical: IOI is **dealer-to-market** (push); RFQ is **client-to-dealer** (pull). In practice, IOIs often **lead to RFQs** — a buy-side trader sees a dealer IOI on a bond, then sends a targeted RFQ to that dealer plus a few others to confirm the level.

IOIs have explicit **qualifiers** preserved through the audit chain: `NATURAL` (real flow, no inventory), `SUPER_NATURAL` (matched against another client), `UNWOUND` (cleaning up old position), `IN_TOUCH_WITH` (already showed level to another client), `DELTA_HEDGED`, `PORTFOLIO_TRADE`. See [[arch-ioi]].

## Example

A Goldman trader publishes an IOI on Bloomberg IOI: "Sell 5M Bond X, +10bp over CBBT, NATURAL." The buy-side trader sees the IOI, sends an RFQ-to-3 to Goldman, JPM, Morgan Stanley on the same bond. Goldman returns the level they advertised; the others come within a few bp. The buy-side elects Goldman based on the original IOI signal.

## Why it matters in an EMS

- IOI provenance feeds [[arch-best-execution]] selection rationale: "we picked dealer X because of their IOI."
- IOIs and RFQ responses are different data structures with different state machines.
- [[arch-ioi]] vs [[arch-rfq]] are architecturally distinct components.

## Related

- [[rfq]] · [[axe]] (one-directional axe ≈ IOI semantics)
- [[arch-ioi]] · [[arch-rfq]]
- [[neptune]] · [[bloomberg-ib]] (IOI carriers)
- [[arch-best-execution]] (IOI attribution)
