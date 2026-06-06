---
type: asset_class
asset_class: crypto
sub_class: crypto-perpetual
trade_type: derivative
liquidity: very_high
status: draft
tags: [asset/crypto/perpetual]
---

# Crypto Perpetuals (Perp Swaps)

Perpetual futures-like contracts on crypto. **No expiry** — funded by a periodic **funding rate** mechanism that pulls the perp price toward the spot index. The dominant crypto-derivative instrument by volume globally; far outweighing [[crypto-futures|dated futures]] and [[crypto-options|options]].

## Venues

- **Binance Futures** (USDT-margined + COIN-margined perps) — dominant by volume.
- **OKX Perpetuals**.
- **Bybit USDT / Inverse Perpetuals**.
- **Bitget**, **HTX (Huobi) DM**, **MEXC**.
- **Deribit** (USDC-margined perps in addition to BTC/ETH options).
- **dYdX v4** — decentralized perpetual exchange.
- **Hyperliquid** — DeFi perp.
- **GMX**, **Aevo**, **Vertex** — DeFi alternatives.

US-regulated equivalents:
- **CME** does not offer perpetuals — it offers dated futures only (see [[crypto-futures]]).
- **Coinbase** offered some perps internationally (Bahamas / non-US).

## How to Access Market

- REST + WebSocket; FIX availability is venue-specific and limited.
- DEX perps via smart-contract.

## Funding rate mechanism

Periodic (typically every 8 hours, or every hour on some venues) cash exchange between longs and shorts based on:

```
funding_rate = clamp(premium_index + interest_rate_component, -cap, +cap)
```

- `premium_index = (perp_mid - spot_index) / spot_index`
- Positive funding → longs pay shorts (perp trading above spot → cool the longs).
- Negative funding → shorts pay longs.
- Settled in margin currency.

Funding is a **first-class P&L component** for perps — strategies often target funding harvesting independent of price direction.

## RFQ & Quote Discovery

- CLOB on CEX venues; depth visible.
- DEX perps may use peer-to-pool models (e.g. GMX) with different price discovery.

## Execution / Allocation

- High-frequency CLOB.
- Cross-margin vs isolated margin (per position) modes.
- Allocation: usually per-account; sub-account models on Binance / OKX / Bybit allow partitioning.

## Basket Trading / Aggregations

- Basis trade: spot long + perp short (or vice versa) earning funding.
- Cross-venue funding arb.
- Index perps (BTC perp vs ETH perp delta-neutral).

## Netting

- CCP-equivalent within exchange (exchange acts as CCP).
- DEX perps have on-chain margin pool.

## Regulatory Reporting

- Most CEX perps are offshore / non-US-regulated. US Persons typically restricted by venue.
- US-regulated perps require CFTC registration as SEF / DCM — limited adoption.
- See [[arch-jurisdictional-compliance]] crypto sections.

## Clearing / Settlement

- Exchange acts as CCP; positions marked at exchange's mark price.
- Liquidations cascade through the order book; insurance fund covers shortfall.
- Auto-deleveraging (ADL) as last resort.
- Funding settled in cash (margin currency) at funding interval.

## Documentation Required

- CEX T&Cs + risk disclosures (margin / liquidation).
- DEX: smart-contract terms.

## Market Notes

- Daily liquidations measured in $100M+ for crypto-wide.
- Funding rates can spike to >100% annualized in stressed markets — alpha source.
- Leverage typically up to 100x on Binance / Bybit; lower elsewhere.
- Mark price ≠ last price; venues use index-based mark to prevent liquidation gaming.
- "Cascade liquidations" can drive 10-20% intraday moves in major coins.

## Typical Counterparties

- Crypto-native hedge funds, market makers (Jump, Wintermute, GSR, Auros), prop firms, sophisticated retail.

## Related Workflows

- [[route-to-algo]] · [[arch-smart-order-router]] · [[arch-risk-engine]] (continuous liquidation risk)
- [[arch-position-service]] (mark-to-market continuous) · [[arch-pricing-service]] (funding-rate-aware valuation)
- Sibling: [[crypto-spot]] · [[crypto-futures]] · [[crypto-options]]
