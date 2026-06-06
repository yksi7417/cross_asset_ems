---
type: documentation
status: draft
tags: [documentation]
---

# DVP (Delivery-vs-Payment) Conventions

**DVP** is the **settlement mechanism** where securities and cash exchange simultaneously and atomically at the depository — if either leg fails, both fail. See the [[dvp-rvp-fop|glossary entry]] for the concept.

This note covers the **operational documentation** around DVP — settlement instructions, standing settlement instructions (SSIs), failed-trade workflows.

## Where DVP is used

- Virtually all institutional cash-securities settlement: US equities (T+1), US corporates (T+2 → T+1), EU equities (T+2), EU corporates (T+2), UST (T+1 via Fedwire), Eurobonds (T+2 via Euroclear / Clearstream).
- Repo settlement at the depositories.
- Cash-leg-only payments use Fedwire/CHIPS/CHAPS without DVP.

## Operational documentation

- **Standing Settlement Instructions (SSI)** — per-counterparty, per-account, per-currency, per-CSD documentation of how to settle. Must be agreed bilaterally and kept current.
- **Trade matching tickets** — DTC's ID Net (TradeSuite), Euroclear's matching service, [[markitserv]] for OTC.
- **Failed trade workflows** — buy-ins under CSDR ([[emir-sftr-csdr]]), penalty calculations, late-trade reporting.
- **Settlement Date** is a first-class field on every trade — driven by the asset class settlement convention ([[tplus-1-tplus-2]]).

## EMS implications

- Allocation generates per-account settlement instructions consumed by [[arch-stp-pipeline]] and [[arch-confirmation-affirmation]].
- SSI database in [[arch-reference-data-service]] — kept current per counterparty.
- Failed-settlement events feed [[arch-notification-service]] and surface in operations.
- CSDR penalties drive STP discipline.

## Related

- [[dvp-rvp-fop]] (glossary) · [[allocation-affirmation-confirmation]] · [[tplus-1-tplus-2]]
- [[dtc]] · [[fedwire]] · [[euroclear]] · [[clearstream]] (DVP venues)
- [[arch-stp-pipeline]] · [[arch-confirmation-affirmation]]
- [[emir-sftr-csdr]] (CSDR settlement discipline)
