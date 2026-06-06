---
type: regulatory
regulator: FINRA
status: draft
tags: [regulatory]
---

# FINRA Reporting (CAT, OATS, ATS, 605/606)

**FINRA** (Financial Industry Regulatory Authority) is the self-regulatory organisation overseeing US broker-dealers. Major reporting regimes the EMS touches:

## Major FINRA reporting regimes

### CAT — Consolidated Audit Trail
The dominant equity / options lifecycle audit trail — see [[finra-cat]] glossary. Every order receipt, route, modify, cancel, execution across US national securities exchanges and ATSs flows to CAT by 8am next-day. Replaces OATS.

### OATS — Order Audit Trail System (legacy)
Historic order audit reporting; **decommissioned 2021** in favour of CAT. Mentioned here because legacy systems may still reference OATS conceptually.

### Rule 605 / 606 — Broker Execution Quality Reports
- **Rule 605**: market centres publish monthly execution-quality statistics per security.
- **Rule 606**: broker-dealers publish quarterly order-routing reports — where they routed flow and any payment-for-order-flow received.

### ATS-N — Form ATS-N
ATSs (alternative trading systems — see [[ats-ecn-mtf]]) must publish detailed Form ATS-N disclosures on their order types, matching mechanics, subscriber arrangements.

### TRACE — see [[trace]] (FINRA-administered)
The fixed-income reporting regime; FINRA operates TRACE for FINRA-jurisdiction broker-dealers.

### SLATE (incoming) — Securities Lending Reporting
Securities-lending transaction reporting under SEC Rule 10c-1 (effective phased 2025-2026). Submission to FINRA SLATE system. See [[arch-borrow-service]].

## Touchpoints in the EMS

- [[arch-regulatory-reporting-service]] handles broker-dealer-side reporting (FINRA member firms).
- 605/606 reports are produced by [[arch-best-execution]] from audit data.
- ATS-N disclosures relate to the venue's own filings, not directly to the EMS.
- SLATE submissions via [[arch-borrow-service]] integration.

## Related

- [[finra-cat]] · [[trace]] (glossary entries)
- [[arch-best-execution]] · [[arch-regulatory-reporting-service]]
- [[cash-equity]] · [[equity-derivatives]] (CAT scope)
- [[arch-jurisdictional-compliance]]
