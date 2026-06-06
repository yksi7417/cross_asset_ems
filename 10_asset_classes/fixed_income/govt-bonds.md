---
type: asset_class
asset_class: fixed_income
sub_class: govt-bonds
trade_type: cash_security
liquidity: very_high
status: draft
tags: [asset/fixed_income/govt-bonds]
---

# Government Bonds

Cash-settled debt instruments issued by sovereign governments and their agencies. The deepest, most liquid fixed-income asset class. **US Treasuries** are the global benchmark; major EU sovereigns (Bund, OAT, BTP, Bonos, Gilt), JGBs (Japan), ACGBs (Australia), and CGBs (China) anchor regional markets.

## Venues

- **D2D (interdealer)**: [[brokertec]] (UST CLOB), [[mts]] (EU govt CLOB), [[yieldbroker]] D2D (AUD govt).
- **D2C**: [[tradeweb]] (UST + EU govt RFQ + CLOB), [[mts]] BondVision (EU D2C), [[marketaxess]] (UST Live Markets), [[opendoor]] (off-the-run UST sessions).
- **Primary**: [[treasury-direct]] (US auctions via TAAPS for primary dealers; web for retail), national DMOs for EU sovereigns.

## How to Access Market

Buy-side EMS routes via FIX to D2C platforms ([[tradeweb]] / [[marketaxess]]) for normal flow; primary dealer access for auctions is through the dealer relationship. See [[bloomberg-fit]] / [[bloomberg-btmm]] for price-discovery screens (not routable).

## RFQ vs CLOB

[[on-the-run-vs-off-the-run]] is the dominant axis: **on-the-runs** trade two-way on CLOB ([[brokertec]] D2D, click-to-trade D2C); **off-the-runs** typically RFQ via [[tradeweb]] / [[opendoor]]. See [[clob-vs-rfq]].

## Aggregations / Basket / Netting

[[netting]] across same-CUSIP buy/sell legs of a desk is standard. **Portfolio trading** of govt baskets exists but is less common than in credit. Repo financing dovetails closely — see [[money-market-repo]] and [[gcf-repo]].

## Regulatory Reporting

US: TRACE reporting for UST (regulator-only since 2017, not public dissemination). EU: MiFID II post-trade transparency via APAs ([[rts-22-27-28]]). UK: UK MiFIR equivalent. Plus per-jurisdiction primary-dealer reporting to the central bank.

## Clearing / Settlement

US UST: T+1 settlement at Fedwire Securities; FICC (the Fixed Income Clearing Corporation) clears interdealer flow + some D2C — see [[ficc-clearing]]. EU govts: T+2 at Euroclear or Clearstream; LCH RepoClear / Eurex Clearing for repo financing. See [[tplus-1-tplus-2]] and [[dvp-rvp-fop]].

## Documentation Required

Standard MSA / GMRA for repo. ISDA for any related derivatives ([[interest-rate-swaps]]). For sovereign-related credit: per-issuer prospectus and any sanctions / AML screening (see [[arch-jurisdictional-compliance]]).

## Market Notes

- **Fungibility**: Fungible per CUSIP — every UST under a given CUSIP is interchangeable. OTRs (on-the-runs) trade as the dominant fungible block per tenor. See [[fungible-vs-non-fungible]].
- **On-the-run / off-the-run liquidity wedge** ([[on-the-run-vs-off-the-run]]) is the structural quirk every algo must model.
- **Auction calendar** anchors UST market — pre-auction WI trading flows; see [[when-issued]].
- **Repo specials** (when a UST trades rich in repo) signal short interest — repo + cash markets are tightly linked.
- US Treasury TRACE post-trade data is regulator-only (not public dissemination like corporates).

## Typical Counterparties

Major US: GS, MS, JPM, Citi, BAML, Barclays, BNP, Deutsche, Morgan Stanley (primary dealers list). EU: BNP, Citi, Goldman, JPM, Morgan Stanley, UBS, Deutsche. APAC: regional primary dealers per sovereign.

## Related Workflows

[[staging-via-fix]] · [[route-single]] (on-the-run CLOB) · [[route-to-rfq]] (off-the-run) · [[multi-route-rfq]] · [[bloomberg-bwic-owic]] (govt BWICs for portfolio rebalancing).
