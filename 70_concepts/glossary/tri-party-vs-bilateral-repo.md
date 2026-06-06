---
type: concept
status: draft
tags: [concept/glossary, glossary/fi, glossary/settlement]
---

# Tri-Party vs. Bilateral Repo

Two operational models for executing a repo trade:

- **Bilateral repo** — buyer and seller exchange cash and securities **directly**, without an intermediating agent. Each side handles its own custody, valuation, mark-to-market, and substitution. Simpler conceptually but operationally heavy — each repo requires custody operations on both sides.
- **Tri-party repo** — a **tri-party agent** (BNY Mellon or JPM in US; BNY Mellon, Clearstream, or Euroclear in EU) sits between the parties. The agent holds the collateral, applies pre-agreed eligibility schedules, marks-to-market daily, processes margin calls, and lets the borrower **substitute collateral intraday**. The two parties agree the trade economics; the agent runs the operational pipeline.

Tri-party is dominant for **general-collateral GC repo** where the cash lender doesn't care about specific CUSIPs — they just want UST or Agency collateral within an eligibility schedule. Bilateral is dominant for **specific repo** where the cash lender wants a particular CUSIP (often because it's "special" — trading rich and they want to short it).

Post-2008 reforms (NY Fed-driven) reduced intraday credit exposure to tri-party agents.

## Example

A money-market fund lends 500M cash overnight against a UST GC collateral schedule via BNY Mellon tri-party. BNY holds the collateral, marks-to-market at the start of day, processes margin calls if values drop, and allows the borrower to substitute one UST CUSIP for another during the day without disturbing the lender. The lender just sees "500M secured by eligible collateral, MTM by BNY."

## Why it matters in an EMS

- Tri-party trades carry different fields (eligibility schedule, agent, haircut policy) than bilateral.
- [[arch-borrow-service]] adjacent for securities-lending overlay.
- See [[triparty-bnym-jpm]] for the tri-party agent venues.

## Related

- [[money-market-repo]] · [[gcf-repo]] · [[triparty-bnym-jpm]]
- [[brokertec]] (electronic GCF / bilateral execution) · [[mts]] (EU repo)
- [[arch-borrow-service]] · [[bloomberg-repo]]
