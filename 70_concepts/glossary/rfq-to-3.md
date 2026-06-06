---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives, glossary/execution]
---

# RFQ-to-3

**RFQ-to-3** is a CFTC requirement under the Dodd-Frank SEF rules: when a US person executes a **MAT swap** in **dealer-to-client RFQ mode**, they must request quotes from **at least three different dealers** simultaneously. The mechanism prevents bilateral-by-name workarounds that would defeat the SEF mandate.

The buy-side trader chooses the three (or more) dealers from their permissioned counterparty list, the SEF sends the RFQs simultaneously, dealers respond within the window, and the buy-side elects. Some SEFs support "RFQ-to-3+1" hybrids that include a streaming-price overlay. CLOB execution on a SEF is not subject to RFQ-to-3 (the CLOB already provides multilateral execution).

The 3-dealer minimum is **a regulatory floor, not a best-ex ceiling** — for liquidity-sensitive trades the buy-side may RFQ 5-10 dealers. The audit trail (which dealers were asked, who responded, who didn't) is preserved for both regulator and internal best-ex review.

## Example

A buy-side firm executes a USD 5y IRS (MAT product) on Bloomberg SEF in D2C mode. The trader sends RFQ to Goldman, JPM, Morgan Stanley. All three respond; the trader elects the Morgan Stanley quote. The other two responses become cover info for best-ex audit.

## Why it matters in an EMS

- The validator enforces RFQ-to-3 minimum for MAT D2C executions.
- The audit trail includes "dealers asked" + "dealers who responded" + "cover info."
- See [[arch-rfq]] for the canonical RFQ state machine including RFQ-to-3 variant.

## Related

- [[mat]] · [[ccp-vs-bilateral]] · [[fcm]] · [[novation]]
- [[rfq]] · [[rfq-rfs-rfm]] · [[arch-rfq]]
- [[bloomberg-sef]] · [[sef-platforms]] · [[tradeweb]]
- [[arch-jurisdictional-compliance]] · [[arch-best-execution]]
