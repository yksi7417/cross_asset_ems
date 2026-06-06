---
type: concept
status: draft
tags: [concept/glossary, glossary/fx]
---

# Spot Date / Value Date

The **value date** of an FX trade is the day **cash settlement actually happens**. The **spot date** is the standard market convention for spot trades — typically **T+2 business days** from the trade date, where T+2 respects the **non-holiday calendars of both currencies** in the pair. Some pairs have shorter conventions: USD/CAD is T+1, USD/MXN is T+1, USD/RUB historically T+1.

For **forward**, **swap**, and **NDF** trades, the value date is later — explicitly chosen by the trader (e.g. "1M forward" = the spot date + 1 calendar month, adjusted for holidays per Modified Following). A **broken date** is any value date that doesn't fall on a standard tenor (1W, 1M, 3M, etc.) — broken dates require interpolated pricing.

Both currencies' calendars must be open on the value date; if one is closed the date rolls forward (or backward, depending on convention).

## Example

Trade EUR/USD spot on Monday 2026-06-08. Spot date = T+2 = Wednesday 2026-06-10 (both ECB and Fedwire open). A 1M forward dealt the same day has value date 2026-07-10 (Modified Following — if 2026-07-10 were a holiday, roll to next business day unless that crosses month-end, then roll back).

## Why it matters in an EMS

- Every FX trade carries a value date as a first-class field — see [[arch-fx-netting]] for the value-date arithmetic.
- The validator checks that value dates are valid (both calendars, business-day rules).
- Cash projections in [[arch-stp-pipeline]] are per value date.

## Related

- [[fx-spot]] · [[fx-forward]] · [[fx-swap]] · [[fx-ndf]]
- [[arch-fx-netting]] · [[arch-reference-data-service]] (calendars)
- [[arch-stp-pipeline]] (cash projection)
