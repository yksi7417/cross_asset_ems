---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives]
---

# FCM (Futures Commission Merchant)

A **Futures Commission Merchant** is a CFTC-registered firm that **carries client positions at a CCP on the client's behalf**. The FCM is the legal member of the CCP; the client is the FCM's customer. Required for any client who wants to clear futures or cleared OTC swaps in the US — clients typically aren't CCP members themselves.

The FCM handles: (1) margin calls — the FCM funds the client's margin to the CCP and recovers from the client; (2) give-up — accepting trades executed by the client at any SEF and clearing them; (3) reporting — providing position and margin statements; (4) credit — extending margin credit to the client.

Major FCMs: Goldman, Morgan Stanley, JPM, Citi, BAML, BNP, Citadel Securities, Marex. Some buy-side firms use multiple FCMs (different FCM per asset class or for failover capacity).

## Example

A buy-side firm executes a USD 10y IRS on Bloomberg SEF with Goldman as the dealer. The trade is sent to LCH for clearing; the buy-side's nominated FCM is JPM. Post-trade, JPM "gives-up" the trade from Goldman's clearing channel and books it into the buy-side's clearing account at JPM. JPM posts the buy-side's initial margin to LCH.

## Why it matters in an EMS

- Every cleared trade carries an FCM field — the EMS routes the clearing instruction accordingly.
- Multi-FCM firms need allocation rules per asset class / desk.
- See [[give-up]] for the post-trade FCM handoff process.

## Related

- [[ccp-vs-bilateral]] · [[novation]] · [[mat]] · [[give-up]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[commodity-futures]]
- [[arch-allocation-service]] · [[arch-stp-pipeline]]
