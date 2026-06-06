---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# On-the-Run vs. Off-the-Run

**On-the-run (OTR)** is the **most recently auctioned** Treasury at a given maturity (e.g. the current 10-year UST). **Off-the-run** is any older issue at the same maturity that has been displaced by a newer OTR.

OTRs trade with a **liquidity premium**: tighter bid-ask, deeper books, and they are the price-discovery benchmark for that tenor. Off-the-runs trade at a small yield concession reflecting the lower liquidity — the "OTR/OFF basis."

When a new auction settles, the previously OTR issue becomes off-the-run; the new issue takes the OTR label. Execution algos must be aware of this rotation — what was the natural hedge yesterday is no longer the deepest book today.

## Example

If the Treasury auctions a new 10-year on 2026-05-15, that bond is the 10y OTR until the next 10y auction (~Aug 2026), at which point it becomes off-the-run. Pre-auction "when-issued" trading also references the upcoming OTR — see [[when-issued]].

## Why it matters in an EMS

- Routing decisions: OTRs go to [[brokertec]] CLOB and [[tradeweb]] D2C with tight depth; off-the-runs route to [[tradeweb]] RFQ or [[opendoor]] sessions.
- TCA: benchmarking off-the-run executions against OTR mid is misleading — use the off-the-run's own curve.
- Convertibles and corporates often hedge with the relevant OTR.

## Related

- [[when-issued]] · [[govt-bonds]] · [[treasury-direct]]
- [[brokertec]] · [[tradeweb]] · [[opendoor]]
- [[arch-best-execution]] (off-the-run benchmarking)
