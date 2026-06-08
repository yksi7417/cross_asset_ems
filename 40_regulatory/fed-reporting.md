---
type: regulatory
regulator: FED
status: draft
tags: [regulatory]
---

# Federal Reserve Reporting

The **US Federal Reserve System** collects various structured reports from regulated financial institutions, bearing on solvency, large-trader activity, and money-market participation. Most are not real-time trade reports (those go to TRACE / RTRS / SDRs), but periodic regulatory filings.

## Major Fed-administered reports

### FR Y-14 (CCAR Stress Tests)

Large bank holding companies submit detailed risk-position and capital data for stress-test analysis.

### FR Y-15 (Banking Organization Systemic Risk Report)

Large banks report systemic-risk indicator data — cross-border activity, intra-financial assets/liabilities, securities outstanding.

### FR 2900 / 2950 (Money Stock)

Deposit-based monetary aggregate inputs.

### FR Y-9C (Consolidated Financial Statements for Bank Holding Companies)

Quarterly call report.

### OFR-100 (Large Position Report — Treasury)

For UST primary dealers and other significant Treasury holders, position-level reporting.

### TIC (Treasury International Capital)

Cross-border securities flows.

### Primary Dealer Statistics

Primary dealers report trading, position, and inventory data to the New York Fed weekly.

## Touchpoints in the EMS

- Most Fed reporting is bank-side (treasury, finance, risk) not EMS-side.
- Position-level reports require accurate position data — supplied by [[arch-position-service]].
- UST positions for primary-dealer reporting flow from [[arch-position-service]] post-trade.
- Stress-test inputs (FR Y-14) include trading positions sourced from EMS data.

## Related

- [[govt-bonds]] · [[money-market-repo]] · [[money-market-tbills]]
- [[arch-position-service]] · [[arch-regulatory-reporting-service]]
- [[fdic-occ]] · [[ficc-reporting]] (sibling US regulator components)
