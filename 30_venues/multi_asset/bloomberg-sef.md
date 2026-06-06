---
type: venue
venue_kind: sef
asset_classes: ["rates_credit_deriv", "fx"]
status: draft
tags: [venue/sef]
---

# Bloomberg SEF (BSEF)

**Bloomberg's CFTC-registered Swap Execution Facility**, covering **interest rate swaps, credit default swap indices, and FX NDFs**. The execution venue for many Bloomberg-originated US swap workflows once the trade is MAT (Made-Available-to-Trade) or voluntarily traded on-SEF.

## Asset classes

- USD / EUR / GBP / JPY interest rate swaps (vanilla, OIS, basis)
- Credit default swap indices (CDX IG, CDX HY, iTraxx Main, iTraxx Crossover)
- Selected single-name CDS
- FX NDFs (major EM)

## Workflow mechanisms

- **RFQ-to-3** for MAT swaps in D2C mode (CFTC requirement).
- **CLOB** order book for liquid IRS tenors.
- **Voice-to-electronic** for less-liquid lines processed through the SEF.

## Connectivity

- **FIX 4.4 / 5.0** with SEF-specific dictionary extensions (clearing broker, FCM, MAT flag, USI/UTI handling).
- BLPAPI for reference and pre-trade.

## Regulatory shape

- **CFTC SEF** rules apply — RFQ-to-3, audit, SDR reporting, mandatory clearing for MAT.
- Trade reported in real-time to a public SDR feed and to the regulator.
- See [[arch-jurisdictional-compliance]] for the full CFTC stack.

## Related

- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]]
- [[sef-platforms]] (overview of all SEFs) · [[bloomberg-bmtf]] (EU regulated sibling)
- [[cftc-sdr]] (real-time reporting destination) · [[arch-regulatory-reporting-service]]
- [[arch-rfq]] (RFQ-to-3 specialization)
