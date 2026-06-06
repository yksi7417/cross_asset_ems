---
type: venue
venue_kind: rfq
asset_classes: ["fixed_income"]
status: draft
tags: [venue/rfq]
---

# Bloomberg BWIC / OWIC (List Trading)

**BWIC** (Bid Wanted In Competition) and **OWIC** (Offer Wanted In Competition) describe the **list-trading workflow** where a holder of bonds (BWIC) or a buyer needing inventory (OWIC) circulates a list of CUSIPs to a dealer set and collects competitive bids/offers within a deadline. Bloomberg supports this as a workflow within the FI platform; on regulated execution it ties into [[bloomberg-bmtf]] or dealer-direct.

## Asset classes

- US IG and HY corporate bonds (dominant use)
- US municipal bonds
- MBS / ABS / CMO portfolios
- Loans (whole-loan BWICs)
- EM bonds

## Workflow mechanisms

- **List circulated** with per-line evaluation period (often 30-60 min).
- **Competitive bid/offer** from invited dealer set.
- **Allocation** at the deadline (winner gets the line; sometimes cover info to the next-best).
- Post-trade reporting per asset class (TRACE for corp, MSRB for muni, etc.).

## Connectivity

- **FIX** for list submission, response receipt, and execution.
- BLPAPI for reference and post-trade.
- Execution leg routes through the matching venue ([[bloomberg-bmtf]], dealer-direct).

## Key facts

- BWICs are the dominant secondary-market workflow for HY portfolio rebalancing.
- Cover information (how close the second-best was) is itself a valuable best-ex datapoint — see [[arch-best-execution]].
- ABS / CMO BWICs are operationally complex (per-tranche evaluation).

## Related

- [[corp-bonds-hy]] · [[corp-bonds-ig]] · [[municipal-bonds]] · [[mbs]] · [[abs]] · [[whole-loans]]
- [[arch-rfq]] (BWIC/OWIC is a list-RFQ specialization)
- [[bloomberg-bmtf]] (execution leg for EU)
