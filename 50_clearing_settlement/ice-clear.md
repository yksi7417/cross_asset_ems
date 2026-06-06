---
type: clearing_settlement
kind: ccp
status: draft
tags: [clearing/ccp]
---

# ICE Clear

**ICE Clear** is the family of **CCPs operated by Intercontinental Exchange (ICE)** — multiple legal entities serving different products and regions. ICE is the second-largest US futures exchange operator (after CME) and a major credit-derivatives clearer.

## ICE Clear entities

- **ICE Clear Credit** — US CDS clearing (dominant US CDS CCP).
- **ICE Clear Europe** — European CDS, EU futures, FX clearing.
- **ICE Clear US** — US futures (energy, agriculture).
- **ICE Clear Singapore** — APAC futures.
- **ICE Clear Netherlands** — post-Brexit EU base.

## Asset classes / instruments

- [[credit-default-swaps|CDS]] — single names and indices (CDX, iTraxx); dominant US clearer.
- **ICE futures**: Brent Crude, Gas Oil, Sugar, Cocoa, Coffee (energy + softs); UK / EU equity indices; rates futures.
- Some FX futures.

## Settlement / margining

- Daily MTM with variation margin.
- Initial margin per portfolio risk (SPAN-style).
- Default-fund contributions per member.

## Membership / access

Clearing members are major banks / FCMs ([[fcm]]). Buy-side via FCM.

## EMS touchpoints

- ICE futures clear automatically.
- CDS index trades cleared via ICE Clear Credit per SEF flow.
- Cross-CCP decisions (LCH vs ICE for CDS) are policy decisions per firm.

## Related

- [[credit-default-swaps]] · [[commodity-futures]]
- [[novation]] · [[fcm]] · [[ccp-vs-bilateral]]
- [[lch]] · [[cme-clear]] · [[ficc-clearing]] (sibling CCPs)
- [[arch-stp-pipeline]] · [[arch-risk-engine]]
