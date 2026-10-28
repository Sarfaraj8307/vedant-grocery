import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Unit-test configuration.
 *
 * Scope is deliberately narrow: `src/**\/*.test.ts` covers the pure logic that
 * decides money and delivery eligibility, and `src/**\/*.test.tsx` covers
 * components whose behaviour is arithmetic over DOM events (the OTP boxes).
 * Full browser journeys are still owned by Playwright (`tests/*.spec.ts`,
 * run against a dev server on port 3100).
 *
 * The default environment stays `node`; a component test opts into jsdom with a
 * `// @vitest-environment jsdom` docblock, so nothing pays for a DOM it does
 * not use.
 *
 * `.mts` (not `.ts`) so the config is loaded as ESM without a warning.
 */
export default defineConfig({
  test: {
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    environment: "node",
  },
  resolve: {
    alias: {
      "@": path.resolve(fileURLToPath(new URL("./src", import.meta.url))),
    },
  },
});
