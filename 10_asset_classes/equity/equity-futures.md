---
type: asset_class
asset_class: equity
sub_class: equity-futures
trade_type: listed_derivative
liquidity: very_high
status: draft
tags: [asset/equity/equity_futures]
---

# Equity Futures

Exchange-listed futures on equity indices (S&P 500, NASDAQ 100, Russell 2000, FTSE, DAX, Nikkei, Hang Seng, KOSPI) and single-stock futures (SSF — limited markets). Equity index futures are among the **most liquid derivatives** in the world, used for hedging, beta exposure, cash management, and tactical positioning.

## Venues

- **CME Group**: ES (E-mini S&P 500), NQ (E-mini NASDAQ), RTY (E-mini Russell), YM (E-mini Dow), MES/MNQ/M2K/MYM (micros).
- **ICE Futures US**: Russell 2000, MSCI indices.
- **EUREX**: FESX (Euro Stoxx 50), FDAX (DAX), FESB (Euro Stoxx Banks), FTI (TAIEX), SSF on top names.
- **JPX / Osaka**: Nikkei 225, TOPIX futures.
- **HKEX**: Hang Seng (HSI, MHI), Hang Seng China Enterprises (HHI).
- **SGX**: A50 (China), Nifty, Nikkei.
- **B3** (Brazil): Bovespa futures.

## How to Access Market

- CLOB on the venue's electronic trading system (CME Globex, EUREX T7, HKEX HKATS, etc.).
- Continuous trading near 24-hour for major contracts.
- Block trade rules above threshold.
- TAS (Trade At Settlement) and TAM (Trade At Marker) for execution at session benchmarks.

## RFQ & Quote Discovery

- Calendar spread RFQ via exchange spread book.
- Index basket arbitrage relative to underlying constituents.

## Execution / Allocation

- Electronic via CLOB; algos abundant (TWAP, VWAP, POV, IS) from FCMs and [[fx-automation-tradebest|TradeBest]]-like flow.
- See [[route-to-algo]] / [[route-single]].

## Basket Trading / Aggregations

- Calendar spreads (front vs. back month).
- Roll trades on the IMM cycle (3rd Wed of Mar/Jun/Sep/Dec).
- Index arb basket against constituents (program trading).

## Netting

- CCP-level (CME Clearing, EUREX Clearing, OCC for SSF in US).
- Daily variation margin.

## Regulatory Reporting

- CFTC large-trader (Part 17/18) for US.
- ESMA EMIR for EU.
- See [[arch-regulatory-reporting-service]] / [[arch-jurisdictional-compliance]].

## Clearing / Settlement

- CCPs: [[cme-clear|CME Clearing]], EUREX Clearing, OSE/JSCC, HKCC.
- Cash settlement at expiry against official index settlement value.
- SSF physical or cash depending on contract.

## Documentation Required

- FCM customer agreement.
- Risk disclosures.

## Market Notes

- ES front-month often trades 1M+ contracts/day.
- Index futures used as the primary tool for portfolio beta hedging.
- Basis trade (cash vs. future) is a fundamental relationship arbitraged tightly.
- Roll period sees concentrated volume in the spread between expiries.
- ETF arb (e.g. SPY vs ES) is high-frequency, ultra-low-latency territory.

## Typical Counterparties

- Asset managers, hedge funds, banks (delta hedging), prop firms, market makers, pension funds (overlay strategies), central banks (occasional).

## Related Workflows

- [[staging-via-fix]] · [[route-to-algo]] · [[route-single]] · [[allocation-prime-broker]]
- [[arch-router-layer]] · [[arch-smart-order-router]] · [[arch-pretrade-analytics]] (slicer / scheduler)
- Sibling: [[cash-equity]] · [[equity-options]] · [[equity-derivatives]] · [[equity-swaps]] · [[fx-futures]]
