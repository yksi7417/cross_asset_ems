# Diagnostics — Verify the Build, Layer by Layer

Each layer of the system has a dedicated check: what it proves, the command, and what healthy
looks like. Run them top to bottom after a fresh clone (or after touching a layer) to localize a
problem fast — every layer assumes the ones above it are green. The toys that used to live in the
README quick start (the OTel toy, the dev-stack checker) live here now; the README's quick start
is the [trader-desktop demo](../DEMO.md).

| Layer | Proves | Command |
|---|---|---|
| 0. Toolchain | Java 21 + wrapper + hooks work | `./scripts/dev/bootstrap.sh` |
| 1. Build | every Java module compiles | `./gradlew assemble` |
| 2. FSM definitions | lifecycle YAML → generated FSMs are sound | `python3 -m pytest tools/fsm-validator/` |
| 3. Unit suites | every module's tests pass | `./gradlew allTests` |
| 4. Transport | Aeron Cluster + Archive round-trip | `./gradlew phase0Smoke` |
| 5. Observability stack | traces/logs/metrics flow end-to-end | `./scripts/dev/check-dev-stack.sh` |
| 6. FIX wire | real FIX session → router → fills → replay | `FixWireSmokeTest` |
| 7. Business end-to-end | order → allocation → confirm → reporting, replay-deterministic | `MvpSmokeTest`, `CrossAssetSmokeTest` |
| 8. The live system | UI + backend operating together | `./scripts/dev/run-trader-demo.sh` |

---

## Layer 0 — Toolchain

```bash
./scripts/dev/bootstrap.sh       # installs Java 21 if missing (idempotent)
./scripts/dev/install-hooks.sh   # Conventional Commits + secret guard pre-commit hooks
java -version                    # expect: openjdk 21.x
```

The Gradle wrapper jar is committed, so `./gradlew` works straight after clone — no Gradle
install. C++ modules are optional locally (`sudo dnf install cmake ninja-build gcc-c++` if you
want them; CI builds them).

**If it fails:** check `JAVA_HOME` doesn't point at an older JDK; sdkman/asdf shims often win
over `PATH`.

## Layer 1 — Build

```bash
./gradlew assemble
```

Compiles all 15 Java modules with `-Werror` and spotless formatting checks.

**If it fails with** `Spotless JVM-local cache is stale`: `rm -rf .gradle/configuration-cache`
and retry; if it persists, a long-lived Gradle daemon holds the stale state — `./gradlew --stop`
(note: this kills anything running inside the daemon, e.g. a live `runTraderEdge`).

## Layer 2 — FSM definitions

```bash
python3 -m pytest tools/fsm-validator/test_lifecycle_chaining.py -v
```

Validates the YAML FSM definitions in `schemas/fsm/` (order/route/multi-leg lifecycle chaining —
the FIX Appendix-D semantics everything above relies on). Regenerate the Java/C++ FSMs after
editing YAML: `python3 tools/codegen/fsm_codegen.py`.

## Layer 3 — Unit suites

```bash
./gradlew allTests              # every module; ~1,750 tests, all green at Phase 18 close
./gradlew :ems-oms:test         # or any single module while iterating
```

Per-module reports land in `java/<module>/build/reports/tests/test/index.html`.

## Layer 4 — Transport (Aeron)

```bash
./gradlew phase0Smoke
```

The Phase-0 acceptance gate: an Aeron Cluster + Archive ping/pong round-trip — proves the
SBE/Aeron substrate (shared memory, media driver, cluster consensus) works on this machine.

**If it fails:** check `/dev/shm` space (Aeron needs it) and that no stale media driver lock
files linger in `/dev/shm/aeron-*`.

## Layer 5 — Observability stack (optional for app work)

The containerized dev stack: OTel collector, Jaeger, Prometheus, Grafana, OpenSearch, Postgres.
Only needed when working on observability itself — the demo (layer 8) does not require it.

```bash
sudo sysctl -w vm.max_map_count=262144     # one-time, required by OpenSearch
docker compose -f infra/docker-compose/compose.dev.yaml pull
./scripts/dev/start-dev-stack.sh
./scripts/dev/check-dev-stack.sh           # 16 checks; exit 0 = all healthy
./scripts/dev/check-dev-stack.sh --no-trace  # skip the Gradle telemetry emit (faster)
```

### The OTel toy

`./scripts/dev/run-otel-toy.sh` emits all three OpenTelemetry signals through the collector,
each landing in a different backend — the quickest way to prove the whole pipeline:

| Signal | What the toy emits | Flows to | View it |
|---|---|---|---|
| **Traces** | `ems-toy-root` + 3 stage spans | collector → **Jaeger** | <http://localhost:16686> → service `ems-otel-toy` |
| **Logs** | 1 INFO record per stage (trace-correlated) | collector → **OpenSearch** | <http://localhost:5601> → index pattern `ems-logs*` |
| **Metrics** | counter `ems.toy.stages.processed` | collector → **Prometheus** → **Grafana** | <http://localhost:9091> → query `ems_toy_stages_processed_total`; or the provisioned **OTel Pipeline Overview** dashboard at <http://localhost:3000/d/ems-otel-overview> |

### Real telemetry from the demo (18.26)

The toy proves the pipeline; the **demo edge emits real traffic** when told to. No service
initializes the OTel SDK by default (tests/CI stay silent) — opt in with one env var:

```bash
docker compose -f infra/docker-compose/compose.dev.yaml up -d otel-collector jaeger
EMS_DEMO_OTEL=1 ./gradlew :ems-fix-bridge:runTraderEdge       # or set OTEL_EXPORTER_OTLP_ENDPOINT
```

Then in **Jaeger** (<http://localhost:16686>), service `trader-desktop-edge`:

- **`demo.order` spans** — one per demo-bot order lifecycle, carrying the internal audit handle
  (`order.id`, `route.id`) plus figi/side/qty/venue/cum_qty as attributes and one span **event
  per fill** (execId, qty, px).
- **HTTP spans** — every REST request the desktop makes, grouped by route template
  (`POST /api/v1/stage_orders`, `GET /api/v1/instruments/{figi}`, …) with method/path/status.

(Verified live 2026-06-12: Jaeger lists the service with both span kinds and fill events.)

### Stack endpoints

| Service | URL |
|---|---|
| Grafana | <http://localhost:3000> |
| Jaeger | <http://localhost:16686> |
| OpenSearch Dashboards | <http://localhost:5601> |
| Prometheus | <http://localhost:9091> |
| OpenSearch API | <http://localhost:9200> |
| Postgres | `postgres://ems:ems_dev@localhost:5432/ems` |
| OTel HTTP | <http://localhost:4318> |
| OTel gRPC | `localhost:4317` |

If you use **Tailscale**, make the stack reachable from your tailnet once:

```bash
sudo firewall-cmd --zone=trusted --add-interface=tailscale0 --permanent
sudo firewall-cmd --reload
```

## Layer 6 — FIX wire

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.FixWireSmokeTest"
```

Drives a real FIX session over TCP against the venue simulator (11.15): logon, resend recovery,
NewOrderSingle → AAA-backed validator → real router → fills back over the wire with tag-9700
trace propagation, then **byte-identical replay** of the slice. The simulator also runs
standalone for manual conformance work: `./gradlew :ems-fix-bridge:runFixSimulator -PsimPort=9876`.

## Layer 7 — Business end-to-end

```bash
./gradlew :ems-it:test --tests "io.crossasset.ems.it.MvpSmokeTest"        # corp bond, full chain
./gradlew :ems-it:test --tests "io.crossasset.ems.it.CrossAssetSmokeTest" # 7 asset classes
./gradlew :ems-it:test --tests "io.crossasset.ems.it.SurfaceParityTest"   # FIX = native = REST
```

The MVP smoke wires one order through validate → stage → route → fill → allocation →
confirmation → TRACE-mock with a single trace ID and replay determinism. The cross-asset smoke
repeats per asset-class profile (FX-spot confirms-not-reports, equity reports-not-confirms, …).
The parity test proves the same operation through FIX / native API / REST yields byte-identical
events.

## Layer 8 — The live system

```bash
./scripts/dev/run-trader-demo.sh          # backend edge + trader desktop; Ctrl-C stops both
```

Open <http://localhost:5173>, token `trader-token`. The guided walkthrough — every panel, every
backend call, troubleshooting — is [`TRADER_DESKTOP_DEMO.md`](TRADER_DESKTOP_DEMO.md); the
recorded tour is [`DEMO.md`](../DEMO.md). Re-record it with
`node ui/trader-desktop/demo/record-demo.mjs` while the stack is up.
