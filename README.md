# Cross-Asset EMS

A green-field institutional Execution Management System built on a **cross-asset architecture** —
one platform designed to carry cash equity, fixed income, FX, rates, credit, commodities and
crypto. Java + C++ on SBE/Aeron with full event sourcing, a single distributed trace ID through
every order, and **byte-identical replay** of any log slice. Phase 18 adds the trader-facing
desktop: a Perspective (WASM) blotter, ticket, baskets, click-to-trade, and a kill-switch-guarded
order path.

**Runtime maturity today: cash equity and FX are trading-complete; fixed income has a live
venue/post-trade edge with equity-shaped order entry; rates is partial; credit, commodities and
crypto are architecture + schema (no live order path yet).** See the
[asset-class maturity matrix](#asset-class-maturity) below — the design vault describes all seven,
the runtime does not yet, and this README distinguishes the two on purpose.

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

## Current status (2026-07-17)

The 195-task plan is code-complete and the full suite is green, but "code-complete" is measured
per task and means *built and unit-tested* — **not** *wired into the live order path*. This table
distinguishes the two honestly, because an institutional evaluation will. Legend:

- ✅ **wired** — instantiated from a live composition root (`TraderDesktopEdgeMain` / the FIX Mains) and exercised end-to-end.
- 📚 **library** — built and unit-tested, but **not reachable from any runtime** (no `Main` constructs it). Real code, not yet a running feature.
- 🚧 **partial** — wired but incomplete (e.g. equity-shaped for a non-equity class).
- 🗺️ **roadmap** — architecture/schema only; no implementation.

| Area | State |
|---|---|
| Design vault — 80+ architecture notes, asset/venue/regulatory catalogs | ✅ |
| Core spine: FSMs, Aeron/SBE transport, event sourcing, reference data, AAA, validator | ✅ |
| OMS: staged orders, router, automation, multi-leg, aggregation, FX netting | ✅ |
| Edges: client + venue FIX gateways, typed API surface, REST/WS binding, bulk CSV I/O, FIX venue simulator | ✅ |
| Pre-trade: compliance gate (rate limiter, restricted/allow/watch lists), override desk (four-eyes), positions, pricing, analytics, borrow/locate (Reg SHO) | 🚧 opt-in (default off)¹ |
| Pre-trade: fat-finger / erroneous-order notional + price-band check | ✅ (wired into the gate)¹ |
| Pre-trade: credit & capital limits (RiskEngine pre-trade check) | 📚 not yet wired to the order path |
| Post-trade: allocation + client drop-copy (live off the fill stream) | ✅ |
| Post-trade: STP, confirmation, regulatory reporting, TRACE-mock | 📚 reachable only from the FIX-wire smoke, not the live edge |
| Routing intelligence: RFQ-to-N workflow | ✅ |
| Routing intelligence: SOR, algo wheel, sweep, broker algos (FIXatdl) | 📚 live edge routes single-destination |
| Regulatory: CAT submission, commissions/fees/net-money, TCA + best-ex audit, surveillance feed | 📚 no live order/fill event reaches these |
| Regulatory: jurisdiction router (MiFID RTS 22), IOI, quote multicast | 📚 not wired |
| 15c3-5 market-access attestation pack — REST-exported | ✅ status **derived from live wiring** (implemented controls attest IMPLEMENTED; unwired controls attest DEFERRED with rationale)¹ |
| End-to-end proof: FIX-wire + cross-asset smoke, single trace ID, byte-identical replay | ✅ (exercises equity, fixed income, FX, listed derivatives — 4 families²) |
| Ops: introspection, admin console, blue/green switchover, cluster lease, failover drills | ✅ |
| **Trader desktop**: Perspective blotter/watchlist/ticket/baskets/P&L/notifications, ESP click-to-trade, kill switch, maker-checker, SSO/SCIM, dockable layout, audit-trail viewer, runnable demo | ✅ |
| Real venue adapters (11.3–11.14); internal market-data fabric (Phase 9) | 🚧 |

¹ Delivered by the compliance-enforcement PR (`fix/compliance-on-by-default`); see [`AUDIT_2026-07-17.md`](AUDIT_2026-07-17.md) L1.
² Four asset *families* span seven instrument *labels*; the "seven asset classes" figure counts the design target, not the runtime proof (see matrix).

<a name="asset-class-maturity"></a>

### Asset-class maturity

Built from the runtime code (not the design vault). "Validation" = asset-class-specific rules
enforced at order time; today the validator's asset-class layer is a stub for **all** classes
(the per-class rule files are loaded only by a consistency test), so no row claims it yet.

| Class | Instrument model | Order entry / OMS | Venue connectivity | Pricing / MD | Overall |
|---|---|---|---|---|---|
| **Cash equity** | ✅ | ✅ | ✅ | ✅ | **trading-complete** |
| **FX** (spot) | ✅ identity | 🚧 no tenor/value-date field at entry | ✅ | 🚧 scalar quote | **spot usable; fwd/swap/NDF not** |
| **Fixed income** | 🚧 identity only (no coupon/maturity in the security master) | 🚧 equity-shaped scalar (no yield/dirty/accrued at entry) | ✅ (MarketAxess is a mock) | 📚 post-trade only | **partial** |
| **Rates** (IRS) | 🚧 fixed rate stuffed into the price scalar | 🚧 no leg economics | 🚧 generic SEF tags | 🗺️ no curve/DV01 | **partial** |
| **Credit** (CDS) | 🗺️ enum + uncompiled SBE schema | 🗺️ | 🗺️ | 🗺️ | **roadmap** |
| **Commodities** | 🗺️ enum + uncompiled SBE schema | 🗺️ | 🗺️ | 🗺️ | **roadmap** |
| **Crypto** | 🗺️ enum + uncompiled SBE schema | 🗺️ | 🗺️ | 🗺️ | **roadmap** |

A production-readiness audit of the gap between claims and runtime — and the prioritized fix
program closing it — is in **[`AUDIT_2026-07-17.md`](AUDIT_2026-07-17.md)**.

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
