---
type: venue
venue_kind: ats
asset_classes: ["fixed_income"]
status: draft
tags: [venue/ats]
---

# OpenDoor

US **electronic trading venue specifically for off-the-run US Treasuries and TIPS** — a market segment historically illiquid relative to on-the-runs and underserved by the main interdealer venues. SEC-registered ATS.

## Asset classes

- Off-the-run US Treasury notes and bonds
- TIPS (all tenors)
- STRIPS (selected)

## Workflow mechanisms

- **Session-based all-to-all matching** — periodic auctions where buy-side and dealers cross at a single clearing price.
- Reduces information leakage relative to continuous RFQ.
- Complements [[brokertec]] (on-the-runs) and [[tradeweb]] (D2C RFQ).

## Connectivity

- **FIX** for order entry and executions.
- REST API for reference and post-trade.

## Key facts

- Niche but important: off-the-run UST liquidity is structurally weaker than on-the-run.
- Session-based matching is a deliberate design choice for low-information-leakage execution.
- TIPS in particular benefit from concentrated matching.

## Related

- [[govt-bonds]]
- [[brokertec]] (on-the-run CLOB) · [[tradeweb]] (D2C RFQ)
- [[arch-best-execution]] (off-the-run benchmarking)
