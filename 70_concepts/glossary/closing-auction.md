---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Closing Auction (MOC / LOC)

The **closing auction** is the **single-price call auction at the end of the trading session** — orders accumulate during a closing-imbalance window, then cross at one print that becomes the official close. NYSE's "Closing Cross," Nasdaq's "Closing Cross," LSE's closing auction, JPX's "Itayose" close — all variations of the same mechanism.

**MOC** (Market-on-Close) orders are unconditional — they execute at whatever the closing price ends up being. **LOC** (Limit-on-Close) orders specify a worst price — they execute only if the cross price is within the limit. Pre-cross, the exchange publishes an "imbalance feed" showing buy-side or sell-side pressure, letting market makers and algos respond.

The closing auction is **the largest single liquidity event of the day** for US equities — typically 6-15% of daily volume. Index rebalances, ETF NAV cuts, and MOC-targeted strategies concentrate flow into this window.

## Example

S&P 500 reconstitution day. Index funds tracking the new composition must trade at the close to match index NAV. They submit MOC orders into NYSE / Nasdaq closing auctions for the added and removed names. The closing cross volume spikes to 5x normal. The closing print is the index reference.

## Why it matters in an EMS

- Closing-auction order types (`OrdType=K` MOC, `OrdType=B` LOC) are first-class FIX semantics the EMS must support.
- Many algos target the close — "VWAP to close," "IS to close," "closing auction."
- Best-ex audit of MOC strategies is distinct (vs continuous market benchmarks).

## Related

- [[nyse]] · [[nasdaq]] · [[lse]] · [[xetra]] · [[jpx-tse]] (each has closing-auction mechanics)
- [[vwap-twap-pov-is]] (close-targeted algos)
- [[arch-best-execution]] · [[arch-tca]]
