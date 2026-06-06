---
type: concept
status: draft
tags: [concept/glossary, glossary/execution]
---

# Netting

**Netting** combines multiple buy/sell exposures into single net obligations. **Trade netting** offsets opposing trades in the same instrument; **payment netting** offsets cash flows between the same counterparties; **CCP netting** offsets all member exposures into a single number per CCP per day.

In an EMS context, the most relevant flavour is **pre-trade order netting** — combining the buy and sell orders across a desk's underlying accounts into a single net market-facing order, then allocating the fills back to the underlying accounts post-trade. This is mandatory in FX swap-net workflows ([[fx-netting]]), heavily used in equity program trading, and common in fixed-income basket execution.

Done badly, netting hides material per-account misbehaviour — a typo on one child order that nets to zero on the parent must still be flagged by [[arch-compliance]]'s fat-finger check. The compliance pipeline therefore runs **on both un-netted and netted views** to avoid this trap.

## Example

Three sub-accounts of a fund: A buys 100k EUR/USD, B sells 30k EUR/USD, C buys 20k EUR/USD. Netted parent: buy 90k EUR/USD. The 90k goes to market via FXSpotStream RFQ; the fill is allocated back to A (100k), B (-30k), C (20k) at the parent fill price.

## Why it matters in an EMS

- Net-vs-unnetted compliance is a recurring failure mode — see [[arch-compliance]].
- FX swap-netting respects value-date arithmetic and PB segregation — see [[arch-fx-netting]].
- Allocation back is part of the [[arch-allocation-service]] / [[arch-stp-pipeline]].

## Related

- [[arch-fx-netting]] · [[arch-aggregation]] · [[arch-allocation-service]]
- [[arch-compliance]] · [[arch-validator]]
- [[ccp-vs-bilateral]] (CCP netting at the post-trade layer)
