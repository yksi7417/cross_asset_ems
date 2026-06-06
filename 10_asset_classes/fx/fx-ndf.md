---
type: asset_class
asset_class: fx
sub_class: fx-ndf
trade_type: cash_security
liquidity: moderate
status: draft
tags: [asset/fx/fx-ndf]
---

# FX NDF (Non-Deliverable Forward)

FX forward in a **non-deliverable currency** — settled in cash in a freely-traded currency (typically USD) rather than via physical exchange of the local currency. Used for currencies with **capital controls** (CNY, INR, BRL, RUB historically, KRW) where the local currency cannot freely cross borders.

## Venues

- **Primary**: [[refinitiv-fxall]], [[360t]] NDF, dealer-direct.
- **SEF-regulated**: [[bloomberg-sef]] for NDFs subject to CFTC SEF mandate.
- **Reference**: per-currency fixing source (e.g. PBOC Mid for CNY, RBI for INR).

## How to Access Market

Buy-side EMS routes via FIX. RFQ to a permissioned dealer set. For CFTC-jurisdiction NDFs, the SEF mandate applies — see [[mat]] and [[rfq-to-3]].

## RFQ vs CLOB

[[rfq]] dominates; some streaming on the most liquid pairs (USD/CNH, USD/INR).

## Aggregations / Basket / Netting

EM hedging baskets [[netting|net]] across currencies. Multi-currency NDF execution common for EM portfolio hedging.

## Regulatory Reporting

US: [[cftc-sdr]] (Dodd-Frank — NDFs are SEC swaps). EU/UK: [[emir-sftr-csdr|EMIR]] reportable; UK MiFIR.

## Clearing / Settlement

Cash-settled at the **fixing date** (typically T+2 from value date) at the fixing rate. No physical local-currency delivery. LCH ForexClear clears NDFs in major EM pairs.

## Documentation Required

ISDA Master + EMTA NDF definitions (for the fixing source and disruption fallbacks).

## Market Notes

- **Fungibility**: Fungible per pair + fixing date + fixing source. The fixing source is part of the instrument definition. See [[fungible-vs-non-fungible]].
- **Onshore vs offshore CNH** — onshore (CNY) is controlled; offshore (CNH) trades freely in HK. NDF references one or the other.
- **CFTC MAT** ([[mat]]) applies to many NDFs — must execute on a SEF.
- **EM crises** — NDFs are the leveraged-bet instrument for EM crisis exposure (BRL, RUB, etc.).
- **Fixing source disruption** — when fixing source becomes unreliable (e.g. RUB in 2022), EMTA defines fallback fixing rules.

## Typical Counterparties

EM-specialist FX desks: HSBC (top APAC EM), Standard Chartered (EMEA EM + APAC), Citi (global EM), GS, BNP, MS.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[rfq-to-3]] (for MAT NDFs).
