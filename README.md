# Cross-Asset EMS

A green-field institutional Execution Management System: every asset class through one platform — cash equity, fixed income, FX, rates, credit, commodities, crypto. Java + C++ on SBE/Aeron with full event sourcing.

Design vault: [`00_index/HOME.md`](00_index/HOME.md) · Implementation plan: [`IMPL/PLAN.md`](IMPL/PLAN.md)

---

## Quick start (Fedora · Podman)

### 1. Install prerequisites

```bash
sudo dnf install podman podman-docker podman-compose git python3 python3-pip
pip3 install --user pytest pyyaml jsonschema
# CMake + GCC are only needed if you build the C++ modules locally
sudo dnf install cmake ninja-build gcc-c++
# Java 21 is handled automatically by bootstrap.sh below
```

### 2. Clone and bootstrap

```bash
git clone git@github.com:yksi7417/cross_asset_ems.git
cd cross_asset_ems
./scripts/dev/bootstrap.sh       # installs Java 21 + generates Gradle wrapper
./scripts/dev/install-hooks.sh   # Conventional Commits + secret guard
```

### 3. Start the dev stack

```bash
# One-time kernel setting required by OpenSearch
sudo sysctl -w vm.max_map_count=262144

docker compose -f infra/docker-compose/compose.dev.yaml pull
./scripts/dev/start-dev-stack.sh
```

If you use **Tailscale**, run once to make the stack reachable from your tailnet:

```bash
sudo firewall-cmd --zone=trusted --add-interface=tailscale0 --permanent
sudo firewall-cmd --reload
```

### 4. Verify

```bash
./gradlew assemble                                                    # Java build
python3 -m pytest tools/fsm-validator/test_lifecycle_chaining.py -v  # FSM tests
./scripts/dev/run-otel-toy.sh                                         # end-to-end OTel trace
```

`run-otel-toy.sh` emits all three OpenTelemetry signals through the collector.
Each lands in a different backend:

| Signal | What the toy emits | Flows to | View it |
|---|---|---|---|
| **Traces** | `ems-toy-root` + 3 stage spans | collector → **Jaeger** | http://localhost:16686 → service `ems-otel-toy` |
| **Logs** | 1 INFO record per stage (trace-correlated) | collector → **OpenSearch** | http://localhost:5601 → index pattern `ems-logs*` |
| **Metrics** | counter `ems.toy.stages.processed` | collector → **Prometheus** → **Grafana** | http://localhost:9091 → query `ems_toy_stages_processed_total`; or the provisioned **OTel Pipeline Overview** dashboard at http://localhost:3000/d/ems-otel-overview |

Or validate the whole stack (liveness + wiring + all three signals) in one shot:

```bash
./scripts/dev/check-dev-stack.sh            # 16 checks; exit 0 = all healthy
./scripts/dev/check-dev-stack.sh --no-trace # skip the Gradle telemetry emit (faster)
```

### Endpoints (once the stack is up)

| Service | URL |
|---|---|
| Grafana | http://localhost:3000 |
| Jaeger | http://localhost:16686 |
| OpenSearch Dashboards | http://localhost:5601 |
| Prometheus | http://localhost:9091 |
| OpenSearch API | http://localhost:9200 |
| Postgres | `postgres://ems:ems_dev@localhost:5432/ems` |
| OTel HTTP | http://localhost:4318 |
| OTel gRPC | `localhost:4317` |

---

## Current status

Codebase is at task **0.8 of ~150** in the [implementation plan](IMPL/PLAN.md).

| Area | State |
|---|---|
| Design vault — 80+ architecture notes, asset/venue/regulatory catalogs | ✅ |
| 14-phase implementation plan, ~150 tasks | ✅ |
| Monorepo — 15 Java modules + 15 C++ modules, layering enforced | ✅ |
| CI, Docker Compose dev stack, OTel pipeline (traces + logs + metrics verified) | ✅ |
| FSM definitions + lifecycle chaining tests | ✅ |
| Core services (FSM, transport, OMS, validator, venue connectivity …) | 🚧 phases 1–12 |

---

## Docs

| | |
|---|---|
| **[SETUP.md](SETUP.md)** | Full setup — all platforms (macOS, dev container, Obsidian, Tailscale) |
| **[DEVELOPMENT.md](DEVELOPMENT.md)** | Build commands, project structure, coding rules, CI overview |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | How to contribute — task workflow, `/goal` loop, commit conventions |
| **[KNOWLEDGE_BASE.md](KNOWLEDGE_BASE.md)** | Design knowledge base — architecture, asset classes, venues, regulatory |
| **[00_index/HOME.md](00_index/HOME.md)** | Design vault entry point — navigate the architecture spine |
| **[IMPL/PLAN.md](IMPL/PLAN.md)** | Implementation task queue — pick the next `[ ]` task |

---

## Licence

Apache 2.0 (pending task 0.10).
