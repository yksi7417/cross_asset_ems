---
type: asset_class
asset_class: fx
sub_class: fx-spot
trade_type: cash_security
liquidity: very_high
status: draft
tags: [asset/fx/fx-spot]
---

# FX Spot

Cash-settled exchange of two currencies for delivery on the **spot date** — typically T+2 (some pairs T+1). The deepest, most-traded financial market by volume.

## Venues

- **Interdealer CLOB**: [[ebs]] (CME), Refinitiv Matching.
- **Anonymous ECNs**: [[hotspot-fx]] (Cboe), [[fxspotstream]] (bank-consortium, disclosed-stream).
- **Multi-dealer RFQ**: [[refinitiv-fxall]], [[360t]], [[currenex]], [[fx-connect]].
- **Reference**: WMR 4pm fix (institutional benchmark — see [[fx-fixing]]).

## How to Access Market

Buy-side EMS routes via FIX to ECNs or multi-dealer platforms. SOR layer to choose ECN vs RFQ vs streaming RFS — see [[arch-smart-order-router]] and [[clob-vs-rfq]].

## RFQ vs CLOB

Both work in FX spot. **CLOB** ([[ebs]], [[hotspot-fx]]) for tight-spread liquid pairs. **RFQ** ([[refinitiv-fxall]], [[360t]], [[currenex]]) for less liquid pairs or larger sizes. **RFS** for short-lived dealer streams. **Last-look** ([[last-look]]) is a venue-level discipline that affects effective execution.

## Aggregations / Basket / Netting

[[netting|FX netting]] — see [[arch-fx-netting]]. Multi-currency portfolios produce paired net positions to limit gross volume. T+1 US equity settlement created funding-driven FX-spot flows.

## Regulatory Reporting

US: limited spot reporting (CFTC focuses on derivatives, not spot). EU/UK: MiFID II classifies most FX spot as out-of-scope (not "financial instrument") but some FX-options-linked spot is in-scope. See [[arch-jurisdictional-compliance]] for the FX-specific rules.

## Clearing / Settlement

T+2 for most pairs (USD/CAD, USD/MXN are T+1). Settlement via CLS (Continuous Linked Settlement) for the 18 major currencies — PvP (Payment-vs-Payment) eliminating Herstatt risk. Non-CLS via correspondent banking.

## Documentation Required

ISDA Master + the FX Master Definitions (ISDA / FX 2010). Standard credit line + collateral via CSA where applicable.

## Market Notes

- **Fungibility**: Fully fungible per currency pair + value date. Every EUR/USD spot trade with the same value date is interchangeable. See [[fungible-vs-non-fungible]].
- **800+ LPs** globally compete for FX spot flow.
- **CLS settlement** is the dominant operational mechanism for the major-currency pairs — PvP eliminates settlement risk.
- **WMR 4pm fix** is the dominant institutional benchmark — flow concentration around 4pm London is structural.
- **Last-look** ([[last-look]]) reshapes effective venue selection; some ECNs are no-last-look (EBS MQL).
- **NDF** ([[fx-ndf]]) is the EM counterpart for non-deliverable currencies.

## Typical Counterparties

Top FX LPs: JPM, Citi, GS, MS, Deutsche, BNP, UBS, BAML, HSBC, Standard Chartered, Mizuho, MUFG, plus non-bank market makers (XTX, Citadel Securities, Jump).

## Related Workflows

[[staging-via-fix]] · [[route-to-rfq]] · [[multi-route-rfq]] · [[fx-automation-tradebest]] · [[fx-automation-rbld]] · [[auto-route-fixing-aim]] · [[fxel]] (corp treasury).
