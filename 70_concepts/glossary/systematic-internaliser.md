---
type: concept
status: draft
tags: [concept/glossary, glossary/equity, glossary/regulatory]
---

# Systematic Internaliser (SI)

A **Systematic Internaliser** is a MiFID II-defined investment firm that **internalizes client order flow against its own proprietary capital** on an organised, frequent, systematic basis. Functionally a single-dealer "venue" — the SI is the counterparty, not a matching engine.

SIs must comply with **quote-publication obligations** (post firm two-way quotes for liquid instruments up to a Standard Market Size threshold), **best-execution obligations**, **post-trade transparency** (report executed trades within strict deadlines), and **fair-access rules** for clients. The SI regime was a MiFID II creation aimed at moving over-the-counter equity internalization into a regulated framework.

Distinct from a dark pool: an SI is **single-dealer** (the firm's own book) while a dark pool is **multilateral** (many participants). Equity bulge-bracket firms typically run both an SI (for principal internalization) and an MTF/ATS (for client crossing).

## Example

UBS operates **UBS SI** for principal-against-client EU equity flow alongside UBS MTF for client crossing. A buy-side EU equity order tagged "OK to SI" can hit UBS SI at the SI's published quote; one tagged "lit-only" cannot.

## Why it matters in an EMS

- The router treats SIs as a distinct destination class — they have unique fill semantics.
- SI quote publication = a data source the EMS may consume for pre-trade pricing.
- See [[arch-jurisdictional-compliance]] for the SI-related MiFID II rule set.

## Related

- [[arch-jurisdictional-compliance]] · [[arch-best-execution]]
- [[ats-ecn-mtf]] · [[lit-vs-dark]]
- [[ubs]] · [[goldman-sachs]] · [[_brokers-overview]] (firms running SIs)
