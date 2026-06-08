---
type: asset_class
asset_class: crypto
sub_class: crypto-spot
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/crypto/spot]
---

# Crypto Spot

Spot trading of digital assets — BTC, ETH, stablecoins (USDT, USDC, DAI), and the long tail. Structurally **similar to FX spot pairs** (e.g. `BTC/USDT`, `ETH/BTC`) but with 24/7/365 trading, on-chain settlement options, no central clearing, and fragmented liquidity across CEXs (centralized exchanges), DEXs (decentralized exchanges), and OTC desks.

## Venues

### Centralized exchanges (CEX)

- **Binance**, **Coinbase Pro / Advanced**, **OKX**, **Bybit**, **Kraken**, **Bitstamp**, **Bitfinex**, **Gemini**, **HTX (Huobi)**, **Bitget**, **KuCoin**.
- **Regulated US**: Coinbase, Kraken, Gemini.
- **Regulated EU**: Bitstamp, Coinbase EU, OKX MiFID.
- **APAC**: Binance Japan, bitFlyer (JP), Upbit / Bithumb (KR), Coinbase Singapore.

### Decentralized exchanges (DEX)

- **Uniswap** (v3, v4), **Curve**, **Balancer**, **PancakeSwap**, **dYdX** (hybrid).
- AMM pricing (constant-product / concentrated-liquidity); not CLOB.

### OTC desks

- Cumberland (DRW), Genesis (defunct), Galaxy Digital, B2C2, Wintermute, GSR, Falcon, Bitstamp OTC. Bilateral block execution; voice + chat.

### RFQ / liquidity-aggregator platforms

- **Talos**, **FalconX**, **Paradigm** (block + complex), **Hidden Road**, **Wintermute Trading API**. Cross-venue RFQ.

## How to Access Market

- REST + WebSocket APIs to CEX (no FIX historically; some venues now offer FIX — e.g. Coinbase, OKX, BitMEX).
- Smart-contract calls for DEXs (gas + slippage considerations).
- Voice / chat for OTC.

## RFQ & Quote Discovery

- CEX CLOB depth aggregated by SOR ([[arch-smart-order-router]]).
- DEX prices via on-chain quote (real-time AMM math).
- OTC block-size RFQ via Paradigm / FalconX / Talos.

## Execution / Allocation

- High-frequency CEX execution via REST/WS (FIX where available).
- DEX execution: signed transactions, MEV protection (private mempools, flashbots).
- Allocation: typically same-account; institutional with sub-accounts; PB models emerging (FalconX, Hidden Road).

## Basket Trading / Aggregations

- Cross-venue best-ex via SOR critical due to fragmentation.
- Stablecoin arbitrage (USDT vs USDC vs DAI).
- Triangular arb (BTC/USDT × ETH/BTC × USDT/ETH).

## Netting

- Bilateral for OTC.
- CEX-internal netting.
- No central clearer for spot (unlike futures, see [[crypto-futures]]).
- On-chain settlement = atomic but irreversible.

## Regulatory Reporting

- **US**: SEC scrutiny for securities-classified tokens; FinCEN MSB rules; FATCA / IRS digital-asset reporting.
- **EU MiCA** (Markets in Crypto-Assets Regulation): full regime effective 2024-2025; CASP licensing.
- **UK**: FCA registration for crypto activities; new regime in progress.
- **HK**: SFC Type 1 + 7 licenses for VASPs.
- **SG**: MAS DPT licensing.
- **JP**: JFSA crypto exchange registration.
- **AML / Travel Rule**: FATF Recommendation 16; counterparty info on transfers > USD 1,000.
- See [[arch-jurisdictional-compliance]] for cross-border.

## Clearing / Settlement

- CEX: internal book + on-chain withdrawal at user request.
- DEX: instantaneous on-chain settlement.
- OTC: typically T+0 wire (USD) + on-chain transfer; sometimes pre-funded.

## Documentation Required

- Exchange T&Cs + KYC.
- OTC: customer agreement + (sometimes) ISDA-equivalent.
- Wallet whitelist arrangements.

## Market Notes

- **24/7/365** — no settlement holidays; price discovery never pauses; PnL / risk continuous.
- Liquidity fragmentation: top-10 CEXs hold majority but no single venue dominates globally.
- Stablecoin de-pegging risk (USDC March 2023, USDT periodic).
- On-chain vs off-chain price divergence (kimchi premium, Mt. Gox style).
- Custody risk distinct from trade risk: cold/hot wallet management is its own discipline.
- MEV (Maximal Extractable Value) on DEX trades — sandwich attacks etc.

## Typical Counterparties

- Crypto-native hedge funds, traditional asset managers entering, market makers, prop firms, family offices, retail (via brokers).

## Related Workflows

- [[staging-via-fix]] (where FIX available) · [[arch-bulk-io]] (CEX trade history imports common)
- [[route-to-rfq]] (Paradigm, FalconX) · [[arch-smart-order-router]] (cross-venue SOR essential)
- [[arch-risk-engine]] · [[arch-position-service]] (multi-venue + on-chain reconciliation)
- [[arch-jurisdictional-compliance]] (MiCA, MAS, sanctions OFAC including tornado-cash style)
- Sibling: [[crypto-perpetual]] · [[crypto-futures]] · [[crypto-options]] · [[fx-spot]]
