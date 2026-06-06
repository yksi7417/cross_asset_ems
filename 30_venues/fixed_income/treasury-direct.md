---
type: venue
venue_kind: exchange
asset_classes: ["fixed_income"]
status: draft
tags: [venue/exchange]
---

# TreasuryDirect (US Treasury Auction Portal)

US Treasury's **primary issuance auction portal**, run by the Bureau of the Fiscal Service. Direct access for individuals and institutional bidders to bid in scheduled auctions for T-bills, T-notes, T-bonds, FRNs, and TIPS.

## Asset classes

- US Treasury bills (4, 8, 13, 17, 26, 52 week)
- US Treasury notes (2, 3, 5, 7, 10 year)
- US Treasury bonds (20, 30 year)
- TIPS (5, 10, 30 year)
- FRNs (2 year)

## Workflow mechanisms

- **Competitive bid** — institutions submit yield bids; allotment by single-price Dutch auction.
- **Non-competitive bid** — retail accepts the average accepted yield.
- Settlement on the announced issue date.

## Connectivity

- **TAAPS** (Treasury Automated Auction Processing System) — the institutional auction interface used by primary dealers and large institutions.
- TreasuryDirect web for retail and small institutional accounts.
- **Primary dealers** participate via direct connection (not via the web portal).
- For EMS integration, the practical route is **through a primary dealer** as agent — most buy-side firms do not connect to TAAPS directly.

## Key facts

- Auction calendar is the **anchor of the UST market** — secondary flow on [[brokertec]] / [[tradeweb]] orbits it.
- WI (when-issued) trading on the secondary venues precedes settlement.

## Related

- [[govt-bonds]] · [[money-market-tbills]]
- [[brokertec]] · [[tradeweb]] (secondary)
- [[bloomberg-tbill]] (monitor concept) · [[bloomberg-btmm]] (monitor concept)
