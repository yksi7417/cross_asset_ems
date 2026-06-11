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

  await page.locator("#orders-viewer").click({ button: "right", position: { x: 300, y: 100 } });
  await page.locator("#ctx-menu button", { hasText: "Cancel (2)" }).click();
  await expect(page.locator("#toast")).toHaveText(/CANCEL: 2\/2 accepted/, { timeout: 10_000 });

  // The rows go terminal through the stream; re-selecting them must DISABLE cancel.
  await expect(page.locator("#orders-viewer regular-table tbody")).toContainText("CANCELED", {
    timeout: 10_000,
  });
  await rowCell(id1).click();
  await page.locator("#orders-viewer").click({ button: "right", position: { x: 300, y: 100 } });
  await expect(
    page.locator("#ctx-menu button", { hasText: "Cancel (0) — no applicable rows" }),
  ).toBeDisabled();
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
  await page.locator("#orders-viewer").click({ button: "right", position: { x: 300, y: 100 } });
  await page.locator("#ctx-menu button", { hasText: "Aggregate 2 into a basket" }).click();
  await expect(page.locator("#toast")).toHaveText(/Basket BSK-\d+ created \(2 orders\)/, {
    timeout: 10_000,
  });
  await expectRowContaining(page, "baskets-viewer", "e2e-basket");
});
