---
type: asset_class
asset_class: crypto
sub_class: crypto-futures
trade_type: listed_derivative
liquidity: high
status: draft
tags: [asset/crypto/futures]
---

# Crypto Futures (Dated)

Exchange-listed crypto futures with fixed expiry dates. Distinct from [[crypto-perpetual|perpetuals]] (no expiry, funding-rate-anchored). The primary instrument for **US-regulated** crypto-derivative exposure (via CME) and for traditional institutional flow.

## Venues

### US-regulated
- **CME Group** — BTC and ETH futures (standard and micro contracts):
  - BTC: standard (5 BTC), micro (0.1 BTC).
  - ETH: standard (50 ETH), micro (0.1 ETH).
  - Monthly + quarterly listings.
- **CME options on futures** for BTC and ETH.
- **Cboe** — entered/exited crypto futures historically.
- **CFTC-regulated**.

### Offshore
- **Binance Futures dated** (quarterly).
- **OKX dated futures**.
- **Bybit dated futures**.
- **Bitmex** (originator of crypto futures concept).
- **Deribit** (BTC/ETH dated futures).
- **CME-equivalent listing** on EUREX (limited).

## How to Access Market

- CME via FIX (institutional standard).
- Offshore via REST + WebSocket.

## RFQ & Quote Discovery

- CLOB on each venue.
- Calendar spreads (front vs. back month) traded as spread book.
- CME Block facility for institutional blocks.

## Execution / Allocation

- CME: standard FCM-cleared institutional flow.
- Offshore: prop trading / crypto-native fund execution.
- See [[route-single]] / [[route-to-algo]].

## Basket Trading / Aggregations

- Calendar spreads.
- BTC vs ETH ratio trades.
- Spot-future basis (CME BTC future vs underlying BTC index).

## Netting

- CCP at exchange.
- CME: variation margin daily; SPAN initial margin.

## Regulatory Reporting

- **US**: CFTC Form 102 + Part 17/18 large-trader reports (same as other CME futures).
- **EU MiCA** for EU-listed equivalents.
- See [[arch-regulatory-reporting-service]].

## Clearing / Settlement

- CME: cash-settled against Bitcoin Reference Rate (BRR) / Ether Reference Rate (ETHRR) — computed by Crypto Facilities.
- Offshore: physical delivery rare; mostly cash settle.
- Expiry: last Friday of the month for CME standards.

## Documentation Required

- FCM customer agreement (CME flow).
- Risk disclosures.

## Market Notes

- CME BTC futures launched Dec 2017; ETH May 2021.
- BRR reference rate methodology subject to manipulation concerns — heavily monitored.
- CME volume periodically rivals top offshore venues during US hours.
- Used by traditional macro funds and ETF arbitrageurs (BTC spot ETF flow).
- Basis (future-spot) reflects funding cost expectations.

## Typical Counterparties

- Macro hedge funds, asset managers, ETF arbitrageurs, FCMs serving prop firms, family offices.

## Related Workflows

- [[staging-via-fix]] · [[route-to-algo]] · [[allocation-prime-broker]] · [[route-single]]
- [[arch-router-layer]] · [[arch-smart-order-router]] · [[arch-risk-engine]]
- [[arch-jurisdictional-compliance]] (US CFTC + offshore regimes)
- Sibling: [[crypto-spot]] · [[crypto-perpetual]] · [[crypto-options]] · [[equity-futures]] · [[fx-futures]]
