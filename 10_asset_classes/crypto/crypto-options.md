---
type: asset_class
asset_class: crypto
sub_class: crypto-options
trade_type: listed_derivative
liquidity: moderate
status: draft
tags: [asset/crypto/options]
---

# Crypto Options

Options on crypto underlyings — BTC and ETH dominate, with a long tail (SOL, others). **European-style** typically (cash-settled at expiry). Most liquidity historically on **Deribit**; CME has grown in regulated flow; new venues (Aevo, Lyra, Premia) bringing on-chain options.

## Venues

- **Deribit** — dominant crypto options venue globally for BTC/ETH; LSE-owned (since Coinbase acquisition).
- **CME** — BTC and ETH options on futures (US-regulated).
- **OKX Options**, **Binance Options** — smaller share, growing.
- **Aevo**, **Lyra Finance**, **Premia**, **Hegic** — DeFi options.
- **Paradigm** — multi-venue options RFQ / block trading.

## How to Access Market

- REST + WebSocket (Deribit, OKX); FIX limited.
- CME via FIX.
- DeFi via smart contracts.

## RFQ & Quote Discovery

- Deribit CLOB for liquid strikes.
- Paradigm multi-venue RFQ for blocks and structured trades.
- Voice / IB for very large blocks.

## Execution / Allocation

- Standard CLOB for vanilla strikes.
- Complex structures (spreads, straddles, condors, dispersion) via Paradigm or voice.
- See [[arch-multileg|multileg]] for combo execution.

## Basket Trading / Aggregations

- Multi-leg combos atomic.
- Volatility trades (long/short vol via straddles + delta hedge).
- Dispersion trades (sell index vol, buy single-name vol).

## Netting

- CCP at Deribit / CME.
- Cross-margin within venue for hedged structures.

## Regulatory Reporting

- CME options on futures: CFTC reporting (Part 17/18).
- Deribit: not subject to US/EU futures-style reporting; subject to local regulator (Dutch / UK).
- Per-jurisdiction: see [[arch-jurisdictional-compliance]].

## Clearing / Settlement

- Deribit: cash-settled against Deribit BTC/ETH index at 08:00 UTC on expiry; daily, weekly, monthly, quarterly expiries.
- CME: cash-settled against BRR / ETHRR.
- DeFi: smart contract settles to underlying or stablecoin.

## Documentation Required

- Venue T&Cs + risk disclosures.

## Market Notes

- Deribit handles vast majority of global crypto-options notional.
- Highly volatile IV — major events (Fed meetings, BTC halvings, ETF news) move vol dramatically.
- 24/7 trading; settlement at single UTC time creates "settlement hour" volatility.
- IV term structure + skew tradeable; OTM puts often expensive vs OTM calls (different from equity).
- Funding-rate-aware pricing: implied funding from option box prices.

## Typical Counterparties

- Crypto market makers (Jump, Wintermute, GSR, Auros), volatility funds, prop firms, sophisticated retail.

## Related Workflows

- [[route-to-rfq]] (Paradigm) · [[arch-multileg]] · [[arch-router-layer]]
- [[arch-pricing-service]] (IV surface for crypto) · [[arch-risk-engine]] (greeks)
- Sibling: [[crypto-spot]] · [[crypto-perpetual]] · [[crypto-futures]] · [[equity-options]]
