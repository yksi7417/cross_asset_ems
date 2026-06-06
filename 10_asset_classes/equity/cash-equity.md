---
type: asset_class
asset_class: equity
sub_class: cash-equity
trade_type: cash_security
liquidity: very_high
status: draft
tags: [asset/equity/cash-equity]
---

# Cash Equity

Common and preferred shares of publicly-listed companies, plus ETFs and ADRs. The deepest, most fragmented, most lit-transparent asset class — shaped by Reg NMS (US) and MiFID II (EU/UK).

## Venues

- **US exchanges**: [[nyse]], [[nasdaq]], [[cboe-bzx]], [[iex]], [[memx]].
- **EU/UK exchanges**: [[lse]], [[xetra]], [[euronext]], [[cboe-europe]].
- **APAC**: [[jpx-tse]], [[hkex]] (plus Stock Connect).
- **Brokers** (dark pools, internalisation, capital commitment): [[goldman-sachs]], [[morgan-stanley]], [[ubs]], [[jpmorgan]], [[citi]], [[bank-of-america]], [[barclays]], [[instinet]].
- **ETF RFQ**: [[bloomberg-rfqe]] (block ETF execution).
- **EMS gateway**: [[bloomberg-emsx]] (FIX gateway to 3,700+ brokers).

## How to Access Market

Buy-side EMS routes through brokers via FIX. Direct exchange routing for firms with member status; most route through a broker DMA. SOR layer handles Reg-NMS compliance — see [[reg-nms]] and [[arch-smart-order-router]].

## RFQ vs CLOB

[[clob-vs-rfq|CLOB]] dominates for everything. RFQ is used only for ETF blocks ([[bloomberg-rfqe]]) and very large block trades through broker capital-commitment desks.

## Aggregations / Basket / Netting

[[portfolio-trading|Program trading]] for basket execution. [[netting]] mostly mechanical (same ISIN buy/sell offsets). Cross-currency basket execution uses paired FX.

## Regulatory Reporting

US: [[finra-cat]] (full lifecycle reporting via brokers/exchanges), TAQ for time-and-sales. EU/UK: MiFID II [[rts-22-27-28|RTS 22]] transaction reports, post-trade transparency via APAs.

## Clearing / Settlement

US: **T+1** since 28 May 2024 — settles at [[dtc]] via DVP. EU/UK: T+2 at [[euroclear]] / [[clearstream]]. APAC: per-exchange CSDs (HKSCC, JASDEC, ASX Clear). See [[tplus-1-tplus-2]] and [[dvp-rvp-fop]].

## Documentation Required

Standard execution agreement with the broker. PB agreements for hedge funds. Short-sale: separate [[arch-borrow-service|borrow]] documentation. No instrument-level documentation beyond exchange listing.

## Market Notes

- **Fungibility**: Fully fungible per ISIN — every share of a class is identical. The basis for CLOB matching. See [[fungible-vs-non-fungible]].
- **Reg NMS** ([[reg-nms]]) and the **NBBO** ([[nbbo-ebbo]]) shape US routing.
- **Lit vs dark** ([[lit-vs-dark]]) — fragmented market; broker SORs allocate between many lit venues and ~30 dark pools.
- **Maker-taker** fee/rebate structures differ per venue, drive flow allocation.
- **Closing auction** ([[closing-auction]]) is the single largest liquidity event of the day.
- **T+1 settlement** in the US (since 2024) compressed post-trade timelines materially.

## Typical Counterparties

All major brokers ([[_brokers-overview]]) plus regional / specialist boutiques. ETF: Authorized Participants ([[authorized-participant]]) like GS, JPM, Citadel Securities.

## Related Workflows

[[staging-via-fix]] · [[route-single]] · [[route-to-algo]] · [[partial-routes]] · [[bloomberg-rfqe]] (ETF blocks) · [[portfolio-trading]] (program trading).
