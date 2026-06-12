/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
import { expect, test } from "@playwright/test";
import { expectRowContaining, logonUi } from "./helpers";

// Workflow (11.18): RFQ for RFQ-traded instruments — fire to the dealer panel, read the
// best-first ladder (ineligible quotes greyed, never executable), accept the best eligible
// quote, and see the execution booked into the blotter like any other fill.
test("RFQ: ladder sorts best-first, eligibility gates, accept books to the blotter", async ({
  page,
}) => {
  await logonUi(page);

  // The Apple '29 corp bond is RFQ-traded; the ticket arms REQUEST QUOTES on lookup.
  await page.fill("#tk-figi", "BBG00DEMOC29");
  await page.dispatchEvent("#tk-figi", "input");
  await expect(page.locator("#rfq-fire")).toBeEnabled({ timeout: 10_000 });

  // An order-book name disarms it.
  await page.fill("#tk-figi", "BBG000B9XRY4");
  await page.dispatchEvent("#tk-figi", "input");
  await expect(page.locator("#rfq-fire")).toBeDisabled();

  // Back to the bond: fire the RFQ.
  await page.fill("#tk-figi", "BBG00DEMOC29");
  await page.dispatchEvent("#tk-figi", "input");
  await expect(page.locator("#rfq-fire")).toBeEnabled();
  await page.fill("#tk-qty", "100000");
  await page.click("#rfq-fire");

  // The ladder renders best-first for a buyer: AXES (2bp) tops it but is INELIGIBLE for
  // ACC-DEMO (no onboarding) — visible, greyed, un-hittable.
  await expect(page.locator("#rfq-ladder li")).toHaveCount(4, { timeout: 10_000 });
  const top = page.locator("#rfq-ladder li").first();
  await expect(top).toContainText("AXES");
  await expect(top).toHaveClass(/ineligible/);
  await expect(top.locator("button")).toBeDisabled();
  await expect(top.locator("button")).toHaveText("NOT ELIGIBLE");

  // The best ELIGIBLE row is highlighted; quotes carry live countdowns.
  const best = page.locator("#rfq-ladder li.best");
  await expect(best).toHaveCount(1);
  await expect(best).toContainText("FADE"); // 3bp — tighter than GS/JPM, eligible
  await expect(page.locator("#rfq-status")).toContainText("4 quotes");

  // FADE is a last-look dealer: accepting it fades, the RFQ re-arms (Elected -> Active).
  await best.locator("button").click();
  await expect(page.locator("#rfq-status")).toContainText("faded", { timeout: 10_000 });

  // Accept the new best eligible (GS, firm): executes and books to the blotter.
  await page.locator("#rfq-ladder li.best button").click();
  await expect(page.locator("#rfq-status")).toContainText("EXECUTED", { timeout: 10_000 });
  await expect(page.locator("#rfq-status")).toContainText("GS");

  // The fill is a first-class execution: the order blotter shows the bond FILLED at the
  // accepted quote (the OMS assigns its own orderId; the RFQ id rides clOrdId).
  await expectRowContaining(page, "orders-viewer", "Apple Inc 3.45% 2029");
  await expect(
    page
      .locator("#orders-viewer regular-table tbody tr", { hasText: "Apple Inc 3.45% 2029" })
      .first(),
  ).toContainText("FILLED");
});
