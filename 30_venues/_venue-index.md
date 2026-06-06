---
type: index
status: draft
tags: [index/venues]
---

# Venue Index

A **venue** in this vault is an external destination the EMS can route an order to via FIX, binary protocol, or API. **Terminal monitor screens** (Bloomberg ALLQ<GO>, BTMM<GO>, FIT<GO>, etc.) are **not venues** — they are price-discovery surfaces. The underlying market data is consumable via B-PIPE / BLPAPI; the execution against that liquidity happens at one of the venues listed below or at a named dealer.

Folder is **asset class**. The `venue_kind:` frontmatter field distinguishes `exchange` / `mtf` / `sef` / `ats` / `dealer_platform` / `interdealer` / `rfq` / `broker_dealer` within each folder — no nested sub-folders.

## Categories

### [[fixed_income/]] — Fixed Income
MTFs, all-to-all platforms, interdealer venues, dealer RFQ networks, primary auction portals, and tri-party financing. Covers govts, IG/HY corp, muni, money markets, repo, TBA-MBS, EM bonds, convertibles.

### [[fx/]] — Foreign Exchange
ECNs and multi-dealer FX platforms covering spot, forward, swap, NDF, and FX options. Bilateral LP streams are abstracted as the dealer-of-record on the venue, not as separate venues.

### [[equity/]] — Equity (cash + listed derivatives)
Lit exchanges, regulated MTFs, independent ATSs (IEX, MEMX), listed options exchanges. **Broker-internalized destinations (dark pools, central risk books) sit under [[brokers/]]**, not here, because they are operationally part of the broker offering.

### [[brokers/]] — Equity Routing Brokers
Bulge-bracket and agency brokers as **equity routing destinations**: algo suite, DMA, dark pool, capital commitment, RFQ desk, central risk book.

> **Dual-role disclaimer.** The same legal entities (Goldman, Morgan Stanley, UBS, JPM, Citi, BAML) are also **FI dealers** (reached via [[marketaxess]], [[tradeweb]], and dealer-direct RFQ — see [[arch-rfq]]) and **FX liquidity providers** (reached via the FX ECNs). They appear under `brokers/` here because the *equity* routing concept is meaningfully different — equity routing involves smart-order-routing decisions across algos, dark pools, and capital commitment. FI and FX routes hit the dealer through a multi-dealer platform's RFQ flow, not through a dedicated broker note.

### [[multi_asset/]] — Multi-Asset Routable Bloomberg Destinations
Bloomberg products that genuinely span asset classes as **routable** destinations (not terminal screens): [[bloomberg-sef]], [[bloomberg-bmtf]], [[bloomberg-tradebook-us]], [[bloomberg-tradebook-sg]].

## What's *not* a venue (lives in [[70_concepts/terminal_screens/]])

These are Bloomberg Terminal monitor screens. Their market data is accessible via BLPAPI / B-PIPE; **execution** does not target the screen — it targets a real venue or dealer.

- [[bloomberg-allq]] — composite quotes display
- [[bloomberg-btmm]] — Treasury & Money Markets monitor
- [[bloomberg-fit]] — fixed-income trading search
- [[bloomberg-cdsw]] — CDS valuation calculator
- [[bloomberg-swpm]] — swap manager calculator
- [[bloomberg-cbnd]] — corporate bond search
- [[bloomberg-cp-cd]] — CP/CD monitor
- [[bloomberg-tbill]] — T-bill monitor
- [[bloomberg-repo]] — repo monitor

## How a workflow note references venues

In a workflow note's **Steps** section, the destination of a routing action should be a routable venue or broker — never a terminal screen. For example:

> *"Trader views the dealer composite via [[bloomberg-allq]]; on election, the order is routed via [[arch-router-layer]] → [[marketaxess]] RFQ to dealer set { GS, MS, JPM, UBS }."*

The terminal screen is **observed**; the venue is **routed to**. Keeping this distinction crisp prevents the vault from describing fictitious routing paths.

## See also

- [[arch-venue-connectivity]] — venue adapter architecture
- [[arch-smart-order-router]] — strategy layer that selects destination
- [[arch-rfq]] — RFQ as first-class
- [[arch-ioi]] — IOI provenance (some destinations also source IOIs)
- [[asset-class-matrix]] — cross-asset matrix that uses these venues as columns
