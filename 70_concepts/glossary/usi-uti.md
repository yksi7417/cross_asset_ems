---
type: concept
status: draft
tags: [concept/glossary, glossary/derivatives, glossary/regulatory]
---

# USI / UTI — Swap Trade Identifiers

**USI** ("Unique Swap Identifier", US/CFTC) and **UTI** ("Unique Transaction Identifier", EU/global ISO 23897) are **globally unique IDs assigned to a swap or derivative trade** at execution, used to track the trade across regulators, SDRs, CCPs, dealers, and the buy-side throughout its lifecycle.

The identifier is created once at execution (by the SEF, dealer, or CCP per the regime's rules), then **never changes** — through novation, partial novations, lifecycle events (rate resets, payments), or amendments. Regulatory reporting submissions all carry the USI/UTI; reconciling reports across regimes uses it as the join key.

USI was the original CFTC concept; UTI is the IOSCO-blessed global successor (used by EMIR, SFTR, MAS, JFSA, etc.). Modern trades typically carry both for cross-jurisdictional consistency.

## Example

A USD 10y IRS executes on Bloomberg SEF, novated to LCH. Bloomberg SEF assigns USI `BSEF.20260606.IRS.7891234`. LCH carries it through to the CFTC SDR submission. EMIR-eligible parties (e.g. an EU counterparty's allocation) also report the same trade under the same UTI to a EU TR. The buy-side EMS surfaces USI/UTI on every cleared trade for cross-system reconciliation.

## Why it matters in an EMS

- USI / UTI is a first-class field carried through the entire trade lifecycle, including [[arch-identity-chaining|the EMS's own chain identity stack]].
- Multi-jurisdictional reporting reconciles by USI/UTI.
- See [[arch-regulatory-reporting-service]] for per-regulator submission profiles.

## Related

- [[ccp-vs-bilateral]] · [[novation]] · [[mat]] · [[rfq-to-3]]
- [[trace]] · [[cftc-sdr]] · [[emir-sftr-csdr]]
- [[arch-regulatory-reporting-service]] · [[arch-identity-chaining]]
