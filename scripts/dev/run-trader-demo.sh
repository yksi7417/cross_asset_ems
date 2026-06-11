#!/usr/bin/env bash
# One-command trader-desktop demo (task 18.15).
#
# Starts the two halves of the demo and wires them together:
#
#   1. The Java demo edge (TraderDesktopEdgeMain): REST :8484 + WebSocket :8485,
#      backed by the real stack — AAA validator, kill-switch-guarded OMS, blotter
#      projection, simulated market data over the 18.12 SPI, EURUSD ESP dealer,
#      notifications — plus a scripted trading session so every panel moves.
#   2. The Perspective desktop (ui/trader-desktop) on the Vite dev server :5173,
#      which proxies /api and /ws to the edge (one origin, no CORS).
#
# Usage:
#   ./scripts/dev/run-trader-demo.sh           # run; Ctrl-C stops both halves
#
# Then open  http://localhost:5173  and log on with token  trader-token .
# The guided walkthrough lives in docs/TRADER_DESKTOP_DEMO.md.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

die() { echo "ERROR: $*" >&2; exit 1; }

REST_PORT=8484
WS_PORT=8485
UI_PORT=5173

# ── preflight ────────────────────────────────────────────────────────────────

command -v npm >/dev/null || die "npm not found — install Node.js (UI half needs it)"
[ -x ./gradlew ] || die "run from a repo checkout (./gradlew missing)"

for port in "$REST_PORT" "$WS_PORT" "$UI_PORT"; do
    if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
        exec 3>&- 3<&-
        die "port $port already in use — is a previous demo still running?"
    fi
done

if [ ! -d ui/trader-desktop/node_modules ]; then
    echo "── Installing desktop dependencies (first run only)…"
    (cd ui/trader-desktop && npm install --no-audit --no-fund)
fi

# ── start both halves ────────────────────────────────────────────────────────

EDGE_LOG=$(mktemp -t trader-edge.XXXXXX.log)
UI_LOG=$(mktemp -t trader-ui.XXXXXX.log)

cleanup() {
    echo
    echo "── Shutting down…"
    [ -n "${UI_PID:-}" ]   && kill "$UI_PID"   2>/dev/null || true
    [ -n "${EDGE_PID:-}" ] && kill "$EDGE_PID" 2>/dev/null || true
    # Gradle runs the edge in a child JVM; make sure it goes too.
    pkill -f TraderDesktopEdgeMain 2>/dev/null || true
    echo "   logs kept: $EDGE_LOG  $UI_LOG"
}
trap cleanup EXIT INT TERM

echo "── Starting demo edge (REST :$REST_PORT, WS :$WS_PORT)…  log: $EDGE_LOG"
./gradlew :ems-fix-bridge:runTraderEdge -q >"$EDGE_LOG" 2>&1 &
EDGE_PID=$!

# Wait for the REST edge to serve (gradle compile on a cold start can take a bit).
for i in $(seq 1 60); do
    if curl -sf -X POST "localhost:$REST_PORT/api/v1/logon" \
            -d '{"token":"trader-token"}' >/dev/null 2>&1; then
        break
    fi
    kill -0 "$EDGE_PID" 2>/dev/null || { tail -20 "$EDGE_LOG"; die "edge exited early — see $EDGE_LOG"; }
    [ "$i" = 60 ] && { tail -20 "$EDGE_LOG"; die "edge not serving after 60s — see $EDGE_LOG"; }
    sleep 1
done
echo "   edge is up."

echo "── Building + serving the desktop (:$UI_PORT)…  log: $UI_LOG"
(cd ui/trader-desktop && npm run build >"$UI_LOG" 2>&1 \
    && npm run preview -- --port "$UI_PORT" --strictPort >>"$UI_LOG" 2>&1) &
UI_PID=$!

for i in $(seq 1 60); do
    if curl -sf "localhost:$UI_PORT/" >/dev/null 2>&1; then
        break
    fi
    kill -0 "$UI_PID" 2>/dev/null || { tail -20 "$UI_LOG"; die "UI dev server exited early — see $UI_LOG"; }
    [ "$i" = 60 ] && { tail -20 "$UI_LOG"; die "UI not serving after 60s — see $UI_LOG"; }
    sleep 1
done
echo "   desktop is up."

cat <<BANNER

  ┌──────────────────────────────────────────────────────────────┐
  │  Trader desktop demo is running                              │
  │                                                              │
  │    Desktop   http://localhost:$UI_PORT                          │
  │    Logon     token: trader-token                             │
  │                                                              │
  │    REST      http://localhost:$REST_PORT/api/v1                  │
  │    WS        ws://localhost:$WS_PORT/ws/events                   │
  │                                                              │
  │  Walkthrough: docs/TRADER_DESKTOP_DEMO.md                    │
  │  Ctrl-C stops everything.                                    │
  └──────────────────────────────────────────────────────────────┘

BANNER

wait "$UI_PID"
