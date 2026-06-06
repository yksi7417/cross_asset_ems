---
type: asset_class
asset_class: cfd
sub_class: cfd-overview
trade_type: synthetic_derivative
liquidity: high
status: draft
tags: [asset/cfd]
---

# CFD — Contract For Difference

CFD is a **synthetic OTC contract** between client and broker tracking the price change of an underlying asset. No ownership of the underlying; cash-settled to the broker. Spans many underlyings: equities, equity indices, FX, commodities, crypto, bonds — any liquid reference.

**Regulatory note**: CFDs are **banned for US retail** (CFTC + SEC stance) and restricted for retail in many jurisdictions (ESMA leverage limits since 2018, FCA's COBS 22 restrictions, ASIC RG 227). The institutional CFD market is much smaller than the retail market — most EMS-level CFD interaction is on the **prime brokerage / dealer / professional-client** side.

## Variants

| Variant | Notes |
|---|---|
| **Equity CFD** | Synthetic equity exposure; popular for cross-border with dividends and corp actions adjusted via cash flows. |
| **Index CFD** | Synthetic exposure to indices without futures roll. |
| **FX CFD** | Effectively a leveraged FX position with broker as counterparty; functionally similar to [[fx-spot]] for the client. |
| **Commodity CFD** | Synthetic commodity exposure. |
| **Crypto CFD** | Synthetic [[crypto-spot]] exposure via CFD provider. |
| **Bond CFD** | Less common; large-tick CFDs on benchmark sovereigns. |
| **DMA CFD** | Direct-market-access CFDs where the broker hedges 1:1 on the underlying venue. |

## Venues / Brokers

- **Retail / pro CFD brokers**: IG Group, CMC Markets, Saxo Bank, Plus500, eToro, Pepperstone, IC Markets, OANDA.
- **Institutional CFD**: Goldman, Morgan Stanley, JPM, UBS, Macquarie (prime brokerage divisions).
- **DMA providers**: Saxo, IG (DMA service tier).

Not typically venues in the exchange sense; the **broker IS the counterparty**.

## How to Access Market

- FIX or proprietary API (institutional).
- REST + WebSocket (retail).
- See [[bloomberg-fit|FIT-equivalent]] integration for some institutional providers.

## RFQ & Quote Discovery

- Broker streams quotes (often with markup vs. underlying — see [[markup]]).
- DMA CFDs reflect underlying CLOB directly.
- Block trades sized over voice / chat.

## Execution / Allocation

- Single-broker counterparty; no SOR across CFD brokers usually (each broker is a closed system).
- Some prime-broker setups route the CFD position to underlying market for hedge.
- Allocation per [[allocation-prime-broker]] template at the broker.

## Basket Trading

- CFDs on baskets / indices common; bespoke baskets via institutional CFD desks.

## Netting

- Bilateral between client and broker; cross-instrument netting per broker collateral terms.
- No CCP — pure bilateral.

## Regulatory Reporting

- **EU MiFID II / MIFIR**: CFDs are MiFID instruments; transaction reporting (RTS 22) applies; post-trade transparency via APA; product-intervention rules limit leverage for retail.
- **UK FCA**: similar regime; retail CFD restrictions in place since 2019.
- **AU ASIC**: RG 227 + leverage limits.
- **JP**: similar leverage limits.
- **US**: prohibited for retail; institutional swaps reporting if structured similarly.
- See [[arch-regulatory-reporting-service]] + [[arch-jurisdictional-compliance]].

## Clearing / Settlement

- Bilateral with broker; no central clearing.
- Mark-to-market daily (sometimes intraday) with margin calls.

## Documentation Required

- Client agreement with broker (terms, leverage, margin).
- Risk disclosure documents (mandated in EU / UK / AU).
- For DMA CFD on equity: prime brokerage agreement governs.

## Market Notes

- **Leverage** up to 30:1 for major FX (ESMA limit retail), up to 500:1 in unregulated jurisdictions.
- **Counterparty risk**: client is exposed to broker insolvency (e.g. Plus500 — public, well-capitalized; smaller brokers higher risk).
- **Funding**: overnight financing charged on the notional position; can be significant.
- **Dividend adjustments**: long equity-CFD holders typically receive ~90% of underlying dividend; shorts pay 100%.
- **Stop-out / margin close** is automatic per broker terms.
- **Negative balance protection** mandated in EU retail.

## Typical Counterparties

- Retail (in regulated jurisdictions, with leverage caps).
- Professional clients (per MiFID II Annex II classification).
- Institutional via prime brokerage (synthetic prime).
- HNW / family office via broker-prime arrangements.

## Related Workflows

- [[staging-via-fix]] (institutional) · [[allocation-prime-broker]] · [[markup]] (broker markup on CFD quotes)
- [[arch-risk-engine]] (continuous mark-to-market) · [[arch-jurisdictional-compliance]] (retail restrictions)
- [[arch-pricing-service]] (mark vs underlying) · [[arch-position-service]] (continuous P&L)
- Underlying-related: [[cash-equity]], [[fx-spot]], [[crypto-spot]], [[equity-futures]]
