---
type: asset_class
asset_class: fixed_income
sub_class: corp-bonds-ig
trade_type: cash_security
liquidity: high
status: draft
tags: [asset/fixed_income/corp-bonds-ig]
---

# Corporate Bonds — Investment Grade

USD / EUR / GBP and major-EM corporate bonds rated BBB- / Baa3 or higher. **Massively electronified** over the last decade — IG corp is one of the highest-success stories of fixed-income electronification, driven by all-to-all venues and portfolio trading.

## Venues

- **Primary**: [[marketaxess]] (dominant US IG; Open Trading all-to-all), [[tradeweb]] (dominant EU IG; growing US), [[trumid]] (HY-leaning but also IG), [[bloomberg-bridge]] (EM IG all-to-all).
- **Pre-trade**: [[neptune]] (axes/IOIs; vendor-neutral aggregation), [[bloomberg-ib]] (chat for high-touch).
- **Reference**: [[bloomberg-allq]] / [[bloomberg-fit]] / [[bloomberg-cbnd]] (price-discovery screens — not routable).

## How to Access Market

Buy-side EMS routes via FIX to [[marketaxess]] and [[tradeweb]] for [[rfq]]. For block sizes consider [[bloomberg-bridge]] or [[trumid]] all-to-all. [[composite-price]] (CBBT / BVAL / CP+) anchors pre-trade reference.

## RFQ vs CLOB

Almost entirely [[rfq]]-based. **Open Trading** ([[marketaxess]]) and **Bridge** ([[bloomberg-bridge]]) extend the model to [[all-to-all]] anonymous matching. Some venues run "Live Markets" CLOB-like books for the most liquid issues but RFQ dominates.

## Aggregations / Basket / Netting

[[portfolio-trading]] is enormous — many buy-side IG rebalances trade as 100-500-line baskets through a single dealer at risk-price. Allocation back to underlying accounts post-trade. [[netting]] applies to same-CUSIP offsetting flow.

## Regulatory Reporting

US: [[trace]] within 15 minutes (1 minute for some categories). EU: MiFID II post-trade transparency via APAs ([[rts-22-27-28]]). UK: UK MiFIR equivalent. Per-trade transaction reporting under RTS 22.

## Clearing / Settlement

US: T+2 at [[dtc]] via DVP; transitioning toward T+1 alignment with equity. EU: T+2 at [[euroclear]] / [[clearstream]]. Post-trade matching via [[markitserv]] / DTCC CTM / Bloomberg VCON — see [[allocation-affirmation-confirmation]].

## Documentation Required

Per-bond: prospectus and indenture (proprietary per issuer). Repo financing: [[gmra]]. Any related derivatives: [[isda]] / [[csa]].

## Market Notes

- **Fungibility**: Fungible per CUSIP. Each bond issue is its own CUSIP (with the same coupon / maturity / covenants); units within a CUSIP are interchangeable. See [[fungible-vs-non-fungible]].
- **All-to-all** all-but-killed the dealer-monopoly model in US IG over 2015-2024.
- **CBBT / BVAL** composite pricing is the price-discovery reference even for trades not routed through MarketAxess.
- **Portfolio trading** (blocks of 100-500 names) is now ~15% of US IG e-volume.
- **TRACE** dissemination shapes algo behaviour — large prints trigger pricing adjustments across the market.

## Typical Counterparties

Major dealers: GS, MS, JPM, Citi, BAML, Barclays, Deutsche, BNP, UBS, Credit Suisse (now UBS), HSBC, Wells Fargo, Mizuho, MUFG, Nomura.

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[bloomberg-bwic-owic]] (BWICs for rebalancing) · [[portfolio-trading]].
