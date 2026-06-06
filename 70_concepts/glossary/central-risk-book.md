---
type: concept
status: draft
tags: [concept/glossary, glossary/equity]
---

# Central Risk Book (CRB)

A **Central Risk Book** is a **broker's centrally-managed proprietary risk account** that aggregates the residual risk from all client agency flow, broker capital-commitment trades, and broker internalization. Flow that the broker takes onto its book sits in the CRB until naturally offset or actively hedged.

The economics of a CRB matter to the client: a CRB-equipped broker can offer **better execution for client orders** because the broker's book likely has natural offsets (other clients going the other way), reducing the need to cross spreads. The CRB effectively internalizes client flow against an aggregated book.

Major bulge-bracket equity desks — Goldman, Morgan Stanley, JPM, Citi, UBS, BAML — all run material CRBs. Agency-only brokers like [[instinet]] **do not run a CRB** by design; this is the structural distinction between agency and bulge models.

## Example

A buy-side firm sends 100K shares to sell via a Goldman algo. Inside Goldman, the CRB has 500K shares in the buy direction from other client flow earlier in the day. Goldman matches 100K against the CRB internally at midpoint (improving the client price) and reduces its net inventory.

## Why it matters in an EMS

- Best-ex audit must distinguish CRB-matched fills from external venue fills.
- Buy-side compliance may prohibit CRB-matched fills for certain order types (best-ex policy).
- TCA must benchmark CRB fills vs externally-routed equivalents.

## Related

- [[capital-commitment]] · [[agency-vs-principal]] · [[systematic-internaliser]]
- [[_brokers-overview]] · [[goldman-sachs]] · [[morgan-stanley]] · [[jpmorgan]]
- [[arch-best-execution]] · [[arch-surveillance]]
