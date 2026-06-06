---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg ALLQ<GO>

**ALLQ** ("All Quotes") is a Bloomberg Terminal screen that displays the **composite of dealer quotes** for a single fixed-income security at a single point in time — typically a bond, but the screen also serves CDS, swaps, and money-market instruments.

> **This is a Bloomberg Terminal monitor screen, not a routable execution destination.** Orders are not "sent to ALLQ." The screen aggregates indicative quotes from multiple dealer feeds; execution against the underlying liquidity is done at a real venue or with a named dealer — see "Where execution actually happens" below.

## What it shows

A grid: rows are dealers; columns are bid / ask / size / time. The trader sees the live, composite picture of who is showing what.

## Where the data comes from

- Dealer-streamed runs (price publications from sell-side firms).
- Last-traded prints from MarketAxess / Tradeweb / direct dealer feeds.
- Bloomberg's own composite (BVAL, CBBT) for an indicative mid-line.

These feeds are accessible to client systems via **B-PIPE** (Bloomberg B-PIPE market data subscription) and **BLPAPI** (Bloomberg API). A buy-side EMS can pull the same composite data programmatically and render its own ALLQ-equivalent grid.

## Where execution actually happens

When a trader elects a quote from the ALLQ screen, the execution path depends on the elected dealer's connectivity:

- Dealer reachable via [[marketaxess]] RFQ → order routed there.
- Dealer reachable via [[tradeweb]] RFQ → order routed there.
- Dealer direct via FIX (bilateral) → routed via [[arch-venue-connectivity|the venue adapter]] to that dealer.
- EM bond all-to-all liquidity → [[bloomberg-bridge]].
- Bloomberg MTF jurisdiction → [[bloomberg-bmtf]].

The ALLQ screen itself does not carry the order; it's a price-discovery surface.

## Related

- [[bloomberg-btmm]] · [[bloomberg-fit]] (other terminal monitor screens)
- [[marketaxess]] · [[tradeweb]] · [[bloomberg-bmtf]] · [[bloomberg-bridge]] (real execution destinations)
- [[arch-quote-server]] (in-house equivalent of the composite stream)
- [[arch-pricing-service]] (CBBT / BVAL pricing)
- [[arch-symbology-figi]] (instrument identification on the composite)
