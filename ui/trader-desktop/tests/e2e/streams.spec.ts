/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { ApiSeeder, expectRowContaining, logonUi } from "./helpers";

// Workflow: logon → live streams → blotter projection → refresh-resume.
test("logon brings streams live; API-staged order appears; refresh resumes the image", async ({
  page,
  request,
}) => {
  await logonUi(page);
  for (const chip of ["chip-md", "chip-orders", "chip-routes", "chip-fills"]) {
    await expect(page.locator(`#${chip}`)).toHaveClass(/live/);
  }

  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const orderId = await seeder.stage(`E2E-S-${Date.now()}`);
  await expectRowContaining(page, "orders-viewer", orderId);

  // Refresh: the blotter is a replayed projection — the same row must come back, and the
  // session must resume WITHOUT a logon click (18.31: sessionStorage token + auto-logon is
  // what turns the backend-restart recovery reload into a seamless resume).
  await page.reload();
  await page.waitForSelector("#app:not(.hidden)", { timeout: 20_000 });
  await page.waitForSelector("#chip-orders.live", { timeout: 20_000 });
  await expectRowContaining(page, "orders-viewer", orderId);
});

// L3-1: a half-open WebSocket — the server stops sending frames but the socket stays OPEN, so
// onclose/onerror never fire — must not leave the chip GREEN on a frozen tape. We route the
// orders stream through a mock that accepts the client's open (chip goes live) but never delivers
// a frame and never proxies to the real server: a stale-but-open socket. The client-side staleness
// watchdog must sever it and drop the chip out of `live`. (Sanctioned browser-mock:
// docs/TESTING.md "transport drops" -> page.routeWebSocket.)
test("stale-but-open orders stream (frames stop, socket stays open) drops the chip out of live", async ({
  page,
}) => {
  await page.routeWebSocket(/\/ws\/events\?.*topic=blotter\.orders/, () => {
    // Do nothing: the client's WebSocket opens (onopen -> chip live), but no frame is ever
    // delivered and we never connectToServer(), so the socket stays open and silent.
  });

  await logonUi(page);
  // It first goes live on the socket open...
  await expect(page.locator("#chip-orders")).toHaveClass(/live/, { timeout: 15_000 });
  // ...then the staleness watchdog (STALE_MS=10s) severs the silent-but-open socket and the
  // chip must leave `live` (reconnecting/down), instead of showing a frozen tape as healthy.
  await expect(page.locator("#chip-orders")).not.toHaveClass(/live/, { timeout: 20_000 });
});
