import { test, expect } from "./fixtures";
import path from "node:path";
import { collectErrors, formatErrors, settle } from "./helpers";
import { loadCatalogueFixtures, type CatalogueFixtures } from "./catalogue";

/**
 * ROUTE COVERAGE — visit and assert every route in the app.
 *
 * For each route we record:
 *   • the raw HTTP status (via an API request that does NOT follow redirects),
 *   • the final URL after the browser follows any redirect,
 *   • whether the EXPECTED content rendered (not the error boundary, not 404),
 *   • every console error / uncaught exception.
 *
 * NOTE on guarded routes: as of the D2 fix, `requireUser()` now calls Next.js
 * `redirect()`, which emits a REAL **HTTP 307** with a `Location` header (e.g.
 * `/login?next=/checkout`). The browser follows it to /login. The tests assert
 * the FINAL url (and the preserved `next` param) and capture the raw 307 status
 * as evidence via the `guard=` annotation. (Before D2 these routes returned
 * 200 + a client-side `<meta http-equiv="refresh">` NEXT_REDIRECT digest.)
 *
 * A screenshot of every route is written to `.logs/verify/`.
 */

const ERROR_BOUNDARY_TEXT = "Something went wrong on our side";
const NOT_FOUND_TEXT = "We couldn't find that page";

type RouteSpec = {
  name: string;
  path: (fx: CatalogueFixtures) => string;
  kind: "public" | "gated" | "asset";
  marker?: string | RegExp;
  /** For gated routes: where the guard should land (default `/login`). */
  redirectTo?: string;
  /** For gated routes that land on /login: the preserved `next` value. */
  gatedNext?: string;
  contentType?: string;
};

const ROUTES: RouteSpec[] = [
  { name: "home", path: () => "/", kind: "public", marker: "What are you cooking?" },
  { name: "about", path: () => "/about", kind: "public", marker: /A local shop/i },
  { name: "contact", path: () => "/contact", kind: "public", marker: /Contact/i },
  { name: "offers", path: () => "/offers", kind: "public", marker: /on offer right now/i },
  {
    name: "categories",
    path: () => "/categories",
    kind: "public",
    marker: /Everything we have today/i,
  },
  {
    name: "category-detail",
    path: (fx) => `/categories/${fx.categoryId}`,
    kind: "public",
    marker: /Fruits|Vegetables|Leafy/i,
  },
  { name: "search", path: () => "/search", kind: "public", marker: /Popular searches/i },
  {
    name: "product-detail",
    path: (fx) => `/products/${fx.productId}`,
    kind: "public",
    marker: /In stock|Out of stock/i,
  },
  { name: "cart", path: () => "/cart", kind: "public", marker: /Your basket/i },
  { name: "checkout", path: () => "/checkout", kind: "gated", gatedNext: "/checkout" },
  { name: "orders", path: () => "/orders", kind: "gated", gatedNext: "/orders" },
  {
    name: "order-detail",
    path: () => "/orders/00000000-0000-0000-0000-000000000000",
    kind: "gated",
    // Next.js overrides the `next` param with the ACTUAL requested path for a
    // dynamic route (the page calls requireUser("/orders"), but the emitted
    // Location is /login?next=%2Forders%2F<id>), so a returning customer lands
    // back on the exact detail page. Assert the full path, not the section.
    gatedNext: "/orders/00000000-0000-0000-0000-000000000000",
  },
  { name: "account", path: () => "/account", kind: "gated", gatedNext: "/account" },
  {
    name: "account-addresses",
    path: () => "/account/addresses",
    kind: "gated",
    gatedNext: "/account/addresses",
  },
  {
    name: "account-reset-password",
    path: () => "/account/reset-password",
    kind: "gated",
    redirectTo: "/forgot-password",
  },
  {
    name: "notifications",
    path: () => "/notifications",
    kind: "gated",
    gatedNext: "/notifications",
  },
  { name: "support", path: () => "/support", kind: "gated", gatedNext: "/support" },
  {
    name: "support-detail",
    path: () => "/support/00000000-0000-0000-0000-000000000000",
    kind: "gated",
    // See order-detail: Next.js preserves the full requested path in `next`,
    // not the section path passed to requireUser().
    gatedNext: "/support/00000000-0000-0000-0000-000000000000",
  },
  { name: "login", path: () => "/login", kind: "public", marker: /Welcome back/i },
  { name: "signup", path: () => "/signup", kind: "public", marker: /Create account/i },
  { name: "forgot-password", path: () => "/forgot-password", kind: "public", marker: /reset/i },
  { name: "privacy", path: () => "/privacy", kind: "public", marker: /Privacy/i },
  { name: "terms", path: () => "/terms", kind: "public", marker: /Terms/i },
  { name: "offline", path: () => "/offline", kind: "public", marker: /offline/i },
  {
    name: "manifest",
    path: () => "/manifest.webmanifest",
    kind: "asset",
    contentType: "application/manifest+json",
  },
  { name: "robots", path: () => "/robots.txt", kind: "asset", contentType: "text/plain" },
  { name: "sitemap", path: () => "/sitemap.xml", kind: "asset", contentType: "application/xml" },
];

const SHOT_DIR = path.resolve(process.cwd(), ".logs", "verify");

test.describe("Route coverage", () => {
  let fx: CatalogueFixtures;
  test.beforeAll(async () => {
    fx = await loadCatalogueFixtures();
  });

  for (const route of ROUTES) {
    test(`${route.name} → ${route.kind}`, async ({ page, request }, testInfo) => {
      const url = route.path(fx);
      const errors = collectErrors(page);

      // --- Raw HTTP status (no redirect following) -------------------------
      const raw = await request.get(url, { maxRedirects: 0 });
      const rawStatus = raw.status();
      const rawBody = route.kind === "gated" ? await raw.text() : "";

      // --- Rendered result (browser follows the client-side redirect) ------
      const response = await page.goto(url, { waitUntil: "load" });

      if (route.kind === "gated") {
        const target = route.redirectTo ?? "/login";
        await page.waitForURL(new RegExp(`${escapeRe(target)}(\\?|$)`), { timeout: 15_000 });
      }
      await settle(page);

      const finalUrl = new URL(page.url());
      const finalPath = finalUrl.pathname + finalUrl.search;
      const body = await page.locator("body").innerText();

      await page.screenshot({
        path: path.join(SHOT_DIR, `route-${route.name}-${testInfo.project.name}.png`),
        fullPage: true,
      });

      // --- Assertions ------------------------------------------------------
      if (route.kind === "asset") {
        expect(rawStatus, `${route.name} raw status`).toBe(200);
        const ct = raw.headers()["content-type"] ?? "";
        expect(ct, `${route.name} content-type`).toContain(route.contentType!);
      } else if (route.kind === "gated") {
        const target = route.redirectTo ?? "/login";
        // Evidence: the guard is delivered as a 200 + client redirect, not a 307.
        testInfo.annotations.push({
          type: "guard",
          description: `${route.name}: raw=${rawStatus} clientRedirect=${/__next-page-redirect|NEXT_REDIRECT/.test(rawBody)} target=${target}`,
        });
        expect(finalUrl.pathname, `${route.name} should land on ${target}`).toBe(target);
        if (route.gatedNext) {
          expect(finalUrl.searchParams.get("next")).toBe(route.gatedNext);
        }
        if (target === "/login") {
          await expect(page.getByRole("heading", { level: 1 })).toContainText(/Welcome back/i);
        }
      } else {
        expect(response?.status(), `${route.name} status`).toBe(200);
        expect(finalPath, `${route.name} should not redirect`).toBe(url);
        expect(body, `${route.name} rendered the error boundary`).not.toContain(ERROR_BOUNDARY_TEXT);
        expect(body, `${route.name} rendered the 404 page`).not.toContain(NOT_FOUND_TEXT);
        expect(body, `${route.name} missing expected content`).toMatch(route.marker!);
      }

      expect(errors, `${route.name} console errors:\n${formatErrors(errors)}`).toEqual([]);
    });
  }
});

function escapeRe(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
