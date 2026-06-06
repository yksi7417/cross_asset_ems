---
type: asset_class
asset_class: event_contracts
sub_class: prediction-markets
trade_type: binary_contract
liquidity: moderate
status: draft
tags: [asset/event_contracts/prediction_markets]
---

# Prediction Markets / Event Contracts

Binary (or scalar) contracts that pay out based on the **outcome of a real-world event** — elections, weather, sports, economic indicators, corporate events, geopolitical outcomes. Distinct asset class because:

- Payouts are **discrete** (typically 0 or 1 for binaries; sometimes scalar in range).
- Underlying is **non-financial** (no continuously-traded reference price).
- Settlement is determined by an **oracle / resolution authority**.
- Regulatory status varies dramatically by jurisdiction.

The user category named was Polymarket; the broader institutional space includes Kalshi (CFTC-regulated), CME event contracts, ICE event contracts, and various decentralized markets.

## Venues

### Regulated US
- **Kalshi** — CFTC-regulated DCM (Designated Contract Market) for event contracts; 2020 launch, expanding rapidly.
- **CME event contracts** — CME's own event-contract launch covering equity index outcomes (e.g. "will S&P 500 close above X").
- **ICE event contracts** — similar to CME.
- **Forecast Foundation** (defunct / Augur) — early DeFi.

### Crypto / DeFi
- **Polymarket** — USDC-settled, on-chain (Polygon); CFTC enforcement action 2022 led to non-US-only access for some markets; reorienting under new framework.
- **Manifold Markets**.
- **PredictIt** — operated under CFTC no-action letter (currently being wound down).
- **Stake.com / Smarkets** — sports / event betting (overlap with gambling).

## Contract types

| Type | Payoff |
|---|---|
| **Binary** | $1 if outcome A, $0 otherwise (or vice versa). Prices reflect implied probability. |
| **Scalar** | Payout proportional to outcome within a range (e.g. "CPI between X and Y"). |
| **Multi-outcome** | One of N possible outcomes pays $1; rest pay $0. |
| **Conditional** | "If A, then B" structures. |

## How to Access Market

- Kalshi / CME / ICE: standard exchange APIs (FIX where supported).
- Polymarket: smart contract calls + signed orders via UMA / Polymarket order book.
- DeFi: on-chain signed orders + smart-contract settlement.

## RFQ & Quote Discovery

- CLOB for liquid markets (elections, major equity-event outcomes).
- Thin books for niche markets (specific weather, individual sports outcomes).
- AMM-style pricing on some DeFi venues.

## Execution / Allocation

- Standard CLOB for institutional venues.
- For DeFi: gas-aware execution; off-chain matched orders, on-chain settlement.

## Basket Trading / Aggregations

- Outcome baskets: e.g. all combinations of an election + Senate + House.
- Conditional baskets: "if X president, then equity index will be Y" — rare; usually traded as separate legs.

## Netting

- Per-venue position netting.
- No cross-venue netting (different oracles / resolutions).

## Regulatory Reporting

- **US**: Kalshi reports as DCM under CFTC rules; participants subject to large-trader reporting where applicable.
- **EU**: prediction markets generally fall outside MiFID (not financial instruments) but some are classified as gambling.
- **Crypto-based markets**: subject to MiCA (where applicable) + AML / Travel Rule + sanctions screening.

## Clearing / Settlement

- Regulated venues: standard CCP (Kalshi clears via its own facilities; CME via CME Clearing).
- DeFi: smart-contract settles on oracle reveal of outcome.
- Time to resolution can be hours (closing prints) to months (long-horizon political outcomes).

## Documentation Required

- Venue T&Cs.
- For DeFi: wallet + smart-contract terms.

## Market Notes

- **Probability-implied pricing**: a binary contract at $0.65 implies 65% probability — useful aggregated signal.
- **Resolution risk**: ambiguous outcomes (e.g. "did X happen" where definition is contested) need clear oracle rules; failure modes have produced disputes.
- **Long-tail thin markets**: prices can be illiquid and prone to manipulation if low volume.
- **Event correlation**: portfolio of event contracts can hedge or amplify exposures from traditional markets.
- **Regulatory grey areas**: line between event contracts and gambling is jurisdiction-specific.

## Typical Counterparties

- Retail (predominantly on Polymarket / Kalshi).
- Quantitative / event-driven funds taking systematic positions.
- Market makers (Jane Street is rumored active in some).
- Information-aggregating firms / journalists (for signal extraction).

## Related Workflows

- [[staging-via-ticket]] · [[staging-via-fix]] (where supported)
- [[route-single]] · [[arch-validator]] · [[arch-compliance]] (jurisdiction screening important)
- [[arch-jurisdictional-compliance]] (US CFTC for Kalshi/CME; non-US for Polymarket)
- [[arch-position-service]] · [[arch-pricing-service]] (probability-mapped valuations)
- Sibling concepts: standard derivatives ([[equity-options]]), crypto ([[crypto-options]])
