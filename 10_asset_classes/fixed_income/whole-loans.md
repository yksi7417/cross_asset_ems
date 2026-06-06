---
type: asset_class
asset_class: fixed_income
sub_class: whole-loans
trade_type: loan
liquidity: low
status: draft
tags: [asset/fixed_income/whole-loans]
---

# Whole Loans / Mortgages / Leveraged Loans

Direct ownership of individual mortgage loans (whole-loan portfolios — residential + commercial) and **leveraged loans** (broadly-syndicated corporate loans). Distinct from securitized MBS or ABS — the holder owns the underlying loan rather than a tranche of a structure.

## Venues

- **Primary**: dealer-direct or LSTA-coordinated for leveraged loans; bespoke for whole-loan mortgage portfolios.
- **Workflow**: [[bloomberg-ib]] chat heavily used for negotiation. [[bloomberg-bwic-owic|BWICs]] for portfolio rebalancing.
- **Loan markets**: IHS Markit (now S&P), Reorg, KopenTech LLC for distressed.

## How to Access Market

Highly bilateral. EMS integration is limited — most flow is voice / chat / bespoke. [[bloomberg-ib]] is the primary surface. Settlement is operationally heavy.

## RFQ vs CLOB

[[rfq]] only; CLOB doesn't exist. Many trades are essentially bilateral negotiations with no formal RFQ structure.

## Aggregations / Basket / Netting

[[bloomberg-bwic-owic|BWIC]] portfolios for rebalancing. Limited [[netting]] (each loan is unique).

## Regulatory Reporting

US: leveraged loans largely outside TRACE. Bank-held whole loans report through bank regulatory reporting (Call Reports, FR Y-14). EU/UK: limited reporting for non-securitized loans.

## Clearing / Settlement

Long settle cycles — leveraged loan settle is typically T+7 (LSTA "T+7" target, often slips), whole loans manual settle. Settlement involves loan-level documentation transfer. The **loan settlement industry-standard problem** — T+7 vs equity T+1 is a structural issue under discussion.

## Documentation Required

For leveraged loans: LSTA-standard documentation (credit agreement, intercreditor agreement, assignment forms). For whole-loan portfolios: per-loan documentation (mortgage notes, mortgages, title docs).

## Market Notes

- **Fungibility**: **Non-fungible** — each loan has unique borrower, terms, collateral, covenants, performance history. Symbology is per-trade or per-loan-ID rather than per-instrument. This is the fundamental reason whole loans don't electronify — there's no fungible unit to RFQ. See [[fungible-vs-non-fungible]].
- **LSTA T+7 settle** is a known operational pain point — many trades slip to T+15+.
- **Distressed loans** (defaulted / restructuring borrowers) trade at large discounts with very specific recovery analytics.
- **CLOs** (Collateralized Loan Obligations) buy leveraged loans — CLO refinancing waves drive loan-market spreads.
- **Private credit** vs syndicated — much loan flow has moved to private credit (BDC, direct-lending funds) over the last decade.

## Typical Counterparties

Leveraged loan: GS, JPM, Citi, BAML, MS, Credit Suisse (now UBS), Deutsche, BNP, Barclays, Jefferies (top dealer/arranger franchise). Whole-loan mortgages: regional banks, mortgage REITs, specialty servicers.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[bloomberg-bwic-owic]] · [[allocation-prime-broker]].
