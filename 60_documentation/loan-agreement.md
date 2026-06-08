---
type: documentation
status: draft
tags: [documentation]
---

# Loan Agreement (LSTA / LMA)

**LSTA (US) / LMA (Europe) standardised loan documentation** governs syndicated leveraged loans and corporate term loans. The **credit agreement** is the master document; supplemented by intercreditor agreements, security agreements, and assignment documentation.

## Where it is required

- All [[whole-loans|syndicated leveraged loans]] and corporate term loans.
- Direct-lending and BDC portfolios (private credit).
- Used as the model for SBLs (single-borrower-loans) in some structured products.

## Key documents

### Credit Agreement

- Principal terms: borrower, lender(s), amount, tenor, interest rate (typically SOFR + spread for USD).
- Covenants — financial covenants (leverage, coverage), affirmative covenants, negative covenants.
- Events of default and remedies.
- Mandatory prepayment events (asset sales, debt issuance, etc.).

### Intercreditor Agreement

- Priority among different lender groups (first-lien, second-lien, mezzanine).
- Standstill periods, payment subordination.

### Assignment / Participation

- LSTA standard assignment forms for trading the loan.
- T+7 LSTA settlement standard (target; often slips).

### Borrower Confidentiality

- Lender obligations regarding non-public borrower information.
- KYC requirements per AML.

## EMS implications

- Trade settlement via LSTA assignment is operationally heavy — see [[whole-loans]] for the T+7 settlement challenges.
- Per-loan reference data — covenants, ratings, performance triggers — feeds [[arch-reference-data-service]].
- Distressed names require restructuring-aware position tracking in [[arch-position-service]].

## Related

- [[whole-loans]] · [[abs]] (CLOs depend on syndicated loans as collateral)
- [[arch-reference-data-service]] · [[arch-stp-pipeline]]
- [[bloomberg-ib]] (chat-based negotiation common in distressed loan trading)
