/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const FIGI_AAPL = "BBG000B9XRY4";
export const FIGI_MSFT = "BBG000BPH459";

/** Log on through the UI and wait for the streams to come up. */
export async function logonUi(page: Page): Promise<void> {
  await page.goto("/");
  await page.click("#logon-form button");
  await page.waitForSelector("#app:not(.hidden)", { timeout: 20_000 });
  await page.waitForSelector("#chip-orders.live", { timeout: 20_000 });
}

/**
 * API-side seeder with its own session + sequence (tests create their data through the same
 * contract the UI uses — never by poking server internals).
 */
export class ApiSeeder {
  private seq = 1;
  private n = 1;
  private session = 0;

  constructor(private readonly request: APIRequestContext) {}

  async logon(): Promise<void> {
    const response = await this.request.post("http://localhost:8484/api/v1/logon", {
      data: { token: "trader-token" },
    });
    expect(response.ok()).toBeTruthy();
    this.session = ((await response.json()) as { sessionId: number }).sessionId;
  }

  async operation(
    operation: string,
    items: object[],
  ): Promise<{ status: string; refId: string | null; errorCode: string | null }[]> {
    const response = await this.request.post(`http://localhost:8484/api/v1/${operation}`, {
      headers: { "X-EMS-Session": String(this.session) },
      data: { requestId: `e2e-${this.session}-${this.n++}`, sessionSeq: this.seq++, items },
    });
    expect(response.ok()).toBeTruthy();
    return ((await response.json()) as { results: never[] }).results;
  }

  /** Stage one order; returns its orderId. */
  async stage(clOrdId: string, figi = FIGI_AAPL, qty = 500): Promise<string> {
    const [result] = await this.operation("stage_orders", [
      { clOrdId, figi, side: 1, qty, price: 1_824_500, account: "E2E", tif: 0 },
    ]);
    expect(result.status).toBe("ACCEPTED");
    return result.refId as string;
  }

  async stageReadyRoute(clOrdId: string, qty = 500): Promise<{ orderId: string; routeId: string }> {
    const orderId = await this.stage(clOrdId, FIGI_AAPL, qty);
    await this.operation("mark_ready", [{ orderId }]);
    const [routed] = await this.operation("route_orders", [
      { orderId, venueMic: "XNAS", qty },
    ]);
    expect(routed.status).toBe("ACCEPTED");
    return { orderId, routeId: routed.refId as string };
  }
}

/**
 * A row of the given grid containing `text` is (eventually) present. Queries the viewer's VIEW
 * (all configured columns), not painted cells — the datagrid virtualizes columns horizontally,
 * so off-screen cell text (orderId on a wide blotter) never reaches the DOM (TESTING.md rule).
 */
export async function expectRowContaining(
  page: Page,
  viewerId: string,
  text: string,
): Promise<void> {
  await expect
    .poll(
      () =>
        page.evaluate(
          async ([id, needle]) => {
            const viewer = document.getElementById(id) as HTMLElement & {
              getView(): Promise<{
                num_rows(): Promise<number>;
                to_json(o: object): Promise<Record<string, unknown>[]>;
              }>;
            };
            if (!viewer) {
              return false;
            }
            const view = await viewer.getView();
            const rows = await view.to_json({ start_row: 0, end_row: await view.num_rows() });
            return rows.some((row) =>
              Object.values(row).some((v) => String(v).includes(needle)),
            );
          },
          [viewerId, text] as const,
        ),
      { timeout: 15_000 },
    )
    .toBe(true);
}
