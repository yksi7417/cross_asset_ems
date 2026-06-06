---
type: asset_class
asset_class: fixed_income
sub_class: mbs
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/fixed_income/mbs]
---

# Mortgage-Backed Securities (MBS)

Pools of residential mortgages packaged into pass-through or structured securities. **Agency MBS** (Fannie Mae, Freddie Mac, Ginnie Mae) is the second-largest fixed-income market after USTs. **Non-agency MBS** (private-label) is much smaller post-2008. The **TBA** market is the operational dominant venue.

## Venues

- **TBA execution**: [[bloomberg-tba]] (workflow + BMTF execution leg), [[tradeweb]] (D2C TBA RFQ + specified pools), dealer-direct via FIX.
- **Specified pools**: dealer-direct RFQ; Tradeweb specified-pool tools; [[bloomberg-bwic-owic|BWICs]] for portfolios.
- **CMOs / structured tranches**: dealer-direct; [[bloomberg-bwic-owic]] for portfolio rebalancing.
- **Reference**: [[bloomberg-fit]] (price discovery).

## How to Access Market

TBA via FIX to [[tradeweb]] or dealer-direct (most major dealers offer TBA RFQ via FIX). Specified pools require RFQ-style negotiation with stipulations. Dollar rolls trade as paired tickets — see [[dollar-roll]].

## RFQ vs CLOB

Almost all [[rfq]]. **TBAs** are homogeneous enough to support fast-RFQ workflows. **Specified pools** require detailed stipulations ([[wac-wam-wala]], geography, FICO bands) per RFQ.

## Aggregations / Basket / Netting

[[dollar-roll]] is a paired-leg trade. **TBA settlement** follows SIFMA monthly Class A/B/C/D schedule — rolls and original trades net at the same settlement date. Specified pools settle separately (often different dates). [[bloomberg-bwic-owic|BWICs]] for portfolio rebalancing.

## Regulatory Reporting

US: [[trace]] for Agency MBS (since 2011) and specified pools (since 2013). ABS / CMBS / CDO reporting added 2018.

## Clearing / Settlement

US TBA: monthly Class A/B/C/D settlement per SIFMA calendar — see [[sifma-tba-guidelines]]. Cleared through DTCC; many dealer-vs-dealer TBA legs net via MBS Direct (DTCC). Specified pools: per-trade settle.

## Documentation Required

[[sifma-tba-guidelines]] — the operating standard for TBA Good Delivery rules. Master Securities Forward Trade Agreement (MSFTA) for TBA. Per-pool factor sheets for specified pools.

## Market Notes

- **Fungibility**: **TBAs are fungible at the trade level** (Fannie 30-year 5.5% July TBA is interchangeable with any other matching coupon/agency/settle) — that's what makes the market liquid. **The underlying deliverable pools are non-fungible** — the seller chooses which specific pools to deliver per Good Delivery rules. See [[fungible-vs-non-fungible]] and [[tba-vs-specified-pool]].
- **TBA / specified pool distinction** ([[tba-vs-specified-pool]]) is foundational.
- **Prepayment risk** is the dominant valuation driver — drives pay-ups, OAS analysis, hedging.
- **Negative convexity** — MBS values poorly in rate moves both directions (prepay accelerates / extends).
- **WAC, WAM, WALA** ([[wac-wam-wala]]) characterise specific pools' prepay profile.
- **Origination supply cycle** drives TBA spreads (more origination → more TBA supply → wider).

## Typical Counterparties

MBS specialists: GS, MS, JPM, Citi, BAML, Wells Fargo, Credit Suisse (now UBS), Mizuho (mortgage REIT relationships), Cantor Fitzgerald.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[bloomberg-bwic-owic]] · [[dollar-roll]] · [[allocation-prime-broker]].
