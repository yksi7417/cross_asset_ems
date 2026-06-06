---
type: venue
venue_kind: dealer_platform
asset_classes: ["fixed_income", "rates_credit_deriv"]
status: draft
tags: [venue/dealer_platform]
---

# Bloomberg TOMS

**TOMS** ("Trade Order Management Solution") is Bloomberg's **sell-side order, position, and inventory management platform** for fixed-income dealers. From a buy-side EMS perspective, TOMS is not a *venue* in the matching sense — but it is a **routable destination**: an order routed to a dealer counterparty whose desk runs TOMS lands in their TOMS workflow and is responded to through standard FIX.

## What it is to a buy-side EMS

A FIX-connected sell-side counterparty stack. Buy-side orders sent via [[marketaxess]] / [[tradeweb]] / dealer-direct that target a TOMS-using dealer are processed by the dealer's TOMS instance, which generates the executions and replies.

## Asset classes the dealer handles

- Govt and credit cash (USD, EUR, GBP, EM)
- Mortgages (TBA, specified pools, CMOs)
- Munis
- Money markets, repo
- IRS, CDS, structured (linked to BBG SEF and SWPM as appropriate)

## Connectivity

- **FIX 4.2 / 4.4** — buy-side EMSs route to TOMS-running dealers via standard FIX channels; the dealer's TOMS handles the inbound and emits ExecutionReports.
- **BLPAPI** for post-trade and reference data integration.
- **CIX** (Custom Index) and **AIM** integration on the dealer side.

## Key facts

- TOMS is a *real* purchasable Bloomberg product (distinct from terminal monitor screens).
- For the EMS, TOMS is one of the most common sell-side targets in the FI complex.

## Related

- [[arch-fix-api-bridge]] · [[arch-venue-connectivity]]
- [[marketaxess]] · [[tradeweb]] (the platforms TOMS-using dealers are typically reached through)
- [[govt-bonds]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[mbs]] · [[municipal-bonds]]
