---
type: asset_class
asset_class: rates_credit_deriv
sub_class: structured-products
trade_type: derivative
liquidity: low
status: draft
tags: [asset/rates_credit_deriv/structured-products]
---

# Structured Products

Bespoke instruments **combining a debt wrapper with embedded derivatives** to produce custom risk/return profiles. Examples: principal-protected notes, callable yield notes, reverse-convertibles, structured-CDS-linked notes, CLNs (Credit-Linked Notes), TARNs.

## Venues

- **Primary**: dealer-direct origination — the issuing dealer prices and creates the note.
- **Secondary**: limited — dealer-direct on the issuing dealer (some).
- **No central matching** — bespoke per term sheet.

## How to Access Market

Bilateral with the structuring dealer. [[bloomberg-ib]] chat for negotiation. ISDA-documented for any embedded derivatives.

## RFQ vs CLOB

Bilateral / [[rfq]] only — no CLOB possible (each term sheet unique).

## Aggregations / Basket / Netting

Each note is its own instrument — no aggregation. Hedging the dealer's risk involves correlated underlying positions.

## Regulatory Reporting

US: depends on structure — SEC for registered notes, [[cftc-sdr]] for embedded swaps. EU/UK: PRIIPs KID for retail-distributed structured products; [[emir-sftr-csdr|EMIR]] for embedded derivatives.

## Clearing / Settlement

T+2 typically for notes via [[dtc]]. Embedded derivatives margin per ISDA.

## Documentation Required

Per-note: term sheet, offering memorandum, ISDA confirmation for the derivative leg, indenture. For retail: PRIIPs KID under EU rules.

## Market Notes

- **Fungibility**: **Fully non-fungible** by design — each structure has unique terms, embedded derivatives, payoff profile, and underlying. The whole point is bespoke risk/return. See [[fungible-vs-non-fungible]].
- **MiFID II PRIIPs** — retail structured products require Key Information Documents.
- **Capital ratio impact** — banks issuing structured notes face capital charges on the embedded derivative hedge.
- **Distribution channels** — many structured products distribute through private banks to wealthy individuals.
- **Crisis sensitivity** — 2008 wiped out much of the principal-protected note market.

## Typical Counterparties

Structured-products specialists: BNP, Société Générale (especially EU equity-linked), GS, JPM, BAML, MS, UBS, Credit Suisse (now UBS), Nomura.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[allocation-prime-broker]] · [[two-step-approval]].
