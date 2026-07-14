# Cross-Asset EMS

A green-field institutional Execution Management System: every asset class through one platform —
cash equity, fixed income, FX, rates, credit, commodities, crypto. Java + C++ on SBE/Aeron with
full event sourcing, a single distributed trace ID through every order, and **byte-identical
replay** of any log slice. Phase 18 adds the trader-facing desktop: a Perspective (WASM) blotter,
ticket, baskets, click-to-trade, and the attestable controls a desk buys.

**Demo: [`DEMO.md`](DEMO.md)** (26-second video + frame-by-frame tour) ·
Walkthrough: [`docs/TRADER_DESKTOP_DEMO.md`](docs/TRADER_DESKTOP_DEMO.md) ·
Plan: [`IMPL/PLAN.md`](IMPL/PLAN.md) · Design vault: [`00_index/HOME.md`](00_index/HOME.md)

---

## 5-minute quick start: run the demo

What you'll get: the real backend (AAA-backed validator, kill-switch-guarded OMS, streaming
blotter projection, simulated market data, an ESP dealer) plus the trader desktop, with a
scripted trading session making every panel move.

```bash
# 1. Prerequisites: Java 21 (installed for you) and Node.js with npm
git clone git@github.com:yksi7417/cross_asset_ems.git
cd cross_asset_ems
./scripts/dev/bootstrap.sh        # installs Java 21 if missing; Gradle wrapper is committed

# 2. One command — starts the backend edge and the desktop, waits for both
./scripts/dev/run-trader-demo.sh             # add --host to reach it from other machines
```

Then open **<http://localhost:5173>** and log on with token **`trader-token`**. Work an order
through the ticket, load the sample basket and route a wave, click-trade the EURUSD stream, and
fire the kill-switch drill — the guided tour is
[`docs/TRADER_DESKTOP_DEMO.md`](docs/TRADER_DESKTOP_DEMO.md), and
[`DEMO.md`](DEMO.md) shows the recorded run if you'd rather watch first. Ctrl-C stops everything.

Contributing? Also run `./scripts/dev/install-hooks.sh` (Conventional Commits + secret guard).

---

## Verify the build, layer by layer

The full diagnostic ladder — toolchain → build → FSM definitions → unit suites (~1,750 tests) →
Aeron transport smoke → observability stack + OTel toy → FIX-wire smoke → cross-asset
end-to-end smokes → the live demo — lives in **[`docs/DIAGNOSTICS.md`](docs/DIAGNOSTICS.md)**,
with one command per layer and what healthy looks like. The two you'll use most:

```bash
./gradlew assemble    # everything compiles (-Werror, spotless)
./gradlew allTests    # every module's tests
```

The containerized observability stack (Jaeger/Prometheus/Grafana/OpenSearch) and its OTel toy
are optional for app work and documented in the same file — the demo does not need them.

---

## Current status (2026-07-14)

**Plan fully complete — 195/195 tasks (100%).** MVP v0, Phase 16 cross-asset coverage, the v1
build-out, Phase 18 (trader desktop & buyer-readiness), and the regulatory/routing-intelligence
batches (CAT, commissions, TCA, surveillance, drop-copy, SOR/algo wheel, RFQ) are all done; the
full suite is green. An overnight fix-factory also runs against the repo, opening gated,
mechanically-verified PRs for coverage gaps and previously-unwired components — every such PR
carries its own gate evidence (build, tests, effectiveness, no-regression) in the description.

| Area | State |
|---|---|
| Design vault — 80+ architecture notes, asset/venue/regulatory catalogs | ✅ |
| Core spine: FSMs, Aeron/SBE transport, event sourcing, reference data, AAA, validator | ✅ |
| OMS: staged orders, router, automation, multi-leg, aggregation, FX netting | ✅ |
| Edges: client + venue FIX gateways, typed API surface, REST/WS binding, bulk CSV I/O, FIX venue simulator | ✅ |
| Pre-trade: compliance gate (rate limiter, restricted/allow/watch lists, fat-finger), override desk (four-eyes release/deny over REST), positions, risk, pricing, analytics, borrow/locate (Reg SHO) | ✅ |
| Post-trade: allocation (event-sourced, live off the fill stream), STP, confirmation, regulatory reporting, TRACE-mock | ✅ |
| Routing intelligence: SOR, algo wheel, RFQ-to-N workflow, broker algos (FIXatdl) | ✅ |
| Regulatory: CAT submission, commissions/fees, TCA + best-ex audit, surveillance feed, client drop-copy | ✅ |
| 15c3-5 market-access attestation pack — REST-exported, evidence pulled from the live services | ✅ |
| End-to-end proof: 7 asset classes, single trace ID, byte-identical replay; FIX-wire smoke | ✅ |
| Ops: introspection, admin console, blue/green switchover, cluster lease, failover drills | ✅ |
| **Trader desktop**: Perspective blotter/watchlist/ticket/baskets/P&L/notifications, ESP click-to-trade, kill switch, maker-checker, SSO/SCIM, dockable VSCode-style layout, audit-trail viewer, runnable demo + video | ✅ |
| Post-MVP: real venue adapters (11.3–11.14); internal market-data fabric (Phase 9, deferred by design) | 🚧 |

---

## Docs

| | |
|---|---|
| **[DEMO.md](DEMO.md)** | The recorded demo — video + frame-by-frame tour |
| **[docs/TRADER_DESKTOP_DEMO.md](docs/TRADER_DESKTOP_DEMO.md)** | Guided walkthrough — spin-up, UI↔backend interaction, operating every panel |
| **[docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md)** | Verify each layer of the build (incl. the OTel toy + dev stack) |
| **[docs/USER_GUIDE.md](docs/USER_GUIDE.md)** | The whole running system — FIX edges, replay, smokes, APIs |
| **[docs/OPERATIONS.md](docs/OPERATIONS.md)** | Operator guide — deploys, drills, runbooks |
| **[SETUP.md](SETUP.md)** | Full setup — all platforms (macOS, dev container, Obsidian, Tailscale) |
| **[DEVELOPMENT.md](DEVELOPMENT.md)** | Build commands, project structure, coding rules, CI overview |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | How to contribute — task workflow, the build loop, commit conventions |
| **[KNOWLEDGE_BASE.md](KNOWLEDGE_BASE.md)** | Design knowledge base — architecture, asset classes, venues, regulatory |
| **[00_index/HOME.md](00_index/HOME.md)** | Design vault entry point — navigate the architecture spine |
| **[IMPL/PLAN.md](IMPL/PLAN.md)** | Implementation task queue + current goal |

---

## Licence

Apache 2.0.
