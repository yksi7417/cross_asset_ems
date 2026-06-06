---
type: asset_class
asset_class: fixed_income
sub_class: convertibles
trade_type: hybrid
liquidity: low
status: draft
tags: [asset/fixed_income/convertibles]
---

# Convertible Bonds

**Convertible bonds** are corporate debt instruments that include an embedded option to convert into the issuer's equity at a defined ratio. The hybrid structure means convertible-bond pricing depends on both the bond floor (credit + rates) and the equity option (volatility + share price).

## Venues

- **Primary**: dealer-direct via FIX / chat (specialist convertible desks).
- **Limited electronification** vs IG corp — convertibles remain mostly bilateral.
- **Some**: [[marketaxess]] selected lines; [[tradeweb]] selected; [[bloomberg-ib]] chat dominant.
- **Reference**: [[bloomberg-cbnd]] (terminal screen for search).

## How to Access Market

Dealer-direct via FIX for active issues. [[bloomberg-ib]] chat for less-active or large-block. Convertible-specialist desks are concentrated at a handful of firms.

## RFQ vs CLOB

[[rfq]] only. Two-sided dealer markets exist for the most active issues but most lines are one-way (dealer has a position to offer or wants to buy).

## Aggregations / Basket / Netting

Convertible-arbitrage funds typically pair the convert with a short equity hedge — multi-leg. Some [[portfolio-trading]] in active convertible portfolios.

## Regulatory Reporting

US: [[trace]] covers convertibles. EU/UK: MiFID II.

## Clearing / Settlement

T+2 at [[dtc]] via DVP. Conversion events require equity-leg coordination.

## Documentation Required

Per-bond: indenture with detailed conversion mechanics, dividend pass-through provisions, call schedule, put dates, change-of-control puts.

## Market Notes

- **Fungibility**: Fungible per CUSIP. The convertible is one CUSIP; the underlying equity is a different CUSIP. The hedge requires routing on both. See [[fungible-vs-non-fungible]].
- **Equity hedge** is essential — most convertible flow is paired with short equity / delta hedge.
- **Vol sensitivity** — convertible value rises with equity vol (the embedded option becomes worth more).
- **Credit + equity link** — distressed convertible names trade like distressed credit + warrant.
- **Mandatory convertibles** (forced conversion at maturity) are a distinct product.
- **Convertible arb hedge funds** are major participants — large position holders during accumulation, can be forced sellers during stress.

## Typical Counterparties

Convertible specialists: BAML, GS, JPM, Citi, MS, Credit Suisse (now UBS), Mizuho, Cantor Fitzgerald. Hedge funds: Citadel, Millennium, Aristeia, Calamos (long-only).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[bloomberg-bwic-owic]] · [[allocation-prime-broker]] · paired with [[cash-equity]] hedge.
