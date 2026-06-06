---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives, glossary/execution]
---

# Give-Up

A **give-up** is the post-trade arrangement where a trade is **executed by one broker but cleared / booked by a different broker** — the executing broker "gives up" the trade to the clearing broker. Standard in futures and cleared OTC swaps where the buy-side has a separate executing broker relationship from its FCM clearing relationship.

The mechanism is contractually governed by **give-up agreements** between the executing broker, the clearing broker (FCM), and the client. The give-up flow: executing broker sends trade to a CCP / give-up service; clearing broker accepts (or rejects within a deadline); on acceptance, the trade is booked in the client's clearing account.

Rejection happens for credit reasons (the FCM hasn't extended sufficient client credit), parameter mismatches (account number wrong, fields missing), or unintended trades.

## Example

A buy-side firm has Goldman as executing broker for IRS execution skill, JPM as FCM for clearing. Trade dealt on Bloomberg SEF with Goldman → Goldman submits to LCH and signals give-up to JPM → JPM accepts → trade lands in the buy-side's JPM clearing account. The buy-side's two broker relationships are operationally separate.

## Why it matters in an EMS

- The executing-broker + clearing-broker pair is a two-field combination on every cleared trade.
- Give-up rejection is a STP exception that the [[arch-stp-pipeline]] must handle.
- Multi-FCM firms have allocation rules for which FCM gets which trade.

## Related

- [[fcm]] · [[ccp-vs-bilateral]] · [[novation]] · [[mat]]
- [[interest-rate-swaps]] · [[credit-default-swaps]] · [[commodity-futures]]
- [[arch-stp-pipeline]] · [[arch-allocation-service]]
