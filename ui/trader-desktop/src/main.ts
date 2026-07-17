/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

import perspective, { type Client, type Table } from "@finos/perspective";
import perspectiveViewer from "@finos/perspective-viewer";
import "@finos/perspective-viewer-datagrid";
import "@finos/perspective-viewer/dist/css/themes.css";
import SERVER_WASM_URL from "@finos/perspective/dist/wasm/perspective-server.wasm?url";
import VIEWER_WASM_URL from "@finos/perspective-viewer/dist/wasm/perspective-viewer.wasm?url";
import { ResumableStream, type StreamStatus } from "./stream";
import { ApiClient, initTicket, type WorkingOrder } from "./ticket";
import { attachMultiBadge } from "./aggregates";
import { initDocking } from "./docking";
import { nameOf, nameOfSync, withInstrument } from "./instruments";
import { attachColumnControls, setLayoutScope } from "./layout";
import { reportStreamStatus } from "./recovery";
import { initRfq } from "./rfq";
import { attachGridInteractions, type GridRow } from "./grid-actions";

const perspectiveReady = Promise.all([
  perspective.init_server(fetch(SERVER_WASM_URL)),
  perspectiveViewer.init_client(fetch(VIEWER_WASM_URL)),
]);

type Schema = Record<string, "string" | "float" | "integer" | "datetime" | "boolean">;

function schemaTable(worker: Client, schema: Schema, index: string): Promise<Table> {
  return worker.table(schema as unknown as Record<string, unknown[]>, { index });
}

const PRICE_SCALE = 10_000;
const SIDES: Record<number, string> = { 1: "BUY", 2: "SELL", 5: "SELL SHORT" };

const ORDERS_SCHEMA = {
  orderId: "string", name: "string", assetClass: "string", issuer: "string",
  ccy: "string", settleCcy: "string", clOrdId: "string", figi: "string",
  side: "string", qty: "float", px: "float", avgPx: "float",
  cumQty: "float", leavesQty: "float", account: "string",
  state: "string", subState: "string", tif: "string", ordType: "string",
  version: "integer", ts: "datetime",
} as const;

const ROUTES_SCHEMA = {
  routeId: "string", name: "string", assetClass: "string", issuer: "string",
  ccy: "string", settleCcy: "string", orderId: "string", clOrdId: "string",
  venueMic: "string", figi: "string", side: "string", qty: "float",
  px: "float", avgPx: "float", cumQty: "float", leavesQty: "float",
  state: "string", ts: "datetime",
} as const;

const FILLS_SCHEMA = {
  execId: "string", name: "string", assetClass: "string", issuer: "string",
  ccy: "string", settleCcy: "string", routeId: "string", orderId: "string",
  venueMic: "string", figi: "string", side: "string", lastQty: "float",
  lastPx: "float", ts: "datetime",
} as const;

const BASKETS_SCHEMA = {
  basketId: "string", name: "string", orders: "integer", qty: "float",
  cumQty: "float", leavesQty: "float", pct: "float", filled: "integer", waves: "integer",
} as const;

const PNL_SCHEMA = {
  key: "string", account: "string", figi: "string", name: "string", ccy: "string",
  netQty: "float", avgCost: "float", markPx: "float", markSource: "string",
  realized: "float", unrealized: "float", total: "float",
} as const;

const WATCHLIST_SCHEMA = {
  figi: "string", name: "string", bid: "float", ask: "float", last: "float",
  volume: "float", ts: "datetime",
} as const;

type WireRow = Record<string, unknown>;

function common(row: WireRow): { side: string; ts: Date } {
  return {
    side: SIDES[row.side as number] ?? `SIDE-${row.side}`,
    ts: new Date((row.ts as number) / 1_000),
  };
}

function px(value: unknown): number | null {
  return typeof value === "number" ? value / PRICE_SCALE : null;
}

function orderRow(row: WireRow): WireRow {
  return { ...row, ...common(row), px: px(row.px), avgPx: px(row.avgPx) };
}
function routeRow(row: WireRow): WireRow {
  return { ...row, ...common(row), px: px(row.px), avgPx: px(row.avgPx) };
}
function fillRow(row: WireRow): WireRow {
  return { ...row, ...common(row), lastPx: px(row.lastPx) };
}
function basketRow(row: WireRow): WireRow {
  return { ...row, pct: (row.pctFilledBp as number) / 100 };
}
function pnlRow(row: WireRow): WireRow {
  const realized = px(row.realized) ?? 0;
  const unrealized = px(row.unrealized);
  return {
    ...row, avgCost: px(row.avgCost), markPx: px(row.markPx),
    realized, unrealized, total: realized + (unrealized ?? 0),
  };
}

function setChip(id: string, status: StreamStatus): void {
  const chip = document.getElementById(id)!;
  chip.classList.remove("live", "reconnecting", "down");
  if (status === "live") chip.classList.add("live");
  else if (status === "reconnecting" || status === "connecting") chip.classList.add("reconnecting");
  else chip.classList.add("down");
  reportStreamStatus(id, status);
}

interface Logon { sessionId: number; firm: string; desk: string; user: string; }

async function logon(token: string): Promise<Logon> {
  const response = await fetch("/api/v1/logon", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
  const body = (await response.json()) as Partial<Logon> & { error?: string };
  if (!response.ok || body.sessionId === undefined) {
    throw new Error(body.error ?? `logon failed (${response.status})`);
  }
  return body as Logon;
}

interface Blotter {
  topic: string; chip: string; viewer: string; table: Table;
  transform: (row: WireRow) => WireRow;
  sort: [string, "desc" | "asc"][];
  columns?: string[];
  expressions?: Record<string, string>;
  aggregates?: Record<string, Aggregate>;
}

type Aggregate = string | [string, string[]];

const SAME_OR_MULTI = [
  "name", "assetClass", "issuer", "ccy", "settleCcy", "side", "px", "state", "subState", "account",
  "orderId", "routeId", "venueMic", "tif", "ordType", "ts", "figi", "clOrdId",
];
function tradingAggregates(extra: Record<string, Aggregate>): Record<string, Aggregate> {
  const aggregates: Record<string, Aggregate> = {};
  for (const column of SAME_OR_MULTI) aggregates[column] = "unique";
  return {
    ...aggregates, qty: "sum", cumQty: "sum", leavesQty: "sum",
    avgPx: ["weighted mean", ["cumQty"]], ...extra,
  };
}

const SIGNED = `if("side" == 'BUY', "qty", -"qty")`;
const BLOTTER_EXPRESSIONS = {
  signedQty: SIGNED,
  notional: `if("assetClass" == 'FX' or "assetClass" == 'RATES_DERIVATIVE', ${SIGNED}, "px" * (${SIGNED}))`,
};

type ViewerEl = HTMLElement & {
  load(table: Table): Promise<void>; restore(config: object): Promise<void>;
};

const viewerBase = new Map<string, { viewer: ViewerEl; config: Record<string, unknown> }>();

const REQUIRED_COLUMNS: Record<string, string[]> = {
  "orders-viewer": ["orderId"],
  "routes-viewer": ["routeId", "orderId"],
  "fills-viewer": ["routeId"],
};

async function applyFilter(viewerId: string, filter: unknown[][]): Promise<void> {
  const base = viewerBase.get(viewerId);
  if (base) await base.viewer.restore({ ...base.config, filter });
}

const watchedSet = new Set<string>();
const liveOrders = new Map<string, WorkingOrder>();
const TERMINAL_STATES = new Set(["FILLED", "CANCELED", "REJECTED", "EXPIRED", "DONE_FOR_DAY"]);

async function startWatchlist(session: Logon, worker: Client): Promise<void> {
  const table = await schemaTable(worker, WATCHLIST_SCHEMA, "figi");
  const viewer = document.getElementById("watchlist-viewer") as HTMLElement & {
    load(table: Table): Promise<void>; restore(config: object): Promise<void>;
  };
  await viewer.load(table);
  const wlConfig = {
    plugin: "Datagrid", theme: "Pro Dark", sort: [["name", "asc"]],
    columns: ["name", "bid", "ask", "last", "volume", "figi", "ts"],
  };
  await viewer.restore(wlConfig);
  attachColumnControls("watchlist-viewer", ["figi"], wlConfig, () => {});
  document.getElementById("watchlist-title")!.textContent =
    `WATCHLIST — ${session.desk.toUpperCase()}`;

  const watched = watchedSet;
  const images = new Map<string, WireRow>();

  new ResumableStream(
    `watchlist.${session.desk}`, session.sessionId,
    (event) => {
      const figi = event.refId;
      if (event.type === "WatchRow" && !watched.has(figi)) {
        watched.add(figi);
        images.set(figi, { figi });
        void nameOf(figi).then((name) => table.update([{ figi, name }]));
      } else if (event.type === "WatchRemoved" && watched.has(figi)) {
        watched.delete(figi);
        images.delete(figi);
        void table.remove([figi]);
      }
    },
    () => {},
  ).connect();

  new ResumableStream(
    "md", session.sessionId,
    (event) => {
      if (event.type !== "MdTick" || !watched.has(event.refId)) return;
      const tick = JSON.parse(event.payload) as WireRow;
      const image = {
        ...images.get(event.refId), figi: event.refId,
        name: nameOfSync(event.refId),
        bid: px(tick.bid) ?? undefined, ask: px(tick.ask) ?? undefined,
        last: px(tick.last) ?? undefined,
        volume: typeof tick.volume === "number" ? tick.volume : undefined,
        ts: new Date(tick.ts as number),
      };
      images.set(event.refId, image);
      void table.update([image]);
    },
    (status) => setChip("chip-md", status),
  ).connect();
}

function trackWorkingOrder(row: WireRow): void {
  const orderId = row.orderId as string;
  const state = row.state as string;
  if (TERMINAL_STATES.has(state)) liveOrders.delete(orderId);
  else liveOrders.set(orderId, {
    orderId, clOrdId: row.clOrdId as string, figi: row.figi as string, state,
    qty: row.qty as number,
    px: typeof row.px === "number" ? (row.px as number) / PRICE_SCALE : null,
  });
}

function toast(message: string, ok: boolean): void {
  const el = document.getElementById("toast")!;
  el.textContent = message;
  el.className = ok ? "ok" : "err";
  el.classList.add("show");
  setTimeout(() => el.classList.remove("show"), 4_000);
}

let sharedApi: ApiClient | null = null;
let sharedSession: Logon | null = null;
function apiFor(session: Logon): ApiClient {
  sharedSession = session;
  sharedApi ??= new ApiClient(session.sessionId);
  return sharedApi;
}

function wireBlotterLinking(api: ApiClient): void {
  const linkToggle = document.getElementById("link-toggle") as HTMLInputElement;
  const routesChip = document.getElementById("routes-linkchip")!;
  const fillsChip = document.getElementById("fills-linkchip")!;

  function clearRoutesLink(): void {
    routesChip.textContent = "";
    routesChip.classList.add("hidden");
    void applyFilter("routes-viewer", []);
    clearFillsLink();
  }
  function clearFillsLink(): void {
    fillsChip.textContent = "";
    fillsChip.classList.add("hidden");
    void applyFilter("fills-viewer", [["routeId", "==", "∅"]]);
  }
  routesChip.addEventListener("click", clearRoutesLink);
  fillsChip.addEventListener("click", clearFillsLink);

  const batch = (verb: string, op: string, items: object[], skipped = 0) =>
    api.operation(op, items).then((results) => {
      const rejected = results.filter((r) => r.status !== "ACCEPTED");
      const ok = results.length - rejected.length;
      const skippedNote = skipped > 0 ? ` (${skipped} skipped: not in an applicable state)` : "";
      if (rejected.length === 0) {
        toast(`${verb}: ${ok}/${results.length} accepted${skippedNote}`, true);
      } else {
        const first = rejected[0];
        toast(
          `${verb}: ${ok}/${results.length} accepted${skippedNote} — ${first.errorCode}: ${first.errorMessage}` +
            (rejected.length > 1 ? ` (+${rejected.length - 1} more)` : ""),
          false,
        );
      }
    }).catch((e: Error) => toast(`${verb} failed: ${e.message}`, false));

  const ORDER_TERMINAL = (row: GridRow) => TERMINAL_STATES.has(row.state as string);

  const fmtPx = (v: unknown) => (typeof v === "number" ? (v / PRICE_SCALE).toFixed(4) : "—");
  const fmtTs = (micros: number) => new Date(micros / 1_000).toLocaleTimeString();
  interface HistoryEvent { kind: "ORDER" | "ROUTE" | "FILL"; asOf: number; row: Record<string, unknown>; }
  function historyLine(event: HistoryEvent): string {
    const r = event.row;
    if (event.kind === "FILL") return `filled ${r.lastQty} @ ${fmtPx(r.lastPx)} on ${r.venueMic} (${r.execId})`;
    if (event.kind === "ROUTE") return `${String(r.state).toLowerCase()} → ${r.venueMic} · cum ${r.cumQty}/${r.qty}` +
      (r.avgPx != null ? ` · avg ${fmtPx(r.avgPx)}` : "") + ` (${r.routeId})`;
    return `${r.state}·${r.subState} · cum ${r.cumQty}/${r.qty}` +
      (r.avgPx != null ? ` · avg ${fmtPx(r.avgPx)}` : "") + ` · v${r.version}`;
  }
  function showHistory(kind: "orders" | "routes", row: GridRow): void {
    const id = String(kind === "orders" ? row.orderId : row.routeId);
    const modal = document.getElementById("history-modal")!;
    const list = document.getElementById("history-list")!;
    document.getElementById("history-title")!.textContent = `HISTORY — ${row.name ?? ""} ${id}`;
    list.replaceChildren();
    modal.classList.remove("hidden");
    fetch(`/api/v1/${kind}/${encodeURIComponent(id)}/history`, {
      headers: { "X-EMS-Session": String(sharedSession!.sessionId) },
    }).then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status))))).then((body: { events: HistoryEvent[] }) => {
      for (const event of body.events) {
        const li = document.createElement("li");
        const ts = document.createElement("span"); ts.className = "h-ts"; ts.textContent = fmtTs(event.asOf);
        const badge = document.createElement("span"); badge.className = `h-kind ${event.kind}`; badge.textContent = event.kind;
        const what = document.createElement("span"); what.className = "h-what"; what.textContent = historyLine(event);
        li.append(ts, badge, what);
        list.append(li);
      }
      if (body.events.length === 0) list.textContent = "no recorded events";
    }).catch((e: Error) => { list.textContent = `history unavailable: ${e.message}`; });
  }
  document.getElementById("history-close")!.addEventListener("click", () => {
    document.getElementById("history-modal")!.classList.add("hidden");
  });
  document.getElementById("history-modal")!.addEventListener("click", (e) => {
    if (e.target === e.currentTarget) document.getElementById("history-modal")!.classList.add("hidden");
  });

  attachGridInteractions("orders-viewer", {
    keyField: "orderId",
    chip: document.getElementById("orders-selchip")!,
    onSelection: (rows) => {
      if (!linkToggle.checked || rows.length === 0) return;
      const orderIds = rows.map((r) => r.orderId as string);
      routesChip.textContent = rows.length === 1
        ? `⛓ ${rows[0].name ?? orderIds[0]} ✕` : `⛓ ${rows.length} orders ✕`;
      routesChip.classList.remove("hidden");
      void applyFilter("routes-viewer", [["orderId", "in", orderIds]]);
      clearFillsLink();
    },
    rowLabel: (row) => `${row.name ?? row.figi} ${row.orderId}`,
    actions: [
      {
        label: (n) => `Mark ready (${n})`,
        applicable: (r) => r.subState === "NEW" || r.subState === "STAGED",
        run: (rows) => batch("READY", "mark_ready", rows.map((r) => ({ orderId: r.orderId }))),
      },
      {
        label: (n) =>
          `Route remaining to ${(document.getElementById("tk-venue") as HTMLSelectElement).value} (${n})`,
        applicable: (r) =>
          !ORDER_TERMINAL(r) && (r.leavesQty as number) > 0 &&
          (r.subState === "READY" || r.subState === "ROUTING"),
        run: (rows) => batch("ROUTE", "route_orders", rows.map((r) => ({
          orderId: r.orderId,
          venueMic: (document.getElementById("tk-venue") as HTMLSelectElement).value,
          qty: r.leavesQty,
        }))),
      },
      {
        label: (n) => `Cancel (${n})`,
        applicable: (r) => !ORDER_TERMINAL(r),
        run: (rows) => {
          if (!window.confirm(`Cancel ${rows.length} order(s)?`)) return;
          batch("CANCEL", "cancel_orders", rows.map((r) => ({ orderId: r.orderId })));
        },
      },
      {
        label: () => "View history",
        run: (rows) => showHistory("orders", rows[0]),
      },
      {
        label: (n) => `Aggregate ${n} into a basket…`,
        applicable: (r) => !ORDER_TERMINAL(r),
        run: (rows) => {
          const name = prompt("Basket name:", "from-selection");
          if (!name) return;
          return api.createBasketFromOrders(name, rows.map((r) => r.orderId as string))
            .then((basketId) => toast(`Basket ${basketId} created (${rows.length} orders)`, true))
            .catch((e: Error) => toast(`Basket failed: ${e.message}`, false));
        },
      },
    ],
  });

  attachGridInteractions("routes-viewer", {
    keyField: "routeId",
    chip: document.getElementById("routes-selchip")!,
    onSelection: (rows) => {
      if (!linkToggle.checked || rows.length === 0) return;
      const routeIds = rows.map((r) => r.routeId as string);
      fillsChip.textContent = rows.length === 1 ? `⛓ ${routeIds[0]} ✕` : `⛓ ${rows.length} routes ✕`;
      fillsChip.classList.remove("hidden");
      void applyFilter("fills-viewer", [["routeId", "in", routeIds]]);
    },
    rowLabel: (row) => `${row.name ?? ""} ${row.routeId}`,
    actions: [
      {
        label: (n) => `Cancel route (${n})`,
        applicable: (r) => r.state === "WORKING" || r.state === "PARTIALLY_FILLED",
        run: (rows) => {
          if (!window.confirm(`Cancel ${rows.length} route(s)?`)) return;
          batch("CANCEL ROUTE", "cancel_routes", rows.map((r) => ({ routeId: r.routeId })));
        },
      },
      { label: () => "View history", run: (rows) => showHistory("routes", rows[0]) },
    ],
  });

  wireWatchlistManagement();
}

function wireWatchlistManagement(): void {
  const input = document.getElementById("wl-add") as HTMLInputElement;
  const session = sharedSession!;
  const headers = { "Content-Type": "application/json", "X-EMS-Session": String(session.sessionId) };

  input.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    const figi = input.value.trim();
    if (!figi) return;
    void fetch(`/api/v1/watchlist/${encodeURIComponent(session.desk)}`, {
      method: "POST", headers, body: JSON.stringify({ figi }),
    }).then(async (r) => {
      const body = (await r.json()) as { added?: boolean; error?: string };
      if (r.ok && body.added) { toast(`Watching ${figi}`, true); input.value = ""; }
      else toast(body.error ?? `already watching ${figi}`, false);
    }).catch((e: Error) => toast(`watch failed: ${e.message}`, false));
  });

  attachGridInteractions("watchlist-viewer", {
    keyField: "figi",
    rowLabel: (row) => String(row.name ?? row.figi),
    chip: document.getElementById("wl-selchip")!,
    actions: [
      {
        label: (n) => `Remove from watchlist (${n})`,
        run: (rows) => {
          if (!window.confirm(`Remove ${rows.length} item(s) from watchlist?`)) return;
          Promise.all(rows.map((r) =>
            fetch(
              `/api/v1/watchlist/${encodeURIComponent(session.desk)}/${encodeURIComponent(String(r.figi))}`,
              { method: "DELETE", headers },
            ),
          )).then((rs) =>
            toast(`Removed ${rs.filter((r) => r.ok).length}/${rows.length} from watchlist`, true),
          );
        },
      },
    ],
  });
}

function startKillSwitch(session: Logon, api: ApiClient): void {
  const banner = document.getElementById("kill-banner")!;
  const bannerText = document.getElementById("kill-banner-text")!;
  const engaged = new Map<string, string>();

  function render(): void {
    if (engaged.size === 0) { banner.classList.add("hidden"); return; }
    bannerText.textContent = `KILL ENGAGED — ${[...engaged.entries()]
      .map(([scope, detail]) => `${scope} (${detail})`).join(" · ")}`;
    banner.classList.remove("hidden");
  }

  new ResumableStream(
    "control.kill", session.sessionId,
    (event) => {
      const payload = JSON.parse(event.payload) as Record<string, unknown>;
      const scope = `${payload.scopeKind}:${payload.scopeValue}`;
      if (event.type === "KillEngage" || event.type === "KillDisconnect") {
        if (event.type === "KillEngage") engaged.set(scope, `${payload.reason} — ${payload.by}`);
      } else if (event.type === "KillRelease") { engaged.delete(scope); }
      render();
    },
    () => {},
  ).connect();

  document.getElementById("kill-btn")!.addEventListener("click", () => {
    const reason = prompt(`ENGAGE FIRM-WIDE KILL for ${session.firm}?\nReason (required):`);
    if (!reason) return;
    if (!window.confirm(`Engage firm-wide kill for ${session.firm}? This is irreversible.`)) return;
    api.kill("kill", "FIRM", session.firm, reason)
      .then((r) => alert(`Kill engaged. ${r.targets} targets, ${r.failures} failures.`))
      .catch((e: Error) => alert(e.message));
  });

  document.getElementById("kill-release")!.addEventListener("click", () => {
    const reason = prompt("Release reason (required):");
    if (!reason) return;
    if (!window.confirm(`Release firm-wide kill for ${session.firm}?`)) return;
    api.kill("kill/release", "FIRM", session.firm, reason)
      .catch((e: Error) => alert(e.message));
  });
}

function startNotifications(session: Logon): void {
  const list = document.getElementById("notify-list")!;
  new ResumableStream(
    `notify.${session.desk}`, session.sessionId,
    (event) => {
      const row = JSON.parse(event.payload) as Record<string, unknown>;
      const item = document.createElement("li");
      item.className = `sev-${row.severity as string}`;
      const meta = document.createElement("span"); meta.className = "nt-meta";
      meta.textContent = `${row.severity} ${row.subject}${(row.count as number) > 1 ? ` ×${row.count}` : ""}`;
      const body = document.createElement("span"); body.className = "nt-body";
      body.textContent = row.body as string; body.title = row.body as string;
      item.append(meta, body);
      if (row.ackRequired) {
        const ackButton = document.createElement("button");
        ackButton.textContent = "ACK";
        ackButton.addEventListener("click", () => {
          void fetch(`/api/v1/notifications/${row.notificationId as string}/ack`, {
            method: "POST", headers: { "X-EMS-Session": String(session.sessionId) },
          }).then((r) => { if (r.ok) { item.classList.add("acked"); ackButton.remove(); } });
        });
        item.append(ackButton);
      }
      list.prepend(item);
      while (list.children.length > 100) list.lastChild?.remove();
    },
    () => {},
  ).connect();
}

function startEsp(session: Logon): void {
  const tiles = document.getElementById("esp-tiles")!;
  const tileByFigi = new Map<string, { bid: HTMLElement; ask: HTMLElement; qty: HTMLInputElement; guard: HTMLInputElement; result: HTMLElement; px: { bid: number; ask: number } }>();

  function ensureTile(figi: string): NonNullable<ReturnType<typeof tileByFigi.get>> {
    let tile = tileByFigi.get(figi);
    if (tile) return tile;
    const root = document.createElement("div"); root.className = "esp-tile";
    const pair = document.createElement("div"); pair.className = "esp-pair"; pair.textContent = figi;
    void nameOf(figi).then((name) => { pair.textContent = name; });
    const sellButton = document.createElement("button"); sellButton.className = "esp-sell";
    sellButton.innerHTML = `<span class="esp-label">SELL (BID)</span><span class="esp-px">—</span>`;
    const buyButton = document.createElement("button"); buyButton.className = "esp-buy";
    buyButton.innerHTML = `<span class="esp-label">BUY (ASK)</span><span class="esp-px">—</span>`;
    const controls = document.createElement("div"); controls.className = "esp-controls";
    const qty = document.createElement("input"); qty.type = "number";
    // TODO(config): source the default click-to-trade size from firm/desk config, not a literal.
    qty.value = "1000000"; qty.title = "Quantity";
    const guard = document.createElement("input"); guard.type = "number"; guard.value = "5"; guard.title = "Max slippage (bp)";
    controls.append(qty, guard);
    const result = document.createElement("div"); result.className = "esp-result";
    root.append(pair, sellButton, buyButton, controls, result);
    tiles.append(root);
    tile = {
      bid: sellButton.querySelector(".esp-px")!, ask: buyButton.querySelector(".esp-px")!,
      qty, guard, result, px: { bid: 0, ask: 0 },
    };
    tileByFigi.set(figi, tile);

    // L3-6: in-flight lock — both buttons are disabled the instant either is clicked so a
    // double-click (or a click on BUY+SELL together) can never yield a second POST while the
    // first order is still outstanding; they re-enable only once the request settles either way.
    const click = (side: number, expected: () => number) => {
      sellButton.disabled = true;
      buyButton.disabled = true;
      fetch("/api/v1/esp/click", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-EMS-Session": String(session.sessionId) },
        body: JSON.stringify({
          figi, side, qty: Number(tile!.qty.value),
          expectedPx: expected(), maxSlippageBp: Number(tile!.guard.value),
        }),
      }).then((r) => r.json()).then((body: Record<string, unknown>) => {
        const ok = body.status === "FILLED";
        tile!.result.className = `esp-result ${ok ? "ok" : "err"}`;
        tile!.result.textContent = ok
          ? `FILLED ${body.qty} @ ${(body.px as number) / PRICE_SCALE} on ${body.venueMic} (accept ${(body.venueAcceptRateBp as number) / 100}%)`
          : `${body.reason}: ${body.detail}`;
      }).catch((error: unknown) => {
        // L3-3: no silent failure — a dead/half-open backend must never leave the trader
        // guessing whether a (possibly large) order reached the market.
        tile!.result.className = "esp-result err";
        const message = error instanceof Error ? error.message : String(error);
        tile!.result.textContent = `ESP click failed: ${message}`;
      }).finally(() => {
        sellButton.disabled = false;
        buyButton.disabled = false;
      });
    };
    sellButton.addEventListener("click", () => click(2, () => tile!.px.bid));
    buyButton.addEventListener("click", () => click(1, () => tile!.px.ask));
    return tile;
  }

  new ResumableStream(
    "esp", session.sessionId,
    (event) => {
      if (event.type !== "EspQuoteRow") return;
      const quote = JSON.parse(event.payload) as Record<string, unknown>;
      const tile = ensureTile(event.refId);
      tile.px.bid = quote.bidPx as number; tile.px.ask = quote.askPx as number;
      tile.bid.textContent = ((quote.bidPx as number) / PRICE_SCALE).toFixed(4);
      tile.ask.textContent = ((quote.askPx as number) / PRICE_SCALE).toFixed(4);
    },
    () => {},
  ).connect();
}

async function start(session: Logon): Promise<void> {
  await perspectiveReady;
  setLayoutScope(session.user, session.desk);
  initDocking(session.user, session.desk);
  const worker = await perspective.worker();
  await startWatchlist(session, worker);
  const apiClient = apiFor(session);
  startKillSwitch(session, apiClient);
  startNotifications(session);
  startEsp(session);
  initTicket(apiClient, () => [...liveOrders.values()], () => [...watchedSet]);
  initRfq(session.sessionId, () => toast("RFQ executed — fill booked to blotter", true));
  const sessionId = session.sessionId;
  const blotters: Blotter[] = [
    {
      topic: "blotter.orders", chip: "chip-orders", viewer: "orders-viewer",
      table: await schemaTable(worker, ORDERS_SCHEMA, "orderId"),
      transform: orderRow, sort: [["ts", "desc"]],
      columns: ["name", "assetClass", "issuer", "side", "qty", "signedQty", "notional", "ordType", "px", "ccy", "avgPx", "cumQty", "leavesQty", "state", "subState", "tif", "account", "orderId", "ts"],
      expressions: BLOTTER_EXPRESSIONS,
      aggregates: tradingAggregates({ signedQty: "sum", notional: "sum" }),
    },
    {
      topic: "blotter.routes", chip: "chip-routes", viewer: "routes-viewer",
      table: await schemaTable(worker, ROUTES_SCHEMA, "routeId"),
      transform: routeRow, sort: [["ts", "desc"]],
      columns: ["name", "venueMic", "side", "qty", "notional", "px", "avgPx", "cumQty", "leavesQty", "state", "routeId", "orderId", "ts"],
      expressions: BLOTTER_EXPRESSIONS,
      aggregates: tradingAggregates({ signedQty: "sum", notional: "sum" }),
    },
    {
      topic: "blotter.fills", chip: "chip-fills", viewer: "fills-viewer",
      table: await schemaTable(worker, FILLS_SCHEMA, "execId"),
      transform: fillRow, sort: [["ts", "desc"]],
      columns: ["name", "venueMic", "side", "lastQty", "lastPx", "ts"],
      aggregates: tradingAggregates({ lastQty: "sum", lastPx: ["weighted mean", ["lastQty"]] }),
    },
    {
      topic: "blotter.baskets", chip: "chip-fills", viewer: "baskets-viewer",
      table: await schemaTable(worker, BASKETS_SCHEMA, "basketId"),
      transform: basketRow, sort: [["basketId", "asc"]],
    },
    {
      topic: "blotter.pnl", chip: "chip-fills", viewer: "pnl-viewer",
      table: await schemaTable(worker, PNL_SCHEMA, "key"),
      transform: pnlRow, sort: [["account", "asc"]],
      columns: ["account", "name", "netQty", "avgCost", "markPx", "realized", "unrealized", "total"],
    },
  ];

  for (const blotter of blotters) {
    const viewer = document.getElementById(blotter.viewer) as ViewerEl;
    await viewer.load(blotter.table);
    const config: Record<string, unknown> = {
      plugin: "Datagrid", theme: "Pro Dark", sort: blotter.sort,
      ...(blotter.columns ? { columns: blotter.columns } : {}),
      ...(blotter.expressions ? { expressions: blotter.expressions } : {}),
      ...(blotter.aggregates ? { aggregates: blotter.aggregates } : {}),
      ...(blotter.topic === "blotter.fills" ? { filter: [["routeId", "==", "∅"]] } : {}),
    };
    viewerBase.set(blotter.viewer, { viewer, config });
    await viewer.restore(config);
    if (blotter.aggregates) attachMultiBadge(blotter.viewer, new Set(SAME_OR_MULTI));
    attachColumnControls(
      blotter.viewer, REQUIRED_COLUMNS[blotter.viewer] ?? [], config,
      (updated) => viewerBase.set(blotter.viewer, { viewer, config: updated }),
    );

    const stream = new ResumableStream(
      blotter.topic, sessionId,
      (event) => {
        if (!event.type.endsWith("Row")) return;
        const wire = JSON.parse(event.payload) as WireRow;
        if (blotter.topic === "blotter.orders") trackWorkingOrder(wire);
        const row = blotter.transform(wire);
        if (typeof row.figi === "string") void withInstrument(row).then((named) => blotter.table.update([named]));
        else void blotter.table.update([row]);
      },
      (status) => setChip(blotter.chip, status),
    );
    stream.connect();
  }

  wireBlotterLinking(apiFor(session));
}

const form = document.getElementById("logon-form") as HTMLFormElement;
let logonInflight = false;
form.addEventListener("submit", (event) => {
  event.preventDefault();
  if (logonInflight) return;
  logonInflight = true;
  const token = (document.getElementById("token") as HTMLInputElement).value.trim();
  const error = document.getElementById("logon-error")!;
  error.textContent = "";
  logon(token).then((session) => {
    sessionStorage.setItem("ems-token", token);
    document.getElementById("logon")!.classList.add("hidden");
    document.getElementById("app")!.classList.remove("hidden");
    document.getElementById("session-label")!.textContent =
      `${session.user} @ ${session.firm}/${session.desk} · SESSION ${session.sessionId}`;
    return start(session);
  }).catch((cause: Error) => { error.textContent = cause.message; })
    .finally(() => { logonInflight = false; });
});

const savedToken = sessionStorage.getItem("ems-token");
if (savedToken) {
  (document.getElementById("token") as HTMLInputElement).value = savedToken;
  form.requestSubmit();
  const retry = setInterval(() => {
    if (!document.getElementById("app")!.classList.contains("hidden")) { clearInterval(retry); return; }
    form.requestSubmit();
  }, 3_000);
}
