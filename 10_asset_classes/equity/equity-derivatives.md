---
type: asset_class
asset_class: equity
sub_class: equity-derivatives
trade_type: derivative
liquidity: high
status: draft
tags: [asset/equity/equity-derivatives]
---

# Equity Derivatives (Overview)

Umbrella term covering listed equity options, equity futures, total-return swaps on equity, variance swaps, dispersion products, and structured equity-linked notes. Distinct sub-pages: [[equity-options]], [[equity-futures]], [[equity-swaps]].

## Venues

- **Listed options**: [[cboe-bzx]] options venues (largest US options family), [[nasdaq]] options venues, [[nyse]] options venues. Plus EU/APAC equivalents (Eurex Options, Euronext Options, HKEX Options).
- **Listed futures (single-stock + index)**: ICE Futures, [[jpx-tse]] (Nikkei futures), Eurex, Hong Kong Futures Exchange.
- **OTC equity swaps**: dealer-direct via [[bloomberg-ib]] / [[bloomberg-swpm]] (pricing tool — see [[bloomberg-swpm]]).
- **EMS gateway**: [[bloomberg-emsx]] handles listed options routing.

## How to Access Market

Listed: FIX routing through brokers to options exchanges or direct DMA. OTC equity swaps: dealer RFQ via FIX or Bloomberg-IB chat. ISDA-documented bilateral.

## RFQ vs CLOB

Listed options: [[clob-vs-rfq|CLOB]] — order-driven. Index futures: CLOB. OTC swaps: [[rfq]] / bilateral.

## Aggregations / Basket / Netting

Dispersion baskets (long single-name vol, short index vol) are paired multi-leg. Listed options spreads / strangles / straddles trade as paired tickets. [[netting]] within strikes / expiries.

## Regulatory Reporting

US: [[finra-cat]] (full lifecycle); listed options also report to OCC. EU/UK: MiFID II [[rts-22-27-28|RTS 22]] + EMIR for OTC swaps. See [[emir-sftr-csdr]].

## Clearing / Settlement

US listed options: OCC clearing, T+1 settle. US index futures: CME clearing. Equity swaps: bilateral, sometimes cleared via LCH SwapAgent.

## Documentation Required

OTC: [[isda]] / [[csa]]. Listed: standard exchange terms.

## Market Notes

- **Fungibility**: Listed contracts are fungible per option series (underlying + strike + expiry); OTC swaps are non-fungible (bespoke terms). See [[fungible-vs-non-fungible]].
- **Listed options skew** — single-name options have stock-specific skew driven by event risk.
- **Index options** (SPX, NDX, RUT) are the dominant listed-vol franchise — Cboe-exclusive in US.
- **Variance swaps** are OTC; index variance vs realised is the dominant trade.

## Typical Counterparties

Listed: market makers (Citadel Securities, Susquehanna, Optiver, Wolverine, IMC). OTC: bulge-bracket dealers (GS, MS, JPM, Citi, BNP, Goldman, Société Générale especially in EU vol).

## Related Workflows

[[staging-via-fix]] · [[route-to-algo]] · [[route-to-rfq]] (OTC) · [[allocation-prime-broker]].
