---
type: concept
status: draft
tags: [concept/glossary, glossary/fx]
---

# Forward Points / Swap Points

**Forward points** (also "swap points") are the **difference between the FX forward rate and the spot rate**, quoted in **pips**. They reflect the **interest-rate differential** between the two currencies — by covered interest parity, the forward rate equals spot adjusted for the rate differential over the period.

When EUR rates are lower than USD rates, EUR/USD trades at a **forward discount** — the forward is below spot, forward points are negative. The reverse means a forward premium. Pips are typically the fourth decimal place (0.0001 for most major pairs; 0.01 for JPY pairs).

A **FX swap** trade is a pair of legs: spot in one direction and forward in the opposite — net cash flow on the spot date and the offsetting cash flow on the forward date. The swap points are what's traded; the spot leg is at a reference rate.

## Example

EUR/USD spot 1.1000. EUR 1y rate 2.0%; USD 1y rate 4.0%. 1y forward points ≈ -200 pips. 1y forward rate ≈ 1.0800. A 1y EUR/USD swap is dealt at swap points -200; the spot leg is at the prevailing reference rate (often the WMR fix or an agreed level).

## Why it matters in an EMS

- The router must understand FX swap as a 2-leg trade ([[arch-multileg]]).
- Forward-point staleness checks belong in the validator (vs interest-rate curve).
- TCA decomposes swap performance into spot-leg slippage + swap-point slippage.

## Related

- [[fx-forward]] · [[fx-swap]] · [[fx-ndf]]
- [[arch-multileg]] · [[arch-pricing-service]] (curve-implied forward points)
- [[fx-fixing]] (reference rates for swap spot legs)
