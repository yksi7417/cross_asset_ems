---
type: documentation
status: draft
tags: [documentation]
---

# CSA (Credit Support Annex)

The **Credit Support Annex** is an annex to the [[isda|ISDA Master Agreement]] that governs **collateral / margin between OTC derivative counterparties**. Defines what collateral can be posted, eligibility, thresholds, minimum transfer amounts, valuation, dispute resolution. Several variants: NY-law CSA, English-law CSA (transfer), English-law VM CSA (uncleared margin rules).

## Where it is required

- Any OTC derivative relationship where margining is required.
- **Uncleared Margin Rules (UMR)** — under post-2008 reforms, in-scope counterparties (most large banks and many asset managers) must exchange both **Variation Margin (VM)** and **Initial Margin (IM)** under prescribed methodologies.

## Key terms

- **Eligible Collateral**: what can be posted (cash, USTs, AAA-rated securities, etc.).
- **Haircuts**: per-asset-class valuation reductions for posted collateral.
- **Threshold**: amount of exposure permitted before any collateral required.
- **Minimum Transfer Amount (MTA)**: collateral movements rounded to MTA (e.g. $250K).
- **Independent Amount / Initial Margin**: amount posted from inception regardless of exposure.
- **Margin call timing**: typically daily; UMR may impose intra-day calls.
- **Dispute resolution**: process for resolving valuation disagreements.

## UMR specifics

- **ISDA SIMM** (Standard Initial Margin Model) — the industry-standard IM methodology.
- **Schedule-based IM** — simpler alternative for smaller exposures.
- **AANA test** — Average Aggregate Notional Amount determines whether you're in scope.

## EMS implications

- Margin requirements per counterparty pair surface in [[arch-risk-engine]] and [[arch-position-service]].
- Daily margin calls land in [[arch-stp-pipeline]] for cash forecasting.
- Eligible-collateral lists are reference data in [[arch-reference-data-service]].
- IM segregation under UMR requires tri-party agent arrangements ([[triparty-clearing]]).

## Related

- [[isda]] (parent) · [[cds-annex]] (CDS-specific)
- [[gmra]] (sister for repo — has its own collateral mechanics)
- [[arch-risk-engine]] · [[arch-stp-pipeline]] · [[arch-jurisdictional-compliance]] (UMR)
- [[triparty-clearing]] (IM segregation)
