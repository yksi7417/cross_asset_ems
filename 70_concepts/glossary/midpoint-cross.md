---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Midpoint Cross

A **midpoint cross** is a trade matched at the **arithmetic mean of the current lit NBBO bid and offer**. The matching venue (usually a dark pool or ATS) doesn't display its book pre-trade — buyers and sellers post at "midpoint" and match when a contra arrives.

This is the dominant dark-pool execution mechanism: by pricing at midpoint, the dark pool delivers **price improvement vs both lit sides** — a buyer pays better than the lit offer, a seller receives better than the lit bid. Both sides of the cross are happy relative to lit execution.

Midpoint cross is regulatory-blessed for dark venues: by definition the cross is **at or better than NBBO**, so Reg-NMS Rule 611 is satisfied without displaying quotes pre-trade. The trade-off is **execution probability** — you only fill if a contra shows up at the same midpoint, which can take time or never happen.

## Example

AAPL NBBO is 145.12 / 145.14. A buyer and seller both post at midpoint (145.13) in IEX's dark book. They match at 145.13. The buyer paid 1 cent less than the lit 145.14 offer; the seller received 1 cent more than the lit 145.12 bid. NBBO is preserved.

## Why it matters in an EMS

- The SOR can elect midpoint-only routes for size-sensitive flow.
- TCA must report midpoint-cross fills with explicit "price improvement vs lit" attribution.
- Some midpoint mechanisms include adverse-selection avoidance (IEX D-Peg uses the speed bump signal to defer crosses when adverse).

## Related

- [[lit-vs-dark]] · [[nbbo-ebbo]] · [[reg-nms]]
- [[iex]] (D-Peg + midpoint mechanics) · [[goldman-sachs]] (Sigma X)
- [[arch-smart-order-router]] · [[arch-best-execution]]
