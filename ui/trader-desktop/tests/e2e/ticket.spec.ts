/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { ApiSeeder, FIGI_AAPL, expectRowContaining, logonUi } from "./helpers";

// Workflow: the order ticket — server-side preview, stage, amend prefill, error rendering.
test("ticket: preview → stage → amend with prefill", async ({ page }) => {
  await logonUi(page);

  await page.fill("#tk-figi", FIGI_AAPL);
  await expect(page.locator("#tk-feedback")).toHaveText(/validator: pass/, { timeout: 10_000 });
  await page.fill("#tk-qty", "700");
  await page.fill("#tk-px", "182.45");
  await page.click("#tk-stage");
  await expect(page.locator("#tk-result")).toHaveText(/STAGE OK — EMS-ORD-/, { timeout: 10_000 });
  const orderId = (await page.locator("#tk-result").textContent())!.split("— ")[1].trim();
  await expectRowContaining(page, "orders-viewer", orderId);

  // Amend prefill (QA #4): selecting the order loads its qty/px and says what's being edited.
  await page.waitForTimeout(1500); // dropdown refresh tick
  await page.selectOption("#tk-order", orderId);
  await expect(page.locator("#tk-qty")).toHaveValue("700");
  await expect(page.locator("#tk-result")).toHaveText(/editing .*EMS-ORD-/);
  await page.fill("#tk-qty", "900");
  await page.click("#tk-amend");
  await expect(page.locator("#tk-result")).toHaveText(/AMEND OK/, { timeout: 10_000 });
});

test("ticket renders catalog errors: route beyond remaining is EMS-RTE-4003", async ({
  page,
  request,
}) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const { orderId } = await seeder.stageReadyRoute(`E2E-T-${Date.now()}`, 500);

  await logonUi(page);
  await page.waitForTimeout(1500);
  await page.selectOption("#tk-order", orderId);
  await page.fill("#tk-qty", "9999"); // far beyond the order's remaining
  await page.click("#tk-route");
  await expect(page.locator("#tk-result")).toHaveText(/EMS-RTE-4003/, { timeout: 10_000 });
});
