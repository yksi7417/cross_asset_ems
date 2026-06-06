---
type: concept
status: draft
tags: [concept/glossary, glossary/settlement]
---

# DVP / RVP / FOP — Settlement Types

Three settlement instructions describing **how cash and securities exchange** on the settlement date:

- **DVP** ("Delivery vs Payment") — securities and cash exchange **simultaneously and conditionally** at the CSD (DTC, Fedwire, Euroclear, Clearstream). If either leg fails, both fail — atomic settlement. The standard for institutional trades. Eliminates principal risk.
- **RVP** ("Receipt vs Payment") — the buy-side variant of DVP. Same atomic mechanism; the buy-side receives securities while paying cash.
- **FOP** ("Free of Payment") — securities transfer **without cash movement**. Used for internal transfers (between fund accounts), collateral movements, gifts, in-kind subscriptions / redemptions. Cash movement happens separately (or not at all).

DVP / RVP exchanges happen at the CSD level — the CSD has both legs visible and matches them. FOP transfers are single-sided and faster but carry no payment-side atomicity.

## Example

A buy-side fund buys 100K shares of IBM from Goldman. The instruction is **RVP at DTC**: on T+1, the fund's custodian receives 100K IBM from Goldman's DTC account in exchange for the cash, atomically. If Goldman doesn't deliver (or the custodian doesn't have cash), DTC fails the entire transaction — both legs unwind.

## Why it matters in an EMS

- The settlement type field is required on every allocation / instruction.
- The validator rejects mismatched DVP/RVP/FOP instructions per account / counterparty conventions.
- See [[arch-stp-pipeline]] and [[arch-confirmation-affirmation]] for the workflow.

## Related

- [[tplus-1-tplus-2]] · [[allocation-affirmation-confirmation]]
- [[arch-stp-pipeline]] · [[arch-confirmation-affirmation]]
- DTC / Fedwire / Euroclear / Clearstream (see [[50_clearing_settlement]])
