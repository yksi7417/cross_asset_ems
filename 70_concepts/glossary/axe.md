---
type: concept
status: draft
tags: [concept/glossary, glossary/fi]
---

# Axe (Dealer Axe)

A **dealer axe** is a **signal of dealer intent** — a specific direction (buy or sell), a specific instrument, and a specific size that a dealer wants to transact for risk-management or inventory reasons. Axes typically come at **better-than-screen prices** because the dealer benefits from the trade (offloads risk).

Axes are published by dealers to chosen audiences via **IOI networks** (Bloomberg IOI, Autex, Liquidnet, dealer-direct chat) and via specialised pre-trade data services like [[neptune]]. The buy-side EMS surfaces axes alongside CBBT / composite prices so the trader can decide whether to hit an axe (likely tighter execution) or send a fresh RFQ (broader market check).

Distinct from a **two-sided market quote**: an axe is one-directional — "I want to sell 10M of CUSIP X at +5bp over CBBT" — not a bid AND offer.

## Example

A HY trader sees a Goldman axe to sell 5M of Bond X at 98.50. The CBBT composite is 98.35. The buy-side trader lifts the axe at 98.50 — a tighter execution than going to MarketAxess RFQ would likely deliver, because Goldman wanted the trade.

## Why it matters in an EMS

- Axe attribution is preserved in the audit chain — [[arch-best-execution]] needs to know "we elected this dealer because they were axed."
- Axes feed the [[arch-ioi]] component as one IOI type.
- [[neptune]] is the canonical aggregator of axe data; [[bloomberg-ib]] carries axes by chat.

## Related

- [[ioi-vs-rfq]] · [[arch-ioi]]
- [[neptune]] · [[bloomberg-ib]]
- [[corp-bonds-ig]] · [[corp-bonds-hy]] (where axes are most common)
- [[arch-best-execution]] (axe attribution)
