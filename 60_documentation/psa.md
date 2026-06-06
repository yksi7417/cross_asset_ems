---
type: documentation
status: draft
tags: [documentation]
---

# PSA (Pooling and Servicing Agreement)

The **Pooling and Servicing Agreement** (PSA) is the **core legal document for a securitization** — defines how the pool of underlying assets (mortgages, auto loans, credit cards, etc.) is to be managed by the servicer, how cash flows are allocated to tranches, and what events trigger waterfalls or remedies. Used in [[mbs]], [[abs]], and CMBS deals.

## Where it is required

- Every non-agency [[mbs|MBS]] deal (private-label residential MBS).
- Every [[abs|ABS]] deal — auto loans, credit cards, student loans, equipment leases.
- CMBS deals (commercial mortgage-backed securities).
- Agency MBS (Fannie / Freddie / Ginnie) has parallel documents (Trust Agreements) with similar function.

## Key terms

- **Trustee** — independent fiduciary representing investor interests.
- **Servicer** — primary servicer (collects payments from underlying borrowers, distributes to trustee).
- **Master Servicer** — supervisory servicer for some deals.
- **Waterfall** — order of cash flow distribution from collections to tranches (senior → mezzanine → equity).
- **Credit enhancement** — overcollateralization, excess spread, reserve fund.
- **Trigger events** — specific performance triggers that change waterfall (early amortization, principal-only payments).
- **Servicer advance** — servicer advances missed borrower payments; recovers later.
- **Loss allocation** — defines which tranches absorb losses first.

## EMS implications

- Per-deal PSAs feed into [[arch-reference-data-service]] for the tranche structure.
- Cash-flow projection in [[arch-pricing-service]] depends on PSA waterfall.
- Pre-trade research depends on PSA understanding — most buy-side firms have specialised structured-credit research teams.

## Related

- [[mbs]] · [[abs]] · [[tba-vs-specified-pool]]
- [[arch-pricing-service]] · [[arch-reference-data-service]]
- [[sifma-tba-guidelines]] (sibling for TBA market)
