import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Unit-test configuration.
 *
 * Scope is deliberately narrow: only `src/**\/*.test.ts`, which covers the pure
 * logic that decides money and delivery eligibility. Browser behaviour is owned
 * by Playwright (`tests/*.spec.ts`, run against a dev server on port 3100).
 * Keeping the two suites in separate directories means neither runner collects
 * the other's files.
 *
 * `.mts` (not `.ts`) so the config is loaded as ESM without a warning.
 */
export default defineConfig({
  test: {
    include: ["src/**/*.test.ts"],
    environment: "node",
  },
  resolve: {
    alias: {
      "@": path.resolve(fileURLToPath(new URL("./src", import.meta.url))),
    },
  },
});
