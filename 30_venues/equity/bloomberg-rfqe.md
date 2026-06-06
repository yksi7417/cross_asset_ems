---
type: venue
venue_kind: rfq
asset_classes: ["equity"]
status: draft
tags: [venue/rfq]
---

# Bloomberg RFQe (ETF RFQ)

Bloomberg's **electronic RFQ workflow for ETFs**, connecting buy-side to **200+ ETF liquidity providers** (authorised participants and market makers). Designed for institutional-size ETF blocks where the lit-market spread is too wide.

## Asset classes

- ETFs (US, EU, UK, APAC listings)
- ETF block trading
- Some closed-end funds where ETF-style market making exists

## Workflow mechanisms

- **RFQ** disclosed to a chosen LP set.
- **NAV-based pricing** option for large blocks (per [[arch-rfq]] ETF specifics).
- **AP-workflow integration** for primary creation/redemption when block size warrants.
- **Multi-leg** (basket of ETFs) supported.

## Connectivity

- **FIX-based RFQ extension** (custom tags for ETF basket, NAV reference, AP capacity).
- **BLPAPI** for ETF reference (iNAV, basket composition).

## Key facts

- ETF RFQ has materially compressed institutional ETF block costs over the last decade.
- iNAV (intraday NAV) is the natural reference price, supplied by [[arch-realtime-analytics]].
- Best-ex for ETFs is distinct (RFQ best price ≠ exchange NBBO).

## Related

- [[arch-rfq]] (RFQ as first-class, ETF specifics)
- [[arch-realtime-analytics]] (iNAV) · [[arch-best-execution]] (ETF best-ex)
- [[cash-equity]] (ETFs are an equity sub-class) · [[bloomberg-emsx]] (parent EMS)
