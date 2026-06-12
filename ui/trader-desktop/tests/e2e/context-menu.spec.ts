/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { ApiSeeder, expectRowContaining, logonUi } from "./helpers";

// Workflow: selection + right-click batch actions with state-aware menus (QA #1/#2/#5).
test("multi-select cancel reports per-item outcome; terminal rows disable cancel", async ({
  page,
  request,
}) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const id1 = await seeder.stage(`E2E-C1-${Date.now()}`);
  const id2 = await seeder.stage(`E2E-C2-${Date.now()}`);

  await logonUi(page);
  await expectRowContaining(page, "orders-viewer", id1);
  await expectRowContaining(page, "orders-viewer", id2);

  const rowCell = (id: string) =>
    page.locator("#orders-viewer regular-table tbody tr", { hasText: id }).first().locator("td").first();

  await rowCell(id1).click();
  await rowCell(id2).click({ modifiers: ["Control"] });
  await expect(page.locator("#orders-selchip")).toHaveText("▸ 2 selected");
  // Selected rows are highlighted in the grid (18.20).
  await expect
    .poll(async () => page.locator("#orders-viewer regular-table tbody tr.ems-sel").count())
    .toBe(2);

  // Right-click ON a selected row keeps the multi-selection (pointer truth, 18.20).
  await rowCell(id1).click({ button: "right" });
  await page.locator("#ctx-menu button", { hasText: "Cancel (2)" }).click();
  await expect(page.locator("#toast")).toHaveText(/CANCEL: 2\/2 accepted/, { timeout: 10_000 });
  await page.keyboard.press("Escape");

  // Both rows are now terminal (still selected). Right-clicking one keeps the 2-selection and the
  // menu offers a DISABLED cancel — actionable, honest feedback rather than a 0/N failure.
  await expect(page.locator("#orders-viewer regular-table tbody")).toContainText("CANCELED", {
    timeout: 10_000,
  });
  await rowCell(id1).click({ button: "right" });
  await expect(
    page.locator("#ctx-menu button", { hasText: "Cancel (0) — no applicable rows" }),
  ).toBeDisabled();
});

test("shift+click range-selects and right-click outside the selection re-targets", async ({
  page,
  request,
}) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const stamp = Date.now();
  for (let i = 1; i <= 4; i++) {
    await seeder.stage(`E2E-R${i}-${stamp}`);
  }
  await logonUi(page);
  await expect
    .poll(async () => page.locator("#orders-viewer regular-table tbody tr").count())
    .toBeGreaterThanOrEqual(4);

  const nthCell = (n: number) =>
    page.locator("#orders-viewer regular-table tbody tr").nth(n).locator("td").first();
  await nthCell(0).click();
  await nthCell(3).click({ modifiers: ["Shift"] });
  await expect(page.locator("#orders-selchip")).toHaveText("▸ 4 selected");
  await expect
    .poll(async () => page.locator("#orders-viewer regular-table tbody tr.ems-sel").count())
    .toBe(4);

  // Right-click on a row OUTSIDE the selection re-selects under the cursor.
  await nthCell(5).click({ button: "right" });
  await expect(page.locator("#orders-selchip")).not.toHaveText("▸ 4 selected");
  await page.keyboard.press("Escape");
});

test("aggregate selection into a basket", async ({ page, request }) => {
  const seeder = new ApiSeeder(request);
  await seeder.logon();
  const id1 = await seeder.stage(`E2E-B1-${Date.now()}`);
  const id2 = await seeder.stage(`E2E-B2-${Date.now()}`);

  await logonUi(page);
  await expectRowContaining(page, "orders-viewer", id1);
  page.on("dialog", (d) => d.accept("e2e-basket"));

  const rowCell = (id: string) =>
    page.locator("#orders-viewer regular-table tbody tr", { hasText: id }).first().locator("td").first();
  await rowCell(id1).click();
  await rowCell(id2).click({ modifiers: ["Control"] });
  await rowCell(id1).click({ button: "right" });
  await page.locator("#ctx-menu button", { hasText: "Aggregate 2 into a basket" }).click();
  await expect(page.locator("#toast")).toHaveText(/Basket BSK-\d+ created \(2 orders\)/, {
    timeout: 10_000,
  });
  await expectRowContaining(page, "baskets-viewer", "e2e-basket");
});
