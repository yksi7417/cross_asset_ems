# Development Guide

Build, test, deploy. The companion to the design knowledge base — read [`README.md`](README.md) for the project vision and [`00_index/HOME.md`](00_index/HOME.md) for the design vault.

## Required toolchain

| Tool | Version | Why |
|---|---|---|
| **Java** | Temurin 21 LTS | Primary language; matches `gradle.properties` `emsJavaVersion` |
| **Gradle** | 8.10 | Build (wrapper committed — `./gradlew` works on clone) |
| **CMake** | 3.25+ | C++ build system for hot-path modules |
| **GCC** | 14+ (or Clang 17+) | C++20 compiler for C++ modules |
| **Docker** | 24+ with Compose v2 | Dev infrastructure stack |
| **git** | 2.30+ | Hooks use `git diff --check` features |
| `shellcheck` | latest | Hook lint (optional, falls back) |
| `yamllint` | latest | Hook lint (optional, falls back) |
| `markdownlint-cli2` | latest | Hook lint (optional, falls back) |

The CI runs all of these in `ubuntu-24.04` runners — see `.github/workflows/ci.yml`.

## First-time setup

```bash
# 1. Clone (you've already done this)
git clone git@github.com:yksi7417/cross_asset_ems.git
cd cross_asset_ems

# 2. Bootstrap Java 21 (installs via dnf/apt/brew; the Gradle wrapper is
#    already committed, so ./gradlew works immediately).
./scripts/dev/bootstrap.sh

# 3. Install local git hooks (Conventional Commits + secret guard)
./scripts/dev/install-hooks.sh

# 4. Verify the build wires up
./gradlew :printSummary
# Expect: "Cross-Asset EMS — 15 modules" followed by the module list.

# 5. Pre-fetch Docker images (one-time, ~2 GB)
docker compose -f infra/docker-compose/compose.dev.yaml pull

# 6. Bring up the dev infrastructure stack
./scripts/dev/start-dev-stack.sh

# 7. Verify the OTel pipeline works end-to-end
./scripts/dev/run-otel-toy.sh
# Then open http://localhost:16686 → service=ems-otel-toy
```

The Gradle wrapper (`gradlew` + `gradle/wrapper/gradle-wrapper.jar`) is committed to the repository, per Gradle's recommended practice, so `./gradlew` works immediately on a fresh clone and in CI. CI verifies the jar's checksum against known-good Gradle releases via `gradle/actions/wrapper-validation`.

**Java version:** the project requires Java 21. If you have a newer JVM as your system default, bootstrap handles this automatically. To make Java 21 permanent: `sdk default java 21.0.7-tem`.

## Project structure (code side)

```text
java/                  15 Gradle modules, layered per architecture spine
  ems-core             Shared types — no upstream deps
  ems-fsm              Shared FIX-compliant FSM (YAML→code)
  ems-transport        SBE + Aeron + Cluster + Archive
  ems-aaa              Authentication / Authorization / Accounting
  ems-validator        Hard-reject + standardized codes
  ems-oms              Staged Order Manager + Router + Automation
  ems-fix-bridge       FIX gateway + REST API + Bulk I/O
  ems-market-data      Quote server + IOI + real-time analytics
  ems-pretrade         Compliance + Risk + Position + Pricing + Pre-trade analytics
  ems-venue-connectivity  Venue adapters + SOR + RFQ orchestration
  ems-posttrade        Allocation + STP + Confirmation/Affirmation + Reg reporting + Best-ex
  ems-observability    OTel + ELK + Prometheus
  ems-ops              JMX introspection + Time/Replay + Config service
  ems-bench            JMH performance benchmarks (run on demand)
  ems-it               Cross-module integration tests

cpp/                   15 CMake modules, 1:1 with the Java modules

schemas/               Single source of truth for cross-component contracts
  sbe/                 SBE XML schemas → codegen Java + C++
  fsm/                 FSM YAML definitions per arch-fix-fsm-design
  fix-dictionaries/    FIX 4.2/4.4/5.0 dictionaries (QuickFIX format)
  reference-data/      Day counts, currency codes, MIC codes, tick sizes
  reject-codes/        EMS-<CAT>-<NNNN> catalog per arch-validator

infra/                 Infrastructure-as-code
  docker-compose/      Local dev stack (Postgres + OpenSearch + Prom + Grafana + Jaeger + OTel)
  k8s/                 Production K8s manifests
  terraform/           AWS infra per arch-deployment
  prometheus/          Scrape configs + alerting rules
  grafana/             Datasources + dashboards
  otel-collector/      OTel collector config
  opensearch/          Index templates + ILM

tools/
  codegen/             SBE/FSM codegen entry points
  replay/              Replay CLI (golden replay verification)
  fsm-validator/       FSM YAML completeness checker

scripts/
  dev/                 Developer-local scripts
  ci/                  CI-only scripts called by GitHub Actions
  release/             Tagging + image promotion
  drills/              Resilience drills per arch-resilience-24x7

tests/
  integration/         Component-level (in-process Aeron + SBE mocks; ~500 tests)
  smoke/               Smoke tests for deployed environments
  e2e/                 BDD scenarios + full Docker Compose stack (~50 scenarios)
```

## Common commands

### Build + test

```bash
# Java
./gradlew assemble                        # compile everything
./gradlew :ems-fsm:test                   # test one module
./gradlew allTests                        # all modules (runs in CI)
./gradlew :ems-bench:jmh                  # benchmarks (on demand)
./gradlew spotlessApply                   # auto-format Java

# C++
cmake -S cpp -B build/cpp -G Ninja -DCMAKE_CXX_STANDARD=20
cmake --build build/cpp
ctest --test-dir build/cpp --output-on-failure
# benchmarks (once ems-bench populates):
# cmake --build build/cpp --target ems-bench && build/cpp/ems-bench/ems_bench
```

### Code generation

```bash
./scripts/dev/regen-schemas.sh             # SBE + FSM codegen (when schemas land)
./gradlew :ems-transport:sbeCodegen        # SBE only
```

Generated code lands in `**/build/generated/` — gitignored. Once the codegen pipeline (task 1.7) lands, a curated set will also land in `src/main/generated/` (committed) so PR reviewers see what consumers see.

### Dev infrastructure

```bash
./scripts/dev/start-dev-stack.sh           # bring up the stack
docker compose -f infra/docker-compose/compose.dev.yaml down -v   # tear down (with volumes)
docker compose -f infra/docker-compose/compose.dev.yaml logs -f opensearch
```

Endpoints:

- Postgres: `postgres://ems:ems_dev@localhost:5432/ems`
- OpenSearch API: `http://localhost:9200` · Dashboards: `http://localhost:5601`
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3000` (anonymous Admin)
- Jaeger UI: `http://localhost:16686`
- OTel: gRPC `localhost:4317` · HTTP `http://localhost:4318`

### Verify OTel pipeline

```bash
./scripts/dev/run-otel-toy.sh
# Open http://localhost:16686, look for service "ems-otel-toy"
```

### Validate the whole dev stack (integration smoke test)

```bash
./scripts/dev/check-dev-stack.sh             # full: liveness + wiring + live trace
./scripts/dev/check-dev-stack.sh --no-trace  # skip the Gradle trace emit
```

Confirms each service is actually serving (not just `Up`) and that the
cross-service wiring works: a real `SELECT 1` on Postgres, OpenSearch cluster
health, Prometheus → otel-collector scrape target UP, Grafana's Prometheus +
Jaeger datasources provisioned, a fresh trace flowing SDK → collector → Jaeger,
and fresh log records flowing SDK → collector → OpenSearch (`ems-logs` index).
Exit code = number of failed checks (0 = healthy), so it doubles as a CI/smoke
gate. Endpoints are overridable via env (`PROM_URL`, `GRAFANA_URL`, …) if you
remapped ports.

### Grafana dashboards

Dashboards are **provisioned from files** — Grafana loads every JSON under
`infra/grafana/dashboards/` into the **EMS** folder on startup (and reloads
every 30s). The first one is **OTel Pipeline Overview**
(<http://localhost:3000/d/ems-otel-overview>): the toy app counter, per-signal
received-vs-exported throughput, collector health, and a live OpenSearch logs
panel.

Datasources are provisioned from `infra/grafana/provisioning/datasources/` with
**stable uids** (`prometheus`, `opensearch`) so dashboard JSON can reference
them deterministically. The OpenSearch datasource needs the
`grafana-opensearch-datasource` plugin, installed at container startup via
`GF_INSTALL_PLUGINS` (Grafana OSS doesn't bundle it) and uses
`timeField: observedTimestamp` — **not** `@timestamp`, which the collector
leaves unset (epoch 0) unless a log carries an explicit record timestamp.

**Editing a provisioned dashboard.** Because the dashboard is file-backed,
changes you make in the Grafana UI are **not persisted** — they vanish on the
next provider reload. The workflow is:

1. Edit the panels in the Grafana UI as normal.
2. Dashboard settings (gear) → **JSON Model**, or **Share → Export → Save to file**.
3. Copy that JSON over `infra/grafana/dashboards/<name>.json` (keep the `uid`
   and `title`; drop any numeric `id`).
4. Commit the file. The next reload (≤30s) serves your changes to everyone.

To add a **new** dashboard, drop another `*.json` in the same directory — no
provider config change needed. Reference datasources by uid
(`{"type":"prometheus","uid":"prometheus"}`) so the panels resolve on a fresh
Grafana volume.

## Commit conventions

Pre-commit + commit-msg hooks enforce:

- **Conventional Commits**: `<type>(<scope>): <subject>` where type is `feat | fix | docs | style | refactor | perf | test | build | ci | chore | task | impl | arch | venues | glossary | home | notes | index | revert`. See `.githooks/commit-msg` for the regex.
- **Task-aligned scopes**: when implementing a task in `IMPL/PLAN.md`, use the task id (`feat(0.5):`, `task(1.7): claim`, etc.) so commits map back to the plan.
- **No accidental secrets** (AKIA, PEM private keys, sk-*, ghp_*). Excludes `.githooks/`, `tests/*/fixtures/`, `docs/decisions/`.
- **No accidental large files** (>10 MB).
- **Trailing-whitespace + CRLF guards** via `git diff --check`.

Bypass with `git commit --no-verify` only after explicit review.

A **pre-push** hook runs `shellcheck` on all shell scripts (the same check as
CI's full-gate `Lint` job) so failures are caught locally before they reach CI.
It uses `shellcheck` if on PATH, else a `podman`/`docker` shellcheck image, and
skips with a warning if neither is available. Bypass once with
`git push --no-verify`. Install all hooks with `./scripts/dev/install-hooks.sh`.

## Implementation plan + the /goal loop

The implementation plan lives in `IMPL/`. Open `IMPL/PLAN.md` for the 14-phase, ~150-task queue.

To run the build loop:

1. Read the `/goal` text in `IMPL/LOOP.md` (first code block).
2. Paste it into Claude Code's `/goal` command.
3. The loop: pick the next unblocked `[ ]` task → choose delegation tier per `IMPL/DELEGATION.md` → implement → commit → mark `[x] (sha)` → advance `CHECKPOINT.md` → repeat. On phase boundary completion, posts a Hermes Discord notification.

The loop is **token-paced** — sessions wrap up gracefully when commit-count or context thresholds hit, and the next session resumes from `IMPL/CHECKPOINT.md`. See `IMPL/LOOP.md` for the protocol.

## Adding a new architecture note

Notes live in `80_architecture/arch-<slug>.md`. Use the existing notes as templates — they share a common shape: lead paragraph stating what + why, sections for components / data model / mechanics, worked examples where useful, anti-patterns, and a `See also` block.

After writing:

1. Add the new note to `00_index/architecture-index.md` under the matching section.
2. Add wikilinks **into** the new note from any older notes that should reference it.
3. Make the commit `arch: add arch-<slug>(...)`.

## Adding a new task to the plan

`IMPL/PLAN.md` is canonical. To add tasks:

1. Insert into the appropriate phase, before any tasks that should follow it (the loop reads in file order).
2. Use the format `- [ ] **<phase>.<n>** Description (delegation-tier) ← blocks: <prereq-ids>`.
3. If the new task changes phase boundary semantics, also update `IMPL/HERMES.md` (notification template) and `IMPL/CHECKPOINT.md` (phase table).

## CI overview — fast lane vs. full gate

CI is split for fast inner-loop feedback without losing the pre-merge quality bar:

| Trigger | What runs | Purpose |
|---|---|---|
| **Push to a feature branch** | Java compile + unit tests, C++ compile + ctest | **Fast lane** — rapid feedback while iterating |
| **Pull request → `main`** | the above **+** Spotless, shell/markdown lint, schema lint | **Full gate** — must pass before merge |
| **Push to `main`** | full gate | post-merge safety net |

The fast lane is a strict subset of the full gate, so nothing skips the pre-merge
check — it's only deferred. Full-gate jobs/steps are guarded in `ci.yml` by
`github.event_name != 'push' || github.ref == 'refs/heads/main'`. The
`concurrency` block cancels superseded in-progress runs on feature branches, so
rapid pushes don't queue.

**Local mirror of the fast lane:**

```bash
./scripts/dev/fast-check.sh          # Java compile + unit tests (no linters)
./scripts/dev/fast-check.sh --cpp    # also build + ctest the C++ tree
# Before opening a PR, run the linters the full gate will run:
./gradlew --no-daemon spotlessApply spotlessCheck
```

**Enforcing the gate (one-time, GitHub UI):** Settings → Branches → add a
protection rule for `main` → *Require status checks to pass before merging*, and
select the full-gate checks (`Java build + unit tests`, `C++ build + unit tests`,
`Schema lint`, `Lint (shell, markdown)`). Without this, the full gate runs but
doesn't *block* a merge.

Workflows under `.github/workflows/`:

- **`ci.yml`** — Java build + tests (Gradle), C++ build + tests (CMake/ctest), schema lint (yamllint + xmllint), shell + markdown lint. (SBOM + dependency-review dropped for now — dependency review needs GHAS on private repos.)
- **`codeql.yml`** — security-and-quality query suite for Java/Kotlin. **Parked** (manual `workflow_dispatch` only): code-scanning upload needs GitHub Advanced Security, a paid feature on private repos. Re-enable by making the repo public or enabling GHAS, then restoring the triggers (see the comment at the top of the file). `c-cpp` added once `cpp/` modules have real source (task 1.7+).

`dependabot.yml` opens weekly Monday bumps for Gradle and GitHub Actions. No CMake/C++ entry — C++ deps are managed via CMake FetchContent, not a versioned registry.

## Coding rules

### Java

- JDK 21 (`emsJavaVersion=21` in `gradle.properties`).
- `-Werror -Xlint:all` (from `ems.java-conventions`).
- **No reflection on the hot path.**
- **No allocation in steady-state FSM event handling.**
- All cross-component boundaries use SBE-encoded messages.
- Spotless w/ google-java-format runs in CI; auto-format with `./gradlew spotlessApply`.

### C++

- C++20, CMake 3.25+, GCC 14+ or Clang 17+.
- `-Wall -Wextra -Wpedantic -Werror` are CI gates once source lands.
- `#pragma once` at every header. No raw `new`/`delete` — use RAII and standard containers.
- No exceptions or dynamic allocation on the hot path (FSM dispatch, tick processing).
- All cross-component boundaries use SBE-encoded messages via `ems-transport`.

### Tests

- Tier-1: every module has unit tests (`src/test/java`, `tests/`).
- Cross-module tests live in `ems-it` and `tests/integration/`.
- Pyramid: ~5,000 unit + 500 component + 50 BDD per `arch-ddd-tdd`.
- Replay determinism is verified on every release via golden-replay diff.

## Where each part of the design lives in code

| Architecture note | Java module | C++ module |
|---|---|---|
| `arch-api-first`, `arch-fix-api-bridge`, `arch-bulk-io` | `ems-fix-bridge` | `ems-fix-bridge` |
| `arch-sbe-aeron-transport`, `arch-sequence-recovery`, `arch-resilience-24x7` | `ems-transport` | `ems-transport` |
| `arch-event-sourcing`, `arch-time-replay-server`, `arch-configuration-service` | `ems-ops` + `ems-transport` | same |
| `arch-fix-fsm-design`, `arch-order-route-lifecycle`, `arch-fix-appendix-d` | `ems-fsm` | `ems-fsm` |
| `arch-order-staged`, `arch-router-layer`, `arch-automation-layer`, `arch-multileg`, `arch-aggregation`, `arch-fx-netting` | `ems-oms` | `ems-oms` |
| `arch-validator` | `ems-validator` | `ems-validator` |
| `arch-firm-desk-user`, `arch-tag-permissions`, `arch-identity-chaining`, `entry-point-aaa` | `ems-aaa` | `ems-aaa` |
| `arch-quote-server`, `arch-ioi`, `arch-realtime-analytics` | `ems-market-data` | `ems-market-data` |
| `arch-compliance`, `arch-risk-engine`, `arch-position-service`, `arch-pricing-service`, `arch-pretrade-analytics`, `arch-surveillance` | `ems-pretrade` | `ems-pretrade` |
| `arch-venue-connectivity`, `arch-smart-order-router`, `arch-rfq` | `ems-venue-connectivity` | `ems-venue-connectivity` |
| `arch-allocation-service`, `arch-stp-pipeline`, `arch-confirmation-affirmation`, `arch-regulatory-reporting-service`, `arch-best-execution`, `arch-jurisdictional-compliance` | `ems-posttrade` | `ems-posttrade` |
| `arch-observability` | `ems-observability` | `ems-observability` |
| `arch-jmx-introspection`, `arch-deployment` | `ems-ops` | `ems-ops` |
| `arch-symbology-figi`, `arch-security-master`, `arch-reference-data-service`, `arch-corporate-actions` | `ems-pretrade` (initially), eventually own modules | same |

## Troubleshooting

- **`Unable to access jarfile gradle-wrapper.jar`** — the committed wrapper jar is missing from your working tree (e.g. a stale `.gitignore` or a bad checkout). Restore it with `git checkout -- gradle/wrapper/gradle-wrapper.jar`.
- **OTel toy doesn't show up in Jaeger** — verify the collector is running (`docker compose ... ps otel-collector`), check the collector logs for connection refused, and confirm `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`.
- **`Hooks reject commit message`** — your message doesn't match Conventional Commits. See `.githooks/commit-msg` for the regex; quickly: `feat(<scope>): <subject under 72 chars>`.
- **`pre-commit caught a secret in my source`** — likely a regex literal in test fixtures or hooks. Add the path to the exclude list in `.githooks/pre-commit` (currently `.githooks/`, `tests/*/fixtures/`, `docs/decisions/`).
- **OpenSearch refuses to start** — bumps `vm.max_map_count`. On Linux: `sudo sysctl -w vm.max_map_count=262144`.

## See also

- [`README.md`](README.md) — project vision and high-level overview
- [`00_index/HOME.md`](00_index/HOME.md) — design knowledge base entry
- [`00_index/architecture-index.md`](00_index/architecture-index.md) — architecture spine
- [`IMPL/PLAN.md`](IMPL/PLAN.md) — implementation task queue
- [`IMPL/LOOP.md`](IMPL/LOOP.md) — the `/goal` loop protocol
- [`IMPL/DELEGATION.md`](IMPL/DELEGATION.md) — when to use local Gemma / Gemini / Claude
- [`docs/README.md`](docs/README.md) — ADRs, runbooks, onboarding
