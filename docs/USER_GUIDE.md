# Cross-Asset EMS — User Guide

How to build, run, test, and drive the system as it exists today. Written 2026-06-10 against the
post-MVP state (MVP v0 + Phase 16 cross-asset coverage complete); extended by Phase 17 of
[`IMPL/PLAN.md`](../IMPL/PLAN.md) as the v1 build-out lands.

> Not to be confused with `00_index/USAGE.md`, which explains how to use the Obsidian design
> vault. This document is about the running software.

---

## 1. What this system is

An institutional Execution Management System: orders enter via FIX, pass validation and
permissioning, are staged and routed to venues, and the resulting fills flow through allocation →
STP → confirmation → regulatory reporting — every step event-sourced with a single distributed
trace ID and **byte-identical replay** of any log slice. Java (architectural spine) + C++ (hot
paths), SBE messages over Aeron transport.

**Current state (2026-06-10):**

- ✅ End-to-end flow proven for **7 asset classes** (US IG corp, treasury, US equity, preferred,
  listed futures/options, FX spot, FX forward) — see the smoke tests below.
- ✅ Client-facing FIX gateway (inbound NewOrderSingle, outbound ExecutionReports) on a resumable
  session channel; validator; staged order manager; router; automation layer.
- ✅ Post-trade tail: allocation, STP pipeline, confirmation/affirmation, regulatory reporting with
  a mock TRACE submission.
- 🔶 Venue side is an **in-process mock** (MarketAxess-shaped). A wire-level FIX venue simulator +
  end-to-end FIX-wire smoke are queued (tasks 11.15 / 15.2).
- 🔶 No trader-facing UI yet (ops UIs only); market data, pre-trade risk/compliance, and the REST/WS
  API are in the active build-out. See [`IMPL/PLAN.md` → Current goal](../IMPL/PLAN.md).

## 2. Prerequisites and first build

Follow the [README quick start](../README.md) for the full Fedora/Podman setup. The short version:

```bash
git clone git@github.com:yksi7417/cross_asset_ems.git
cd cross_asset_ems
./scripts/dev/bootstrap.sh       # installs Java 21 (Gradle wrapper is committed)
./scripts/dev/install-hooks.sh   # Conventional Commits + secret guard
./gradlew assemble               # build all Java modules
```

C++ modules need `cmake ninja-build gcc-c++` and are built via CMake under `cpp/`.

## 3. The dev stack (observability + persistence)

```bash
sudo sysctl -w vm.max_map_count=262144          # one-time, for OpenSearch
docker compose -f infra/docker-compose/compose.dev.yaml pull
./scripts/dev/start-dev-stack.sh
./scripts/dev/check-dev-stack.sh                # 16 health checks; exit 0 = healthy
```

| Service | URL |
|---|---|
| Grafana | <http://localhost:3000> |
| Jaeger (traces) | <http://localhost:16686> |
| OpenSearch Dashboards (logs) | <http://localhost:5601> |
| Prometheus (metrics) | <http://localhost:9091> |
| OpenSearch API | <http://localhost:9200> |
| Postgres | `postgres://ems:ems_dev@localhost:5432/ems` |

`./scripts/dev/run-otel-toy.sh` emits one trace/log/metric through the whole pipeline if you want
to verify the three pillars end to end.

## 4. Running the system today: the executable entry points

There is no long-running "EMS server" binary yet — the system is exercised through its test
harnesses (this is deliberate until the venue and API edges are real). The two flows worth running
first:

**The MVP smoke — one corp-bond order, end to end:**

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.MvpSmokeTest"
```

Drives: FIX `NewOrderSingle` (US IG corp) → validator → staged order → route → mock MarketAxess →
fill ack → allocation → STP → confirmation → TRACE-mock submission. Asserts a **single trace ID**
through the chain and **byte-identical replay** of the event log.

**The cross-asset smoke — all 7 asset classes:**

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.CrossAssetSmokeTest"
```

Same pipeline, parametrized per asset class with per-class post-trade profiles (e.g. FX spot
confirms but doesn't report; equity reports to CAT-mapping but doesn't confirm; corp/treasury do
both; lot sizing per class: equities whole units, FI $1k denominations, FX 10k min-notional).

**Everything else:**

```bash
./gradlew test                 # all module unit tests (FSM transitions, validator goldens, …)
./gradlew :ems-it:test        # all integration tests
./scripts/dev/fast-check.sh   # quick pre-commit loop
python3 -m pytest tools/fsm-validator/ -v   # FSM YAML validation suite
```

## 5. How an order flows (module map)

| Stage | Module | Notes |
|---|---|---|
| FIX in / ExecutionReports out | `ems-fix-bridge` | Resumable session channel: heartbeat/TEST_REQUEST, resend buffer, resume-from-seq. |
| Validation (hard reject) | `ems-validator` | Layered pipeline; reject codes `EMS-<CAT>-<NNNN>` per `schemas/reject-codes/catalog.yaml`. |
| Identity / permissions | `ems-aaa` | Firm/Desk/User hierarchy; 3-layer tag-permission AND-gate; trace ID stamped at logon. |
| Staged orders + routing + automation | `ems-oms` | Orders are containers of intent; routes are obligations to venues. |
| Venue adapters | `ems-venue-connectivity` | Adapter framework + in-process mock MarketAxess. FIX simulator (11.15) lands here. |
| Allocation → STP → confirmation → reg reporting | `ems-posttrade` | Per-asset-class profiles (stage set, match tolerance, regulator set, lot sizing). |
| State machines | `ems-fsm` | YAML-defined Order/Route/MultiLeg/VenueSession/SOR FSMs → generated Java/C++. |
| Event sourcing / replay | `ems-core` + `ems-transport` | Append-only event log, projections, replay engine, sim-clock. |
| Observability | `ems-observability` | OTel traces, Prometheus metrics, OpenSearch logs. |
| Ops surface | `ems-ops` | JMX introspection (queued), Time/Replay + Config service backends. |

Module details: [`java/README.md`](../java/README.md). Design rationale: the vault
([`00_index/HOME.md`](../00_index/HOME.md), `80_architecture/`).

## 6. Replay: re-deriving any state from the log

The core guarantee — any slice of the event log re-derives identical state:

- Both smokes assert byte-identical replay as their final step; the replay harness lives in
  `ems-core` (projection framework + replay engine, tasks 3.4/3.5) with a sim-clock
  Time/Replay server (3.6).
- The **Time/Replay UI** (`ui/time-replay/`, Next.js — see its README) drives the sim-clock
  interactively; the **Config service UI** (`ui/config-service/`) edits versioned configuration.

## 7. Operational scripts

```bash
scripts/drills/          # leader-kill (weekly) + cold-start (monthly) drill scripts
scripts/dev/regen-schemas.sh   # SBE codegen after schema changes
scripts/ci/              # what CI runs; mirror locally before pushing
```

Podman specifics for the dev stack: [`docs/runbooks/podman.md`](runbooks/podman.md).

## 8. What's coming (and where to watch)

The active build-out (executed continuously by the [[LOOP]] in `IMPL/LOOP.md`) adds, in order:
OMS multi-leg/aggregation/FX-netting (7.x) → venue-facing FIX gateway, API surface, bulk I/O,
REST/WS (8.x) → market data (9.x) → pre-trade compliance/risk/positions/pricing (10.x) → **FIX
venue simulator + wire-level end-to-end smoke** (11.15/15.2) → ops resilience (14.x) → full docs
(17.x). Progress cursor: [`IMPL/CHECKPOINT.md`](../IMPL/CHECKPOINT.md). After that, the trader
desktop + buyer-readiness controls (Phase 18).

---

*Maintained under Phase 17 (17.1). If something here is stale, the smoke tests are the source of
truth — run them.*
