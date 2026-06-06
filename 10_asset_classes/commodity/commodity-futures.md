---
type: asset_class
asset_class: commodity
sub_class: commodity-futures
trade_type: derivative
liquidity: high
status: draft
tags: [asset/commodity/commodity-futures]
---

# Commodity Futures

Standardised exchange-traded contracts for **future delivery of a physical commodity** at a defined price, on a defined date, at a defined location. Includes energy (WTI, Brent, Natural Gas), metals (Gold, Silver, Copper), agriculture (Corn, Wheat, Soybeans), softs (Coffee, Cocoa, Sugar), and livestock.

## Venues

- **Primary US**: CME Group (NYMEX energy + metals + agriculture + livestock).
- **Primary EU/UK**: ICE Futures Europe (Brent, Gas Oil, Sugar, Cocoa), Euronext (European grains).
- **APAC**: SGX (rubber, palm oil), DCE (Chinese soybeans), TOCOM (Japanese rubber).
- **EMS gateway**: [[bloomberg-emsx]] handles futures routing alongside equities.

## How to Access Market

Buy-side EMS routes via FIX through a futures broker (FCM — see [[fcm]]). Direct exchange access for member firms via CME/ICE proprietary protocols (iLink for CME).

## RFQ vs CLOB

[[clob-vs-rfq|CLOB]] — order-driven, price-time priority. Block trades exist (privately negotiated, reported to exchange post-trade) for very large size.

## Aggregations / Basket / Netting

Calendar spreads (long-month vs short-month) trade as paired multi-leg. CCP novation provides interday netting.

## Regulatory Reporting

US: CFTC LTRS (Large Trader Reporting), Form 102/40. EU/UK: position-limits reporting under MiFID II RTS 21. CCP-cleared netting reduces explicit reporting.

## Clearing / Settlement

CCP clearing mandatory — **CME ClearPort** for CME products, **ICE Clear US/Europe** for ICE products. Daily MTM via the [[fcm]] relationship. Physical-delivery contracts settle physically at expiry (rare for most participants — most close out before).

## Documentation Required

Standard futures customer agreement with the FCM. No per-trade documentation (exchange terms standard).

## Market Notes

- **Fungibility**: **Fully fungible** per contract (commodity + delivery month + exchange spec). The standardisation is what makes futures liquid. Per-month contracts are non-fungible across months. See [[fungible-vs-non-fungible]].
- **Roll** — most participants don't take physical delivery; they roll positions from near month to next month.
- **Contango vs backwardation** — futures curve shape signals supply/demand.
- **Position limits** — CFTC and exchanges impose per-trader limits to prevent corner attempts.
- **Globex** — CME's near-23-hour electronic platform; 1-hour daily pause is the only maintenance window — see [[arch-resilience-24x7]].
- **Storage costs** for physical commodities drive the cost-of-carry calculation in futures pricing.

## Typical Counterparties

Major FCMs: GS, JPM, MS, Citi, BAML, BNP, RBC, Marex. Plus commercial firms (oil majors, grain traders, miners) and physical-commodity hedgers.

## Related Workflows

[[staging-via-fix]] · [[route-single]] · [[route-to-algo]] · [[allocation-prime-broker]].
