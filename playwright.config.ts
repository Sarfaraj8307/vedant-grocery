import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright configuration for the Vedant Fruit & Vegetables storefront.
 *
 * The suite targets a dev server on **port 3100**, started separately by the QA
 * harness for the duration of the run. Two approaches were tried:
 *
 *   1. `webServer` block (Playwright owns the process) — the server stayed up
 *      for the whole run (good), but Playwright's TEARDOWN HUNG on Windows:
 *      the tests all passed, port 3100 was released, yet the runner process
 *      never exited, so the JSON reporter's `onEnd` never fired and no
 *      `results.json` was written. Reproduced with a single test.
 *   2. A server started by the harness on port 3100, with no `webServer` block —
 *      Playwright exits cleanly and writes `results.json`.
 *
 * Approach 2 is used. The server must be started before the run and kept alive
 * for its duration (same turn).
 *
 * Two projects: `mobile` (390x844 — the primary target) and `desktop`
 * (1280x800 — spot-check). Everything runs serially (`workers: 1`) because the
 * dev server compiles routes on first hit and we want deterministic timing.
 */
export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: false,
  reporter: [
    ["list"],
    ["json", { outputFile: ".logs/verify/results.json" }],
  ],
  // A UNIQUE output dir per run: this sandbox blocks Playwright's bulk
  // `rm -rf` of a populated output dir (>50 files), so we always start from a
  // fresh, empty directory instead of deleting the previous one.
  outputDir: `.logs/verify/pw-${Date.now()}`,
  use: {
    baseURL: "http://localhost:3100",
    trace: "off",
    screenshot: "only-on-failure",
    video: "off",
    actionTimeout: 15_000,
    navigationTimeout: 45_000,
  },
  // NOTE: deliberately NO `webServer` block — see the header comment. The QA
  // harness starts `next dev --port 3100` before the run and keeps it alive.
  projects: [
    {
      name: "mobile",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        isMobile: false,
      },
    },
    {
      name: "desktop",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
});
