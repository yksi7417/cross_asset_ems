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
| **Desktop** (`ui/trader-desktop`, Perspective WASM) | The trader UI on the Vite dev server, proxying `/api` and `/ws` to the edge — the browser sees one origin, exactly like production behind a gateway | `:5173` |

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

Ctrl-C in the script's terminal stops everything. To run the halves by hand instead:

```bash
./gradlew :ems-fix-bridge:runTraderEdge          # terminal 1
cd ui/trader-desktop && npm install && npm run dev   # terminal 2
```

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
**ORDER BLOTTER** on top; **ROUTES / FILLS / BASKETS / INTRADAY P&L** below. The demo bot stages,
routes and drip-fills orders continuously — rows appear, `cumQty`/`leavesQty` count up, states walk
`NEW → ROUTING → PARTIALLY_FILLED → FILLED`. Every panel is a Perspective viewer: drag a column
header into "Group By", sort, filter — it keeps updating live while pivoted (that is the point of
Perspective).

### 4.3 Watchlist

Four equities tick several times a second (bid/ask/last/volume). Per-desk membership streams on
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
| Logon fails | token is exactly `trader-token`; check the edge log for the banner |
| Grids empty after edge restart | expected — in-memory demo world resets; refresh the tab and log on again |

## 8. Where to go next

- Desktop internals + stream/resume code: [`ui/trader-desktop/README.md`](../ui/trader-desktop/README.md)
- The full system (FIX edges, replay determinism, cross-asset smokes): [`USER_GUIDE.md`](USER_GUIDE.md)
- Kill switch / approvals / 15c3-5 evidence: `io.crossasset.ems.api.control` + `MarketAccessPackTest`
- The plan behind all of this: [`IMPL/PLAN.md`](../IMPL/PLAN.md) Phase 18
