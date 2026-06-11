# Java modules

Cross-asset EMS Java source — primary language for the architectural spine (FSM, transport, OMS, validator). Targets JDK 21 LTS.

Module structure mirrors the architectural layers documented in [`docs/`](../docs/) and the [architecture index](../00_index/architecture-index.md):

| Module | Layer | Notes |
|---|---|---|
| `ems-core` | Shared types, utils | No upstream module deps. |
| `ems-fsm` | The shared FIX-compliant FSM | YAML → codegen output lives here. |
| `ems-transport` | SBE + Aeron transport | Cluster + Archive primitives. |
| `ems-aaa` | Authentication / Authorization / Accounting | Identity + sessions + traces. |
| `ems-validator` | Hard-reject layer | Standardized reject codes. |
| `ems-oms` | Staged Order Manager + Router + Automation | OMS core. |
| `ems-fix-bridge` | Both FIX edges + API surface + REST + bulk I/O + FIX simulator | Client gateway (`fix`), venue gateway (`fix.venue`), simulator (`fix.sim`), typed API (`api`), REST binding (`api.rest`), CSV import/export (`bulk`). |
| `ems-market-data` | Quote server + IOI + real-time analytics | Input streams. |
| `ems-pretrade` | Compliance + Risk + Position + Pricing + Pre-trade analytics | Block-with-override and position-aware. |
| `ems-venue-connectivity` | Venue adapters + SOR + RFQ orchestration | Outbound to market. |
| `ems-posttrade` | Allocation + STP + Confirmation + Reg reporting + Best-ex | Settlement pipeline. |
| `ems-observability` | OTel + ELK + metrics integrations | Three-pillar observability. |
| `ems-ops` | Introspection + admin console + blue/green deploy + Time/Replay + Config backends | Read surface + security-gated write surface (`ops.introspect`); switchover protocol, cluster lease, fenced credentials (`ops.deploy`). |
| `ems-bench` | JMH performance benchmarks | Hot-path verification. |
| `ems-it` | Integration tests (in-process Aeron + SBE mocks) | Component-level. |

Build system: Gradle multi-module (see `settings.gradle.kts` — added in task 0.2).

Coding rules:

- No reflection on the hot path.
- No allocation in steady-state FSM event handling.
- All cross-component boundaries use SBE-encoded messages.
- Tests are tier-1: every module has unit tests; cross-module tests live in `ems-it`.
