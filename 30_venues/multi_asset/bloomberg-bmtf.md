---
type: venue
venue_kind: mtf
asset_classes: ["fixed_income", "rates_credit_deriv", "equity", "fx"]
status: draft
tags: [venue/mtf]
---

# Bloomberg MTF (BMTF & BTFE)

Bloomberg's **EU regulated multilateral trading facilities** — BMTF (Bloomberg MTF) in the UK/EU and BTFE (Bloomberg Trading Facility BV) in the Netherlands post-Brexit. The execution leg for many EU-regulated trades that Bloomberg's terminal workflows surface.

## Asset classes

- Cash bonds (govts, IG, HY, EM)
- Repos
- Credit default swaps (CDS index + selected single-names)
- Interest rate swaps (cleared)
- ETFs and equity (selected)
- FX derivatives (NDFs and selected forwards)

## Workflow mechanisms

- **RFQ** — disclosed dealer set per MiFID II requirements.
- **CLOB** for liquid lines (selected govts).
- **List trading** (BWIC/OWIC execution leg).
- **Voice-to-electronic** processed-on-platform.

## Connectivity

- **FIX 4.4 / 5.0 SP2** for order entry, RFQ, executions, allocations, post-trade.
- BLPAPI for reference and pre-trade.
- Post-trade APA reporting integrated.

## Regulatory shape

- **MiFID II MTF** with the full obligations: order-record-keeping, RTS 22 transaction reporting, RTS 27/28 reports.
- Pre/post-trade transparency per RTS 1/2.
- Tick-size regime (RTS 11) enforced.
- See [[arch-jurisdictional-compliance]] for the EU stack.

## Key facts

- BMTF is the primary regulated EU execution destination for Bloomberg-originated EU FI flow.
- Post-Brexit split: BMTF (UK) + BTFE (Netherlands) — firms need both for EU and UK access.
- The screens (FIT, ALLQ) feed into BMTF for execution.

## Related

- [[bloomberg-sef]] (US CFTC sibling) · [[bloomberg-tradebook-us]] (US broker-dealer destination)
- [[govt-bonds]] · [[corp-bonds-ig]] · [[corp-bonds-hy]] · [[interest-rate-swaps]] · [[credit-default-swaps]]
- [[arch-jurisdictional-compliance]] · [[arch-regulatory-reporting-service]]
