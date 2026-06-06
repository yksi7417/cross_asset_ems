---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# Agency vs. Principal

Two ways a broker can execute a client trade:

- **Agency** — the broker acts as the client's agent, **routing the order to an external venue** and passing the resulting fill back. The broker doesn't own the position at any point; they earn a commission for the service. Best-ex obligations are clean — the broker had no skin in the game.
- **Principal** — the broker takes the **other side of the trade** out of its own balance sheet. The broker now owns the position (or short) and must work out of it. They earn the bid-ask spread (and risk) rather than a commission.

Most modern broker offerings blend both: client orders may match against the broker's central risk book ([[central-risk-book]]) for some quantity (principal), with the residual routed agency. The **trade capacity** field on every execution distinguishes the two, both for the buy-side's records and for regulatory reporting.

Agency-only brokers (like [[instinet]], Liquidnet) **structurally cannot do principal** by their business model. This is the key reason some buy-side firms prefer them for sensitive flow — no conflict between the broker's principal book and the client's intent.

## Example

A buy-side trader sells 100K shares to Goldman. 30K shares match against Goldman's central risk book at midpoint (principal — Goldman now owns the 30K to work out); 70K go to NYSE / Nasdaq / IEX via Goldman algos (agency — fill back to client at market prices). The execution report distinguishes the 30K principal fill from the 70K agency fills.

## Why it matters in an EMS

- Trade capacity (agency vs principal) is a first-class field on every fill.
- Best-ex audit benchmarks differ (agency vs midpoint NBBO; principal vs midpoint + risk premium).
- [[arch-surveillance]] watches for principal trades against client flow that might be conflicted.

## Related

- [[capital-commitment]] · [[central-risk-book]]
- [[systematic-internaliser]] (a regulated principal-internalization model)
- [[_brokers-overview]] · [[instinet]] (agency-only example)
- [[arch-best-execution]] · [[arch-surveillance]]
