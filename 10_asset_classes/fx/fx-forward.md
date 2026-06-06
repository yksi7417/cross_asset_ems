---
type: asset_class
asset_class: fx
sub_class: fx-forward
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/fx/fx-forward]
---

# FX Forward (Outright)

Single-leg FX trade with **value date later than spot** — e.g. "buy EUR/USD 1M forward." Economically [[fx-spot|spot]] + [[forward-points-swap-points|forward points]], traded as a single instrument.

## Venues

- **Primary**: [[refinitiv-fxall]], [[360t]], [[currenex]], [[fx-connect]] (multi-dealer RFQ).
- **Streaming**: [[fxspotstream]] (selected pairs).
- **Pricing**: forward points reference SOFR/SONIA/ESTR curves via [[arch-pricing-service]].

## How to Access Market

Buy-side EMS routes via FIX, typically [[rfq]] mode. Some streaming-price desks for short-dated standard tenors (1W, 1M, 3M).

## RFQ vs CLOB

Almost entirely [[rfq]]. CLOB doesn't work because forward tenors fragment the order book.

## Aggregations / Basket / Netting

Forward + spot legs often paired as [[fx-swap]]. Multi-currency forward hedging baskets [[netting|net]] same-value-date legs.

## Regulatory Reporting

US: [[cftc-sdr]] for deliverable forwards (Dodd-Frank). EU/UK: [[emir-sftr-csdr|EMIR]] reportable for FX forwards (some carve-outs for commercial use).

## Clearing / Settlement

Mostly bilateral. CLS settlement for the major pairs (PvP). Some clearing initiatives via LCH ForexClear but cleared FX forwards remain a minority.

## Documentation Required

ISDA Master + FX Definitions. CSA for variation margin (uncleared margin rules under UMR — Uncleared Margin Rules — for in-scope counterparties).

## Market Notes

- **Fungibility**: Fungible per currency pair + value date. Each forward with a specific currency pair and value date is interchangeable. Broken dates create per-trade non-fungible instances. See [[fungible-vs-non-fungible]].
- **Forward points** ([[forward-points-swap-points]]) reflect rate differentials.
- **Broken dates** trade at interpolated levels; standard tenors trade tighter.
- **UMR** ([[arch-jurisdictional-compliance|Uncleared Margin Rules]]) applies — many forwards now require IM exchange.
- **Hedge accounting** — corporates often use forwards specifically for hedge-accounting documentation purposes.

## Typical Counterparties

Same major dealer FX desks as [[fx-spot]]: JPM, Citi, GS, MS, Deutsche, BNP, UBS, BAML, HSBC, plus regional.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[fxel]] · [[auto-route-fixing-aim]] (corp-treasury forward execution).
