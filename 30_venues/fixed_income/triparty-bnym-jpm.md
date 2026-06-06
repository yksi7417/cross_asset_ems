---
type: venue
venue_kind: dealer_platform
asset_classes: ["fixed_income"]
status: draft
tags: [venue/triparty]
---

# Tri-Party Repo (BNY Mellon / JPM)

**BNY Mellon** and **JPMorgan Chase** are the two US tri-party agents for **tri-party repo** — they sit between the repo borrower and lender, holding collateral in custody, performing daily mark-to-market, collateral substitution, and margin calls.

## Asset classes

- US Treasury / Agency GC repo
- IG corporate GC repo (selected programs)
- Equity repo (selected programs)
- International repo (BNY Mellon has a sizeable EUR tri-party book)

## Workflow mechanisms

- **Bilaterally negotiated trade** — counterparties agree term, rate, collateral schedule.
- **Tri-party settlement** — agent receives collateral, applies haircuts, monitors margin daily.
- **Collateral substitution** — borrower can swap eligible collateral intraday.

## Connectivity

- **FIX** post-trade allocation feeds from each agent.
- Proprietary APIs (BNY Mellon's RepoEdge, JPM's Tri-Party connectivity) for collateral schedules, eligibility, intraday margin.
- SWIFT for cash settlement instructions.

## Key facts

- Tri-party is operationally distinct from **bilateral repo** (DVP) and **FICC GCF**.
- Reform after 2008 reduced intraday credit exposure to the agents.
- Eligibility schedules are a first-class concept the EMS must understand.

## Related

- [[money-market-repo]] · [[arch-borrow-service]] (sec lending adjacency)
- [[brokertec]] (electronic GCF / bilateral) · [[bloomberg-repo]] (monitor concept)
