/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Demo recorder (task 18.16): drives the trader desktop through the
// docs/TRADER_DESKTOP_DEMO.md walkthrough in headless Chromium, recording a
// video and key screenshots for docs/demo/.
//
// Prereqs: the demo stack is running (scripts/dev/run-trader-demo.sh) and
// Playwright's Chromium is installed (npx playwright install chromium).
//
// Usage:  node demo/record-demo.mjs [outDir]      (default ../../docs/demo)

import { chromium } from "playwright";
import { mkdirSync, renameSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(process.argv[2] ?? join(here, "../../../docs/demo"));
const BASE = process.env.DEMO_URL ?? "http://localhost:5173";
const FIGI = "BBG000B9XRY4"; // Apple Inc in the demo security master
mkdirSync(OUT, { recursive: true });

const shot = (page, name) => page.screenshot({ path: join(OUT, name) });
const pause = (page, ms) => page.waitForTimeout(ms);

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1280, height: 720 },
  recordVideo: { dir: OUT, size: { width: 1280, height: 720 } },
});
const page = await context.newPage();

// The kill switch uses prompt()/alert(); answer them as the walkthrough does.
const dialogAnswers = [];
page.on("dialog", (dialog) =>
  dialog.type() === "prompt"
    ? dialog.accept(dialogAnswers.shift() ?? "demo")
    : dialog.accept(),
);

try {
  // ── 1. Logon ────────────────────────────────────────────────────────────────
  await page.goto(BASE, { waitUntil: "networkidle" });
  await shot(page, "01-logon.png");
  await page.click("#logon-form button");
  await page.waitForSelector("#app:not(.hidden)", { timeout: 20000 });

  // Streams connect, WASM grids hydrate, the demo bot's flow starts rendering.
  await page.waitForSelector("#chip-orders.live", { timeout: 20000 });
  await pause(page, 8000);
  await shot(page, "02-desktop-live.png");

  // ── 2. Ticket: preview → stage → ready → route ─────────────────────────────
  await page.fill("#tk-figi", FIGI);
  await page.waitForFunction(
    () => document.getElementById("tk-feedback").textContent.includes("pass"),
    { timeout: 10000 },
  );
  await page.fill("#tk-qty", "500");
  await page.fill("#tk-px", "182.45");
  await shot(page, "03-ticket-preview.png");
  await page.click("#tk-stage");
  await page.waitForFunction(
    () => document.getElementById("tk-result").textContent.includes("OK"),
    { timeout: 10000 },
  );
  const orderId = await page.evaluate(() =>
    document.getElementById("tk-result").textContent.split("— ").pop().trim(),
  );
  await pause(page, 1500); // working-order dropdown refreshes on a 1s tick
  await page.selectOption("#tk-order", orderId);
  await page.click("#tk-ready");
  await pause(page, 700);
  await page.click("#tk-route");
  await pause(page, 1500);
  await shot(page, "04-routed.png");

  // ── 2b. Linked blotter (18.17): order click filters routes; route click reveals fills ──
  await page.locator("#orders-viewer regular-table tbody tr td").first().click();
  await pause(page, 1200);
  await page.locator("#routes-viewer regular-table tbody tr td").first().click();
  await pause(page, 1500);
  await shot(page, "04b-linked-fills.png");
  await page.click("#fills-linkchip"); // unlink fills again
  await page.click("#routes-linkchip"); // and routes
  await pause(page, 600);

  // ── 3. Basket: load the sample CSV, route a 25% wave ───────────────────────
  await page.fill("#bk-name", "demo-program");
  await page.setInputFiles("#bk-file", join(here, "sample-basket.csv"));
  await page.click("#bk-load");
  await page.waitForFunction(
    () => document.getElementById("bk-result").textContent.includes("accepted"),
    { timeout: 10000 },
  );
  await pause(page, 2500); // basket dropdown refreshes on a 2s tick
  const basketId = await page.evaluate(() => {
    const select = document.getElementById("bk-select");
    return select.options[select.options.length - 1].value;
  });
  await page.selectOption("#bk-select", basketId);
  await page.click("#bk-wave");
  await page.waitForFunction(
    () => document.getElementById("bk-result").textContent.includes("wave"),
    { timeout: 10000 },
  );
  await pause(page, 1500);
  await shot(page, "05-basket-wave.png");

  // ── 4. ESP click-to-trade ───────────────────────────────────────────────────
  await page.waitForSelector(".esp-tile button.esp-buy", { timeout: 10000 });
  await page.click(".esp-tile button.esp-buy");
  await page.waitForFunction(
    () => document.querySelector(".esp-tile .esp-result").textContent.length > 0,
    { timeout: 10000 },
  );
  await pause(page, 1200);
  await shot(page, "06-esp-fill.png");

  // ── 5. Kill drill: engage → locked out → release ────────────────────────────
  dialogAnswers.push("walkthrough drill"); // engage reason; the result alert auto-accepts
  await page.click("#kill-btn");
  await page.waitForSelector("#kill-banner:not(.hidden)", { timeout: 15000 });
  await pause(page, 1200);
  await page.click("#tk-stage"); // entry is locked: EMS-ORD-9601 renders on the ticket
  await page.waitForFunction(
    () => document.getElementById("tk-result").textContent.includes("9601"),
    { timeout: 10000 },
  );
  await shot(page, "07-kill-lockout.png");
  dialogAnswers.push("drill complete");
  await page.click("#kill-release");
  await page.waitForSelector("#kill-banner", { state: "hidden", timeout: 10000 });

  // ── 6. Ack the CRITICAL kill notification, then linger on the live desk ────
  // The queue can overflow the small panel at 720p; click via the DOM, not the pointer.
  await page.evaluate(() => document.querySelector("#notify-list li button")?.click());
  await pause(page, 5000);
  await shot(page, "08-final.png");
} finally {
  await context.close(); // flushes the video
  const video = await page.video().path();
  renameSync(video, join(OUT, "trader-desktop-demo.webm"));
  await browser.close();
}
console.log("recorded:", join(OUT, "trader-desktop-demo.webm"));
