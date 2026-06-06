---
type: concept
status: draft
tags: [concept/terminal_screen, concept/price_discovery]
---

# Bloomberg REPO<GO>

**REPO<GO>** is a Bloomberg Terminal monitor screen for the **repo market** — dealer-indicative rates by collateral type (GC, specials), tenor (overnight, term), and currency.

> **This is a Bloomberg Terminal monitor screen, not a routable execution destination.** The repo market is venue-fragmented; execution routes to real platforms or bilaterally.

## What it shows

- GC rates (UST GC, Agency GC, IG corp GC) by tenor.
- Special-collateral indications (specific CUSIPs trading rich).
- Tri-party benchmarks vs. bilateral.
- Cross-currency repo (EUR, GBP, JPY).

## Where execution actually happens

| Repo type | Routable destination |
|---|---|
| Tri-party (UST / Agency) | [[triparty-bnym-jpm]] (BNY Mellon / JPM as tri-party agents) |
| GCF Repo (USD, FICC-cleared) | [[brokertec]] GCF (FICC-cleared GC), Dealerweb GCF |
| Bilateral term repo | Dealer-direct via [[arch-venue-connectivity]] |
| EUR Repo | MTS Repo, [[brokertec]] EU Repo, Eurex Repo |
| Securities Lending (related) | [[arch-borrow-service]] (locate / borrow execution) |

## Related

- [[bloomberg-btmm]] (parent money-markets monitor)
- [[triparty-bnym-jpm]] · [[brokertec]] (real destinations)
- [[money-market-repo]] (asset class) · [[arch-borrow-service]] (sec-lending adjacency)
