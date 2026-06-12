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
