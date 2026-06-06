---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# FINRA CAT

**FINRA CAT** ("Consolidated Audit Trail") is the SEC-mandated database of **every equity and options order lifecycle event across all US national securities exchanges and ATSs**. Replaces the older OATS regime. Reporting parties: exchanges, ATSs, broker-dealers — they submit every order receipt, route, modify, cancel, and execution by 8:00am next-day.

CAT is the **regulator's microscope** on US equity market activity. Cross-venue investigations of spoofing, layering, marking-the-close, and front-running rely on CAT's complete event chain. Per-event timestamps must be **PT-class accurate** (millisecond or better depending on participant type).

For a buy-side EMS, CAT obligations typically flow through the executing broker (the broker reports on the client's behalf), but the firm must ensure its trade data carries the **required identifiers** (broker order ID, route ID, customer ID — the LOPR / CAID variants).

## Example

A buy-side firm sends a 1000-share AAPL order via Goldman algos. Goldman receives the parent order, generates a CAT report. The algo slices into child orders to NYSE, Nasdaq, IEX — each child route is a separate CAT event. Each fill is another CAT event. The buy-side never directly reports to CAT — Goldman does — but the entire chain must be reconstructable to CAT.

## Why it matters in an EMS

- The EMS must produce timestamps and identifiers that meet CAT's accuracy requirements.
- Identity chaining ([[arch-identity-chaining]]) is partially motivated by CAT-style audit needs.
- See [[40_regulatory/finra-cat]] (when written) for deeper detail.

## Related

- [[cash-equity]] · [[equity-options]]
- [[trace]] · [[msrb-rtrs]] · [[cftc-sdr]] (sibling US regimes)
- [[arch-regulatory-reporting-service]] · [[arch-identity-chaining]]
- [[arch-jurisdictional-compliance]]
