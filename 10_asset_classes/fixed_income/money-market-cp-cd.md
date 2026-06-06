---
type: asset_class
asset_class: fixed_income
sub_class: money-market-cp-cd
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/fixed_income/money-market-cp-cd]
---

# Money Market — Commercial Paper / Certificates of Deposit

**Commercial paper (CP)** is short-term unsecured corporate or bank debt (typically 1-270 days). **Certificates of Deposit (CD)** are bank-issued short-term debt. Both are core money-market instruments — issued for working-capital funding and bought by money-market funds and corporate treasuries.

## Venues

- **Primary**: dealer-direct bilateral by phone or FIX. Most CP traded with a small number of dealer specialists per issuer.
- **Secondary**: limited [[marketaxess]] CP RFQ; some Tradeweb. Many CD issues trade on [[ice-bondpoint]] for retail-sized lots.
- **Reference**: [[bloomberg-cp-cd]] (monitor screen).

## How to Access Market

Bilateral with the issuer's dealer panel via FIX or chat. The CP market is **dealer-intermediated** — no dominant central CLOB or MTF. CD primary issuance via dealer auction networks; secondary on [[ice-bondpoint]] for retail.

## RFQ vs CLOB

[[rfq]] or chat for institutional CP. Click-to-trade dealer-streamed for retail CD on [[ice-bondpoint]].

## Aggregations / Basket / Netting

[[netting]] same-CUSIP. Money-market funds typically build CP portfolios across many issuers — per-issuer concentration limits enforced by fund mandate.

## Regulatory Reporting

US: [[trace]] coverage for some CP categories (mostly the larger / liquid issues). EU: MiFID II post-trade transparency where applicable.

## Clearing / Settlement

T+1 same-day or T+1 settlement at [[dtc]] via DVP. Bank CDs may settle through Fedwire.

## Documentation Required

CP: dealer's issuing memorandum, master notes (the dealer issues CP under a master-note program). CD: issuing bank's CD agreement.

## Market Notes

- **Fungibility**: Fungible per issuance / CUSIP within a CP program. The program itself is reused across many tenors so the same program issues multiple non-fungible-across-tenor instruments under shared documentation. See [[fungible-vs-non-fungible]].
- **CP rates** track Fed funds + a credit spread (typically 5-50 bp for IG corp issuers; more for finance-co paper).
- **Roll risk** — CP must be refinanced regularly; periods of stress can shut programs (e.g. 2008 financial-CP freeze).
- **Tier 1 / Tier 2 distinction** matters to money-market funds under SEC Rule 2a-7.
- **A1/P1 ratings** vs lower-tier divides the buyer base.
- **CD insurance** (FDIC) is a feature for retail-sized CDs — not relevant for institutional.

## Typical Counterparties

Major CP dealers: JPM, Citi, BAML, MS, GS, Wells Fargo, Barclays, BNP, Deutsche, Mizuho (specialist Japanese-corp CP). CD: any major bank issuer.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[allocation-prime-broker]].
