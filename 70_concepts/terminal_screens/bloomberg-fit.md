---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg FIT<GO>

**FIT** ("Fixed Income Trading") is a Bloomberg Terminal search and discovery screen for fixed-income securities — bonds, CDS, IRS, repos, MBS pools. It provides search by issuer / sector / maturity / rating and surfaces indicative dealer pricing.

> **This is a Bloomberg Terminal search and pricing screen, not a routable execution destination.** A trader searches in FIT, picks the security and dealer, then **execution routes through a real venue**, not through FIT itself.

## What it does

- Universe search across the FI complex (issuer, sector, maturity, rating, currency).
- Surfaces dealer composite (CBBT) and last-trade prints.
- Links into [[bloomberg-allq]] for the per-security composite view.

## Where execution actually happens

When a security is selected in FIT and the trader elects to trade, the routed destination depends on the security and the elected counterparty — same destinations as listed in [[bloomberg-allq]] and [[bloomberg-btmm]]:

- IG / HY corp via [[marketaxess]] or [[tradeweb]] RFQ.
- Govts via [[brokertec]] (interdealer) or [[tradeweb]] D2C.
- EM bonds via [[bloomberg-bridge]] or dealer-direct.
- Munis via [[municenter]] or dealer RFQ.

## Related

- [[bloomberg-allq]] · [[bloomberg-btmm]] (other terminal monitor screens)
- [[marketaxess]] · [[tradeweb]] · [[brokertec]] · [[municenter]] · [[bloomberg-bridge]] (real destinations)
- [[govt-bonds]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[municipal-bonds]] (asset classes)
