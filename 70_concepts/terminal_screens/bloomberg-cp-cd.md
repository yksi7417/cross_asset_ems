---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg CP<GO> / CD<GO>

**CP<GO>** and **CD<GO>** are Bloomberg Terminal monitor screens for **commercial paper** and **certificates of deposit** — short-end unsecured funding instruments. Each screen displays dealer-indicative rates by tenor, issuer / program, and currency.

> **These are Bloomberg Terminal monitor screens, not routable execution destinations.** A trader sees indicative CP/CD rates here; execution is bilateral dealer-direct, or via a small number of MarketAxess-style RFQ flows where available.

## What they show

- Dealer-streamed runs for CP programs (US, Euro CP, ABCP).
- CD issuance schedule and indicative rates.
- Roll calendar and maturity buckets.

## Where execution actually happens

- **CP**: dealer-direct bilateral by phone or via FIX-connected dealer-direct adapters; partial automation through MarketAxess money-market RFQ for some dealers.
- **CD**: largely bilateral; some primary issuance via dealer auction networks.

There is no dominant central CLOB or MTF for US CP; the market is dealer-intermediated.

## Related

- [[bloomberg-btmm]] (parent money-markets monitor)
- [[marketaxess]] (limited CP RFQ) · [[arch-venue-connectivity]] (dealer-direct adapters)
- [[money-market-cp-cd]] (asset class)
