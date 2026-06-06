---
type: clearing_settlement
kind: cash_clearing
status: draft
tags: [clearing/cash_clearing]
---

# DTC (Depository Trust Company)

DTC is the **primary US securities depository** — the central record-keeper for US equity and corporate-bond ownership. Settles equities, corporate bonds, munis, and most non-Treasury cash debt via DVP. DTC is a DTCC subsidiary; sibling to NSCC (equity clearing), FICC (Treasury/MBS clearing).

## Asset classes / instruments

- US [[cash-equity]] (equities, ADRs, ETFs, REITs).
- US [[corp-bonds-ig]] / [[corp-bonds-hy]] / [[municipal-bonds]].
- US [[abs]] / [[mbs|non-agency MBS]] / [[convertibles]].
- Money-market instruments ([[money-market-cp-cd]] when DTC-eligible).

## Settlement cycle

- **US equity**: T+1 (since 28 May 2024) — see [[tplus-1-tplus-2]].
- **US corporate / muni**: T+2 typically; transitioning toward T+1.
- **DVP / RVP / FOP** all supported — see [[dvp-rvp-fop]].

## Membership / access

DTC participants: broker-dealers, banks, custodians. Buy-side firms access DTC through their custodian (most common) or as direct participants (very large firms only).

## EMS touchpoints

- Settlement instructions generated post-execution per the allocation step in [[arch-allocation-service]].
- DTC ID numbers (DTC participant codes) are reference data per account in [[arch-reference-data-service]].
- Settlement breaks flow as exceptions through [[arch-stp-pipeline]] and surface via [[arch-notification-service]].
- T+1 timing compresses [[arch-confirmation-affirmation|confirmation/affirmation]] windows materially.

## Related

- [[cash-equity]] · [[corp-bonds-ig]] · [[municipal-bonds]] · [[mbs]] · [[abs]]
- [[allocation-affirmation-confirmation]] · [[dvp-rvp-fop]] · [[tplus-1-tplus-2]]
- [[fedwire]] (UST sibling) · [[ficc-clearing]] (Treasury/MBS sibling)
- [[arch-stp-pipeline]] · [[arch-confirmation-affirmation]]
