---
type: asset_class
asset_class: commodity
sub_class: commodity-physical
trade_type: cash_security
liquidity: low
status: draft
tags: [asset/commodity/commodity-physical]
---

# Commodity Physical

Physical delivery of commodities — actual cargoes of oil, LNG, grains, metals, etc., bought and sold for physical settlement at specific delivery locations. Distinct from [[commodity-futures|futures]] which most participants close out before delivery.

## Venues

- **Primary**: bilateral via voice / chat / specialist physical-broker platforms.
- **Some electronified**: ICE Brent assessments, Platts ePlatts windows for oil/refined-products.
- **Physical broker platforms**: Trayport (gas/power), specialist commodity brokerages.

## How to Access Market

Highly bilateral. EMS integration is limited — most trades through specialist physical-commodity desks (separate from futures desks). Voice-driven negotiation, chat for paper-trail, FIX for confirmation in some cases.

## RFQ vs CLOB

Bilateral negotiation; CLOB doesn't work for cargo-level trades because each cargo has unique specifications.

## Aggregations / Basket / Netting

Physical positions don't net easily — different locations / specifications. Position management uses paper-futures or financial swaps as hedge overlays.

## Regulatory Reporting

US: limited (CFTC may treat some physicals as commodity-pool-act-exempt). EU/UK: REMIT for energy, some grain reporting under EU Common Agricultural Policy.

## Clearing / Settlement

Physical delivery at the specified location and date. Letter-of-credit common for international commodity trades. Title transfer at delivery point.

## Documentation Required

Per-trade: physical sales contract with detailed specs (quality, quantity, location, delivery date, payment terms). Industry-standard templates (ISDA Energy Annex, GAFTA forms for grains, FOSFA for oils/fats).

## Market Notes

- **Fungibility**: **Non-fungible** at cargo level — each shipment has specific origin, quality, delivery location. Fungible at the **grade** level (WTI oil, Brent oil are fungible within their grade specifications). See [[fungible-vs-non-fungible]].
- **Grade differentials** — WTI vs Brent, Light Sweet vs Heavy Sour, etc., trade as paired strategies.
- **Location basis** — same grade at different delivery locations trades at a basis to the futures benchmark.
- **Quality specs** — sulphur content, API gravity, moisture, protein for grains, etc. Each adjustment matters to value.
- **Counterparty credit risk** is material in physical commodities (large notional + settlement-date risk).
- **Sanctions / OFAC** — energy commodities particularly sensitive to sanctions (Iran, Russia, Venezuela).

## Typical Counterparties

Physical-commodity specialists: Glencore, Trafigura, Vitol, Mercuria (independent commodity traders), plus banks (BNP, Société Générale, MUFG, Mizuho for trade finance), plus oil majors / mining companies as physical counterparties.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[allocation-prime-broker]] · [[bloomberg-ib]] (chat-driven negotiation).
