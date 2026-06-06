---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# LEI (Legal Entity Identifier)

The **Legal Entity Identifier** is the **global ISO 17442 standard 20-character code** uniquely identifying a legal entity participating in financial markets. Issued by Local Operating Units (LOUs) accredited by GLEIF (the Global LEI Foundation), renewed annually for a fee.

LEIs are mandated for most regulatory reporting in the major jurisdictions: MiFID II RTS 22 (parties on every transaction), EMIR (counterparties on every derivative), Dodd-Frank Title VII (cleared and reported swaps), UK SMCR variants, FATCA / CRS in many cases. **No LEI = no trade** for many regulated transaction types.

For a buy-side EMS: LEI is a **first-class reference data field** on every trading entity. The firm itself has an LEI; each managed fund / sub-fund / SPV has its own LEI (because the regulatory reporting party is the fund, not the manager). The EMS must maintain LEI validity (annual renewals, status: ISSUED / LAPSED / RETIRED) and reject trades for lapsed-LEI counterparties at the validator.

## Example

A buy-side firm manages 200 sub-funds. Each sub-fund has its own LEI registered with GS1 US (a LOU). The reference-data service syncs LEI status nightly from GLEIF. If a sub-fund's LEI lapses, the validator rejects new trades for that sub-fund until renewal — preventing the firm from incurring reporting failures.

## Why it matters in an EMS

- LEI status is a hard pre-trade check at [[arch-validator]].
- The reference data service ([[arch-reference-data-service]]) syncs LEI status from GLEIF.
- See [[arch-jurisdictional-compliance]] for the LEI-required regimes.

## Related

- [[rts-22-27-28]] · [[emir-sftr-csdr]] · [[cftc-sdr]] · [[trace]] (regimes requiring LEI)
- [[arch-validator]] · [[arch-reference-data-service]] · [[arch-firm-desk-user]]
- [[arch-jurisdictional-compliance]]
