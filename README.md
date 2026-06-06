# Cross-Asset EMS

A green-field, **cross-asset Execution Management System** built from the ground up. Two parallel artefacts live in this repository:

- A **design knowledge base** — an Obsidian vault documenting the system's architecture, workflows, venues, regulatory regimes, clearing systems, and industry terminology across every asset class an institutional EMS must support.
- A **working codebase** — Java + Rust modules, SBE schemas, FSM definitions, and infrastructure-as-code that implement the design.

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
├── rust/                      Rust Cargo workspace (15 crates, 1:1 with Java)
├── schemas/                   SBE XML, FSM YAML, FIX dicts, ref-data, reject codes
├── infra/                     Docker Compose, K8s, Terraform, Grafana, OTel
├── docs/                      ADRs, runbooks, onboarding — see DEVELOPMENT.md
├── scripts/                   dev / ci / release / drills shell scripts
├── tools/                     Build-time codegen + replay + fsm-validator
├── tests/                     Cross-module integration / smoke / e2e suites
└── templates/                 Skeletons for new vault notes
```

The design vault is published-ready (Quartz / MkDocs work without restructure). The code follows standard Gradle + Cargo layouts; both are unified by the `schemas/` source-of-truth contract.

## Get started in 5 minutes

### Read the design (no installation needed)

1. Open [`00_index/HOME.md`](00_index/HOME.md) in any markdown viewer. Wikilinks render in [GitHub's web preview](https://github.com/) and Obsidian.
2. Read [`00_index/architecture-index.md`](00_index/architecture-index.md) for the system architecture spine.
3. Browse [`00_index/asset-class-matrix.md`](00_index/asset-class-matrix.md) for a one-table view of every sub-asset class with its venues, RFQ, netting, and fungibility.
4. Skim [`70_concepts/glossary-index.md`](70_concepts/glossary-index.md) when you hit a term you don't recognise (on-the-run, BWIC, MAT, RFQ-to-3, etc.).

### Open the vault in Obsidian (optional, recommended for daily navigation)

```bash
flatpak install --user -y flathub md.obsidian.Obsidian
flatpak run md.obsidian.Obsidian
```

Then **Open folder as vault** → point at this repository. The [vault runbook](00_index/USAGE.md) covers navigation hotkeys, the graph view, and authoring conventions.

### Bring up the development stack

```bash
# Install hooks (one-time)
./scripts/dev/install-hooks.sh

# Generate the Gradle wrapper (one-time, requires gradle in PATH)
gradle wrapper --gradle-version=8.10

# Start the infrastructure tier (Postgres, OpenSearch, Prometheus,
# Grafana, Jaeger, OTel collector)
./scripts/dev/start-dev-stack.sh

# Emit a toy trace to verify the OTel pipeline end-to-end
./scripts/dev/run-otel-toy.sh
```

After the toy trace runs, open **http://localhost:16686** (Jaeger UI) and look for service `ems-otel-toy`. You should see a three-leaf trace tree (`stage:validate`, `stage:route`, `stage:ack`). That's the observability spine working.

More development specifics live in [`DEVELOPMENT.md`](DEVELOPMENT.md).

## What to expect right now

This is a **mid-construction project**. The design vault is dense (295 notes, including a full architecture spine, every asset class characterised, every venue categorized, every regulator, and an industry-jargon glossary). The codebase is at task **0.8 of ~150** in the [implementation plan](IMPL/PLAN.md) — bootstrap is mostly done, but the real services (FSM, transport, OMS, validator, market data, venue connectivity) are not yet built.

Concretely:

| Area | State |
|---|---|
| Design vault | ✅ comprehensive — 80+ architecture notes, 50+ glossary entries, asset/venue/regulatory catalogs complete |
| Implementation plan | ✅ 14 phases, ~150 tasks, delegation tiers, loop protocol |
| Monorepo + Gradle/Cargo wiring | ✅ 15 Java modules + 15 Rust crates, dependency layering enforced |
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
