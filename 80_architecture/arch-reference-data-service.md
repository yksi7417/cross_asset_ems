---
type: architecture
layer: reference_data
status: draft
tags: [architecture/reference_data]
---

# Reference Data Service

A unified **reference data registry and distribution layer** for slow-moving "facts" the EMS depends on: accounts, broker codes, counterparty enablement, allocation templates, compliance lists, broker registries, calendars, holiday tables, market microstructure (tick sizes, lot sizes), and the universe of instrument metadata beyond [[arch-symbology-figi|FIGI]].

The FIGI-specific symbology layer is its own service ([[arch-symbology-figi]]) because of licensing; this service handles **everything else** that's reference data.

## What goes here

| Domain | Examples |
|---|---|
| Account / KYC | account master, KYC status, beneficial owners, ID-market identifiers |
| Counterparty | counterparty master, enablement records, LEI, panel definitions |
| Broker | broker code registry, broker external IDs per system |
| Compliance lists | allow / restricted / watch / sanctions lists |
| Allocation | template registry |
| Market microstructure | tick sizes, lot sizes, contract multipliers, market hours |
| Calendars | per-currency / per-market holiday calendars, half-days |
| Curves / surfaces (definitions) | curve metadata; actual values live in [[arch-pricing-service]] |
| Strategy & algo wheel definitions | wheel registries, slicer configs |
| Validator / Compliance rule sets | rule definitions, parameter tables |
| FSM definitions | order/route FSM YAML definitions (see [[arch-fix-fsm-design]]) |
| SBE schemas | versioned schema definitions |
| Per-firm settings registries | per-key cascade [[arch-firm-desk-user]] |

If it changes at human (not market) speed and isn't a transactional event, it belongs here.

## Architecture

```mermaid
flowchart LR
  subgraph "Reference Data Service"
    REG[Registries per domain]
    EV[Event-sourced changes<br/>RefDataChanged events]
    VER[Version manager]
    PUB[Distribution / cache invalidation]
  end

  subgraph "Admin path"
    UI[Admin UI / CLI]
    AP[Approval workflow<br/>(signoff)]
  end

  subgraph "Consumer caches"
    CC[Component caches<br/>read on startup + invalidate on update]
  end

  UI --> AP
  AP --> EV
  EV --> REG
  REG --> VER
  VER --> PUB
  PUB --> CC
```

Like everything else, **the event log is the truth**. Reference data is a projection per domain; consumers cache locally and listen for invalidation events.

## Common shape

Each domain shares a uniform pattern:

```
RefDataRecord {
  domain               string
  key                  composite                # e.g. (cpty_id) or (firm_id, broker_code)
  value                domain-specific
  version              int                      # per record
  effective_date, expiry_date?
  status               ACTIVE | SUPERSEDED | RETIRED | DRAFT
  changed_by, changed_at, change_reason
  signoffs?            [{ identity, at }]       # for domains requiring approval
}
```

Operations:

```
operation: register_{domain}      # create
operation: amend_{domain}         # update with new version
operation: retire_{domain}        # mark inactive
operation: query_{domain}(filter, at_time?)
operation: subscribe_{domain}_changes(filter)
```

## Change-approval workflows

Sensitive domains require sign-off:

| Domain | Approval |
|---|---|
| Counterparty enablement | Compliance + Risk |
| Compliance lists (restricted, watch) | Compliance Officer + (optional) four-eyes |
| Allocation template | Account owner + PB ops |
| Wheel weights | Best Execution Committee |
| FSM definitions / SBE schemas | Engineering + QA |
| Broker codes | Ops + Risk |
| Calendars | Ops |

Approvals are events on the change. Without required signoffs, the change is `DRAFT` and not visible to consumers.

## Effective-date and supersession

Each amendment specifies an `effective_date`. Multiple versions can coexist:

- `version=1, effective 2025-01-01 to 2026-06-30`
- `version=2, effective 2026-07-01 onwards`

Consumers querying `as_of(2026-04-01)` get version 1. Queries default to "now". Replay queries pin to the historical version.

## Distribution

Consumers register interest at startup. Initial state delivered as a snapshot; subsequent changes stream as `RefDataChanged` events. Components cache locally for read latency.

Cache invalidation: every change event carries the domain + key; consumers invalidate matching cache entries. Some consumers care about all changes (e.g. compliance); others care about specific keys (e.g. an order-routing layer caring only about its enabled brokers).

## Licensing & metering

Some reference data is licensed (broker rate tables, certain account / customer data tiers). Same pattern as [[arch-symbology-figi|symbology]]: per-firm license registry, access metering, outbound scrubbing where receiving counterparty's license doesn't cover.

## Determinism / replay

Reference data state at any moment is `apply(events_up_to_t)`. [[arch-time-replay-server|Replay]] uses the historical state, not "current". Consumers reading reference data inside business logic snapshot the values they read on the consuming event so replay can verify.

## Anti-patterns

- **Direct database access to reference tables.** Bypasses the event audit. Always go through the service.
- **In-code constants.** Tick sizes, holidays, broker codes hard-coded in business logic = reference data divergence over time. Always pull from the service.
- **Asynchronous-only access in the hot path.** Pre-fetch and cache; never block on ref-data lookup during a hot transition.

## See also

- [[arch-symbology-figi]] (sibling, FIGI-specific) · [[arch-event-sourcing]] · [[arch-time-replay-server]]
- [[arch-validator]] · [[arch-compliance]] · [[arch-position-service]] · [[arch-allocation-service]]
- [[arch-firm-desk-user]] · [[arch-tag-permissions]] · [[counterparty-enablement]] · [[broker-codes]]
- [[arch-fix-fsm-design]] (FSM defs are ref data) · [[arch-sbe-aeron-transport]] (SBE schemas are ref data)
- [[arch-pricing-service]] (curve / surface metadata)
