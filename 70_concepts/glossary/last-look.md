---
type: concept
status: draft
tags: [concept/glossary, glossary/fx, glossary/execution]
---

# Last-Look

**Last-look** is the **liquidity provider's brief window** (typically 10-200 milliseconds) to **accept or reject an incoming aggressive order** at the LP's previously-streamed price. Predominantly an FX-ECN feature, also present in some equity dark pools and dealer-streamed FI venues.

The mechanism exists because LPs stream prices to many venues simultaneously; without last-look, an LP can be "picked off" by latency-arbitrageurs holding stale prices longer than the LP can update them. Last-look lets the LP verify the price is still valid before honouring the fill.

The trade-off is **adverse selection vs fade risk**: with last-look, the taker can have orders rejected (faded); without last-look, the LP must widen the spread to compensate for adverse selection. Modern FX venues publish **fade-rate statistics** and impose **maximum hold-time discipline** on participating LPs (e.g. EBS MQL — minimum quote life — addresses the same issue from a different angle).

## Example

A taker hits a 1.1000 EUR/USD bid from a streaming LP on Hotspot FX. The LP has 25ms to last-look. If during those 25ms the LP's hedge price has moved to 1.0998, the LP fades the trade (rejects the fill). The taker now must re-route to another LP — and the EBBO has likely moved against them.

## Why it matters in an EMS

- The EMS must measure fade-rate per LP per pair and feed it into [[arch-best-execution]] selection.
- TCA must distinguish "didn't trade" (faded) from "traded poorly."
- Routing decisions ([[arch-smart-order-router]]) should de-prioritize high-fade LPs for time-sensitive flow.

## Related

- [[fx-spot]] · [[refinitiv-fxall]] · [[hotspot-fx]] · [[fxspotstream]] · [[ebs]]
- [[arch-smart-order-router]] · [[arch-best-execution]] · [[arch-tca]]
