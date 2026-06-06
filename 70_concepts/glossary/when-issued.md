---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# When-Issued (WI)

**When-issued** trading is **secondary trading in a security that has been announced but not yet settled** — most commonly used for upcoming Treasury auctions. Trades transact at a yield/price, contingent on the auction settling normally; if the auction fails or is cancelled the WI trades cancel too.

For US Treasury auctions, WI trading typically begins on the **announcement date** (a week before the auction for most maturities) and ends when the auction settles. During this window, dealers are price-discovering the upcoming on-the-run, and the buy-side can build positions ahead of settlement.

WI trading helps the auction process: by the time the auction happens, dealers have a good read on demand and can bid more confidently. WI prints are often visible on [[bloomberg-btmm]] / [[bloomberg-tbill]] as a "WI" line separate from the current OTR.

## Example

On 2026-06-01 the Treasury announces a new 10y auction for 2026-06-08 with settle 2026-06-15. From 2026-06-01 to 2026-06-15, the "new 10y" trades WI on [[brokertec]] and [[tradeweb]] at a yield, contingent on the auction settling. On 2026-06-15 the WI trades become real settled positions; the previous 10y becomes off-the-run.

## Why it matters in an EMS

- The EMS must support WI tickers as distinct from the current OTR until settlement.
- Risk and TCA must understand WI's contingent nature — settlement failure cancels trades.
- See [[on-the-run-vs-off-the-run]] for the rotation that WI feeds into.

## Related

- [[on-the-run-vs-off-the-run]] · [[govt-bonds]] · [[money-market-tbills]]
- [[treasury-direct]] (the auction)
- [[brokertec]] · [[tradeweb]] (where WI trades)
