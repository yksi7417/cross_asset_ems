---
type: regulatory
regulator: FDIC
status: draft
tags: [regulatory]
---

# FDIC / OCC Reporting

The **FDIC** (Federal Deposit Insurance Corporation) and **OCC** (Office of the Comptroller of the Currency) jointly regulate insured depository institutions — banks. Both collect periodic regulatory reports relevant to trading activity in banks.

## Major reports

### Call Report (FFIEC 031 / 041 / 051)

Quarterly consolidated financial report submitted by all FDIC-insured banks. Includes detailed trading-asset, derivative-position, and OTC-derivative data. Required of all US banks.

### Bank Holding Company reports

Joint Fed-FDIC-OCC reports on bank holding companies.

### Volcker Rule reporting

OCC oversees Volcker Rule compliance — banks limited in proprietary trading; required to report trading activity, risk limits, position turnover. Drives the "trading vs market-making" determination.

### CECL — Current Expected Credit Losses

Quarterly credit-loss provision under accounting rules (FASB ASU 2016-13); not a trade report but interacts with credit-exposure data.

## Touchpoints in the EMS

- Banks' EMS deployments must produce reportable position and exposure data.
- Volcker Rule classifications drive flag-handling: market-making (allowed) vs proprietary trading (restricted).
- [[arch-compliance]] enforces Volcker-related limits.
- Call Report inputs sourced from [[arch-position-service]] and ledger systems.

## Related

- [[fed-reporting]] (sister regulator) · [[finra]] (broker-dealer side)
- [[arch-compliance]] · [[arch-position-service]] · [[arch-regulatory-reporting-service]]
- [[arch-jurisdictional-compliance]]
