---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# Dollar Roll

A **dollar roll** is a two-legged transaction in TBA-MBS where a dealer **sells the near-month TBA** and **buys back the next-month TBA** simultaneously — economically a **financing trade** that funds an MBS position from one Class settlement to the next.

The "roll" price (the spread between the two months, typically negative — the next-month settles cheaper by the drop) implies a financing rate. A "**special**" dollar roll is one where the implied financing rate is meaningfully cheaper than GC repo — signalling specific demand for the underlying coupon/agency in the near month (often around delivery dates when MBS originator supply is light).

The buy-side uses dollar rolls to fund MBS positions; the dealer uses them to manage front-month delivery obligations. Both sides are constantly trading the rolls as MBS positions move.

## Example

In late June, a buy-side firm holds 100M of Fannie 5.5% July TBA. They "roll the position" by selling the July and buying the August TBA simultaneously — net cash flow is the drop (e.g. $0.25 / 100 face), and the position now settles in August.

## Why it matters in an EMS

- The EMS must model dollar rolls as **paired transactions** for risk and best-ex.
- The financing-rate implication links MBS to repo / SOFR — a TCA decomposition.
- See [[arch-multileg]] for paired-leg execution.

## Related

- [[tba-vs-specified-pool]] · [[mbs]]
- [[arch-multileg]] (paired-leg execution)
- [[money-market-repo]] (the financing-rate cross-reference)
