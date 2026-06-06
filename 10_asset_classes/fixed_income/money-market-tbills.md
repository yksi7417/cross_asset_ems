---
type: asset_class
asset_class: fixed_income
sub_class: money-market-tbills
trade_type: cash_security
liquidity: very_high
status: draft
tags: [asset/fixed_income/money-market-tbills]
---

# Money Market — Treasury Bills

US Treasury bills — short-term US government zero-coupon debt issued in 4-, 8-, 13-, 17-, 26-, and 52-week tenors. Auctioned weekly (most maturities) by the US Treasury via [[treasury-direct]]. The deepest money-market cash instrument.

## Venues

- **Primary**: [[treasury-direct]] (auction portal; primary dealers via TAAPS).
- **Secondary**: [[brokertec]] (interdealer CLOB), [[tradeweb]] (D2C RFQ + CLOB), [[marketaxess]] (US Live Markets).
- **Reference**: [[bloomberg-tbill]] / [[bloomberg-btmm]] (monitor screens).

## How to Access Market

Primary: most buy-side firms access via a primary dealer or via TreasuryDirect for retail-sized. Secondary: FIX to [[brokertec]] or [[tradeweb]] for institutional execution.

## RFQ vs CLOB

[[clob-vs-rfq|CLOB]] for on-the-run bills (most volume); RFQ for off-the-run. On-the-runs trade two-way continuously.

## Aggregations / Basket / Netting

[[netting]] same-CUSIP. T-bill positions often paired with repo financing — see [[money-market-repo]].

## Regulatory Reporting

US: [[trace]] UST coverage (regulator-only since 2017, not public). Reported transactions go to the Federal Reserve via TRACE infrastructure.

## Clearing / Settlement

T+1 at Fedwire Securities; FICC clearing for interdealer flow — see [[ficc-clearing]] and [[tplus-1-tplus-2]].

## Documentation Required

Standard MSA for direct dealer relationships. Repo financing: [[gmra]] (US) / similar.

## Market Notes

- **Fungibility**: Fungible per CUSIP — and on-the-run T-bills are particularly fungible because traders substitute across the on-the-run set freely. See [[fungible-vs-non-fungible]].
- **Discount basis** — T-bills are quoted as a yield discount, not a price (e.g. "4.85" means 4.85% discount yield).
- **Auction calendar** — 4-week / 8-week / 13-week / 26-week typically Mondays; 17-week Tuesdays; 52-week monthly.
- **WI trading** ([[when-issued]]) is heavy in the days before auction.
- **Fed RRP** (reverse repo) rate is the implicit floor — when bills trade below RRP rates, the Fed accepts the spread via RRP.
- **Sweep** by money-market funds creates predictable end-of-month / quarter / year T-bill demand.

## Typical Counterparties

All US primary dealers + major money-market funds (Vanguard, Fidelity, Federated, BlackRock MM, Schwab, JPM Asset Management).

## Related Workflows

[[staging-via-fix]] · [[route-single]] · [[route-to-rfq]] (off-the-run) · [[allocation-prime-broker]].
