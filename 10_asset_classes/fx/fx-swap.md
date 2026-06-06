---
type: asset_class
asset_class: fx
sub_class: fx-swap
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/fx/fx-swap]
---

# FX Swap

Paired-leg FX trade: **buy one currency vs sell another at spot, simultaneously reversing the trade at a later value date**. Net cash flow = the [[forward-points-swap-points|swap points]] (the rate differential). Economically a **collateralized loan** — common funding tool for banks and corporates.

## Venues

- **Primary**: [[refinitiv-fxall]], [[360t]], [[currenex]], [[fx-connect]].
- **Interdealer**: dealer-direct via FIX for major pairs and tenors.
- **Pricing**: forward-points reference curves via [[arch-pricing-service]].

## How to Access Market

Buy-side EMS routes via FIX as a paired-leg trade — see [[arch-multileg]]. [[rfq]] dominates.

## RFQ vs CLOB

[[rfq]] only — multi-leg requires bespoke quote.

## Aggregations / Basket / Netting

Swap legs net naturally — spot leg + forward leg cancel partially in cash flow terms. Multi-currency portfolios use FX swaps to manage funding gaps.

## Regulatory Reporting

US: [[cftc-sdr]] for the forward leg of swaps (Dodd-Frank treats FX swaps as commodity-pool-act-exempt for some structures). EU/UK: [[emir-sftr-csdr|EMIR]] reportable.

## Clearing / Settlement

Mostly bilateral. Both legs through CLS for major pairs (PvP). Cleared FX swaps via LCH ForexClear is minority.

## Documentation Required

ISDA Master + FX Definitions. CSA for margin.

## Market Notes

- **Fungibility**: Fungible per currency pair + paired value dates. The paired trade structure is the unit of fungibility, not the legs separately. See [[fungible-vs-non-fungible]].
- **Funding tool** — most bank FX-swap flow is interbank funding, not directional.
- **End-of-year** turn — FX swaps spike at year-end for balance-sheet reasons (turn premium).
- **CIP** (Covered Interest Parity) — FX swap rates should track the interest-rate differential; persistent CIP breaks signal funding stress.

## Typical Counterparties

Same major dealer FX desks as [[fx-spot]] and [[fx-forward]].

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[netting-swap-net]] · [[fxel]].
