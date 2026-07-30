# 0002 — Port a vertical slice at full depth, cash equity only

Status: accepted
Date: 2026-07-30
Deciders: Anthony Si
Context: ADR [0001](0001-reinstate-rust-three-language-port.md), [[arch-order-route-lifecycle]], [[arch-order-staged]]

## Context

The Java tree is 684 source files across 15 modules covering seven asset classes. Porting all of
it to two more languages is roughly 1,400 additional source files. That is not a schedule risk —
it is a certainty of producing stubs, which is exactly the failure already sitting in `cpp/`.

The port has to be shaped so that "done" is a small enough target to actually reach, while still
being deep enough that a differential gate has something to bite on and the study guide has real
idioms to document.

## Decision

**One asset class (cash equity), one order path, full production depth.** The slice is traced from
the actual Java call path, not invented:

| Stage | Java reference | Ported |
|---|---|---|
| Order intake | `ems-oms`: `OrderRequest`, `StagedOrder`, `StageResult` | Yes |
| Authorization | `ems-aaa`: subject resolution + entitlement decision | Yes — authz decision only, no SSO/SCIM |
| Validation | `ems-validator`: `LayeredValidatorPipeline`, `ValidationLayer`, `ValidationRequest`, `ValidationResult`, `ValidatorPipeline` | Yes — all 5 layers |
| Order lifecycle | `ems-fsm`: generated from `schemas/fsm/order.fsm.yaml` | Yes — code-generated, never hand-written |
| Staging + routing | `ems-oms`: `StagedOrderManager`, `RouteManager`, `InMemoryRouteManager`, `Route`, `RouteRequest`, `RouteResult` | Yes |
| Route lifecycle | `ems-fsm`: generated from `schemas/fsm/route.fsm.yaml` | Yes — code-generated |
| Venue edge | `ems-venue-connectivity`: single-destination FIX out, ExecutionReport in, session state | Yes |
| Fill handling | `ems-posttrade`: allocation | Yes — allocation only; drop-copy is a recorded stub |
| Transport | `ems-transport` | Yes — interface with two implementations (ADR 0006) |
| Event sourcing + replay | `ems-core`: journal append, replay | Yes — this is what the conformance gate exercises |

**Out of scope, staying Java-only:** SOR, algo wheel, sweep, broker algos, multi-leg, FX netting,
compliance gate, override desk, positions, pricing, analytics, borrow/locate, STP, confirmation,
regulatory reporting, CAT, TCA, surveillance, jurisdiction router, IOI, quote multicast, ops
console, blue/green switchover, cluster lease, the trader desktop, and the six non-equity asset
classes.

Expected size: 45–60 source files per new language, plus tests.

## Consequences

- Feature parity with the Java tree is explicitly **not** a goal, and a PR that adds an out-of-scope
  module to `rust/` or `cpp/` should be rejected on those grounds alone.
- Anything added later is a new sub-project with its own spec, not a scope amendment to this one.
- Java remains the only place several capabilities exist. `docs/polyglot/README.md` carries the
  scope table so a reader can tell at a glance which tree to look in for a given capability.
- The slice is end-to-end, so a single conformance case exercises intake → authz → validation →
  FSM → routing → venue → fill → journal. That is the property that makes the gate meaningful.

## Alternatives considered

**Hot-path only** (FSM dispatch + SBE decode, no end-to-end path). Rejected: nothing for an
end-to-end differential gate to test; equivalence would be asserted at the function level, which
is exactly the kind of claim that stays true while the system diverges.

**Full seven-asset-class parity.** Rejected: 1,000+ files per language. Becomes stubs. See `cpp/`.

**Core spine only** (`ems-core` + `ems-fsm`, no order path). Rejected: no end-to-end order, so no
corpus case can express "a trader sent this and got that", which is the only claim a reader cares
about.
