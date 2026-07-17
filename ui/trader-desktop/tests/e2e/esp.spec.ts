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

// L3-3: the ESP click POST can be network-failed on demand (sanctioned browser-mock,
// docs/TESTING.md "transport drops"). A dead/half-open backend must surface a visible error,
// never leave the trader guessing whether a (possibly large) order reached the market.
test("ESP click surfaces a visible error within 2s when the order POST network-fails", async ({
  page,
}) => {
  await logonUi(page);
  const buyPx = page.locator(".esp-tile button.esp-buy .esp-px");
  await expect(buyPx).not.toHaveText("—", { timeout: 15_000 });

  await page.route("**/api/v1/esp/click", (route) => route.abort("failed"));
  await page.locator(".esp-tile button.esp-buy").click();

  const result = page.locator(".esp-tile .esp-result");
  await expect(result).toHaveClass(/err/, { timeout: 2_000 });
  await expect(result).toHaveText(/ESP click failed/, { timeout: 2_000 });
});

// L3-6: an in-flight order disables both tile buttons, so a double-click can never fire a second
// order. We hold the mocked response open across both clicks to make any race maximally likely to
// manifest, then assert exactly one POST reached the (mocked) route.
test("double-clicking an ESP tile yields exactly one order POST", async ({ page }) => {
  await logonUi(page);
  const buy = page.locator(".esp-tile button.esp-buy");
  await expect(buy.locator(".esp-px")).not.toHaveText("—", { timeout: 15_000 });

  let posts = 0;
  let release!: () => void;
  const held = new Promise<void>((resolve) => (release = resolve));
  await page.route("**/api/v1/esp/click", async (route) => {
    posts += 1;
    await held; // keep the first order outstanding while the second click is attempted
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        status: "FILLED",
        qty: 1_000_000,
        px: 108_500,
        venueMic: "LMAX",
        venueAcceptRateBp: 5_000,
      }),
    });
  });

  await buy.click(); // POST #1 fires; both buttons disable; response held open
  // A real second click on a now-disabled button dispatches no click event — force past
  // actionability to prove the disabled guard, not Playwright's own wait, is what blocks it.
  await buy.click({ force: true, timeout: 1_500 }).catch(() => {});
  await page.waitForTimeout(500);
  expect(posts).toBe(1);
  release();
});
