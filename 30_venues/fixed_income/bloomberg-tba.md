---
type: venue
venue_kind: dealer_platform
asset_classes: ["fixed_income"]
status: draft
tags: [venue/dealer_platform]
---

# Bloomberg TBA Trading (BMTF MBS leg)

Electronic TBA (To-Be-Announced) **mortgage trading workflow** on Bloomberg. The terminal-side `TBA<GO>` monitor is a price-discovery view; the **routable execution leg** is on [[bloomberg-bmtf]] (the EU MTF, where mandated) or via dealer-direct FIX RFQ for US flow.

## Asset classes

- Agency MBS TBA (Fannie 30, Fannie 15, Freddie 30, Freddie 15, Ginnie 30 by coupon)
- Specified pools (pool-specific characteristics outside TBA stipulations)
- Agency CMO tranches
- Dollar rolls (TBA settlement-date roll between months)

## Workflow mechanisms

- **TBA RFQ** to dealer set on coupon / settlement-month / quantity.
- **Stipulations** for specified pools (loan size, geography, FICO, WAC bands).
- **Dollar roll** as a two-legged RFQ.
- Settlement via [[arch-confirmation-affirmation]] feeds (DTCC for many MBS).

## Connectivity

- **FIX** for RFQ and execution.
- BLPAPI for reference (TBA conventions, settlement calendar from SIFMA).

## Key facts

- US TBA market is huge and largely electronic at the dealer-direct level.
- Settlement-date discipline is rigid (monthly Class A/B/C/D dates per SIFMA).
- Dollar rolls are a financing trade and must be modelled as paired transactions.

## Related

- [[mbs]] · [[sifma-tba-guidelines]] (settlement convention)
- [[bloomberg-bmtf]] (EU regulated execution) · [[tradeweb]] (US D2C TBA)
- [[arch-confirmation-affirmation]] · [[dtcc]] (post-trade)
