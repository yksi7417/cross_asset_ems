/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { ApiSeeder, expectRowContaining, logonUi } from "./helpers";

// Workflow: linked blotter — order click filters routes; route click reveals fills (QA #3/#4).
test("order click filters routes; route click opens the fills panel; chips clear links", async ({
  page,
  request,
}) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const a = await seeder.stageReadyRoute(`E2E-L1-${Date.now()}`);
  await seeder.stageReadyRoute(`E2E-L2-${Date.now()}`);

  await logonUi(page);
  await expectRowContaining(page, "orders-viewer", a.orderId);
  // Narrow grids virtualize columns horizontally (routeId isn't painted) — assert by count.
  await expect
    .poll(async () => page.locator("#routes-viewer regular-table tbody tr").count(), {
      timeout: 15_000,
    })
    .toBeGreaterThanOrEqual(2);

  // Fills are hidden until a route is selected.
  expect(await page.locator("#fills-viewer regular-table tbody tr").count()).toBe(0);

  // Click order A's row (find it via its orderId cell text).
  await page
    .locator("#orders-viewer regular-table tbody tr", { hasText: a.orderId })
    .first()
    .locator("td")
    .first()
    .click();
  await expect(page.locator("#routes-linkchip")).toContainText("⛓", { timeout: 10_000 });
  // Routes grid now shows exactly order A's route.
  await expect
    .poll(async () => page.locator("#routes-viewer regular-table tbody tr").count(), {
      timeout: 10_000,
    })
    .toBe(1);

  // Click the route: the fills link engages (no fills exist in the quiet world — the chip and
  // empty-but-armed grid are the assertion; fill row content is covered at the API layer).
  await page
    .locator("#routes-viewer regular-table tbody tr td")
    .first()
    .click();
  await expect(page.locator("#fills-linkchip")).toContainText(a.routeId);

  // Chips clear their links.
  await page.click("#fills-linkchip");
  await expect(page.locator("#fills-linkchip")).toBeHidden();
  await page.click("#routes-linkchip");
  await expect(page.locator("#routes-linkchip")).toBeHidden();
});

// 18.27: linking is selection-aware — N selected orders filter ROUTES to all N, not just the
// last-clicked row.
test("multi-select links: two selected orders show both routes; chip counts the selection", async ({
  page,
  request,
}) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const stamp = Date.now();
  const a = await seeder.stageReadyRoute(`E2E-M1-${stamp}`);
  const b = await seeder.stageReadyRoute(`E2E-M2-${stamp}`);
  await seeder.stageReadyRoute(`E2E-M3-${stamp}`); // noise: must be filtered OUT

  await logonUi(page);
  await expectRowContaining(page, "orders-viewer", a.orderId);
  await expectRowContaining(page, "orders-viewer", b.orderId);

  const rowCell = (orderId: string) =>
    page
      .locator("#orders-viewer regular-table tbody tr", { hasText: orderId })
      .first()
      .locator("td")
      .first();

  // Click A, then ctrl+click B: the routes link must cover BOTH orders.
  await rowCell(a.orderId).click();
  await rowCell(b.orderId).click({ modifiers: ["ControlOrMeta"] });
  await expect(page.locator("#routes-linkchip")).toContainText("2 orders", { timeout: 10_000 });
  await expect(page.locator("#orders-selchip")).toContainText("2 selected");
  await expect
    .poll(async () => page.locator("#routes-viewer regular-table tbody tr").count(), {
      timeout: 10_000,
    })
    .toBe(2);

  // Multi-select the two routes: the fills link arms for both (no fills in the quiet world).
  await page.locator("#routes-viewer regular-table tbody tr").nth(0).locator("td").first().click();
  await page
    .locator("#routes-viewer regular-table tbody tr")
    .nth(1)
    .locator("td")
    .first()
    .click({ modifiers: ["ControlOrMeta"] });
  await expect(page.locator("#fills-linkchip")).toContainText("2 routes");
});
