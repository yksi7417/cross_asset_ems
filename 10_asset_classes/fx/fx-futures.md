---
type: asset_class
asset_class: fx
sub_class: fx-futures
trade_type: listed_derivative
liquidity: high
status: draft
tags: [asset/fx/fx_futures]
---

# FX Futures

Exchange-listed, centrally-cleared FX futures contracts. Distinct from OTC [[fx-forward|FX forwards]] in clearing model (CCP vs. bilateral), standardization (fixed notional + expiry vs. bespoke), and venue (CME / ICE / EUREX vs. dealer platforms).

## Venues

- [[bloomberg-fit|Bloomberg FIT<GO>]] (FX futures view)
- **CME Group** — primary global exchange for FX futures (EUR/USD, GBP/USD, JPY/USD, CAD/USD, AUD/USD, CHF/USD, MXN/USD, micros)
- **ICE Futures US** — secondary
- **EUREX FX futures** — EU-listed
- **Brokerage front-ends**: FIX or proprietary to broker-clearing-member

## How to Access Market

- CME Globex (electronic, 23-hour) via FIX
- Open outcry remnants (effectively electronic now)
- Block-trading thresholds for off-exchange execution (still cleared)

## RFQ & Quote Discovery

- Continuous order book (CLOB) on Globex.
- Block-trade RFQ for >= block size; reported within 15 min.
- Spread / butterfly RFQ for calendar spreads.

## Execution / Allocation

- Electronic via CLOB; algos available from FCMs (futures-commission-merchants).
- See [[route-to-algo]] / [[route-single]]; venue dispatched via [[arch-smart-order-router|SOR]] or direct.
- Allocation post-fill to client sub-accounts; FCM relays to CCP.

## Basket Trading / Aggregations

- Calendar spreads (near vs. far).
- Inter-currency spreads (EUR/USD vs. GBP/USD).
- Roll trades around quarterly IMM dates.

## Netting

- CCP netting at clearer (CME Clearing); per-contract net long/short.
- Variation margin daily.

## Regulatory Reporting

- CFTC large-trader reporting ([[cftc-sdr]] adjacent — futures reporting under Part 17/18).
- CFTC Form 102 for reportable positions.
- See [[arch-regulatory-reporting-service]] + [[arch-jurisdictional-compliance]].

## Clearing / Settlement

- [[cme-clear|CME Clearing]] as CCP.
- Variation margin daily; initial margin per SPAN.
- Physical delivery on expiry for most pairs (some cash-settle).

## Documentation Required

- FCM customer agreement (replaces [[isda]] for listed).
- No CSA needed (CCP manages margin).

## Market Notes

- Far more standardized than OTC forwards; smaller per-contract notional (typically EUR 125,000 for EUR/USD majors; micro = EUR 12,500).
- Quarterly expiry on IMM dates (3rd Wed of Mar/Jun/Sep/Dec).
- Roll-aware trading; basis between front and back month.
- Cleared = capital-efficient under SA-CCR for buy-side.

## Typical Counterparties

- FCMs, hedge funds, asset managers, central banks (rare), retail platforms.

## Related Workflows

- [[staging-via-fix]] · [[route-to-algo]] · [[route-single]] · [[allocation-prime-broker]]
- [[arch-router-layer]] · [[arch-smart-order-router]] · [[arch-fx-netting]] (CCP-level)
- Sibling: [[fx-spot]] · [[fx-forward]] · [[fx-swap]] · [[fx-ndf]] · [[fx-options]]
