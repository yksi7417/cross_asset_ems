---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg CBND<GO>

**CBND** ("Corporate Bond Database") is a Bloomberg Terminal **search and reference screen** for the global corporate bond universe — issuer / sector / rating / structure / call schedule / covenants. Surfaces indicative dealer pricing per security.

> **This is a Bloomberg Terminal search screen, not a routable execution destination.** A trader uses CBND to find and analyze a bond; execution routes to a real venue.

## What it does

- Issuer-level search and screening (sector, rating, currency, tenor, structure).
- Indicative pricing surfaces (links to [[bloomberg-allq]] for the composite).
- Covenants, call schedules, ratings history, indenture references.

## Where execution actually happens

| Sub-asset | Routable destination |
|---|---|
| USD IG corp | [[marketaxess]] (dominant), [[tradeweb]] |
| USD HY corp | [[marketaxess]] (incl. Open Trading all-to-all), [[tradeweb]], dealer-direct |
| EUR / GBP corp | [[tradeweb]] (dominant in EU), [[marketaxess]] |
| EM corp | [[marketaxess]], [[bloomberg-bridge]], dealer-direct |
| Convertibles | Dealer-direct via [[arch-venue-connectivity|venue adapter]]; some MarketAxess |

## Related

- [[bloomberg-allq]] · [[bloomberg-fit]] (terminal companions)
- [[marketaxess]] · [[tradeweb]] · [[bloomberg-bridge]] (real destinations)
- [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[convertibles]] (asset classes)
