---
type: asset_class
asset_class: equity
sub_class: equity-swaps
trade_type: derivative
liquidity: moderate
status: draft
tags: [asset/equity/equity-swaps]
---

# Equity Swaps

OTC derivative contracts where one party pays a return based on an equity (or equity basket) in exchange for a financing leg (typically floating rate plus spread). Includes **total-return swaps (TRS)**, **price-return swaps**, **variance swaps**, **dividend swaps**.

## Venues

- **Primary**: dealer-direct via FIX or [[bloomberg-ib]] chat. No multilateral matching venue.
- **Pricing**: [[bloomberg-swpm]] (terminal calculator).
- **Some**: cleared variance swaps via LCH SwapAgent.

## How to Access Market

Bilateral dealer-direct via FIX (for active flows) or [[bloomberg-ib]] chat. ISDA-documented before trading. Cleared variance swaps via LCH have a CCP novation step.

## RFQ vs CLOB

[[rfq]] / bilateral only.

## Aggregations / Basket / Netting

Basket TRS (long TRS on a custom basket) common in long-short hedge fund strategies. [[netting]] within counterparty + ISDA — see [[isda]] and [[csa]] for margin netting.

## Regulatory Reporting

US: [[cftc-sdr]] for swap data repository. EU/UK: [[emir-sftr-csdr|EMIR]] T+1 reporting to a TR. Single-name TRS gained notoriety after Archegos (March 2021) → tightened regulatory focus on cross-bank concentration.

## Clearing / Settlement

Mostly bilateral. Variance swaps can clear via LCH SwapAgent. Equity TRS clear via DTC's GTR (for cash flow) or directly bilateral.

## Documentation Required

[[isda]] + [[csa]] for variation/initial margin. Per-trade confirmation per ISDA Equity Derivative Definitions.

## Market Notes

- **Fungibility**: Non-fungible — each TRS is bespoke (counterparty, underlying basket, financing rate, notional, term). See [[fungible-vs-non-fungible]].
- **Synthetic exposure** — TRS gets long exposure without owning the underlying, common for tax / regulatory / leverage reasons.
- **Archegos (2021)** — the largest TRS-blowup in recent history; cross-counterparty concentration was the failure mode.
- **Dividend swaps** — pure dividend exposure separated from equity price.
- **PRA / Fed limits** — many regulators now scrutinise TRS concentration tightly.

## Typical Counterparties

Major OTC dealers: GS, MS, JPM, Citi, BAML, Credit Suisse (now UBS), Nomura, Mizuho, BNP, Deutsche.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[allocation-prime-broker]] · [[two-step-approval]] (concentration risk approval).
