---
type: venue
venue_kind: rfq
asset_classes: ["fixed_income", "rates_credit_deriv", "fx"]
status: draft
tags: [venue/chat]
---

# Bloomberg IB (Instant Bloomberg Chat)

**Instant Bloomberg (IB)** is the Bloomberg Terminal's instant-message channel — used heavily by sell-side and buy-side traders for **chat-based trade negotiation, axes, and IOIs**. For an EMS, IB is a *liquidity surface* that must be programmatically integrated, not a matching venue.

## Why it matters to an EMS

A meaningful share of voice and high-touch FI / FX flow is initiated and negotiated over IB messages between sales and trader. Capturing this flow into structured order events is required for best-execution audit, surveillance, and STP.

## Integration mechanism

- **IBAPI** — Bloomberg's API for IB chat ingestion and posting. Allows the EMS to listen to chat rooms, parse axes/RFQs, and post responses.
- **Structured chat** plugins where the dealer publishes axes in a parseable format.
- **Voice-to-IB** workflows where sales types up a phone conversation into a structured chat-trade.

## Asset classes typically negotiated on IB

- FI: HY corporate, EM bonds, off-the-run govts, complex MBS / ABS, whole loans, distressed.
- FX: large NDF blocks, exotic options.
- Rates / credit derivs: structured / bespoke.
- Equity blocks (where IB substitutes for a phone call).

## Best-execution implication

Trades initiated over IB chat must produce the **same chain identity and audit trail** as electronic trades — [[arch-identity-chaining]] applies, with the IB-derived "intent" becoming the `initial_order_id` source event.

## Related

- [[arch-ioi]] (IOI provenance — IB-sourced IOIs need explicit network=IB tagging)
- [[arch-best-execution]] (chat-trade audit)
- [[arch-fix-api-bridge]] (bridging chat events into the API surface)
- [[corp-bonds-hy]] · [[fx-options]] · [[whole-loans]] (typical IB use cases)
