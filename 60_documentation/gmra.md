---
type: documentation
status: draft
tags: [documentation]
---

# GMRA (Global Master Repurchase Agreement)

The **Global Master Repurchase Agreement** (GMRA) is the **industry-standard legal framework for institutional repo** — a single signed Master between two counterparties governs all repo trades they will do. Multiple versions in use: **GMRA 1995**, **GMRA 2000**, **GMRA 2011**. Published by SIFMA and ICMA.

## Where it is required

- All bilateral institutional [[money-market-repo|repo]] and reverse repo.
- Tri-party repo (alongside the tri-party agreement with the agent — see [[triparty-clearing]]).
- Buy-sell-backs (the European equivalent of repo without a securities-borrowing rationale).

## Key terms

- **Purchase Price**: the cash amount paid for the security at the start.
- **Repurchase Price**: cash returned at the end (Purchase Price + repo rate × tenor).
- **Margin Maintenance**: variation margin if collateral value falls.
- **Substitution**: borrower can replace collateral with equivalent securities.
- **Income payments**: coupon / dividend paid on the collateral during the trade passes back to the seller via the buyer.
- **Events of Default**: failure to pay, failure to deliver, insolvency, misrepresentation, cross-default.
- **Close-out netting**: on default, all repos terminate; net replacement value calculated.

## Variants

- **GMRA 2011** is the current version — best for new relationships.
- **GMRA 2000** is widely-used; still active.
- **MRA** (Master Repurchase Agreement) — US-specific SIFMA version, sometimes called "the SIFMA MRA."
- **GMSLA** (Global Master Securities Lending Agreement) — sister document for securities lending.

## EMS implications

- Counterparty enablement for repo requires GMRA in place — checked at [[arch-validator]].
- Eligible collateral per GMRA Schedule is reference data in [[arch-reference-data-service]].
- Tri-party schedules layered on top.
- See [[arch-borrow-service]] for securities-lending overlap (GMSLA mechanics).

## Related

- [[isda]] (sister for derivatives)
- [[money-market-repo]] · [[gcf-repo]] · [[tri-party-vs-bilateral-repo]] · [[triparty-clearing]]
- [[arch-borrow-service]] (sec-lending) · [[arch-validator]] · [[counterparty-enablement]]
