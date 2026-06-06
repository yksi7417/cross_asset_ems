---
type: documentation
status: draft
tags: [documentation]
---

# ISDA Master Agreement

The **International Swaps and Derivatives Association (ISDA) Master Agreement** is the **industry-standard legal framework for OTC derivatives**. A single signed Master between two counterparties governs all derivative trades they will do — eliminating per-trade legal negotiation. Two main versions in current use: **1992 ISDA Master** and **2002 ISDA Master**.

## Where it is required

- All OTC [[interest-rate-swaps|IRS]], [[credit-default-swaps|CDS]], [[fx-options]], [[fx-swap]], [[fx-ndf]], [[equity-swaps]].
- Structured products and bespoke derivatives.
- Variance swaps and exotic structures.

## Key terms

- **Schedule**: per-counterparty elections to the Master — events of default, termination events, calculation agent, governing law.
- **Confirmation**: per-trade economic terms, references the Master + Schedule + Definitions (Equity, FX, Interest Rate, etc.).
- **Credit Support Annex (CSA)**: separate document governing collateral / margin — see [[csa]].
- **Events of Default**: failure to pay/deliver, breach of agreement, credit support default, misrepresentation, bankruptcy, NAV decline.
- **Termination Events**: illegality, tax event, change in tax law, credit event upon merger.
- **Close-out netting**: on default, all transactions terminate; net replacement value calculated.
- **Set-off**: cross-trade and cross-product netting.

## EMS implications

- Counterparty enablement requires an ISDA in place before any OTC derivative trade — checked at [[arch-validator]] pre-trade.
- [[arch-reference-data-service]] tracks per-counterparty ISDA status (signed / pending / negotiation).
- Close-out netting calculation depends on counterparty exposure data from [[arch-position-service]].
- CSA terms ([[csa]]) drive margin calculations and [[arch-stp-pipeline]] post-trade.

## Related

- [[csa]] (margin annex) · [[cds-annex]] (CDS-specific)
- [[gmra]] (sister agreement for repo)
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-options]] · [[equity-swaps]]
- [[arch-reference-data-service]] · [[arch-validator]] · [[counterparty-enablement]]
