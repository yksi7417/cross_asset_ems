/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { logonUi } from "./helpers";

// Workflow: click-to-trade on the streaming EURUSD tile (quiet edge keeps the ESP pump on).
test("ESP tile streams quotes and a click yields an attributable outcome", async ({ page }) => {
  await logonUi(page);
  const buyPx = page.locator(".esp-tile button.esp-buy .esp-px");
  await expect(buyPx).not.toHaveText("—", { timeout: 15_000 });

  await page.fill(".esp-tile .esp-controls input >> nth=1", "50"); // generous guard
  await page.locator(".esp-tile button.esp-buy").click();
  const result = page.locator(".esp-tile .esp-result");
  await expect(result).not.toHaveText("", { timeout: 10_000 });
  // Either a fill with venue accept-rate or a REASONED reject — never a silent outcome.
  await expect(result).toHaveText(/FILLED .* on LMAX \(accept \d+(\.\d+)?%\)|: .+/);
});
