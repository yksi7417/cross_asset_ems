import { defineConfig } from "@playwright/test";

// E2E suite (task 18.19): one spec per trader workflow against the REAL backend in QUIET mode
// (no demo bot — deterministic world; tests seed their own orders via the API). Strategy and
// mock-vs-real decision rules: docs/TESTING.md.
export default defineConfig({
  testDir: "tests/e2e",
  timeout: 60_000,
  fullyParallel: false, // one shared backend world; specs run serially
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: "http://localhost:5173",
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
  },
  webServer: [
    {
      // The quiet demo edge: real validator/OMS/streams, no scripted bot.
      command: "cd ../.. && EMS_DEMO_QUIET=1 ./gradlew :ems-fix-bridge:runTraderEdge -q",
      url: "http://localhost:8484/api/v1/instruments/BBG000B9XRY4",
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: "npm run build && npm run preview -- --port 5173 --strictPort",
      url: "http://localhost:5173",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
