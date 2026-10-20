import { test as base, expect } from "@playwright/test";

/**
 * Shared test fixture.
 *
 * The app renders catalogue photography through the Next.js image optimizer
 * (`/_next/image?url=https://images.unsplash.com/...`). This sandbox's egress to
 * `images.unsplash.com` times out, so every product image becomes a failed
 * upstream request that floods the dev server (observed: the server died after
 * the 24-image `/categories` page). To keep the behavioural suite deterministic
 * we fulfil every optimizer request with a tiny transparent PNG instead of
 * letting it hit the network.
 *
 * Remote image *availability* is verified separately (direct HEAD probes) — see
 * `docs/VERIFICATION_2026-10-11_rerun.md`. This fixture isolates APP behaviour
 * (routing, content, cart, auth) from the sandbox's unreliable image egress.
 */

// 1x1 transparent PNG.
const TRANSPARENT_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.route("**/_next/image*", (route) =>
      route.fulfill({ status: 200, contentType: "image/png", body: TRANSPARENT_PNG }),
    );
    await page.route("**://images.unsplash.com/**", (route) => route.abort());
    // `use` here is Playwright's fixture callback, not a React hook — the
    // react-hooks rule cannot tell them apart.
    // eslint-disable-next-line react-hooks/rules-of-hooks
    await use(page);
  },
});

export { expect };
