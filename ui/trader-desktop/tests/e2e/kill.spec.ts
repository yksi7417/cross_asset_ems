/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { FIGI_AAPL, logonUi } from "./helpers";

// Workflow: the kill drill — engage, lockout rendered on the ticket, release, ack the alert.
test("kill switch: banner, EMS-ORD-9601 lockout on the ticket, release, notification ack", async ({
  page,
}) => {
  await logonUi(page);
  page.on("dialog", (d) => d.accept(d.type() === "prompt" ? "e2e drill" : undefined));

  await page.click("#kill-btn");
  await expect(page.locator("#kill-banner")).toBeVisible({ timeout: 15_000 });
  await expect(page.locator("#kill-banner-text")).toContainText("FIRM:firm-demo");

  // Order entry is locked — the ticket renders the catalog code.
  await page.fill("#tk-figi", FIGI_AAPL);
  await page.fill("#tk-qty", "100");
  await page.click("#tk-stage");
  await expect(page.locator("#tk-result")).toHaveText(/EMS-ORD-9601/, { timeout: 10_000 });

  // The CRITICAL alert arrived; ack it.
  const ack = page.locator("#notify-list li button").first();
  await expect(ack).toBeVisible({ timeout: 10_000 });
  await page.evaluate(() => (document.querySelector("#notify-list li button") as HTMLButtonElement).click());
  await expect(page.locator("#notify-list li.acked").first()).toBeVisible({ timeout: 10_000 });

  // Release restores order entry.
  await page.click("#kill-release");
  await expect(page.locator("#kill-banner")).toBeHidden({ timeout: 10_000 });
  await page.click("#tk-stage");
  await expect(page.locator("#tk-result")).toHaveText(/STAGE OK/, { timeout: 10_000 });
});
