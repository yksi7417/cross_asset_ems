---
type: venue
venue_kind: sef
asset_classes: ["rates_credit_deriv", "fx"]
status: draft
tags: [venue/sef]
---

# Swap Execution Facilities (SEFs)

**SEFs** are CFTC-registered execution venues required (under Dodd-Frank) for trading **mandated** swaps. The EMS must support SEF execution as a distinct workflow because of regulatory requirements (MAT/RFQ-3 rule, audit, reporting, clearing).

## Major SEFs

| SEF | Coverage | Notes |
|---|---|---|
| [[bloomberg-sef]] | IRS, OIS, CDS index, FX NDF | Multi-asset; "BSEF" |
| [[tradeweb]] SEF | IRS, OIS, CDS index, MBS | "TW SEF LLC" |
| ICE Swap Trade | CDS index, IRS | Credit-leaning |
| TP-ICAP (multiple) | IRS, basis, inflation | Interdealer-leaning |
| BGC Derivatives Markets | IRS, FRA | Interdealer-leaning |
| GFI Swaps Exchange | CDS, IRS | Interdealer-leaning |
| 360T SEF | FX NDF | FX-focused |

## Workflow mechanisms

- **RFQ-to-3** — required by CFTC for MAT (Made-Available-to-Trade) swaps in D2C mode.
- **CLOB** — order-driven matching (for liquid IRS like USD 2y/5y/10y/30y).
- **Streaming permissioned prices** with click-to-trade.
- **Voice-to-electronic** workflows for less-liquid instruments.

## Common connectivity model

- **FIX 4.4 / 5.0** for order entry, RFQ, executions, and post-trade.
- Each SEF has its own FIX dictionary extensions (custom tags for clearing broker, give-up arrangements, FCM, MAT flag).
- **Allocation** via FIX `J` AllocationInstruction or via SEF-specific post-trade APIs.

## Regulatory shape

- **Mandated trades** must execute on a SEF; bilateral execution is forbidden.
- **Clearing** through CME, LCH, ICE Clear Credit, etc., is mandatory for MAT swaps.
- **SDR reporting** is the SEF's responsibility (real-time public + regulator).
- See [[arch-jurisdictional-compliance]] for the full CFTC/Dodd-Frank set.

## Related

- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]]
- [[bloomberg-sef]] · [[tradeweb]] (specific SEFs with their own notes)
- [[arch-rfq]] (RFQ-to-3 is a specialized RFQ workflow)
- [[cftc-sdr]] (post-trade reporting destination)
- [[arch-regulatory-reporting-service]] · [[arch-jurisdictional-compliance]]
