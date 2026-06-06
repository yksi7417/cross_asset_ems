---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives]
---

# Novation

**Novation** is the legal substitution of one party in a contract by another, with the consent of all original parties. In a derivatives context, novation typically refers to the **CCP novation** — the moment the central counterparty becomes the legal counterparty to both original parties of a cleared trade, replacing their bilateral relationship.

Pre-novation: A and B have a bilateral IRS trade with credit exposure to each other. Post-novation: A has an IRS with the CCP, B has an IRS with the CCP, and the CCP nets across all its members. The original A-to-B contract is **extinguished**.

A separate sense: **client-novation** in OTC swaps — a buy-side firm assigns its swap position to another buy-side (with dealer consent) without unwinding. Operational headache: the new party must be onboarded and credit-approved by the dealer.

## Example

A buy-side firm dealt a 5y USD IRS with Goldman in March. Both parties send the trade to LCH SwapClear. LCH novates: the buy-side firm now has an IRS with LCH; Goldman has an IRS with LCH. Both parties' margin obligations are to LCH. Goldman's bilateral exposure to the buy-side is extinguished.

## Why it matters in an EMS

- The EMS must track post-trade state through novation — pre-novation status (Pending) vs post-novation status (Cleared).
- USI/UTI identifiers track the trade through the novation event.
- Margin and risk calculations differ pre- vs post-novation.

## Related

- [[ccp-vs-bilateral]] · [[mat]] · [[fcm]] · [[usi-uti]]
- [[interest-rate-swaps]] · [[credit-default-swaps]]
- [[arch-stp-pipeline]] · [[arch-allocation-service]]
