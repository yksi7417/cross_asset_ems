# Testing Strategy

How this system is tested, what goes in which layer, and the decision rules for mocking versus
running the real thing. Shape: the **test trophy** — a broad middle of fast API-level workflow
tests over a foundation of unit tests, capped by a deliberately small set of browser E2E specs.

```
        ▲  Manual / showcase        the recorded demo (bot on) — NOT an assertion surface
       ▲▲  UI E2E (Playwright)      8 tests · one spec per trader workflow · quiet edge
   ▲▲▲▲▲▲  API workflow contracts   REST over the full wiring · pins codes the UI renders
 ▲▲▲▲▲▲▲▲  Unit / module            ~1,800 Java tests · in-memory fakes · per-module gradle
```

## The layers

### 1. Unit / module tests (the foundation)

`./gradlew allTests` — every Java module, in-memory collaborators, no I/O. This is where
business logic lives (FSM transitions, allocation math, locate sourcing, kill mass-cancel
accounting…). **Default home for any new logic test.**

### 2. API workflow-contract tests (the broad middle — prefer these)

`UiWorkflowContractTest` and siblings (`TicketSupportTest`, `WsEventStreamServerTest`,
`BasketServiceTest`, …) run **real HTTP against `RestHttpServer`** with the full demo-edge wiring
(validator, guards, blotter projection, registry) but in-process and clock-controlled. They pin
the *contract the UI depends on* — above all the **exact catalog codes** for every failure path
the desktop must render (`EMS-ORD-3001` cancel-terminal, `EMS-RTE-4002` not-ready, `EMS-RTE-4003`
beyond-remaining, `EMS-ORD-9601` kill lockout, `EMS-SES-1002` bad session, watchlist 409/404,
ESP `SLIPPAGE_GUARD`, double-ack 409…).

API tests are **fast (~ms each), robust, and parallel-safe** — when a behavior can be asserted
here, it must be asserted here, not in a browser. QA round 1 (docs/QA_REVIEW.md) showed why: every
S1 defect was information *lost between a correct server and the screen*; the contract test pins
the server side cheaply, leaving the browser test only the rendering half.

### 3. UI end-to-end (small, one spec per workflow)

```bash
cd ui/trader-desktop
npx playwright install chromium     # once per machine
npm run test:e2e                    # boots the QUIET edge + built UI itself (webServer config)
```

Eight tests in `tests/e2e/` — exactly one spec per trader workflow, asserting what the **user
sees**: stream chips going live and refresh-resume; ticket preview/stage/amend-prefill and a
catalog error rendered in the result line; order→routes linking and fills-on-demand; multi-select
context-menu cancel with per-item outcome toasts and state-disabled entries; basket aggregation
from a selection; an ESP click with an attributable outcome; the kill drill (banner →
`EMS-ORD-9601` on the ticket → release → ack).

Rules that keep this layer trustworthy:

- **They run against the real backend in `--quiet` mode** (`EMS_DEMO_QUIET=1`): no demo bot, so
  the world only contains what the test seeded — **through the same API the UI uses**
  (`ApiSeeder`), never by poking server internals.
- Serial (`workers: 1`): one shared backend world per run.
- Grid assertions: wide grids may assert row text; **narrow grids virtualize columns
  horizontally** — assert by row count or by interaction result (chips/toasts), not by off-screen
  cell text. (Interactions are safe regardless: `perspective-click` rows carry all *configured*
  columns.)

### 4. The demo (not a test)

`./scripts/dev/run-trader-demo.sh` with the scripted bot is the showcase and manual-exploration
surface. It is intentionally non-deterministic — record videos and explore there; **never assert
against it**.

## Mock vs real — the decision rules

| Situation | Use | Why |
|---|---|---|
| Business logic, state machines, math | Unit test, in-memory fakes | fastest feedback, no transport noise |
| Anything expressible as request→response/code | **API contract test, real wiring, no mocks** | the real components are cheap here; mocks would just re-state assumptions |
| User-visible behavior (rendering, linking, menus, toasts) | Playwright vs **quiet real edge**, data seeded via API | keeps the UI↔server contract honest end to end |
| States unreachable through the public API (5xx, malformed payloads, transport drops) | Playwright `page.route()` network mock — **the only sanctioned browser-mock case** | the real edge can't be asked to misbehave on demand |
| Vendor boundaries (Bloomberg BLPAPI, real venues) | Seam + scripted fake (e.g. `BlpapiDriver`/`MockEspVenue`) in unit/contract layers | licensed/external systems can't run in CI |
| Cross-process/wire proof (FIX session, replay determinism) | `ems-it` smokes (`FixWireSmokeTest`, `MvpSmokeTest`, `CrossAssetSmokeTest`) | the one place real sockets/replay matter |

Heuristic: **push every assertion down to the cheapest layer that can falsify it.** A browser test
that re-checks an error code is waste; an API test that re-checks pixel behavior is impossible.
Each UI defect class found in QA gets one E2E pin (the rendering) plus one API pin (the data).

## Where things live

| What | Where | Run |
|---|---|---|
| Unit/module | `java/*/src/test` | `./gradlew allTests` |
| API workflow contracts | `java/ems-fix-bridge/src/test/.../rest/` | `./gradlew :ems-fix-bridge:test` |
| Wire/business smokes | `java/ems-it/src/test` | `./gradlew :ems-it:test` |
| UI E2E | `ui/trader-desktop/tests/e2e` | `npm run test:e2e` (boots its own stack) |
| Layer-by-layer build diagnostics | [`DIAGNOSTICS.md`](DIAGNOSTICS.md) | per layer |
| QA findings driving all this | [`QA_REVIEW.md`](QA_REVIEW.md) | — |

CI ordering: gradle suites first (cheap, catch most), then `npm run test:e2e` (Playwright caches
Chromium per runner; the webServer config builds the UI and boots the quiet edge itself).
