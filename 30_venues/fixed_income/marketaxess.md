---
type: venue
venue_kind: mtf
asset_classes: ["fixed_income"]
status: draft
tags: [venue/mtf]
---

# MarketAxess

US-headquartered electronic trading platform for credit. Dominant venue for **USD IG and HY corporate bonds**, expanding in EM, EUR credit, munis, and ETF RFQ. SEC-registered ATS in the US; MarketAxess Europe is an MTF; MarketAxess Singapore is a recognised market operator.

## Asset classes

- US IG corporate bonds (D2C RFQ + Open Trading all-to-all)
- US HY corporate bonds
- EUR / GBP corporate bonds
- EM bonds (hard currency and growing local currency)
- US munis
- US Treasuries (Live Markets — CLOB)
- ETF RFQ (the ETF block)
- CDS index (limited)

## Workflow mechanisms

- **Disclosed RFQ** to a chosen dealer set.
- **Open Trading** — all-to-all anonymous matching; buy-side can respond to other buy-sides' RFQs and vice versa.
- **Live Markets** — order-driven CLOB for on-the-run USTs.
- **Portfolio trading** — basket negotiation.

## Connectivity

- **FIX 4.4** for order entry, RFQ, allocations. Standard tags plus MarketAxess custom tags for OT participation, target-price, basket IDs.
- REST API for reference data and post-trade.
- TRACE post-trade reporting integration on the platform side.

## Key facts

- Dominant share of US IG e-trading.
- Open Trading is a structural shift — it lets the buy-side intermediate other buy-sides, compressing spreads.
- BondTicker / CP+ pricing analytics ride on the platform.

## Related

- [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[municipal-bonds]] · [[govt-bonds]] · [[ems-rfq]]
- [[tradeweb]] · [[bloomberg-bridge]] (competitors / complements)
- [[arch-rfq]] · [[arch-ioi]] (IOIs feed RFQ flow) · [[trace]] (reporting)
