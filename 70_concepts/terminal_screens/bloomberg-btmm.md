---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg BTMM<GO>

**BTMM** ("Treasury & Money Markets monitor") is a Bloomberg Terminal screen that displays **dealer-indicative rates and prices for short-end USD instruments**: T-bills, T-notes, T-bonds (on-the-runs), repo, Fed funds, SOFR, and CP/CD.

> **This is a Bloomberg Terminal monitor screen, not a routable execution destination.** A trader does not "route to BTMM." BTMM is the dashboard view of money-market rate indications; execution against the underlying liquidity routes to a real venue or dealer — see "Where execution actually happens" below.

## What it shows

A monitor of dealer-streamed prices and rates for the US money-market complex: bid / ask / yield / spread to benchmark, by tenor and dealer.

## Where the data comes from

- Dealer rate publications via Bloomberg's contributed-data network.
- Bloomberg-derived benchmarks (USD index curves, OIS curves).
- Accessible programmatically via **B-PIPE** / **BLPAPI** for in-house monitoring.

## Where execution actually happens

| Instrument shown on BTMM | Routable destination |
|---|---|
| On-the-run UST | [[brokertec]] (interdealer CLOB), [[tradeweb]] D2C, dealer RFQ via [[marketaxess]] |
| T-bill auctions | [[treasury-direct]] (primary), then [[brokertec]] / [[tradeweb]] secondary |
| Repo | [[triparty-bnym-jpm]] (tri-party leg), dealer-direct GCF, BrokerTec Repo |
| CP / CD | Dealer-direct via [[arch-venue-connectivity|venue adapters]]; some MarketAxess CP RFQ |
| Fed funds / SOFR cash | Bilateral; no central electronic venue |

The BTMM screen is the **price-discovery view**; the venues above are where the trade happens.

## Related

- [[bloomberg-allq]] (composite quote view) · [[bloomberg-fit]] (FI trading search)
- [[brokertec]] · [[tradeweb]] · [[marketaxess]] · [[treasury-direct]] · [[triparty-bnym-jpm]] (real destinations)
- [[govt-bonds]] · [[money-market-tbills]] · [[money-market-repo]] · [[money-market-cp-cd]] (asset classes)
