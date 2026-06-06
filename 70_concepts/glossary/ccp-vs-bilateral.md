---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives]
---

# CCP vs. Bilateral Clearing

A **CCP (central counterparty)** sits between buyer and seller after the trade and becomes the counterparty to both — replacing the bilateral credit relationship with a single exposure to the clearinghouse. **Bilateral** means buyer and seller carry direct credit exposure to each other for the life of the trade.

CCPs require **initial margin** + daily **variation margin** plus default-fund contributions; in exchange they provide netting across all member exposures and standardized risk management. Post-2008 reforms (Dodd-Frank, EMIR) **mandated CCP clearing** for many liquid OTC derivatives — particularly standard IRS and CDS index — making bilateral a residual workflow for bespoke / non-cleared trades.

Major CCPs: LCH SwapClear (IRS), CME (IRS, futures), ICE Clear Credit (CDS), FICC (UST cash, GCF repo), DTC (US equity), Eurex Clearing (EU rates / equity), JSCC (JGB).

## Example

A USD 10y IRS dealt today between two US banks **must** clear through a CFTC-registered CCP (LCH or CME) — it's MAT (Made-Available-to-Trade — see [[mat]]). A bespoke 47-year structured FX swap with a Hong Kong corporate is non-cleared bilateral.

## Why it matters in an EMS

- Clearing destination is a first-class field on every trade — the EMS routes the post-trade leg to the correct CCP via the FCM.
- Margin calculation, give-up, and SDR reporting all depend on cleared vs bilateral status.
- See [[arch-stp-pipeline]] and [[arch-allocation-service]].

## Related

- [[novation]] · [[fcm]] · [[mat]] · [[give-up]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[money-market-repo]]
- [[arch-jurisdictional-compliance]] (CCP mandates per regime)
