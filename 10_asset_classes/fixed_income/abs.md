---
type: asset_class
asset_class: fixed_income
sub_class: abs
trade_type: cash_security
liquidity: moderate
status: draft
tags: [asset/fixed_income/abs]
---

# Asset-Backed Securities (ABS)

Bonds backed by pools of consumer or commercial receivables — auto loans, student loans, credit cards, equipment leases, aircraft leases, solar loans, and **CLOs** (Collateralized Loan Obligations) backed by syndicated loans. Smaller than MBS but more diverse. Frequently traded as structured tranches with different seniorities.

## Venues

- **Primary**: dealer-direct RFQ (specialist ABS desks).
- **Workflow**: [[bloomberg-bwic-owic]] heavily used for portfolio rebalancing — ABS BWICs are operationally complex (per-tranche evaluation).
- **All-to-all** is limited — most ABS trades still dealer-intermediated.
- **Reference**: [[bloomberg-fit]] (price discovery, less granular than corporates).

## How to Access Market

Buy-side EMS connects to specialist dealer-direct via FIX or Bloomberg-IB chat. [[bloomberg-bwic-owic|BWICs]] sent through Bloomberg's BWIC workflow with execution leg on the relevant matching venue.

## RFQ vs CLOB

[[rfq]] only. CLOB doesn't work for ABS — tranches are too specific. [[bloomberg-bwic-owic|BWICs]] are the dominant rebalancing tool.

## Aggregations / Basket / Netting

[[bloomberg-bwic-owic|BWIC]] portfolio execution. [[netting]] across tranches is conceptually possible but rarely done in practice.

## Regulatory Reporting

US: [[trace]] for ABS / CMBS / CDO (added 2018, with delayed dissemination rules). EU: MiFID II post-trade transparency via APAs. UK: UK MiFIR.

## Clearing / Settlement

US: T+2 at [[dtc]] via DVP. CLOs and complex structures sometimes have longer manual settlement timelines.

## Documentation Required

Per-deal: offering memorandum (OM), trust agreement, servicing agreement. CLOs: indenture + collateral admin agreement.

## Market Notes

- **Fungibility**: Fungible per tranche CUSIP — each tranche is its own CUSIP and units within are interchangeable. But the universe is highly tranche-specific: an AAA tranche of a particular ABS deal trades very differently from any other tranche of the same deal. See [[fungible-vs-non-fungible]].
- **Diverse sub-sectors** — auto, credit card, student loan, CLO, esoteric ABS all have different liquidity / pricing dynamics.
- **CLO market** has its own pricing dynamics tied to leveraged loan market — see [[whole-loans]].
- **Tranche structure** matters — AAA tranche of an ABS trades very differently from a BBB-tranche of the same deal.
- **Refinancing waves** drive CLO price moves (when CLOs reset their interest rate, equity tranches benefit).
- **Esoteric ABS** (aircraft, solar, tax liens) is a specialist game.

## Typical Counterparties

ABS specialists: JPM, Citi, BAML, Wells Fargo, Mizuho, MUFG, Credit Suisse (now UBS), Barclays, Deutsche Bank.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[bloomberg-bwic-owic]] · [[allocation-prime-broker]].
