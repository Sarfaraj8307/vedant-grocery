import type { Page, ConsoleMessage } from "@playwright/test";

/** One captured console/page error, tagged with its source page. */
export type CapturedError = {
  page: string;
  kind: "console" | "pageerror";
  text: string;
};

/**
 * Attach console + uncaught-error listeners to a page.
 *
 * Only genuine failures are captured: `console.error` and uncaught exceptions.
 * Next.js dev-tooling advisories (HMR, React DevTools banner, LCP hint,
 * `scroll-behavior: smooth` hint) are `console.warn`/advisory and are filtered
 * out so they cannot masquerade as defects. Returns the live array — it keeps
 * filling as the page runs, so read it after navigation settles.
 */
export function collectErrors(page: Page): CapturedError[] {
  const errors: CapturedError[] = [];
  const tag = (p: Page) => p.url() || "about:blank";

  const isNoise = (text: string) =>
    /\[HMR\] connected|React DevTools|Download the React DevTools|favicon\.ico|net::ERR_ABORTED|scroll-behavior: smooth|data-scroll-behavior|Largest Contentful Paint/i.test(
      text,
    );

  page.on("console", (msg: ConsoleMessage) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    if (isNoise(text)) return;
    errors.push({ page: tag(page), kind: "console", text });
  });

  page.on("pageerror", (err) => {
    const text = err.message || String(err);
    if (isNoise(text)) return;
    errors.push({ page: tag(page), kind: "pageerror", text });
  });

  return errors;
}

/** Render the captured errors as a newline-joined string for test output. */
export function formatErrors(errors: CapturedError[]): string {
  return errors.map((e) => `[${e.kind}] ${e.text}`).join("\n");
}

/**
 * Wait for the page to be visually settled. `networkidle` alone is unreliable
 * against a dev server (Turbopack keeps a websocket open), so we combine a
 * `load` wait with a short settle for client hydration.
 */
export async function settle(page: Page, ms = 1200): Promise<void> {
  await page.waitForLoadState("load").catch(() => {});
  await page.waitForTimeout(ms);
}
