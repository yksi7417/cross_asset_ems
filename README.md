# Cross-Asset EMS

A green-field, **cross-asset Execution Management System** built from the ground up. Two parallel artefacts live in this repository:

- A **design knowledge base** — an Obsidian vault documenting the system's architecture, workflows, venues, regulatory regimes, clearing systems, and industry terminology across every asset class an institutional EMS must support.
- A **working codebase** — Java + C++ modules, SBE schemas, FSM definitions, and infrastructure-as-code that implement the design.

Both are first-class. The design vault stays the source of truth for **what** the system is; the code is the source of truth for **how** it runs. They cross-reference each other heavily.

## Vision

Build the kind of EMS a buy-side firm could deploy to trade **every asset class through one consistent platform** — cash equity, equity derivatives, equity swaps, the whole fixed-income complex (government bonds, IG/HY corporates, munis, money markets, repo, MBS/ABS, whole loans, convertibles), FX spot/forward/swap/NDF/options, IRS, CDS, structured products, listed commodities, physical commodities, crypto, prediction markets, and CFDs.

Guiding principles:

- **API-first.** Every operation is an API call. FIX is a subset of the API. Batch-by-default at the operation surface.
- **SBE + Aeron internally.** Zero-allocation binary protocol on a reliable UDP transport, with Raft-replicated clusters and position-precise replay for continuity and audit.
- **Event sourcing as the spine.** All state is derived from an append-only log; replay determinism is a hard architectural property — not an afterthought.
- **Three-layer permissions** (firm, desk, user) — every action evaluated under an AND-gate over hashtag-style tags.
- **24/7 continuity.** Crypto markets never stop; FX is 24/5; futures take a one-hour pause. The architecture leans on Aeron Cluster + Archive primitives so power-cycling is invisible to clients.
- **Standardized validator rejects.** One source of "no," with codes traders can act on.
- **Replay-deterministic configuration.** Configuration changes are events on the log; every event resolves to the values that were in effect when it happened.
- **One template per asset class** in SBE schemas — generic processing reads `InstrumentCore`; specialised paths dispatch on the template type.

The full architecture lives in [`80_architecture/`](80_architecture/) — navigate via [`00_index/architecture-index.md`](00_index/architecture-index.md).

## Repository layout

```
.
├── 00_index/                  Design-vault MOCs: HOME, architecture, matrix
├── 10_asset_classes/          One note per sub-asset class (25+ classes)
├── 20_workflows/              Staging, pre-trade, routing, treasury, others
├── 30_venues/                 Categorized by FI / FX / Equity / Brokers / Multi-asset
├── 40_regulatory/             TRACE, MSRB, CFTC SDR, FINRA, EMIR, etc.
├── 50_clearing_settlement/    DTC, Fedwire, Euroclear, LCH, FICC, ...
├── 60_documentation/          ISDA, CSA, GMRA, DVP, PSA, SIFMA TBA, ...
├── 70_concepts/               Glossary + Terminal-screen concept group
│   ├── glossary/              50+ industry-jargon entries
│   └── terminal_screens/      ALLQ / BTMM / FIT / CDSW etc. (not venues)
├── 80_architecture/           50+ architecture notes — the system design
│
├── IMPL/                      Implementation plan, /goal loop, checkpoint
│   ├── PLAN.md                14-phase task queue (~150 tasks)
│   ├── DELEGATION.md          Local Gemma / Gemini / Claude tier rules
│   ├── LOOP.md                The /goal text and pacing protocol
│   ├── HERMES.md              Discord notification spec
│   └── CHECKPOINT.md          State cursor (current task / phase progress)
│
├── java/                      Java multi-module Gradle build (15 modules)
├── cpp/                       C++ CMake workspace (15 modules, 1:1 with Java)
├── schemas/                   SBE XML, FSM YAML, FIX dicts, ref-data, reject codes
├── infra/                     Docker Compose, K8s, Terraform, Grafana, OTel
├── docs/                      ADRs, runbooks, onboarding — see DEVELOPMENT.md
├── scripts/                   dev / ci / release / drills shell scripts
├── tools/                     Build-time codegen + replay + fsm-validator
├── tests/                     Cross-module integration / smoke / e2e suites
└── templates/                 Skeletons for new vault notes
```

The design vault is published-ready (Quartz / MkDocs work without restructure). The code follows standard Gradle (Java) + CMake (C++) layouts; both are unified by the `schemas/` source-of-truth contract.

## Prerequisites

Everything you need to build, test, and run the dev stack locally.

| Tool | Min version | Why | Install hint |
|---|---|---|---|
| **Docker** | 24+ with Compose v2 | Dev infrastructure stack (Postgres, OpenSearch, Prometheus, Grafana, Jaeger, OTel) | [docs.docker.com/engine/install](https://docs.docker.com/engine/install/) — use the Compose plugin, **not** the legacy `docker-compose` v1 standalone |
| **Java** (Temurin) | 21 LTS | Java build; matches `gradle.properties` `emsJavaVersion` | `sdk install java 21-tem` via [SDKMAN](https://sdkman.io/) — or `apt install temurin-21-jdk` / `brew install --cask temurin@21` |
| **Gradle** | 8.10 | Java build wrapper generation (one-time) | `sdk install gradle 8.10` — only needed once to materialise `gradlew` |
| **CMake** | 3.25+ | C++ build | `apt install cmake` / `brew install cmake` |
| **GCC** | 14+ (or Clang 17+) | C++20 compiler | `apt install g++-14` / `brew install llvm` |
| **Python** | 3.10+ | FSM validator + lifecycle chaining tests | Usually pre-installed; `python3 --version` to check |
| **pytest + pyyaml + jsonschema** | latest | FSM test suite | `pip install pytest pyyaml jsonschema` |
| **git** | 2.30+ | Hooks use `git diff --check` features | System package manager |

**Linux-only: OpenSearch memory lock**

OpenSearch requires a higher `vm.max_map_count` or it will refuse to start:

```bash
sudo sysctl -w vm.max_map_count=262144          # current session
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.d/99-opensearch.conf  # persist across reboots
```

Check: `cat /proc/sys/vm/max_map_count` — must be ≥ 262144.

## Get started

### Read the design (no installation needed)

1. Open [`00_index/HOME.md`](00_index/HOME.md) in any markdown viewer. Wikilinks render in [GitHub's web preview](https://github.com/) and Obsidian.
2. Read [`00_index/architecture-index.md`](00_index/architecture-index.md) for the system architecture spine.
3. Browse [`00_index/asset-class-matrix.md`](00_index/asset-class-matrix.md) for a one-table view of every sub-asset class with its venues, RFQ, netting, and fungibility.
4. Skim [`70_concepts/glossary-index.md`](70_concepts/glossary-index.md) for industry jargon (on-the-run, BWIC, MAT, RFQ-to-3, etc.).

### Open the vault in Obsidian (optional, recommended for daily navigation)

```bash
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

Then **Open folder as vault** → point at this repository. The [vault runbook](00_index/USAGE.md) covers navigation hotkeys, the graph view, and authoring conventions.

### One-time local setup

Run these once after cloning:

```bash
# 1. Wire up git hooks (Conventional Commits + secret guard)
./scripts/dev/install-hooks.sh

# 2. Materialise the Gradle wrapper (requires gradle 8.10 in PATH)
gradle wrapper --gradle-version=8.10
chmod +x gradlew

# 3. Pre-fetch Docker images so the first stack start is instant
docker compose -f infra/docker-compose/compose.dev.yaml pull
```

Step 3 pulls ~2 GB on first run; after that `docker compose up` starts in seconds from the local image cache.

### Spin up the dev stack

```bash
./scripts/dev/start-dev-stack.sh
```

Expected output (services come up sequentially, postgres + opensearch report healthy):

```
Bringing up ems-dev stack...
Waiting for services to become healthy...
  postgres: ready
  opensearch: ready

Endpoints:
  Postgres        postgres://ems:ems_dev@localhost:5432/ems
  OpenSearch      http://localhost:9200
  Dashboards      http://localhost:5601
  Prometheus      http://localhost:9090
  Grafana         http://localhost:3000  (anonymous, Admin role)
  Jaeger UI       http://localhost:16686
  OTel gRPC       localhost:4317
  OTel HTTP       http://localhost:4318
```

Tear down (keeps data volumes): `docker compose -f infra/docker-compose/compose.dev.yaml down`

Tear down + wipe data: `docker compose -f infra/docker-compose/compose.dev.yaml down -v`

### Verify progress

**FSM definitions (no stack needed):**

```bash
# Structural validation — all 5 FSM YAML files pass the schema
python3 tools/fsm-validator/validate.py --all

# Lifecycle chaining — 14 cross-FSM cascade assertions
python3 -m pytest tools/fsm-validator/test_lifecycle_chaining.py -v
```

Both should exit 0 with no failures.

**OTel pipeline (stack must be up):**

```bash
./scripts/dev/run-otel-toy.sh
# Then open http://localhost:16686 → search service "ems-otel-toy"
# Expect a 3-span trace: stage:validate, stage:route, stage:ack
```

**C++ scaffold (no stack needed):**

```bash
cmake -S cpp -B build/cpp -DCMAKE_CXX_STANDARD=20
cmake --build build/cpp
ctest --test-dir build/cpp --output-on-failure
# "No tests were found" is expected — stubs populate at task 1.7
```

**Java build (no stack needed):**

```bash
./gradlew assemble      # compiles all 15 modules
./gradlew allTests      # runs all unit tests (currently only ems-observability has source)
```

More detail in [`DEVELOPMENT.md`](DEVELOPMENT.md).

## What to expect right now

This is a **mid-construction project**. The design vault is dense (295 notes, including a full architecture spine, every asset class characterised, every venue categorized, every regulator, and an industry-jargon glossary). The codebase is at task **0.8 of ~150** in the [implementation plan](IMPL/PLAN.md) — bootstrap is mostly done, but the real services (FSM, transport, OMS, validator, market data, venue connectivity) are not yet built.

Concretely:

| Area | State |
|---|---|
| Design vault | ✅ comprehensive — 80+ architecture notes, 50+ glossary entries, asset/venue/regulatory catalogs complete |
| Implementation plan | ✅ 14 phases, ~150 tasks, delegation tiers, loop protocol |
| Monorepo + Gradle/CMake wiring | ✅ 15 Java modules + 15 C++ modules, dependency layering enforced |
| CI / Docker Compose / OTel toy | ✅ green path verified |
| Pre-commit + commit-msg hooks | ✅ enforce Conventional Commits + secret-pattern guard |
| FSM, transport, OMS, validator, ... | 🚧 not yet built (phases 1-7) |
| Venue connectivity, post-trade | 🚧 not yet built (phases 11-12) |
| MVP smoke test | 🚧 not yet runnable |

If you want to **read about how an EMS works across every asset class**, the design vault is ready today. If you want to **see the system running**, you'll need to wait for phases 1-12 to land, or contribute by picking the next unblocked task in `IMPL/PLAN.md`.

## How to contribute

1. **Pick a task** from `IMPL/PLAN.md` — the first `[ ]` line whose `← blocks:` prerequisites are all `[x]`.
2. **Apply the delegation tier** per `IMPL/DELEGATION.md`. Bootstrap tasks were `(local)` / `(claude)`; phase 4 instrument templates are mostly `(local first draft, claude review)`.
3. **Mark `[~]`** in PLAN, claim with a commit, implement, run tests, commit, mark `[x] (sha1234)`.
4. **Hermes notifications** on phase boundaries (`IMPL/HERMES.md`) — Discord targets verified.

The `/goal` loop in `IMPL/LOOP.md` automates this for Claude Code sessions; each session works through tasks until pacing triggers fire, then resumes from `IMPL/CHECKPOINT.md` next session.

## Three docs, three audiences

- **[`README.md`](README.md)** (this file) — project vision and 5-minute get-started. Read first.
- **[`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md)** — the design knowledge base guide. Read if you want to understand the architecture, asset classes, workflows, venues, regulatory regimes, and clearing/settlement systems.
- **[`DEVELOPMENT.md`](DEVELOPMENT.md)** — the implementation guide. Read if you want to build, contribute code, run tests, or deploy.

## Licence

Apache 2.0 (see `LICENSE` once the file is added — pending task 0.10).
