/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Trading blotter (task 18.1): live orders + routes + fills in Perspective (WASM) tables,
// fed by the WS edge as keyed JSON row deltas — table.update() merges by index, never a
// full refresh — per the order-manager workflow note.

import perspective, { type Client, type Table } from "@finos/perspective";
import "@finos/perspective-viewer";
import "@finos/perspective-viewer-datagrid";
import "@finos/perspective-viewer/dist/css/themes.css";
import { ResumableStream, type StreamStatus } from "./stream";

type Schema = Record<string, "string" | "float" | "integer" | "datetime" | "boolean">;

/**
 * Create an empty keyed table from an explicit schema. Perspective 3.8 documents and supports
 * schema construction, but its TS overloads only list data forms — hence the one cast, here.
 */
function schemaTable(worker: Client, schema: Schema, index: string): Promise<Table> {
  return worker.table(schema as unknown as Record<string, unknown[]>, { index });
}

/** Fixed-point price scale used across the EMS (4 implied decimals). */
const PRICE_SCALE = 10_000;

const SIDES: Record<number, string> = { 1: "BUY", 2: "SELL", 5: "SELL SHORT" };

// ── Table schemas (column → Perspective type), keyed by row identity ───────────

const ORDERS_SCHEMA = {
  orderId: "string",
  clOrdId: "string",
  figi: "string",
  side: "string",
  qty: "float",
  px: "float",
  cumQty: "float",
  leavesQty: "float",
  account: "string",
  state: "string",
  subState: "string",
  version: "integer",
  ts: "datetime",
} as const;

const ROUTES_SCHEMA = {
  routeId: "string",
  orderId: "string",
  clOrdId: "string",
  venueMic: "string",
  figi: "string",
  side: "string",
  qty: "float",
  px: "float",
  cumQty: "float",
  leavesQty: "float",
  state: "string",
  ts: "datetime",
} as const;

const FILLS_SCHEMA = {
  execId: "string",
  routeId: "string",
  orderId: "string",
  venueMic: "string",
  figi: "string",
  side: "string",
  lastQty: "float",
  lastPx: "float",
  ts: "datetime",
} as const;

// ── Row transforms: wire JSON → grid row ───────────────────────────────────────

type WireRow = Record<string, unknown>;

function common(row: WireRow): { side: string; ts: Date } {
  return {
    side: SIDES[row.side as number] ?? `SIDE-${row.side}`,
    ts: new Date((row.ts as number) / 1_000), // micros → millis
  };
}

function px(value: unknown): number | null {
  return typeof value === "number" ? value / PRICE_SCALE : null;
}

function orderRow(row: WireRow): WireRow {
  return { ...row, ...common(row), px: px(row.px) };
}

function routeRow(row: WireRow): WireRow {
  return { ...row, ...common(row), px: px(row.px) };
}

function fillRow(row: WireRow): WireRow {
  return { ...row, ...common(row), lastPx: px(row.lastPx) };
}

// ── Wiring ─────────────────────────────────────────────────────────────────────

function setChip(id: string, status: StreamStatus): void {
  const chip = document.getElementById(id)!;
  chip.classList.remove("live", "reconnecting", "down");
  if (status === "live") chip.classList.add("live");
  else if (status === "reconnecting" || status === "connecting") chip.classList.add("reconnecting");
  else chip.classList.add("down");
}

async function logon(token: string): Promise<number> {
  const response = await fetch("/api/v1/logon", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
  const body = (await response.json()) as { sessionId?: number; error?: string };
  if (!response.ok || body.sessionId === undefined) {
    throw new Error(body.error ?? `logon failed (${response.status})`);
  }
  return body.sessionId;
}

interface Blotter {
  topic: string;
  chip: string;
  viewer: string;
  table: Table;
  transform: (row: WireRow) => WireRow;
  sort: [string, "desc" | "asc"][];
}

async function start(sessionId: number): Promise<void> {
  const worker = await perspective.worker();
  const blotters: Blotter[] = [
    {
      topic: "blotter.orders",
      chip: "chip-orders",
      viewer: "orders-viewer",
      table: await schemaTable(worker, ORDERS_SCHEMA, "orderId"),
      transform: orderRow,
      sort: [["ts", "desc"]],
    },
    {
      topic: "blotter.routes",
      chip: "chip-routes",
      viewer: "routes-viewer",
      table: await schemaTable(worker, ROUTES_SCHEMA, "routeId"),
      transform: routeRow,
      sort: [["ts", "desc"]],
    },
    {
      topic: "blotter.fills",
      chip: "chip-fills",
      viewer: "fills-viewer",
      table: await schemaTable(worker, FILLS_SCHEMA, "execId"),
      transform: fillRow,
      sort: [["ts", "desc"]],
    },
  ];

  for (const blotter of blotters) {
    const viewer = document.getElementById(blotter.viewer) as HTMLElement & {
      load(table: Table): Promise<void>;
      restore(config: object): Promise<void>;
    };
    await viewer.load(blotter.table);
    await viewer.restore({ plugin: "Datagrid", theme: "Pro Dark", sort: blotter.sort });

    const stream = new ResumableStream(
      blotter.topic,
      sessionId,
      (event) => {
        if (!event.type.endsWith("Row")) {
          return; // projection rows only (e.g. skip future control events)
        }
        const row = blotter.transform(JSON.parse(event.payload) as WireRow);
        void blotter.table.update([row]);
      },
      (status) => setChip(blotter.chip, status),
    );
    stream.connect();
  }
}

// ── Logon overlay ──────────────────────────────────────────────────────────────

const form = document.getElementById("logon-form") as HTMLFormElement;
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const token = (document.getElementById("token") as HTMLInputElement).value.trim();
  const error = document.getElementById("logon-error")!;
  error.textContent = "";
  logon(token)
    .then((sessionId) => {
      document.getElementById("logon")!.classList.add("hidden");
      document.getElementById("app")!.classList.remove("hidden");
      document.getElementById("session-label")!.textContent = `SESSION ${sessionId}`;
      return start(sessionId);
    })
    .catch((cause: Error) => {
      error.textContent = cause.message;
    });
});
