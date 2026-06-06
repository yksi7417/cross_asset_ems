---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# RFQ / RFS / RFM — Quote-Driven Workflows

Three quote-driven execution mechanisms that look similar but are operationally distinct:

- **RFQ** (Request for Quote) — client asks N dealers for a **firm price**; dealers respond within a deadline (10-60 seconds); client elects best. Discrete event. The default in fixed income and FX swaps. See [[rfq]].
- **RFS** (Request for Stream) — client asks a single dealer for a **short-lived streaming price** (typically 5-30 seconds). The dealer streams updates continuously during the window; client clicks to execute on any tick. Effectively a "let me see your live price for a moment." Common in FX spot and short-end rates.
- **RFM** (Request for Market) — client asks for a **two-sided market** (both bid and offer) without revealing direction. The dealer quotes "I'll bid 100.00, offer 100.10"; the client can hit either side. Defeats the dealer's incentive to skew toward the side they think the client wants.

The distinction matters because each has different fill semantics, different dealer behaviour, and different best-ex implications. RFS exposes the dealer to multiple price updates within the stream; RFM doubles the dealer's risk (must price both sides without information); both pay for the better client information protection.

## Example

A FX spot buy-side trader for EUR/USD: (a) **RFQ to 3 dealers** for 25M — receives three firm bids/offers, elects best; (b) **RFS to one dealer** for 30 seconds — sees the dealer's live stream, clicks to execute when the level moves favourably; (c) **RFM to one dealer** for 100M — receives a two-sided market, elects either side without leaking direction up front.

## Why it matters in an EMS

- The router treats RFQ / RFS / RFM as three distinct `route_mode` values.
- The FSM differs: RFS has streaming-update events; RFQ has discrete responses; RFM has a special "side-elected-at-execution" lifecycle.
- See [[arch-rfq]] for the canonical state machines.

## Related

- [[rfq]] · [[rfq-to-3]] · [[clob-vs-rfq]]
- [[refinitiv-fxall]] · [[360t]] · [[tradeweb]] (platforms supporting all three)
- [[arch-rfq]] · [[arch-router-layer]]
