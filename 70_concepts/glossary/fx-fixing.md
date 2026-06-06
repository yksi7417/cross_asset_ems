---
type: concept
status: draft
tags: [concept/glossary, glossary/fx]
---

# FX Fixing

An **FX fixing** is a **reference exchange rate published at a specific time each day**, used to settle trades whose price was agreed as "at the fix" rather than at a live quote. Major fixings: **WMR 4pm London** (the dominant institutional benchmark — used by index funds, NAV cuts, treasury-flow hedging), **ECB reference rates 14:15 CET**, **PBOC mid-rate 09:15 SH**, **BBC 1pm**.

Fixings exist because **passive flows need a single agreed price** — an S&P 500 fund tracking a USD-EUR investor base must convert its NAV-cut cash flow at a single rate, not a stream of live executions. Trading "at the fix" means the broker commits to deliver the official fix rate (typically with a small spread or commission).

The fix is itself the result of executed flow (WMR uses a 5-minute window of actual trades), which historically created market impact around the fix window — the "**fix at the fix**" gaming problem that drove the 2014 FX manipulation settlements and tightened WMR methodology.

## Example

A pension fund needs to hedge $500M USD into EUR at month-end NAV. It books a "WMR 4pm London EUR/USD" with its bank. At 4pm London, WMR publishes (say) 1.1042. The trade settles at 1.1042 regardless of where the live market is at 4:01pm.

## Why it matters in an EMS

- Fixing orders are a first-class order type — see [[auto-route-fixing-aim]] for the automation pattern.
- Pre-fix risk is taken by the bank, not the buy-side — but TCA against fix vs arrival mid is reported.
- Compliance must monitor fix-window trading for [[arch-surveillance|surveillance]] of marking-the-close patterns.

## Related

- [[fx-spot]] · [[fx-forward]]
- [[auto-route-fixing-aim]] (fixing-order automation)
- [[arch-surveillance]] (fix-window patterns)
- [[arch-tca]] (fix vs arrival benchmarking)
