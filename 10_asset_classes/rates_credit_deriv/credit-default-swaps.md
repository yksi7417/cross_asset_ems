---
type: asset_class
asset_class: rates_credit_deriv
sub_class: credit-default-swaps
trade_type: derivative
liquidity: high
status: draft
tags: [asset/rates_credit_deriv/credit-default-swaps]
---

# Credit Default Swaps (CDS)

OTC derivative where the buyer pays a periodic spread to the seller in exchange for protection against a **credit event** on a reference entity (or index). **Index CDS** (CDX, iTraxx) is liquid and electronified; **single-name CDS** is concentrated in dealer-specialist desks.

## Venues

- **Index CDS (MAT)**: [[bloomberg-sef]], [[tradeweb]] CDS SEF, ICE Swap Trade.
- **Single-name CDS**: dealer-direct via FIX or [[bloomberg-ib]] chat. Some on [[bloomberg-bmtf]] (EU).
- **Pricing**: [[bloomberg-cdsw]] (terminal calculator).

## How to Access Market

Index CDS via FIX RFQ-to-3 ([[rfq-to-3]]) for MAT products. Single-name via dealer-direct.

## RFQ vs CLOB

Index: [[rfq]] dominant; CLOB for the most liquid CDX series. Single-name: RFQ / bilateral only.

## Aggregations / Basket / Netting

Tranche trades (long IG, short HY for credit spread) execute as paired multi-leg. CCP novation provides netting.

## Regulatory Reporting

US: [[cftc-sdr]] real-time + regulator. EU/UK: [[emir-sftr-csdr|EMIR]] T+1 to TR.

## Clearing / Settlement

**Cleared at ICE Clear Credit** (US dominant) or LCH CDSClear (EU). MAT index CDS must clear. Single-name CDS: optional clearing.

## Documentation Required

[[isda]] Master + [[csa]] + CDS-specific [[cds-annex|annex]] (referencing ISDA Credit Derivative Definitions and per-entity reference obligation).

## Market Notes

- **Fungibility**: Index CDS (CDX, iTraxx) are fully fungible per series (Series + Vintage define the instrument). Single-name CDS are fungible per (reference entity, restructuring clause, term). Custom-basket CDS are non-fungible. See [[fungible-vs-non-fungible]].
- **Index series rolls** — CDX rolls every 6 months (March, September); each roll creates a new series.
- **Credit events** — ISDA Determinations Committee decides if a credit event occurred; triggers settlement via auction.
- **Recovery rates** — auction-determined post credit event; drives the loss distribution.
- **Single-name liquidity** is concentrated — most names trade <10 trades/year.

## Typical Counterparties

Credit specialists: JPM, GS, Citi, BAML, MS, BNP, Deutsche, Goldman, Barclays. Hedge funds active in single-name CDS short bets.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[rfq-to-3]] (MAT).
