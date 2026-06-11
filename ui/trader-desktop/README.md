# EMS Trader Desktop

The trader-facing desktop (Phase 18) on [Perspective](https://github.com/finos/perspective)
(WASM streaming-pivot grids). Task 18.1 ships the **trading blotter** — live orders, routes and
fills — streaming from the Java edge as keyed JSON row deltas over WebSocket (never a full
refresh: tables are indexed by `orderId` / `routeId` / `execId` and `table.update()` merges by
key).

## Architecture

```
OMS managers ──(blotter decorators)──▶ SubscriptionRegistry topics      Java (ems-fix-bridge)
                                          blotter.orders|routes|fills
                                          md / md.{figi} (18.12 SPI)
                                                  │
                              WsEventStreamServer (RFC 6455, cursor resume)
                                                  │  one socket per topic
                                          ResumableStream (lastSeq + 1)    this app
                                                  │
                                   Perspective tables (indexed, WASM)
```

The stream is resumable end-to-end: the client owns its cursor (`from = lastSeq + 1` on
reconnect), the server keeps no per-client state, and a refreshed tab rebuilds the same image
from topic seq 1 (the blotter is a projection per the order-manager workflow note).

## Run it

One command from the repo root (starts both halves, waits for health, prints the banner):

```bash
./scripts/dev/run-trader-demo.sh    # http://localhost:5173 — logon token: trader-token
```

**The guided walkthrough — every panel, every backend call — is
[`docs/TRADER_DESKTOP_DEMO.md`](../../docs/TRADER_DESKTOP_DEMO.md).**

Or by hand:

```bash
# 1. The demo edge (REST :8484, WS :8485, scripted order flow + simulated quotes)
./gradlew :ems-fix-bridge:runTraderEdge

# 2. The desktop
cd ui/trader-desktop
npm install
npm run dev          # http://localhost:5173 — logon token: trader-token
```

The Vite dev server proxies `/api` and `/ws` to the Java edge (one origin, no CORS).

## Conventions

- Prices cross the wire as fixed-point longs (4 implied decimals); the UI divides by
  `PRICE_SCALE = 10_000` once, in the row transform.
- Timestamps cross as epoch micros; transformed to `Date` for Perspective `datetime` columns.
- FIX side codes map to labels in one place (`SIDES`).
