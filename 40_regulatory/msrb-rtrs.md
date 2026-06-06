---
type: regulatory
regulator: MSRB
status: draft
tags: [regulatory]
---

# MSRB RTRS

**Municipal Securities Rulemaking Board — Real-Time Transaction Reporting System** — the post-trade reporting facility for **US municipal bonds**. Mandatory for broker-dealers; disseminated publicly via MSRB EMMA system. See the [[msrb-rtrs|glossary entry]] for the quick definition.

## Scope (asset classes)

- US [[municipal-bonds]] (GO, revenue, refunding, BABs, taxable munis, 529s).
- Includes new-issue and secondary trades.

## Reporting fields / timing

- **Timing**: **15 minutes** from execution.
- **Fields**: ~40 fields including CUSIP, side, quantity, price, when-issued flag, capacity (agency vs principal), commission, settlement date.
- **Block-size caps** — analogous to TRACE.
- **Submission channel**: MSRB-managed submission via FIX or MSRB portal; data flows to EMMA system for public dissemination.

## Touchpoints in the EMS

- [[arch-regulatory-reporting-service]] handles RTRS submission for trades the firm is the obligated reporter on.
- The asset-class detection identifies muni trades and routes through the MSRB submission profile.
- Submission deadlines tracked; missed deadlines escalate via [[arch-notification-service]].

## Related

- [[msrb-rtrs|MSRB RTRS glossary]] · [[municipal-bonds]] · [[municenter]] · [[ice-bondpoint]]
- [[trace]] · [[cftc-sdr]] · [[finra-cat]] (sibling US reporting regimes)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
