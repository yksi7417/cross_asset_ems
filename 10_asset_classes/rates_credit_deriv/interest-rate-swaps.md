---
type: asset_class
asset_class: rates_credit_deriv
sub_class: interest-rate-swaps
trade_type: derivative
liquidity: very_high
status: draft
tags: [asset/rates_credit_deriv/interest-rate-swaps]
---

# Interest Rate Swaps (IRS)

OTC derivative exchanging a **fixed rate for a floating rate** (or floating for floating — basis swap). The dominant rates-derivative product. Post-Dodd-Frank: USD/EUR/GBP vanilla IRS at standard tenors are **MAT** ([[mat]]), executed on SEFs, cleared at CCPs.

## Venues

- **SEFs**: [[bloomberg-sef]], [[tradeweb]] SEF, ICE Swap Trade, [[sef-platforms]].
- **EU MTFs**: [[bloomberg-bmtf]], [[tradeweb]] MTF.
- **Bilateral (non-MAT)**: dealer-direct for bespoke tenors / structures.
- **Pricing**: [[bloomberg-swpm]] (terminal calculator, not routable).

## How to Access Market

Buy-side EMS routes via FIX to SEFs in [[rfq-to-3|RFQ-to-3]] mode for MAT D2C or CLOB for liquid USD tenors. Bilateral for bespoke.

## RFQ vs CLOB

Both: CLOB for liquid USD tenors (2y, 5y, 10y, 30y), RFQ ([[rfq-to-3]]) for less liquid or D2C.

## Aggregations / Basket / Netting

Curve trades (e.g. 2s10s steepener) execute as paired multi-leg. CCP novation provides multilateral netting at the CCP — see [[novation]] and [[ccp-vs-bilateral]].

## Regulatory Reporting

US: [[cftc-sdr]] real-time + regulator. EU/UK: [[emir-sftr-csdr|EMIR]] T+1 to TR.

## Clearing / Settlement

**Cleared at LCH SwapClear** (dominant) or CME (secondary) for MAT products. See [[novation]]. Cash settled with daily MTM and margin via [[fcm]].

## Documentation Required

[[isda]] Master + [[csa]] for variation/initial margin (where bilateral).

## Market Notes

- **Fungibility**: Cleared IRS are fungible per (currency, tenor, structure, floating-leg index, day-count, payment frequency). Post-novation, the CCP is the counterparty so all matching trades are perfectly fungible. Bespoke / non-MAT swaps are non-fungible. See [[fungible-vs-non-fungible]].
- **SOFR vs LIBOR transition** — completed 2023; USD IRS now reference SOFR. Other regimes transitioned similarly (€STR, SONIA, TONA).
- **Basis swaps** — float vs float across two indices. Common for OIS / IBOR basis trades.
- **Forward-starting swaps** for hedging future fundings (corporate / pension).
- **Compression** — periodically clearing members run compression cycles via TriOptima to reduce gross notional via netting.

## Typical Counterparties

Major rates desks: JPM, GS, Citi, BAML, MS, Barclays, Deutsche, BNP, UBS, HSBC, Mizuho, MUFG, plus hedge funds (Citadel, Brevan Howard, Renaissance).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[rfq-to-3]] (MAT) · [[allocation-prime-broker]].
