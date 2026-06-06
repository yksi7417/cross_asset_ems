---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# RFQ — Request-for-Quote

A **Request-for-Quote** is a workflow where the buy-side **asks a chosen dealer set** for a price on a specific instrument and size, the dealers respond within a deadline (typically 10-60 seconds), and the buy-side **elects** the best response (or none).

RFQ is the dominant interaction model in markets where the instrument is **heterogeneous, illiquid, or block-sized** — most corporate bonds, FX outrights/swaps/options, cleared OTC swaps, ETF blocks, BWICs. It contrasts with **CLOB** (continuous order-driven matching — see [[clob-vs-rfq]]) and with **streaming** (a single LP continuously posts a price you can click — see [[rfq-rfs-rfm]]).

Variants: **disclosed RFQ** (the dealer sees the requester's identity and direction), **anonymous RFQ** (identity hidden), **two-way RFQ** (request a price on both sides without revealing direction), **RFQ-to-3** (CFTC requirement for MAT swaps — see [[rfq-to-3]]). Many platforms also support **list RFQ** for baskets / BWICs.

## Example

A buy-side trader sends a 5M USD corp bond RFQ to 5 dealers via [[marketaxess]]. After 30 seconds, three respond (cover info: 98.45, 98.40, 98.35). The trader elects 98.45 — the fill prints and the cover info is preserved for [[arch-best-execution]] audit.

## Why it matters in an EMS

- RFQ is one of three first-class route modes alongside CLOB and streaming — see [[arch-router-layer]].
- The [[arch-rfq]] architecture note covers the canonical state machine (Requested → Active → Elected → Executed) and the last-look fade problem.

## Related

- [[clob-vs-rfq]] · [[rfq-rfs-rfm]] · [[rfq-to-3]] · [[ioi-vs-rfq]]
- [[bwic-owic]] (list RFQ for fixed income)
- [[arch-rfq]] · [[route-to-rfq]] · [[multi-route-rfq]]
- [[marketaxess]] · [[tradeweb]] · [[bloomberg-bridge]] (RFQ venues)
