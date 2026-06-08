---
type: documentation
status: draft
tags: [documentation]
---

# CDS Annex (ISDA Credit Derivative Definitions)

The **CDS-specific annex to the [[isda|ISDA Master Agreement]]**, plus the **2014 ISDA Credit Derivative Definitions** that define standard CDS terms. Together they govern the legal mechanics of credit default swaps.

## Where it is required

- All [[credit-default-swaps|CDS]] trades — single-name and index.
- Standalone single-name CDS particularly require detailed reference-entity setup.

## Key terms

### Reference Entity

- The legal entity whose default triggers the swap.
- Successor mechanics for mergers, demergers, name changes.

### Reference Obligation

- The specific debt issue used to determine pricing (par value vs market value).
- Substitution rules if the reference obligation is repaid.

### Credit Events

- **Bankruptcy** — Chapter 7, 11, equivalents.
- **Failure to Pay** — missed coupon / principal payment after grace period.
- **Restructuring** — debt restructuring; multiple variants (No Restructuring, Mod-R, Mod-Mod-R, Full Restructuring) — the regional convention varies.
- **Repudiation/Moratorium** — sovereign-specific.
- **Obligation Acceleration** — debt accelerated and declared due.

### Determinations Committee

- ISDA-administered committee that decides whether a credit event has occurred.
- Decisions are binding industry-wide.

### Auction Settlement

- Post-event, an industry auction sets the recovery rate.
- Used to determine cash settlement on bilateral and cleared trades.

## EMS implications

- Reference entity master data per CDS — in [[arch-reference-data-service]].
- Credit events feed [[arch-position-service]] for revaluation.
- Auction recovery rates drive cash settlement in [[arch-stp-pipeline]].
- Trade confirmation via [[markitserv]] uses standardised credit-derivative definitions.

## Related

- [[isda]] (parent) · [[csa]] (collateral) · [[credit-default-swaps]]
- [[markitserv]] · [[ice-clear]] · [[lch]] (clearing)
- [[arch-reference-data-service]] · [[arch-position-service]] · [[arch-stp-pipeline]]
