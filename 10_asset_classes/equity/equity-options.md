---
type: asset_class
asset_class: equity
sub_class: equity-options
trade_type: listed_derivative
liquidity: high
status: draft
tags: [asset/equity/equity_options]
---

# Equity Options

Listed equity and equity-index options (calls, puts, spreads, butterflies, condors, straddles, strangles, ratios, and combos). Settlement is **American** for single-stock options (early exercise possible), **European** for cash-settled index options. Distinct from over-the-counter equity-linked derivatives ([[equity-swaps|equity swaps]], [[convertibles|convertibles]]).

## Venues

- **US**: Cboe (BZX, EDGX, C2), NYSE Arca/Amex Options, NASDAQ PHLX/BX/ISE, MIAX (MIAX, PEARL, EMERALD), BOX. 16+ US options exchanges with linkage.
- **EU**: EUREX, Euronext Equity Derivatives, NASDAQ OMX Nordic.
- **HK**: HKEX Stock Options.
- **JP**: Osaka Exchange (single-stock + Nikkei index).
- **Bloomberg FIT / OMON** for monitoring.

## How to Access Market

- Continuous CLOB on each exchange.
- Cross-exchange linkage (US) per Reg NMS for protected quotes.
- Block trade rules per exchange (FLEX vs standard).
- Voice / IB ([[bloomberg-ib]]) for complex orders, large blocks.

## RFQ & Quote Discovery

- COB (Complex Order Book) per US exchange for spreads / combos.
- Price-improvement auction mechanisms (e.g. Cboe AIM / MIAX PRIME / BOX PIP / NYSE Arca SIM).
- BWIC-like flow for off-the-run names: dealer voice.
- Multi-leg RFQ on EUREX, HKEX.

## Execution / Allocation

- Single-leg: standard CLOB execution per [[route-single]] / [[route-to-algo]].
- Multi-leg (spreads, combos): atomic execution via [[arch-multileg|multileg]] — see complex order book mechanics.
- Allocation per [[allocation-prime-broker]].

## Basket Trading / Aggregations

- Complex spreads atomic by exchange COB.
- Roll trades around expiry weeks.
- Delta-hedged trades (option + stock as multileg).

## Netting

- CCP-level: [[ficc-clearing|OCC]] (US — Options Clearing Corp).
- Position netting per (underlying, expiry, strike, right).

## Regulatory Reporting

- FINRA OATS/CAT for options (CAT scope includes listed options).
- SEC large-options-position reporting.
- Per-jurisdiction equivalents (see [[arch-jurisdictional-compliance]]).

## Clearing / Settlement

- US: OCC clears all listed equity/index options (T+1 settle).
- Variation + initial margin daily; SPAN-equivalent risk model.
- Physical settlement on expiry for single-stock (deliver/receive 100 shares per contract typical).
- Cash-settled for index options.

## Documentation Required

- OCC participant agreements (broker-clearer).
- Options Disclosure Document delivered to clients (US OCC).
- ISDA / CSA not required (listed).

## Market Notes

- Greeks (delta, gamma, vega, theta, rho) computed in real-time and consumed by [[arch-risk-engine|risk]].
- IV (implied volatility) surface management critical — see [[arch-pricing-service]].
- American-exercise risk: early-exercise possible esp. ahead of dividends.
- Pin risk around expiry — gamma blows up near strike.
- Volume highly concentrated in weeklies + near-the-money strikes.

## Typical Counterparties

- Market makers (Citadel Securities, Susquehanna, Optiver, IMC, Wolverine, Jane Street), prop firms, hedge funds, asset managers, retail (via brokers).

## Related Workflows

- [[staging-via-fix]] · [[route-to-rfq]] · [[route-to-algo]] · [[arch-multileg]] · [[arch-router-layer]]
- [[arch-risk-engine]] (greeks-aware) · [[arch-pricing-service]] (IV surface)
- [[arch-jurisdictional-compliance]] (Reg NMS, CAT)
- Sibling: [[cash-equity]] · [[equity-derivatives]] · [[equity-futures]] · [[equity-swaps]]
