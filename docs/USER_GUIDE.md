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

**Current state (2026-06-11, v1 build-out):**

- ✅ End-to-end flow proven for **7 asset classes** (US IG corp, treasury, US equity, preferred,
  listed futures/options, FX spot, FX forward) — see the smoke tests below.
- ✅ **Both edges of the FIX wire**: client-facing gateway (inbound NewOrderSingle with tag-9700
  trace adoption, outbound ExecutionReports) and venue-facing gateway (outbound 35=D/F/G, inbound
  ExecutionReports), each on the resumable session channel, plus a **FIX venue simulator**
  (acceptor side: resend recovery, Appendix-D pending states, configurable fills) for wire-level
  testing — `FixWireSmokeTest` drives the whole chain over a real FIX session.
- ✅ **OMS core complete**: staged orders, router, automation, multi-leg/package orders (all three
  execution modes), block aggregation with pro-rata/sequenced/avg-price allocation back, FX
  netting (PB/PAC-isolated buckets, residual parents, internal crosses).
- ✅ **One typed API surface** (Session/Service/Request/Subscription/Event): batch envelope with
  idempotency + all-or-nothing/STOP semantics, pub/sub with cursor resume, a REST edge binding
  (JDK HTTP), CSV bulk import/export with idempotent re-import, and a three-surface parity test
  (FIX = native = REST, byte-identical projections).
- ✅ **Pre-trade suite**: compliance gate (BLOCK-with-override, machine-gun limiter,
  allow/restricted/watch lists, tag-gated four-eyes overrides), positions (WAC, busts), risk
  engine (notional caps), pricing fallback chain, advisory pre-trade analytics.
- ✅ **Ops/deploy subsystem**: introspection registry + security-gated admin console, blue/green
  switchover protocol with cluster lease + fence-token credentials, and three scripted drills
  (weekly leader-kill, monthly cold-start, quarterly region failover).
- ✅ Post-trade tail: allocation, STP pipeline, confirmation/affirmation, regulatory reporting with
  a mock TRACE submission.
- 🔶 **Deferred by decision**: internal market-data quote server (Phase 9) — the trader desktop
  will pull market data through a pluggable feed SPI backed by Bloomberg Desktop/Server API; the
  desktop itself (Perspective WASM grid) is the next goal (Phase 18). Fat-finger (10.2) waits on
  the reference-price feed.

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

The system is exercised through its test harnesses plus two standalone processes (the REST edge
and the FIX simulator). The flows worth running first:

**The FIX-wire smoke — the full chain over a real FIX session (the flagship):**

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.FixWireSmokeTest"
```

Drives: client FIX `NewOrderSingle` carrying a W3C trace on tag 9700 → real AAA logon + layered
validator → staged → real router → venue-facing FIX gateway over a **wire FIX session** to the
venue simulator → ExecutionReports back through the Route and Order FSMs → allocation → STP →
confirmation → TRACE-mock. Asserts the venue-side wire bytes, terminal FILLED FSMs, a **single
trace ID** across every hop, and **byte-identical replay** of both the event log and the wire.

**The MVP smoke (in-process mock venue) and the cross-asset smoke:**

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.MvpSmokeTest"
./gradlew :ems-it:test --tests "io.crossasset.ems.it.CrossAssetSmokeTest"
./gradlew :ems-it:test --tests "io.crossasset.ems.it.SurfaceParityTest"   # FIX = API = REST
```

**Drive it over HTTP — the REST edge binding:**

The API surface is reachable over real HTTP (JDK server, no frameworks). In a test or a main:
`new RestHttpServer(new RestEdgeBinding(aaa, api, subscriptions), 8080).start()`, then:

```bash
curl -X POST localhost:8080/api/v1/logon -d '{"token":"tok-ui"}'          # -> {"sessionId":N}
curl -X POST localhost:8080/api/v1/stage_orders -H "X-EMS-Session: N" \
     -d '{"requestId":"r1","sessionSeq":1,"items":[{"clOrdId":"CL-1","figi":"BBG...","side":1,"qty":100,"account":"acc-1"}]}'
curl 'localhost:8080/api/v1/events?topic=orders&from=1'                    # resumable cursor fetch
```

Operations: `stage_orders`, `amend_orders`, `cancel_orders`, `mark_ready`, `route_orders`,
`cancel_routes`, `subscribe`/`unsubscribe`; batch options `{"partialOk":false}` (all-or-nothing
with compensation) and `{"onError":"STOP"}`.

**Run the FIX venue simulator standalone** (manual conformance against any FIX initiator):

```bash
./gradlew :ems-fix-bridge:runFixSimulator -PsimPort=9876
```

**Bulk import/export (CSV):** `BulkOrderImporter.importCsv(uploadId, sessionId, seq, csvText)`
stages a whole file through the API batch envelope — header aliases (`Quantity`, `Buy_Sell`, …),
`1.5M`/`"1,000"` coercion, per-row errors with cell locations; re-import with the same uploadId is
idempotent, re-import with a new uploadId rejects duplicates by ClOrdID (`EMS-ORD-2510`).
`BlotterExporter.toCsv(orders, ExportTemplate.DEFAULT_BLOTTER)` renders deterministic snapshots.

**Everything else:**

```bash
./gradlew test                 # all module unit tests (~400 across 15 modules)
./gradlew :ems-it:test        # all integration tests
./scripts/dev/fast-check.sh   # quick pre-commit loop
python3 -m pytest tools/fsm-validator/ -v   # FSM YAML validation suite
scripts/drills/quarterly-region-failover.sh --dry-run   # failover drill (also weekly/monthly)
```

## 5. How an order flows (module map)

| Stage | Module | Notes |
|---|---|---|
| FIX edges (client + venue) / API surface / REST / bulk I/O / FIX simulator | `ems-fix-bridge` | Resumable session channels both directions; tag-9700 trace propagation; typed batch API with idempotency + pub/sub cursor resume; CSV import/export. |
| Validation (hard reject) | `ems-validator` | Layered pipeline; reject codes `EMS-<CAT>-<NNNN>` per `schemas/reject-codes/catalog.yaml`. |
| Identity / permissions | `ems-aaa` | Firm/Desk/User hierarchy; 3-layer tag-permission AND-gate; trace ID stamped at logon. |
| Staged orders + routing + automation + multi-leg + aggregation + netting | `ems-oms` | Orders are containers of intent; routes are obligations to venues; packages, blocks, and net groups ride the same FSMs. |
| Compliance / risk / positions / pricing / pre-trade analytics | `ems-pretrade` | BLOCK-with-override gate, machine-gun limiter, lists, WAC positions with busts, notional caps, price fallback chain, advisory models. |
| Venue adapters | `ems-venue-connectivity` | Adapter framework + in-process mock MarketAxess; the FIX-wire path pairs `FixVenueGateway` (8.2) with the simulator. |
| Allocation → STP → confirmation → reg reporting | `ems-posttrade` | Per-asset-class profiles (stage set, match tolerance, regulator set, lot sizing). |
| State machines | `ems-fsm` | YAML-defined Order/Route/MultiLeg/VenueSession/SOR FSMs → generated Java/C++. |
| Event sourcing / replay | `ems-core` + `ems-transport` | Append-only event log, projections, replay engine, sim-clock. |
| Observability | `ems-observability` | OTel traces, Prometheus metrics, OpenSearch logs, ClOrdID trace rejoin map. |
| Ops surface | `ems-ops` | Introspection registry + health, security-gated admin console, blue/green switchover + lease + fenced credentials, Time/Replay + Config backends. |

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

The v1 build-out goal is **complete** (Phases 7, 8, 10-in-scope, 14, the FIX simulator + wire
smoke, and these docs — see [`IMPL/CHECKPOINT.md`](../IMPL/CHECKPOINT.md) for the task-by-task
record). Next up, per [`IMPL/PLAN.md`](../IMPL/PLAN.md): **Phase 18 — the trader desktop** on
**Perspective** (WASM streaming-pivot grid) fed by a pluggable market-data SPI with a Bloomberg
Desktop/Server API adapter first (18.12/18.13), the blotter/ticket/watchlist screens, kill
switch, 15c3-5 pack, and the rest of the buyer-readiness controls; plus the queued Phase 11/12
additions (real venue adapters, CAT, commissions, TCA). The internal market-data quote server
(Phase 9) stays deferred until SOR/venue work needs it.

---

*Maintained under Phase 17 (17.1). If something here is stale, the smoke tests are the source of
truth — run them.*
