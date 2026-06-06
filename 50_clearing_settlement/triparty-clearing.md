---
type: clearing_settlement
kind: triparty
status: draft
tags: [clearing/triparty]
---

# Tri-Party Clearing

**Tri-party clearing** describes the operational arrangement where a **third-party agent** (a tri-party agent) sits between two principals and handles collateral management, valuation, margining, and substitution. Most common in repo and securities-lending; emerging in other contexts.

## Major tri-party agents

- **BNY Mellon** ([[triparty-bnym-jpm]]) — dominant US tri-party.
- **JPMorgan** ([[triparty-bnym-jpm]]) — dominant US tri-party.
- **Clearstream** ([[clearstream]]) — EU tri-party.
- **Euroclear** ([[euroclear]]) — EU tri-party (also bilateral).

## What the agent does

- Receives collateral from the borrower against an eligibility schedule.
- Marks-to-market collateral daily; processes margin calls if value drops below required.
- Allows borrower to **substitute collateral intraday** within the eligibility schedule.
- Distributes coupon / dividend payments on collateral securities back to the rightful owner.
- Handles maturity processing.

## Asset classes / instruments

- [[money-market-repo|Repo / Reverse Repo]] (primary use case — see [[tri-party-vs-bilateral-repo]]).
- Securities lending (similar mechanics, different documentation).
- Some structured-products collateral pools.
- Initial-margin segregation for uncleared OTC swaps (UMR — see [[arch-jurisdictional-compliance]]).

## EMS touchpoints

- Tri-party trades require eligibility-schedule and haircut-policy fields not present in bilateral repo.
- The agent's daily MTM / collateral movement events feed [[arch-stp-pipeline]].
- Initial-margin tri-party arrangements (e.g. for UMR) integrate via [[arch-confirmation-affirmation]] for margin instructions.

## Related

- [[tri-party-vs-bilateral-repo]] (the conceptual framework)
- [[triparty-bnym-jpm]] · [[clearstream]] · [[euroclear]] (specific agents)
- [[money-market-repo]] · [[arch-borrow-service]]
- [[arch-stp-pipeline]]
