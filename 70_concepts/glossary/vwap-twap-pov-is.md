---
type: concept
status: draft
tags: [concept/glossary, glossary/equity, glossary/execution]
---

# VWAP / TWAP / POV / IS — Execution Benchmarks & Algos

Four canonical execution **benchmarks** that double as **algorithm names** because algos target them:

- **VWAP** (Volume-Weighted Average Price) — average price of all market trades over a period, weighted by traded size. **VWAP algo** trades to track that pattern (more in busy periods, less in quiet).
- **TWAP** (Time-Weighted Average Price) — average price over a period, weighted equally by time slices. **TWAP algo** trades evenly across the window regardless of volume.
- **POV** (Percentage Of Volume) — also called "Participation" — algo trades at a fixed proportion of current observed volume (e.g. "10% POV"). Adapts to live volume rather than a historical pattern.
- **IS** (Implementation Shortfall) — Almgren-Chriss-style algo that **balances market impact vs opportunity cost** to minimise expected slippage vs arrival price. The most "intelligent" of the four — dynamically front- or back-loads based on market signals.

Plus variants: VWAP-to-close, IS-with-aggression, POV-with-cap, dark-only IS, lit-only VWAP. Each algo has dozens of tuning knobs (start-time, end-time, aggression, dark-fraction, anti-gaming, urgency).

## Example

A trader buys 200K shares over 9:30-16:00. Choosing a strategy:
- "Match the day's tape" → VWAP algo. Risk: if late-day volume spikes, VWAP backloads heavily.
- "Be steady, predictable" → TWAP. Risk: ignores live conditions.
- "Don't push >10% of the prints" → POV 10%. Risk: never finishes if volume is light.
- "Minimise expected slippage" → IS algo. Risk: more complex, harder to audit.

## Why it matters in an EMS

- The EMS surfaces broker algos by these names — clients select by intuition.
- TCA benchmarks against these (slippage vs arrival, vs VWAP, vs IS).
- The pre-trade analytics service ([[arch-pretrade-analytics]]) recommends strategy by expected slippage.

## Related

- [[arch-pretrade-analytics]] · [[arch-realtime-analytics]] · [[arch-tca]]
- [[arch-smart-order-router]] (algos compose with SOR strategies)
- [[_brokers-overview]] (each broker's algo suite)
- [[closing-auction]] (close-targeted algos)
