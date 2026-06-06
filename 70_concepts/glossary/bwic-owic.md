---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# BWIC / OWIC — List Trading

**BWIC** ("Bid Wanted In Competition") is a list of bonds the holder wants to **sell** circulated to a dealer set for competitive bids. **OWIC** ("Offer Wanted In Competition") is the buy-side mirror — a list of bonds the buyer wants to **buy** circulated for competitive offers.

The dominant secondary-market workflow for **HY corporate, MBS / ABS / CMO, and whole-loan portfolios** — situations where line-by-line RFQ would be slow and leak the seller's intentions. The list is sent to N dealers with a deadline (typically 30-60 minutes); each dealer submits per-line bids/offers; the originator allocates each line to the best bidder.

The **cover info** — how close the second-best bid was — is itself valuable best-execution evidence. Some BWICs include "cover not seen" rules to prevent dealers from gaming the next-best as the new floor.

## Example

A HY portfolio manager rebalancing out of 80 BB-rated names sends a BWIC to 8 dealers via Bloomberg / MarketAxess. The list is bid line-by-line over a 45-minute window. Each line goes to its best bidder. The dealer-by-dealer hit rates feed back into [[arch-best-execution]] audit.

## Why it matters in an EMS

- BWIC / OWIC is a list-RFQ workflow — see [[arch-rfq]] for the canonical state machine.
- ABS / CMO BWICs are operationally complex (per-tranche evaluation).
- Cover-info preservation is a regulatory best-ex datapoint.

## Related

- [[rfq]] · [[clob-vs-rfq]] · [[arch-rfq]]
- [[corp-bonds-hy]] · [[mbs]] · [[abs]] · [[whole-loans]]
- [[bloomberg-bwic-owic]] (the workflow as supported by Bloomberg)
- [[arch-best-execution]] (cover info preservation)
