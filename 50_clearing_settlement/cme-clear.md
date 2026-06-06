---
type: clearing_settlement
kind: ccp
status: draft
tags: [clearing/ccp]
---

# CME Clearing

**CME Clearing** (CME ClearPort) is the **CCP for CME futures and selected OTC derivatives**. CME is the largest US futures exchange operator; its clearing arm services CME Group products (CME, CBOT, NYMEX, COMEX) and via ClearPort an array of OTC commodity and energy swaps.

## Asset classes / instruments

- All **CME-traded [[commodity-futures|futures]]** — interest-rate (Eurodollar, SOFR, Fed funds futures), equity-index (S&P 500, NDX, Russell), FX, agriculture, energy, metals, livestock.
- CME [[interest-rate-swaps|IRS]] (alternative to LCH SwapClear for USD).
- CME selected OTC commodity / energy swaps via ClearPort.
- BrokerTec GCF Repo ([[gcf-repo]]) since 2021.

## Settlement / margining

- Real-time clearing — every trade clears as it matches.
- Daily MTM via SPAN margining (CME's proprietary methodology).
- Initial margin per portfolio risk.
- Default fund contributions per member.

## Membership / access

Clearing members are major US banks and FCMs ([[fcm]]). Buy-side access via FCM relationship.

## EMS touchpoints

- CME-traded futures clear automatically via the FCM.
- ClearPort OTC submissions for clearing via FIX / proprietary API.
- IRS clearing decision (CME vs LCH) is per-trade and per-counterparty.

## Related

- [[commodity-futures]] · [[interest-rate-swaps]] · [[gcf-repo]]
- [[novation]] · [[mat]] · [[fcm]] · [[ccp-vs-bilateral]]
- [[lch]] · [[ice-clear]] · [[ficc-clearing]] (sibling CCPs)
- [[brokertec]] (CME-owned execution venue)
