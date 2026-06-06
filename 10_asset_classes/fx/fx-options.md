---
type: asset_class
asset_class: fx
sub_class: fx-options
trade_type: derivative
liquidity: moderate
status: draft
tags: [asset/fx/fx-options]
---

# FX Options

Options on FX rates — vanilla calls/puts plus a large universe of exotics (barriers, digitals, lookbacks, baskets, target-redemption). Common hedging tool for corporate treasury and structured-product manufacturing.

## Venues

- **Primary**: [[refinitiv-fxall]] FX options, [[360t]] FX options, dealer-direct via FIX or [[bloomberg-ib]] for less standard structures.
- **Pricing**: vol surface from [[arch-pricing-service]] (per-pair smile/skew/butterfly).

## How to Access Market

Buy-side EMS routes via FIX with structure-aware messages (strike, expiry, notional, exercise style). Exotic structures may flow through [[bloomberg-ib]] chat to a structured-products specialist.

## RFQ vs CLOB

[[rfq]] only. Two-way prices common for vanilla majors; one-way for exotics.

## Aggregations / Basket / Netting

Strategy structures (straddle, strangle, risk-reversal, butterfly) trade as multi-leg packages. Volatility-surface hedging requires correlated positions across strikes/expiries.

## Regulatory Reporting

US: [[cftc-sdr]] (FX options are CFTC-regulated swaps). EU/UK: [[emir-sftr-csdr|EMIR]].

## Clearing / Settlement

Mostly bilateral. UMR ([[arch-jurisdictional-compliance|Uncleared Margin Rules]]) applies — IM exchange for in-scope counterparties.

## Documentation Required

ISDA Master + FX Definitions + exotic-specific term sheets. CSA for margin.

## Market Notes

- **Fungibility**: Vanilla options are fungible per (pair, strike, expiry, exercise style); exotics are non-fungible (each barrier / structure is bespoke). See [[fungible-vs-non-fungible]].
- **Vol surface** — implied vol by strike and tenor; the surface shape encodes skew (currency direction sensitivity) and butterfly (smile).
- **Risk reversal** is the standard skew benchmark (25-delta call vol minus 25-delta put vol).
- **TARN** (Target Redemption Note) — popular EM-yield-enhancement product with knock-out features.
- **Corporate hedging** — many corporates use plain-vanilla options for FX risk management.

## Typical Counterparties

Vol specialists: GS, JPM, MS, Citi, Deutsche, BNP, UBS, HSBC (APAC vol), Standard Chartered (EM vol), Nomura (JPY vol).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[allocation-prime-broker]].
