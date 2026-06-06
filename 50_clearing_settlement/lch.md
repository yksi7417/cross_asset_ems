---
type: clearing_settlement
kind: ccp
status: draft
tags: [clearing/ccp]
---

# LCH (London Clearing House)

**LCH (London Clearing House)** is the **dominant global CCP for OTC interest rate swaps** through **SwapClear**, plus other key segments via separate services. Owned by LSEG. The world's largest OTC swap clearing operation.

## Asset classes / instruments

- **LCH SwapClear** — [[interest-rate-swaps|IRS]] (USD, EUR, GBP, JPY and 17+ other currencies), OIS, basis swaps, inflation swaps. Dominant CCP for USD and EUR IRS post-Dodd-Frank.
- **LCH CDSClear** — credit default swaps (CDS indices and single names).
- **LCH RepoClear** — European repo clearing (Gilts, Bunds, OATs, etc.).
- **LCH ForexClear** — FX NDFs ([[fx-ndf]]) and selected FX products.
- **LCH EquityClear** — European cash equity clearing.
- **LCH SwapAgent** — bilateral OTC variation/initial margin services (variance swaps, equity TRS).

## Settlement / margining

- Cleared trades novate ([[novation]]) to LCH at the moment of clearing.
- Daily MTM with variation margin calls.
- Initial margin per portfolio risk (SPAN-style).
- See [[ccp-vs-bilateral]] for the conceptual framework.

## Membership / access

Direct clearing members are major banks. Buy-side access via [[fcm|FCM]] — see [[fcm]]. Client-clearing services let buy-side firms clear through a member-FCM with their own dedicated account.

## EMS touchpoints

- Cleared-trade routing via the FCM relationship.
- USI/UTI ([[usi-uti]]) propagated through clearing.
- Margin calls landed in the [[arch-stp-pipeline]] for cash forecasting.
- Default rules and waterfalls — relevant for [[arch-risk-engine]] simulation of CCP-default scenarios.

## Related

- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[fx-ndf]] · [[money-market-repo]]
- [[novation]] · [[mat]] · [[fcm]] · [[ccp-vs-bilateral]]
- [[cme-clear]] · [[ice-clear]] · [[ficc-clearing]] (sibling CCPs)
- [[arch-stp-pipeline]] · [[arch-risk-engine]]
