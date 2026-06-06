---
type: concept
status: draft
tags: [concept/glossary, glossary/fi, glossary/settlement]
---

# GCF Repo

**GCF Repo** ("General Collateral Finance Repo") is a **FICC-cleared interdealer repo product** for US Treasuries and Agency MBS. The execution is on a CLOB (typically BrokerTec GCF or Dealerweb GCF); settlement clears through FICC's GCF Repo Service, which acts as a CCP for the dealer-to-dealer leg.

The "GCF" distinguishes this from bilateral repo (uncleared) and from tri-party repo (operational mechanism, distinct from clearing). FICC clearing means: (1) reduced credit exposure between dealers; (2) multilateral netting across all GCF positions; (3) standardized eligibility, haircuts, and operational discipline. Each evening, FICC nets all GCF activity into per-member positions.

The GCF market is **interdealer only** — buy-side firms don't directly access GCF; they get GCF-like financing through a dealer's tri-party leg or through bilateral repo arrangements. But GCF rates are a key benchmark for dealer-to-buy-side pricing.

## Example

Goldman finances 1B of UST collateral overnight in GCF Repo on BrokerTec, borrowing from JPM. Both dealers' positions clear through FICC; FICC nets Goldman's and JPM's GCF exposure across all their counterparties. The next morning, GCF prints feed back into Bloomberg REPO monitor and dealer-to-buy-side pricing for tri-party repo.

## Why it matters in an EMS

- For buy-side EMS, GCF is a **benchmark rate**, not a directly executed product.
- Sell-side EMS implementations integrate with FICC GCF clearing directly.
- See [[bloomberg-repo]] for the pricing-monitor concept.

## Related

- [[money-market-repo]] · [[tri-party-vs-bilateral-repo]] · [[triparty-bnym-jpm]]
- [[brokertec]] (GCF execution) · [[bloomberg-repo]]
- [[ficc-clearing]] (the GCF clearing destination)
