---
type: venue
venue_kind: broker_dealer
asset_classes: ["equity"]
status: draft
tags: [venue/broker, venue/broker_dealer]
---

# Instinet (Nomura)

**Agency-only equity broker** — Instinet (a Nomura subsidiary) executes on the buy-side's behalf without taking a principal position. Distinct from the bulge-bracket brokers in that there is **no CRB, no internalization for proprietary benefit** — execution flows are pure agency.

> Distinct from the bulge-bracket model: agency-only brokers are often preferred for **best-ex-sensitive flow** because there is no conflict between the broker's principal book and the client's order.

## Algorithmic suite

- **Newport Pro** — algo suite (VWAP, TWAP, POV, IS, Close, Volume-Adaptive).
- **EX** — global equity execution platform.
- **Dark-Seek / Hunter** — broker-dark + external-dark routing.
- **Implementation Shortfall (IS)** with proprietary impact model.

## DMA

- DMA across US/EU/UK/APAC.
- Colocation across the majors.

## Dark pool

- **BlockMatch** — Instinet's ATS (US), focused on size discovery rather than retail-flow internalization.
- **BlockMatch Europe** (EU MTF).
- **BlockMatch Asia**.

## Capital commitment

- None by design — agency-only.

## Central Risk Book (CRB)

- None — agency model precludes a CRB.

## ETF block / RFQ desk

- ETF agency execution; no AP role (no principal balance sheet for AP).

## Connectivity

- **FIX 4.4 / 5.0**.
- Drop-copy.
- Instinet destination codes per algo.

## Key facts

- Long-standing pioneer of electronic equity execution (one of the original ECNs).
- Nomura acquired in 2007; runs as the agency arm distinct from Nomura's principal business.
- Preferred destination for size that wants to avoid information leakage to principal-trading desks.

## Related

- [[arch-smart-order-router]] · [[arch-best-execution]] (best-ex prefers Instinet for sensitive flow)
- [[bloomberg-emsx]] (reachable via EMSX)
- [[liquidnet-broker|liquidnet]] (peer agency block specialist)
