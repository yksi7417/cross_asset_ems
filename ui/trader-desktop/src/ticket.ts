/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Order ticket (task 18.2, per the staging-via-ticket workflow note): an asset-class-aware form
// that is a thin client over the 8.4 API surface. Field feedback is server-authoritative —
// PREVIEW_VALIDATE dry-runs the same layered validator the stage path enforces — and every action
// (stage / amend / cancel / mark-ready / route) is a batch envelope POST to /api/v1/{operation}.

import { nameOfSync } from "./instruments";
import { setQuoteStyle } from "./rfq";

/** Fixed-point price scale used across the EMS (4 implied decimals). */
const PRICE_SCALE = 10_000;

const VENUES = ["XNAS", "XNYS", "ARCX"];

const TIFS: [string, number][] = [
  ["DAY", 0],
  ["GTC", 1],
  ["IOC", 3],
  ["FOK", 4],
];

// Currency roles (18.30, [[currency-in-execution]]): the px label names the TRADING currency
// (what the price is quoted in — "GBp" for minor-unit lines, the QUOTE ccy for FX) and the hint
// names the SETTLEMENT currency (what cash moves; FX moves both legs).
const tradingCcy = (i: Instrument) =>
  i.tradingMinorUnit ? `${i.tradingCurrency} pence (GBp)` : (i.tradingCurrency ?? i.currency);
const settleCcy = (i: Instrument) =>
  i.baseCurrency
    ? `both legs ${i.baseCurrency}+${i.quoteCurrency}`
    : (i.settlementCurrency ?? i.currency);

/** Per-asset-class ticket layout: labels and contextual hint line. */
const LAYOUTS: Record<string, { qty: string; px: (i: Instrument) => string; hint: (i: Instrument) => string }> = {
  EQUITY: {
    qty: "QTY (SHARES)",
    px: (i) => `LIMIT PX ${tradingCcy(i)} (BLANK = MARKET)`,
    hint: (i) => `settle ${settleCcy(i)} · ${i.settlement.replaceAll("_", "")}`,
  },
  FIXED_INCOME: {
    qty: "NOTIONAL (FACE)",
    px: (i) => `CLEAN PRICE per 100 (${tradingCcy(i)})`,
    hint: (i) => `settle ${settleCcy(i)} · ${i.settlement.replaceAll("_", "")}`,
  },
  FX: {
    qty: "BASE NOTIONAL",
    px: (i) =>
      i.baseCurrency
        ? `ALL-IN RATE (${i.quoteCurrency} per ${i.baseCurrency})`
        : "ALL-IN RATE",
    hint: (i) => `settle ${settleCcy(i)} · value date per ${i.settlement.replaceAll("_", "")}`,
  },
  DEFAULT: {
    qty: "QUANTITY",
    px: (i) => `PRICE ${tradingCcy(i)} (BLANK = MARKET)`,
    hint: (i) => `${i.assetClass} · settle ${settleCcy(i)}`,
  },
};

interface Instrument {
  figi: string;
  name: string;
  assetClass: string;
  currency: string;
  settlement: string;
  quoteStyle?: string;
  tradingCurrency?: string;
  tradingMinorUnit?: boolean;
  settlementCurrency?: string;
  baseCurrency?: string;
  quoteCurrency?: string;
}

export interface ItemResult {
  status: "ACCEPTED" | "REJECTED" | "DEFERRED";
  refId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface WorkingOrder {
  orderId: string;
  clOrdId: string;
  figi: string;
  state: string;
  qty: number;
  px: number | null;
}

/** Session-sequenced operation client over the REST edge (one instance per logon). */
export class ApiClient {
  private seq = 1;
  private requestN = 1;
  /** Sequenced operations are SERIALIZED (18.18): the channel gap-checks sessionSeq, so two
   *  concurrent fetches (a preview racing a stage) must not arrive out of order. */
  private chain: Promise<unknown> = Promise.resolve();

  constructor(private readonly sessionId: number) {}

  operation(operation: string, items: object[]): Promise<ItemResult[]> {
    const next = this.chain.then(() => this.send(operation, items));
    this.chain = next.catch(() => {}); // a failed op must not poison the queue
    return next;
  }

  private async send(operation: string, items: object[]): Promise<ItemResult[]> {
    const response = await fetch(`/api/v1/${operation}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-EMS-Session": String(this.sessionId) },
      body: JSON.stringify({
        requestId: `ui-${this.sessionId}-${this.requestN++}`,
        sessionSeq: this.seq++,
        items,
      }),
    });
    const body = (await response.json()) as { results?: ItemResult[]; error?: string };
    if (!response.ok || !body.results) {
      throw new Error(body.error ?? `${operation} failed (${response.status})`);
    }
    return body.results;
  }

  async instrument(figi: string): Promise<Instrument | null> {
    const response = await fetch(`/api/v1/instruments/${encodeURIComponent(figi)}`);
    return response.ok ? ((await response.json()) as Instrument) : null;
  }

  /** Basket list-load (18.3): the CSV import consumes one session sequence. */
  async createBasket(name: string, csv: string): Promise<{ accepted: number; rejected: number }> {
    const response = await fetch("/api/v1/baskets", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-EMS-Session": String(this.sessionId) },
      body: JSON.stringify({
        name,
        uploadId: `bk-${this.sessionId}-${this.requestN++}`,
        sessionSeq: this.seq++,
        csv,
      }),
    });
    const body = (await response.json()) as {
      accepted?: number;
      rejected?: number;
      error?: string;
      fileError?: string;
    };
    if (!response.ok) {
      throw new Error(body.fileError ?? body.error ?? `basket load failed (${response.status})`);
    }
    return { accepted: body.accepted ?? 0, rejected: body.rejected ?? 0 };
  }

  /** Aggregate already-staged orders into a basket (context-menu path, 18.17). */
  async createBasketFromOrders(name: string, orderIds: string[]): Promise<string> {
    const response = await fetch("/api/v1/baskets", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-EMS-Session": String(this.sessionId) },
      body: JSON.stringify({ name, orderIds }),
    });
    const body = (await response.json()) as { basketId?: string; error?: string };
    if (!response.ok || !body.basketId) {
      throw new Error(body.error ?? `basket create failed (${response.status})`);
    }
    return body.basketId;
  }

  async listBaskets(): Promise<{ basketId: string; name: string }[]> {
    const response = await fetch("/api/v1/baskets", {
      headers: { "X-EMS-Session": String(this.sessionId) },
    });
    if (!response.ok) {
      return [];
    }
    return ((await response.json()) as { baskets: { basketId: string; name: string }[] }).baskets;
  }

  /** Kill switch (18.4): engage/release; the audit summary is the response. */
  async kill(
    path: "kill" | "kill/release",
    kind: string,
    value: string,
    reason: string,
  ): Promise<{ targets: number; failures: number }> {
    const response = await fetch(`/api/v1/${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-EMS-Session": String(this.sessionId) },
      body: JSON.stringify({ kind, value, reason }),
    });
    const body = (await response.json()) as {
      targets?: number;
      failures?: number;
      error?: string;
    };
    if (!response.ok) {
      throw new Error(body.error ?? `${path} failed (${response.status})`);
    }
    return { targets: body.targets ?? 0, failures: body.failures ?? 0 };
  }

  async wave(
    basketId: string,
    fractionBp: number,
    venueMic: string,
  ): Promise<{ wave: number; lines: { orderId: string; ok: boolean; detail: string }[] }> {
    const response = await fetch(`/api/v1/baskets/${encodeURIComponent(basketId)}/wave`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-EMS-Session": String(this.sessionId) },
      body: JSON.stringify({ fractionBp, venueMic }),
    });
    const body = (await response.json()) as {
      wave?: number;
      lines?: { orderId: string; ok: boolean; detail: string }[];
      error?: string;
    };
    if (!response.ok || body.wave === undefined) {
      throw new Error(body.error ?? `wave failed (${response.status})`);
    }
    return { wave: body.wave, lines: body.lines ?? [] };
  }
}

function el<T extends HTMLElement>(id: string): T {
  return document.getElementById(id) as T;
}

function debounced<A extends unknown[]>(ms: number, fn: (...args: A) => void): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return (...args: A) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

export function initTicket(
  api: ApiClient,
  workingOrders: () => WorkingOrder[],
  watchedFigis: () => string[],
): void {
  const figiInput = el<HTMLInputElement>("tk-figi");
  const figiList = el<HTMLDataListElement>("tk-figi-list");
  const nameLabel = el<HTMLElement>("tk-name");
  const hintLabel = el<HTMLElement>("tk-hint");
  const qtyLabel = el<HTMLElement>("tk-qty-label");
  const pxLabel = el<HTMLElement>("tk-px-label");
  const sideSelect = el<HTMLSelectElement>("tk-side");
  const qtyInput = el<HTMLInputElement>("tk-qty");
  const pxInput = el<HTMLInputElement>("tk-px");
  const tifSelect = el<HTMLSelectElement>("tk-tif");
  const accountInput = el<HTMLInputElement>("tk-account");
  const feedback = el<HTMLElement>("tk-feedback");
  const result = el<HTMLElement>("tk-result");
  const orderSelect = el<HTMLSelectElement>("tk-order");
  const venueSelect = el<HTMLSelectElement>("tk-venue");

  for (const [label, value] of TIFS) {
    tifSelect.add(new Option(label, String(value)));
  }
  for (const venue of VENUES) {
    venueSelect.add(new Option(venue, venue));
  }

  let clOrdN = 1;

  function setFeedback(message: string, ok: boolean): void {
    feedback.textContent = message;
    feedback.className = ok ? "ok" : "err";
  }

  function setResult(message: string, ok: boolean): void {
    result.textContent = message;
    result.className = ok ? "ok" : "err";
  }

  function renderResult(verb: string, results: ItemResult[]): void {
    const r = results[0];
    if (r.status === "ACCEPTED") {
      setResult(`${verb} OK — ${r.refId}`, true);
    } else if (r.errorCode === "EMS-SES-2001") {
      setResult(`${r.errorCode}: session sequence desync — reload the page to re-logon`, false);
    } else {
      setResult(`${r.errorCode}: ${r.errorMessage}`, false);
    }
  }

  // ── Instrument resolution + server-side preview (live as the FIGI changes) ───

  const refresh = debounced(300, () => {
    void (async () => {
      const figi = figiInput.value.trim();
      figiList.replaceChildren(
        ...watchedFigis().map((f) => new Option(f)),
      );
      if (figi.length < 12) {
        nameLabel.textContent = "—";
        setFeedback("", true);
        return;
      }
      const instrument = await api.instrument(figi);
      const layout = instrument
        ? (LAYOUTS[instrument.assetClass] ?? LAYOUTS.DEFAULT)
        : LAYOUTS.DEFAULT;
      nameLabel.textContent = instrument ? instrument.name : "unknown instrument";
      hintLabel.textContent = instrument ? layout.hint(instrument) : "";
      qtyLabel.textContent = layout.qty;
      pxLabel.textContent = instrument
        ? layout.px(instrument)
        : "PRICE (BLANK = MARKET)";
      setQuoteStyle(instrument?.quoteStyle); // 11.18: arm REQUEST QUOTES for RFQ-traded names
      try {
        const results = await api.operation("preview_validate", [{ figi }]);
        const r = results[0];
        setFeedback(
          r.status === "ACCEPTED" ? "validator: pass" : `${r.errorCode}: ${r.errorMessage}`,
          r.status === "ACCEPTED",
        );
      } catch (cause) {
        setFeedback((cause as Error).message, false);
      }
    })();
  });
  figiInput.addEventListener("input", refresh);

  // ── Working-orders dropdown (fed from the live blotter stream) ───────────────

  function refreshOrders(): void {
    const selected = orderSelect.value;
    orderSelect.replaceChildren(
      new Option("— select working order —", ""),
      ...workingOrders().map(
        (o) => new Option(`${nameOfSync(o.figi)} · ${o.state} · ${o.orderId}`, o.orderId),
      ),
    );
    orderSelect.value = selected;
  }
  setInterval(refreshOrders, 1_000);

  // Amend prefill (18.18): selecting a working order loads its qty/px into the ticket so AMEND
  // edits current values instead of silently reusing whatever was last typed for a new order.
  orderSelect.addEventListener("change", () => {
    const order = workingOrders().find((o) => o.orderId === orderSelect.value);
    if (!order) {
      setResult("", true);
      return;
    }
    qtyInput.value = String(order.qty);
    pxInput.value = order.px != null ? String(order.px) : "";
    setResult(`editing ${nameOfSync(order.figi)} ${order.orderId} — qty/px prefilled`, true);
  });

  // ── Actions ──────────────────────────────────────────────────────────────────

  function pxFixedPoint(): number | undefined {
    const raw = pxInput.value.trim();
    return raw === "" ? undefined : Math.round(Number(raw) * PRICE_SCALE);
  }

  async function act(verb: string, run: () => Promise<ItemResult[]>): Promise<void> {
    try {
      renderResult(verb, await run());
    } catch (cause) {
      setResult((cause as Error).message, false);
    }
  }

  el<HTMLButtonElement>("tk-stage").addEventListener("click", () => {
    void act("STAGE", () =>
      api.operation("stage_orders", [
        {
          clOrdId: `TKT-${Date.now()}-${clOrdN++}`,
          figi: figiInput.value.trim(),
          side: Number(sideSelect.value),
          qty: Number(qtyInput.value),
          price: pxFixedPoint(),
          account: accountInput.value.trim(),
          tif: Number(tifSelect.value),
        },
      ]),
    );
  });

  el<HTMLButtonElement>("tk-amend").addEventListener("click", () => {
    void act("AMEND", () =>
      api.operation("amend_orders", [
        {
          orderId: orderSelect.value,
          qty: qtyInput.value ? Number(qtyInput.value) : undefined,
          price: pxFixedPoint(),
        },
      ]),
    );
  });

  el<HTMLButtonElement>("tk-ready").addEventListener("click", () => {
    void act("READY", () => api.operation("mark_ready", [{ orderId: orderSelect.value }]));
  });

  el<HTMLButtonElement>("tk-cancel").addEventListener("click", () => {
    void act("CANCEL", () => api.operation("cancel_orders", [{ orderId: orderSelect.value }]));
  });

  // ── Basket / program (18.3) ──────────────────────────────────────────────────

  const basketResult = el<HTMLElement>("bk-result");
  const basketSelect = el<HTMLSelectElement>("bk-select");

  function setBasketResult(message: string, ok: boolean): void {
    basketResult.textContent = message;
    basketResult.className = ok ? "ok" : "err";
  }

  async function refreshBaskets(): Promise<void> {
    const selected = basketSelect.value;
    const list = await api.listBaskets();
    basketSelect.replaceChildren(
      new Option("— select basket —", ""),
      ...list.map((b) => new Option(`${b.basketId} · ${b.name}`, b.basketId)),
    );
    basketSelect.value = selected;
  }
  setInterval(() => void refreshBaskets(), 2_000);

  el<HTMLButtonElement>("bk-load").addEventListener("click", () => {
    void (async () => {
      const file = el<HTMLInputElement>("bk-file").files?.[0];
      const name = el<HTMLInputElement>("bk-name").value.trim();
      if (!file || !name) {
        setBasketResult("name and CSV file are required", false);
        return;
      }
      try {
        const result = await api.createBasket(name, await file.text());
        setBasketResult(`loaded: ${result.accepted} accepted, ${result.rejected} rejected`, true);
        await refreshBaskets();
      } catch (cause) {
        setBasketResult((cause as Error).message, false);
      }
    })();
  });

  el<HTMLButtonElement>("bk-wave").addEventListener("click", () => {
    void (async () => {
      const basketId = basketSelect.value;
      const pct = Number(el<HTMLInputElement>("bk-wave-pct").value);
      if (!basketId || !(pct > 0 && pct <= 100)) {
        setBasketResult("select a basket and a wave % in (0, 100]", false);
        return;
      }
      try {
        const result = await api.wave(basketId, Math.round(pct * 100), venueSelect.value);
        const bad = result.lines.filter((l) => !l.ok);
        setBasketResult(
          `wave ${result.wave} routed (${result.lines.length - bad.length}/${result.lines.length} ok)` +
            (bad.length ? ` — first issue: ${bad[0].detail}` : ""),
          bad.length === 0,
        );
      } catch (cause) {
        setBasketResult((cause as Error).message, false);
      }
    })();
  });

  el<HTMLButtonElement>("tk-route").addEventListener("click", () => {
    void act("ROUTE", () =>
      api.operation("route_orders", [
        {
          orderId: orderSelect.value,
          venueMic: venueSelect.value,
          qty: Number(qtyInput.value),
          price: pxFixedPoint(),
        },
      ]),
    );
  });
}
