# Trader Desktop — Guided Demo

A runnable, end-to-end demonstration of the Phase 18 trader desktop: how to spin the system up,
how the UI and the backend interact, and how to operate it from the screen. Written 2026-06-11
(task 18.15) and verified live against the exact commands below.

Companion documents: [`USER_GUIDE.md`](USER_GUIDE.md) (the whole system),
[`OPERATIONS.md`](OPERATIONS.md) (ops/runbooks), [`ui/trader-desktop/README.md`](../ui/trader-desktop/README.md)
(desktop internals).

---

## 1. What you are about to run

Two processes, one command:

| Half | What it is | Port |
|---|---|---|
| **Demo edge** (`TraderDesktopEdgeMain`, Java) | The real stack in one JVM: AAA-backed validator, kill-switch-guarded OMS, blotter projection, market-data SPI with a simulated feed, EURUSD ESP dealer, baskets, notifications, intraday P&L — plus a scripted demo bot that stages/routes/fills orders continuously so every panel moves on its own | REST `:8484`, WS `:8485` |
| **Desktop** (`ui/trader-desktop`, Perspective WASM) | The trader UI — built assets served by Vite preview, proxying `/api` and `/ws` to the edge so the browser sees one origin, exactly like production behind a gateway | `:5173` |

**Real vs simulated.** The order path (validator → staged orders → routes → fills → blotter
projection), the kill switch, baskets, notifications, approvals, P&L math, and every stream are
the production components. Simulated: market data (`SimulatedFeed` random walk), the venue (fills
are scripted, not a real exchange), and the ESP dealer (`MockEspVenue`). Identity is a bootstrap
token credential — production logon would ride SSO (18.9).

## 2. Spin it up

Prerequisites: Java 21 (`./scripts/dev/bootstrap.sh` installs it) and Node.js with `npm`.

```bash
./scripts/dev/run-trader-demo.sh
```

The script installs UI dependencies on first run, starts both halves, waits until each one
actually serves, and prints the banner. Then:

1. Open **http://localhost:5173**
2. Log on with token **`trader-token`**

To reach the UI from another machine (e.g. you're SSH'd into a server), run the launcher with
`--host` — it binds the UI on all interfaces and prints the LAN URLs. Only port 5173 needs to be
reachable: the UI proxies `/api` and `/ws` to the edge server-side, so 8484/8485 stay private.
(Demo credentials only — don't expose it on untrusted networks. Over SSH, a port forward works
with no exposure at all: `ssh -L 5173:localhost:5173 <server>`.)

Ctrl-C in the script's terminal stops everything. To run the halves by hand instead:

```bash
./gradlew :ems-fix-bridge:runTraderEdge                            # terminal 1
cd ui/trader-desktop && npm install && npm run build && npm run preview   # terminal 2 (built assets)
# (npm run dev also works, with hot reload, for hacking on the UI itself)
```

> Prefer watching first? A recorded run + frame-by-frame tour: [`../DEMO.md`](../DEMO.md). Re-record
> with `node ui/trader-desktop/demo/record-demo.mjs` while the stack is up (Playwright Chromium).

## 3. How the UI and backend interact

```
 Browser (Perspective WASM tables)
   │  one origin: http://localhost:5173
   ▼
 Vite dev server ──── /api/* ───▶ RestEdgeBinding (HTTP :8484)   ── actions, lookups
   │                                   │ POST /api/v1/{operation} → ApiSurface (8.4)
   └──── /ws/*  ────▶ WsEventStreamServer (RFC 6455 :8485)       ── all live data
                                       │ one socket per topic
                                       ▼
                          SubscriptionRegistry topics (cursor-resumable)
```

Two channels, strict division of labour:

- **Actions go over REST.** Every mutation is a `POST /api/v1/{operation}` batch envelope with a
  `requestId` (idempotency key) and a per-session `sessionSeq` (gap-checked). The ticket, baskets,
  kill switch, ESP clicks, and notification acks all go this way.
- **Data arrives over WebSocket.** The desktop opens one socket per topic
  (`/ws/events?session=&topic=&from=`). The server replays buffered events from the client's
  cursor, then streams live. Rows are **keyed deltas** — Perspective tables are indexed by
  `orderId`/`routeId`/`execId`/`figi`, and `table.update()` merges by key; the UI never re-renders
  a full table.

Topics the desktop consumes:

| Topic | Rows | Feeds |
|---|---|---|
| `blotter.orders` / `blotter.routes` / `blotter.fills` | full order/route/fill images after every applied mutation | the three blotter grids |
| `md` (+ `md.{figi}`) | tick deltas from the 18.12 feed SPI | watchlist grid |
| `watchlist.{desk}` | the desk's symbol set (`WatchRow`/`WatchRemoved`) | watchlist membership |
| `blotter.baskets` | basket rollups (qty/cum/%/waves), republished on every constituent event | BASKETS grid |
| `blotter.pnl` | per-position P&L rows + `TOTAL`, snapshotted every 2s | INTRADAY P&L grid |
| `esp` | executable EURUSD quotes from the dealer | CLICK TO TRADE tiles |
| `notify.{desk}` | routed notifications | NOTIFICATIONS queue |
| `control.kill` | kill engage/release audits | red banner |

**Resume contract** (worth demonstrating — see §4.9): the client owns its cursor. Every delivered
event advances `lastSeq`; a reconnect asks for `from = lastSeq + 1`. The server keeps no
per-client state, so a refreshed tab rebuilds the exact image with no missed and no doubled rows.

## 4. Operating it — the walkthrough

Follow these in order; ~10 minutes. Keep the script's terminal visible — `[EMAIL]`/`[SMS]` sink
lines print there.

### 4.1 Log on

Token `trader-token` → `POST /api/v1/logon` returns `{sessionId, firm, desk, user}`. The header
shows `trader-1 @ firm-demo/desk-1 · SESSION n`, the chips (MARKET DATA / ORDERS / ROUTES / FILLS)
go green as each WS topic connects, and every grid back-fills from topic seq 1 — the blotter you
see is a replayed projection, not a snapshot endpoint.

### 4.2 Read the screen

Left column: **ORDER TICKET**, **CLICK TO TRADE**, **WATCHLIST**, **NOTIFICATIONS**. Right:
**ORDER BLOTTER** on top; **ROUTES / FILLS / BASKETS / INTRADAY P&L** below. The panels are
**dockable, VSCode-style** (18.24): drag any tab to split, stack, or rearrange; drag splitters to
resize. The arrangement persists per user/desk; **⟲ LAYOUT** in the top bar resets the default.
(Stacked-away grids paint when their tab activates — Perspective renders only visible viewers.) The demo bot stages,
routes and drip-fills orders continuously — rows appear, `cumQty`/`leavesQty` count up, states walk
`NEW → ROUTING → PARTIALLY_FILLED → FILLED`. Rows show the **security name** everywhere (resolved
from the security master and cached); the FIGI is demoted to an optional column.

Every panel is a Perspective viewer: **click a column header to sort**, and hit **⚙ COLUMNS** in
any grid header (18.23) for the full picker — drag columns in/out, *Group By / Split By / Sort /
Filter* — the grid keeps updating live while pivoted (that is the point of Perspective). Your
layout **persists per user/desk** and survives reloads; **⟲** resets the grid to its default.
Interaction-key columns (orderId/routeId/figi) are re-asserted automatically — hiding them would
break right-click menus and linking. To add a NEW column to a blotter there is one place to edit:
the grid's schema in `ui/trader-desktop/src/main.ts` (plus the server row payload if it's new
data) — the picker offers it from then on.

**Linked blotter (the LINK toggle in the ORDER BLOTTER header, on by default):** click an order →
ROUTES filters to that order (a ⛓ chip shows the link; click it to clear). Click a route → FILLS
reveals that route's fills. **Fills are hidden until a route is selected** — fill volume is the
render cost on a busy desk, so nothing paints until you ask.

**Row actions:** click selects a row, **ctrl/cmd+click multi-selects** (the header shows
"N selected"), and **right-click opens the action menu** for the selection — Mark ready, Route
remaining to the ticket's venue, Cancel, and *Aggregate N into a basket…* on orders; Cancel route
on routes. Results land as a toast, and the rows update through the stream like any other action.

### 4.3 Watchlist + the cross-asset universe

Eighteen instruments tick several times a second (bid/ask/last/volume) — the demo universe is
**cross-asset** (18.21), at least one US and one international name per supported class, each
trading in its natural unit and routing to class-appropriate venues. Securities carry their
**issuer** (18.29): group the order blotter by `issuer` and Microsoft's stock, 0% 2030
convertible and Sep26 450 call collapse into one capital-structure node (Apple likewise with its
3.45% '29 bond); FX/IRS/index futures have no single issuer and bucket under blank:

| Class | US | International | Qty unit | Venues |
|---|---|---|---|---|
| Equity | Apple, Microsoft | Toyota (JPY) | shares ×100 | XNAS/XNYS/ARCX, XTKS |
| Govt bonds | UST 4.25% 2035 | UK Gilt 4.5% 2034 (GBP) | $1k face ×100 | BTEC/DWFI, TWEU |
| Corp credit | Apple 3.45% 2029 | Volkswagen 4.125% 2031 (EUR) | $1k face ×100 | MKAX, MAEL |
| FX | EUR/USD spot | USD/JPY 1M fwd | 100k notional | FXAL, EBSX |
| Listed deriv | E-mini S&P Sep26 | EURO STOXX 50 Sep26 (EUR) | contracts | XCME, XEUR |
| Rates (IRS) | USD SOFR 5Y | EUR €STR 5Y | 1M notional | TWSD, BGCD |

(Defined in `DemoUniverse.java`; non-equity FIGIs are synthetic demo identifiers. IRS "price" is
the fixed rate in percent.) Currency roles (18.30, see [[currency-in-execution]]) are visible in
the ticket and the `ccy`/`settleCcy` columns: EUR/USD quotes USD-per-EUR and settles both legs,
the Microsoft JPY samurai trades/settles JPY under a US issuer, the Toyota ADR trades USD beside
its JPY XTKS line, and Shell quotes in GBp pence (P&L converts at the pence rate, not 100x off). Non-USD P&L converts to the USD base via demo FX rates. The order
blotter shows the `assetClass` column, resolved from `GET /api/v1/instruments/{figi}` and cached
like security names.

Per-desk watchlist membership streams on
`watchlist.desk-1`; ticks stream on the `md` firehose and are filtered client-side to that set,
merged into a per-FIGI last-value image before `table.update`.

### 4.4 Work an order through the ticket

1. **FIGI**: type `BBG000B9XRY4` (or pick from the datalist). Two calls fire as you type:
   `GET /api/v1/instruments/{figi}` (name + asset class drive the field labels — equity shows
   QTY (SHARES) / LIMIT PX; an FI instrument would show NOTIONAL / CLEAN PRICE) and
   `POST /api/v1/preview_validate` — a **server-side dry run of the same validator the stage path
   uses**. "validator: pass" in green is the server speaking, not client-side validation. Type a
   wrong FIGI and the catalog reject (`EMS-REF-2001` + admin hint) renders inline.
2. **STAGE**: qty 500, price 182.45, account `ACC-UI` → `POST /api/v1/stage_orders`. The result
   line shows the assigned order ID, and the same order arrives in the blotter via the stream —
   the round trip you just watched is REST in, WebSocket out.
3. **Working-order actions**: pick your order in the dropdown (live, fed from the blotter
   stream) → **READY** (`mark_ready`), then **ROUTE** to XNAS (`route_orders`) — a `SENT` row
   appears in ROUTES. **AMEND** changes qty/px pre-route; **CANCEL** cancels. Fills on demo-bot
   orders keep flowing around yours; INFO fill notifications collapse with a ×count (throttling).

### 4.4½ Request for quote (RFQ)

Bonds and ETF blocks are **RFQ-traded** (11.18): look one up in the ticket (try the Apple '29
corp `BBG00DEMOC29` or the SPDR ETF `BBG000BDTBL9`) and **REQUEST QUOTES** arms. Firing it
solicits the demo dealer panel and renders the **quote ladder** — competing quotes sorted
best-first for your side, each with dealer, price, FIRM/LAST LOOK badge, and a live countdown:

- the **best eligible** quote is highlighted (green border, ACCEPT button);
- **AXES** quotes tightest but shows greyed **NOT ELIGIBLE** — your account lacks the dealer
  relationship; the better price you can't access stays visible on purpose (onboarding action);
- **FADE** demonstrates last look: accept it and the quote fades — the RFQ drops back to ACTIVE,
  the faded quote is demoted, and you elect another;
- accepting a firm quote **books through the same OMS path as any order** — the fill lands in
  the blotter, P&L and notifications like every other execution.

Auto-execution exists at the API level (`autoEx` + `maxSpreadBp` on `POST /api/v1/rfq/request`):
the best eligible quote inside the spread bar executes with no trader interaction.

### 4.5 Baskets / program trading

1. In the ticket's **BASKET / PROGRAM** section: name `walkthrough`, choose
   [`ui/trader-desktop/demo/sample-basket.csv`](../ui/trader-desktop/demo/sample-basket.csv)
   (4 rows), **LOAD BASKET** → `POST /api/v1/baskets` runs the 8.6 CSV importer; accepted rows
   become staged orders (they appear in the blotter) and the basket lands in the BASKETS grid.
2. Select the basket, WAVE % = 25, **ROUTE WAVE** → `POST /api/v1/baskets/{id}/wave`. The server
   slices 25% of each constituent's *unrouted remaining* (mark-readying NEW names on the way) and
   routes each slice — 4 new rows in ROUTES, and the basket's `waves` and `pct` tick in the grid
   as fills arrive. Wave again at 100% to take the rest.

### 4.6 Click-to-trade (ESP)

The EURUSD tile updates ~2×/second from the dealer stream. SELL/BUY show the live bid/ask.

- Click **BUY (ASK)** with qty 1,000,000 and guard 5bp → `POST /api/v1/esp/click` carries the
  price you saw (`expectedPx`). Normally: `FILLED … on LMAX (accept 100%)` — the dealer's running
  last-look accept rate on every fill.
- Set the guard to **1bp**, wait for a visible tick, click — you'll often get
  `SLIPPAGE_GUARD: price moved Nbp … not sent to venue`: the EMS refused locally **before the
  dealer ever saw the order**. That is the slippage guard + last-look awareness pair from 18.11.

### 4.7 Intraday P&L

The INTRADAY P&L grid re-snapshots every 2s from the demo bot's fills: per-position rows carry
`markSource` (`LIVE:live-l1-mid` while ticks are fresh — the 10.8 fallback chain's provenance),
realized/unrealized, and a `TOTAL (USD)` row.

### 4.8 Notifications

The NOTIFICATIONS queue shows INFO fills (throttle-collapsed) and HIGH/CRITICAL alerts with an
**ACK** button → `POST /api/v1/notifications/{id}/ack`. Unacked alerts escalate per policy
(email/SMS sinks print in the edge terminal). Every dispatch/delivery/ack is journaled.

### 4.9 The kill switch drill (do this last)

1. Press **KILL** in the header, reason `walkthrough drill` → `POST /api/v1/kill
   {kind:FIRM, value:firm-demo}`. The alert reports targets and failures — lockout engages
   *before* mass-cancel, and partial-cancel failures (orders racing their own fills) are counted
   and audited, never silent. Working orders flip to `CANCELED`; routes to
   `PENDING_CANCEL_AT_VENUE`; the red banner shows the engaged scope; a CRITICAL notification
   arrives (ack it).
2. Try to **STAGE** from the ticket → `EMS-ORD-9601: Kill switch engaged — order entry is locked
   out. Cancels remain allowed.` The demo bot is locked out too (the blotter goes quiet).
3. **RELEASE** on the banner with a reason → order entry resumes, the bot picks back up. Both
   actions are in the audit journal with who/scope/reason/outcomes — the 15c3-5 evidence pack
   (18.5) exports exactly this.

### 4.10 Resilience: refresh the tab

Hard-refresh the browser (F5), log on again: every grid rebuilds to the identical image — the
blotter is a projection replayed from topic cursors (§3). The chips also tell the truth: stop the
edge (Ctrl-C the script) with the tab open and they turn amber (RECONNECTING) with exponential
backoff. Note: the demo edge is in-memory, so restarting *it* starts a fresh world by design;
production durability is the event-sourced spine (Phase 3), not this demo JVM.

## 5. UI action → backend call reference

| You do | The desktop calls | Inside the edge | You see (via stream) |
|---|---|---|---|
| Log on | `POST /api/v1/logon` | AAA token logon → session | header identity; chips green; grids back-fill |
| Type a FIGI | `GET /api/v1/instruments/{figi}` + `POST …/preview_validate` | security master lookup; `LayeredValidatorPipeline` dry-run | per-field hint (server verdict) |
| STAGE | `POST …/stage_orders` | kill-guard → validator → `StagedOrderManager` → blotter publish | `OrderRow` on `blotter.orders` |
| READY / ROUTE / AMEND / CANCEL | `POST …/mark_ready` / `route_orders` / `amend_orders` / `cancel_orders` | OMS managers behind the same guards | order/route rows update |
| LOAD BASKET | `POST /api/v1/baskets` | 8.6 CSV importer stages rows; basket registered | orders + `BasketRow` rollup |
| ROUTE WAVE | `POST /api/v1/baskets/{id}/wave` | slices unrouted-remaining per name via `RouteManager` | route rows + rollup `waves`/`pct` |
| Click BUY/SELL tile | `POST /api/v1/esp/click` | EMS slippage guard → dealer last look | fill/reject + venue accept-rate |
| ACK a notification | `POST /api/v1/notifications/{id}/ack` | ack journaled; escalation stops | row dims |
| KILL / RELEASE | `POST /api/v1/kill` / `…/kill/release` | tag check → lockout → audited mass-cancel | banner, `CANCELED` rows, CRITICAL alert |
| (everything live) | `GET ws://…/ws/events?topic=…&from=` | `SubscriptionRegistry` replay + live | keyed row deltas into Perspective |

## 6. No browser? Same demo over curl

Start only the edge (`./gradlew :ems-fix-bridge:runTraderEdge`), then:

```bash
S=$(curl -s -X POST localhost:8484/api/v1/logon -d '{"token":"trader-token"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["sessionId"])')
H="X-EMS-Session: $S"

curl -s localhost:8484/api/v1/instruments/BBG000B9XRY4                       # lookup
curl -s -X POST localhost:8484/api/v1/preview_validate -H "$H" \
  -d '{"requestId":"c1","sessionSeq":1,"items":[{"figi":"BBG000B9XRY4"}]}'   # validator dry-run
curl -s -X POST localhost:8484/api/v1/stage_orders -H "$H" \
  -d '{"requestId":"c2","sessionSeq":2,"items":[{"clOrdId":"CURL-1","figi":"BBG000B9XRY4","side":1,"qty":500,"price":1824500,"account":"ACC-UI","tif":0}]}'
curl -s "localhost:8484/api/v1/events?topic=blotter.orders&from=1&max=5"     # the stream, as cursor fetch
curl -s -X POST localhost:8484/api/v1/kill -H "$H" \
  -d '{"kind":"FIRM","value":"firm-demo","reason":"curl drill"}'             # kill drill
curl -s -X POST localhost:8484/api/v1/kill/release -H "$H" \
  -d '{"kind":"FIRM","value":"firm-demo","reason":"done"}'
```

`GET /api/v1/events` is the same resumable stream the WS carries, as a polling fetch — handy for
scripting and for seeing exactly what the UI consumes (prices are fixed-point ×10⁴; timestamps
epoch micros on blotter rows, millis on md/esp).

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `port 8484/8485/5173 already in use` | a previous demo is still up — `pkill -f TraderDesktopEdgeMain`, `pkill -f vite` |
| Edge slow to start first time | cold Gradle compile; the script waits up to 60s — watch the printed edge log |
| `Spotless JVM-local cache is stale` on unrelated gradle/git work | `rm -rf .gradle/configuration-cache` (known spotless issue #987) |
| Scripted WS clients can't connect to `127.0.0.1:5173` | Vite may bind IPv6 — use `localhost` (the browser handles this itself) |
| Desktop loads but grids never populate | Perspective's WASM must be explicitly booted (`init_server`/`init_client` with `?url` assets in `src/main.ts`) — if you fork the boot code, keep that block; without it `perspective.worker()` waits forever |
| Logon fails | token is exactly `trader-token`; check the edge log for the banner |
| Grids empty after edge restart | expected — in-memory demo world resets; refresh the tab and log on again |

## 8. Where to go next

- Desktop internals + stream/resume code: [`ui/trader-desktop/README.md`](../ui/trader-desktop/README.md)
- The full system (FIX edges, replay determinism, cross-asset smokes): [`USER_GUIDE.md`](USER_GUIDE.md)
- Kill switch / approvals / 15c3-5 evidence: `io.crossasset.ems.api.control` + `MarketAccessPackTest`
- The plan behind all of this: [`IMPL/PLAN.md`](../IMPL/PLAN.md) Phase 18
