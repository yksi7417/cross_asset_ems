---
type: venue
venue_kind: broker_dealer
asset_classes: ["fixed_income", "equity", "fx", "rates_credit_deriv"]
status: draft
tags: [venue/broker_dealer]
---

# Bloomberg Tradebook (Singapore — BTBS)

**Bloomberg Tradebook Singapore** is Bloomberg's MAS-regulated trade-negotiation broker-dealer serving the **APAC region** across multiple asset classes — bonds, equities, rates / credit derivatives, FX derivatives. The APAC counterpart to [[bloomberg-tradebook-us]].

## Asset classes

- APAC equities (HK, JP, SG, KR, TW, AU, IN, etc.)
- APAC govt and credit bonds (JGB, CGB, HK, SG, KR, AU)
- IRS, CDS, NDF derivatives in APAC currencies
- FX (selected)

## Workflow mechanisms

- **Trade negotiation** — combines voice / chat / electronic for APAC market structure.
- **Cross-border block execution** — Asia → US / EU bridge flows.
- **Algorithmic execution** for equities where supported.

## Connectivity

- **FIX 4.2 / 4.4** with APAC dialect support (multiple sub-exchange handling for HK/SG/AU).
- **BLPAPI** integration.
- Localized post-trade ack/affirm with regional CSDs.

## Regulatory shape

- **MAS Capital Markets Services licence** in Singapore.
- Cross-references to Hong Kong SFC, Japan JFSA where applicable per asset class.
- See [[arch-jurisdictional-compliance]] for the APAC stack.

## Related

- [[bloomberg-tradebook-us]] (US sibling) · [[bloomberg-emsx]]
- [[yieldbroker]] (AUD rates) · [[arch-jurisdictional-compliance]]
