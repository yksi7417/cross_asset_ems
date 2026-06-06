---
type: asset_class
asset_class: fixed_income
sub_class: municipal-bonds
trade_type: cash_security
liquidity: low
status: draft
tags: [asset/fixed_income/municipal-bonds]
---

# Municipal Bonds

US tax-exempt (and some taxable) debt issued by states, cities, counties, and authorities. **Highly fragmented universe** (~1M+ outstanding CUSIPs vs <50K corporates), with a dominant **retail / wealth-management buyer base** — distinctive market structure compared with the corporate complex.

## Venues

- **Retail / wealth ATSs**: [[ice-bondpoint]] (largest retail muni venue historically), [[municenter]] (Tradeweb Direct), Bonds.com.
- **Institutional**: [[marketaxess]] (growing institutional muni), [[tradeweb]].
- **BWIC workflow**: heavily used via Bloomberg / institutional venues — see [[bloomberg-bwic-owic]].
- **Primary**: bond-counsel-led negotiated and competitive new-issue offerings (per-issuer; not centralised).

## How to Access Market

Buy-side institutional EMS routes via FIX to [[marketaxess]] / [[tradeweb]]. Retail / wealth flow goes through [[ice-bondpoint]] / [[municenter]]. New-issue subscriptions go through the lead dealer's order book.

## RFQ vs CLOB

Almost entirely [[rfq]] with significant dealer-streamed firm pricing for retail-sized lots. CLOB does not work because each CUSIP is so rarely traded.

## Aggregations / Basket / Netting

[[bloomberg-bwic-owic|BWIC / OWIC]] is the dominant rebalancing mechanism. Per-account allocation per buy-side policy. Limited cross-bond [[netting]].

## Regulatory Reporting

US: [[msrb-rtrs]] within 15 minutes of execution. Disseminated via MSRB EMMA system. Block-size caps similar to TRACE.

## Clearing / Settlement

US: T+2 at [[dtc]] via DVP. Some new-issue settles longer; refunding bonds settle per their specified date.

## Documentation Required

Per-bond: OS (Official Statement — the muni equivalent of a prospectus), indenture, bond counsel opinions on tax treatment. New-issue: order-confirmation paperwork; allocation rules per syndicate agreement.

## Market Notes

- **Fungibility**: Fungible per CUSIP, but with ~1M+ outstanding CUSIPs most issues trade so rarely that they are effectively non-fungible in practice — each search is a discovery problem. See [[fungible-vs-non-fungible]].
- **~1M+ outstanding CUSIPs** — the universe is enormous and most CUSIPs are inactive most of the time.
- **Tax-exempt income** is the primary buyer motivation (US retail investors); this drives the unique buyer base.
- **General Obligation (GO)** vs **Revenue** vs **Refunding** are the major bond types — different risk profiles.
- **Build America Bonds (BAB)** were taxable issuance under a federal subsidy program (2009-2010) — small remaining outstanding.
- **Pre-refunded** (escrowed-to-maturity) munis trade like USTs — credit quality is the underlying UST.

## Typical Counterparties

Muni specialists: Citi (top US muni desk historically), JPM, BAML, MS, GS, Wells Fargo, Raymond James, Stifel, Piper Sandler (regional specialists).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[bloomberg-bwic-owic]] · [[allocation-prime-broker]].
