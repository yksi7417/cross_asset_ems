---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# MSRB RTRS

**MSRB RTRS** ("Municipal Securities Rulemaking Board — Real-Time Transaction Reporting System") is the **post-trade reporting facility for US municipal bonds**. Reporting obligations apply to broker-dealers; reports are due **within 15 minutes** of execution and are disseminated publicly via the MSRB EMMA system.

Like TRACE, RTRS surfaces price-discovery information into a market that otherwise has thin pre-trade transparency — munis trade primarily via dealer RFQ with limited continuous quote streams. The RTRS tape is consumed by composite-pricing services (CBBT, IDC) and by buy-side TCA.

Block-size dissemination rules apply: large trades can be reported on a delayed basis or with capped size disclosure to avoid market disruption, similar to TRACE.

## Example

A buy-side fund executes a 5M GO bond block via [[municenter]]. The executing dealer reports the trade to MSRB RTRS within 15 minutes. RTRS disseminates the print to EMMA; the trade appears on Bloomberg muni screens within minutes and feeds composite pricing across the market.

## Why it matters in an EMS

- The EMS submits RTRS reports for muni trades the firm is reporter on.
- Timing and field requirements are distinct from TRACE.
- See [[40_regulatory/msrb-rtrs]] for the deeper note.

## Related

- [[municipal-bonds]] · [[municenter]] · [[ice-bondpoint]]
- [[trace]] · [[cftc-sdr]] · [[finra-cat]] (sibling US reporting destinations)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
