---
type: index
status: draft
tags: [index/glossary]
---

# Glossary Index

Industry-jargon definitions for finance-literate readers who haven't worked in a specific corner of EMS land. Each entry is **hover-friendly** — the first paragraph answers "what does this mean," the rest is context.

For Bloomberg-Terminal monitor screens (ALLQ, BTMM, FIT, CDSW, SWPM, CBND, CP/CD, TBILL, REPO) see [[terminal_screens]]. Those are a separate concept group: they're price-discovery surfaces, not venues — see [[_venue-index]].

## Fixed Income

- [[on-the-run-vs-off-the-run]] — most-recent UST vs. older issues; liquidity premium
- [[when-issued]] — pre-settlement trading on announced auctions
- [[bwic-owic]] — Bid-Wanted-In-Competition / Offer-Wanted; list trading
- [[tba-vs-specified-pool]] — TBA generic delivery vs. named MBS pool with pay-up
- [[dollar-roll]] — paired TBA near-month / next-month financing
- [[wac-wam-wala]] — MBS pool characteristics driving pay-up vs TBA
- [[axe]] — directional dealer signal of intent
- [[composite-price]] — CBBT / BVAL / CP+ synthetic reference prices
- [[clean-vs-dirty-price]] — quoted price vs. price + accrued
- [[accrued-interest]] — between-coupon interest accumulation
- [[dv01-and-duration]] — rate sensitivities
- [[two-way-vs-one-way]] — continuous BBO vs. one-sided indication
- [[portfolio-trading]] — single-ticket basket execution

## FX

- [[spot-date-value-date]] — settlement date conventions
- [[forward-points-swap-points]] — fwd-spot differential = rate-differential
- [[fx-fixing]] — daily reference rates (WMR 4pm etc.)
- [[last-look]] — LP's brief accept/reject window on aggressive fills

## Equity

- [[nbbo-ebbo]] — National / European Best Bid and Offer
- [[reg-nms]] — US trade-through protection regulation
- [[lit-vs-dark]] — pre-trade visible vs. hidden markets
- [[ats-ecn-mtf]] — regulated non-exchange venues per region
- [[systematic-internaliser]] — MiFID II principal-internalization venue
- [[midpoint-cross]] — dark-pool matching at NBBO midpoint
- [[closing-auction]] — MOC / LOC end-of-session price discovery
- [[capital-commitment]] — broker takes the other side of a client trade
- [[central-risk-book]] — broker's aggregated proprietary risk account
- [[authorized-participant]] — ETF create/redeem broker
- [[inav]] — intraday ETF NAV reference price
- [[vwap-twap-pov-is]] — execution benchmarks and namesake algos

## Derivatives

- [[ccp-vs-bilateral]] — central clearing vs. bilateral credit exposure
- [[novation]] — CCP becomes legal counterparty post-trade
- [[fcm]] — Futures Commission Merchant; client's gateway to a CCP
- [[mat]] — Made-Available-to-Trade: SEF mandate trigger
- [[rfq-to-3]] — CFTC 3-dealer minimum for MAT D2C
- [[give-up]] — executing broker hands trade to clearing broker
- [[usi-uti]] — globally unique swap trade identifiers

## Execution Lifecycle

- [[fungible-vs-non-fungible]] — structural driver of market mechanics (CLOB requires fungibility)
- [[fix-protocol-basics]] — FIX message types and key tags
- [[clordid-origclordid]] — FIX order identifiers and their lifetimes
- [[ordstatus-exectype]] — FIX order state vs. event-of-this-report
- [[pending-replace-pending-cancel]] — in-flight cancel/replace states
- [[clob-vs-rfq]] — continuous order book vs. discrete quote request
- [[rfq]] — Request for Quote (one of three quote-driven modes)
- [[rfq-rfs-rfm]] — RFQ vs. Request-for-Stream vs. Request-for-Market
- [[ioi-vs-rfq]] — dealer-published interest vs. client-initiated quote request
- [[all-to-all]] — buy-side trades anonymously with other buy-side
- [[agency-vs-principal]] — broker as router vs. broker as counterparty
- [[netting]] — combine opposing trades into single net obligations

## Regulatory

- [[trace]] — FINRA's US bond post-trade reporting facility
- [[msrb-rtrs]] — MSRB's US muni post-trade reporting facility
- [[cftc-sdr]] — CFTC swap data repository regime
- [[finra-cat]] — Consolidated Audit Trail for US equity/options
- [[rts-22-27-28]] — MiFID II reporting and best-ex standards
- [[mar-stor]] — Market Abuse Regulation + Suspicious Transaction & Order Reports
- [[emir-sftr-csdr]] — EU post-trade trio: derivatives / SFT / settlement discipline
- [[lei]] — Legal Entity Identifier (ISO 17442)

## Settlement / Clearing

- [[tplus-1-tplus-2]] — settlement cycle conventions per asset class
- [[dvp-rvp-fop]] — Delivery-vs-Payment / Receipt-vs-Payment / Free-of-Payment
- [[allocation-affirmation-confirmation]] — three sequential post-trade steps
- [[tri-party-vs-bilateral-repo]] — tri-party agent vs. direct repo operational models
- [[gcf-repo]] — FICC-cleared interdealer GC repo

## Bloomberg Terminal Monitor Screens (separate group)

See [[terminal_screens]] — these are price-discovery surfaces, not routable venues:

- [[bloomberg-allq]] — composite quotes (ALLQ<GO>)
- [[bloomberg-btmm]] — Treasury & Money Markets monitor
- [[bloomberg-fit]] — Fixed-income trading search
- [[bloomberg-cdsw]] — CDS valuation calculator
- [[bloomberg-swpm]] — Swap manager / pricing
- [[bloomberg-cbnd]] — Corporate bond search
- [[bloomberg-cp-cd]] — CP / CD monitor
- [[bloomberg-tbill]] — T-bill monitor
- [[bloomberg-repo]] — Repo monitor

## See also

- [[_venue-index]] — venue categorization (FI / FX / Equity / Brokers / Multi-asset)
- [[asset-class-matrix]] — cross-asset matrix using glossary terms
- [[architecture-index]] — the architectural component index
- [[workflow-index]] — workflow index by category
