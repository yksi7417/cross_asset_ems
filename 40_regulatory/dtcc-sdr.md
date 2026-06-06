---
type: regulatory
regulator: DTCC
status: draft
tags: [regulatory]
---

# DTCC SDR (Data Repository)

**DTCC Data Repository (DDR)** is the **dominant CFTC-registered SDR** (Swap Data Repository) — see [[cftc-sdr]]. Operates under DTCC, registered with the CFTC; the destination for most US swap-data reporting.

## What it is

DTCC's swap-data reporting service — accepts swap data submissions from reporting parties (SEFs, dealers, buy-side), publishes anonymized real-time public reports, and stores regulator-level detailed records for CFTC consumption.

## Scope (asset classes)

- All CFTC-jurisdictional swaps: IRS, CDS, FX swaps/forwards/NDFs (where applicable), commodity swaps, equity swaps.
- Plus EU/UK [[emir-sftr-csdr|EMIR]] reporting via DTCC's EU operation (DTCC Data Repository Ireland) for European entities.
- Plus selected APAC regimes.

## Reporting timing / fields

- Real-time public report: minutes.
- Regulator report: T+1.
- Lifecycle events: amend, novate, terminate as they occur.
- Fields: ISDA Common Domain Model alignment.

## Touchpoints in the EMS

- [[arch-regulatory-reporting-service]] submits to DDR via SFTP / FIX / proprietary API per DDR submission rules.
- Submission acks/nacks tracked; correction workflow on rejection.
- Multi-jurisdictional firms submit the same trade to DDR (US) and DDR Ireland (EU) under different reporting profiles.

## Related

- [[cftc-sdr]] (the regime) · [[emir-sftr-csdr]] (EU regime)
- [[sef-reporting]] (CFTC SEF reporting) · [[ficc-reporting]] (DTCC's other reporting arm)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
