---
type: asset_class
asset_class: fixed_income
sub_class: corp-bonds-hy
trade_type: cash_security
liquidity: moderate
status: draft
tags: [asset/fixed_income/corp-bonds-hy]
---

# Corporate Bonds — High Yield

USD / EUR / GBP corporate bonds rated BB+ / Ba1 and lower (including non-rated). Less liquid than [[corp-bonds-ig|IG]], more idiosyncratic, more bilateral / voice-driven historically. **Significant electronification progress** post-2020 driven by all-to-all venues.

## Venues

- **Primary**: [[marketaxess]] (US HY incl. Open Trading), [[trumid]] (anonymous all-to-all + Swarms), [[tradeweb]] (growing), [[bloomberg-bridge]] (EM HY).
- **Pre-trade**: [[neptune]] (axes), [[bloomberg-ib]] (chat — heavily used for HY).
- **Reference**: [[bloomberg-allq]] / [[bloomberg-cbnd]] (price discovery).

## How to Access Market

Buy-side EMS routes via FIX to [[marketaxess]] / [[trumid]] / [[tradeweb]] for [[rfq]] and [[all-to-all]]. Block flow often originates from a [[bloomberg-ib|chat]] conversation that ends as a programmatic [[rfq]]. [[bloomberg-bwic-owic|BWICs]] are heavily used for portfolio rebalancing.

## RFQ vs CLOB

[[rfq]] dominates; CLOB does not work for most HY instruments because the bid-offer is too wide and trading is too sparse. [[all-to-all]] anonymous (Open Trading, Trumid) addresses the spread problem. [[bloomberg-ib|Chat]] remains material for the most idiosyncratic flow.

## Aggregations / Basket / Netting

[[portfolio-trading]] now significant in HY — Trumid PT and MarketAxess PT both grow. [[bloomberg-bwic-owic|BWICs]] are the dominant rebalancing mechanism. [[netting]] applies but offsets are rarer than in IG.

## Regulatory Reporting

US: [[trace]] — with **dissemination caps for HY block trades** ($5M reported, actual size with delay). The cap rules are designed to preserve dealer ability to work block positions out without flagging to the market. EU/UK MiFIR.

## Clearing / Settlement

US: T+2 at [[dtc]] via DVP. EU: T+2 at [[euroclear]] / [[clearstream]]. Distressed names may settle longer — manual settlement instructions common for stressed counterparties.

## Documentation Required

Per-bond: prospectus and indenture. Distressed names: covenants, intercreditor agreements, restructuring documentation become first-class concerns. Repo financing: [[gmra]].

## Market Notes

- **Fungibility**: Fungible per CUSIP, but per-CUSIP volume is small — practically each CUSIP trades as its own micro-market with thin two-way liquidity. See [[fungible-vs-non-fungible]].
- **TRACE dissemination caps** matter — algos must understand reported size ≠ actual size.
- **Liquidity premium** is large: HY bid-offer often 50-200 bp vs IG 5-15 bp.
- **Idiosyncratic risk** (each name needs research) means single-name HY is rarely truly algo-driven.
- **Distressed names** trade like private credit — dealer-specialist concentrated.
- **Recovery rate assumptions** drive valuation of distressed HY tied to CDS.

## Typical Counterparties

Specialised HY desks: GS, MS, JPM, Citi, BAML, Barclays, Jefferies, Citadel Securities, Millennium (HY-trading hedge funds also active as all-to-all responders).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[bloomberg-bwic-owic]] · [[portfolio-trading]] · [[allocation-prime-broker]].
