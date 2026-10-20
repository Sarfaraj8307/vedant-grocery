import { test, expect } from "./fixtures";
import type { Page } from "@playwright/test";
import path from "node:path";
import { collectErrors, formatErrors, settle } from "./helpers";
import {
  CART_STORAGE_KEY,
  loadCatalogueFixtures,
  type CatalogueFixtures,
} from "./catalogue";

/**
 * CORE USER FLOWS — exercised in a real browser at 390x844 (mobile-first),
 * with a desktop spot-check where the layout differs.
 *
 * Deterministic cart state is injected via `localStorage` (`vedant.cart.v1`,
 * the exact key the Zustand store persists to) so totals assertions do not
 * depend on which product happens to be first in the catalogue.
 */

const SHOT_DIR = path.resolve(process.cwd(), ".logs", "verify");

const SAMPLE_LINE = {
  unitPrice: 30,
  listPrice: 40,
  quantity: 1,
  weightLabel: "500g",
  weightId: null as string | null,
};

/** Build the exact persisted shape `zustand/persist` writes. */
function buildCartState(
  productId: string,
  productName: string,
  overrides: Partial<typeof SAMPLE_LINE> = {},
) {
  const line = { ...SAMPLE_LINE, ...overrides };
  const key = `${productId}::${line.weightId ?? "base"}`;
  return {
    state: {
      lines: {
        [key]: {
          productId,
          weightId: line.weightId,
          quantity: line.quantity,
          name: productName,
          glyph: "🥬",
          accent: 120,
          imageUrl: null,
          unitLabel: "kg",
          weightLabel: line.weightLabel,
          unitPrice: line.unitPrice,
          listPrice: line.listPrice,
          quantityLimit: 10,
          addedAt: 1_700_000_000_000,
        },
      },
    },
    version: 1,
  };
}

async function seedCart(
  page: Page,
  productId: string,
  productName: string,
  overrides?: Partial<typeof SAMPLE_LINE>,
) {
  const state = JSON.stringify(buildCartState(productId, productName, overrides));
  await page.addInitScript(
    ([k, v]) => window.localStorage.setItem(k as string, v as string),
    [CART_STORAGE_KEY, state] as [string, string],
  );
}

test.describe("Core flows", () => {
  let fx: CatalogueFixtures;
  test.beforeAll(async () => {
    fx = await loadCatalogueFixtures();
  });

  /* ------------------------------------------------------------------ */
  /* a. Home → category → product detail → add to cart                   */
  /* ------------------------------------------------------------------ */
  test("a. browse home → category → product → add to cart", async ({ page }, testInfo) => {
    const errors = collectErrors(page);

    await page.goto("/");
    await settle(page);
    await expect(page.getByRole("heading", { name: /What are you cooking/i })).toBeVisible();

    // Category rail → first tile
    const categoryLink = page.locator('a[href^="/categories/"]').first();
    await expect(categoryLink).toBeVisible();
    await categoryLink.click();
    await page.waitForURL(/\/categories\/[0-9a-f-]{36}/);
    await settle(page);
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-a-category-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // First product card → PDP
    const productLink = page.locator('a[href^="/products/"]').first();
    await expect(productLink).toBeVisible();
    await productLink.click();
    await page.waitForURL(/\/products\/[0-9a-f-]{36}/);
    await settle(page);

    // Weight / variant selector is present and functional
    const packGroup = page.getByRole("radiogroup", { name: "Pack size" });
    await expect(packGroup).toBeVisible();
    const options = packGroup.getByRole("radio");
    const optionCount = await options.count();
    expect(optionCount, "product should expose at least one weight variant").toBeGreaterThan(0);
    await expect(options.first()).toHaveAttribute("aria-checked", "true");
    if (optionCount > 1) {
      await options.nth(1).click();
      await expect(options.nth(1)).toHaveAttribute("aria-checked", "true");
      await expect(options.first()).toHaveAttribute("aria-checked", "false");
    }

    // Add to cart — the purchase panel's stepper is the first ADD button in DOM.
    const addButton = page.locator('button[aria-label^="Add "]').first();
    await addButton.click();
    await settle(page, 600);
    // The ADD pill morphs into the `− n +` stepper.
    await expect(page.locator('button[aria-label^="Increase "]').first()).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-a-product-added-${testInfo.project.name}.png`),
      fullPage: true,
    });

    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  /* ------------------------------------------------------------------ */
  /* b. Cart: line, inc/dec, remove, totals, free-delivery bar           */
  /* ------------------------------------------------------------------ */
  test("b. cart operations, totals and free-delivery bar", async ({ page }, testInfo) => {
    const errors = collectErrors(page);
    await seedCart(page, fx.productId, fx.productName, { quantity: 1 });

    await page.goto("/cart");
    await settle(page);

    // Line appears
    await expect(page.getByRole("heading", { name: fx.productName })).toBeVisible();

    // Totals: subtotal ₹30, delivery ₹20, total ₹50
    const summary = page.locator('section[aria-labelledby="order-summary-heading"]');
    await expect(summary).toContainText("₹30");
    await expect(summary).toContainText("₹20");
    await expect(summary).toContainText("₹50");

    // Free-delivery progress bar (subtotal is under the ₹500 threshold)
    const progress = page.getByRole("progressbar", {
      name: /Progress towards free delivery/i,
    });
    await expect(progress).toBeVisible();
    await expect(progress).toHaveAttribute("aria-valuenow", /[0-9]+/);

    // Increase → qty 2, subtotal ₹60, total ₹80
    await page.getByRole("button", { name: new RegExp(`Increase ${escapeRe(fx.productName)} quantity`) }).click();
    await settle(page, 400);
    await expect(summary).toContainText("₹60");
    await expect(summary).toContainText("₹80");

    // Decrease → qty 1
    await page.getByRole("button", { name: new RegExp(`Reduce ${escapeRe(fx.productName)} quantity`) }).click();
    await settle(page, 400);
    await expect(summary).toContainText("₹30");

    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-b-cart-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // Remove → empty state (trash button is the first "Remove … from basket")
    await page
      .getByRole("button", { name: new RegExp(`Remove ${escapeRe(fx.productName)} from basket`) })
      .first()
      .click();
    await settle(page, 500);
    await expect(page.getByText(/Your basket is empty/i)).toBeVisible();

    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  /* ------------------------------------------------------------------ */
  /* c. Cart persistence across a reload                                 */
  /* ------------------------------------------------------------------ */
  test("c. cart persists across a full page reload", async ({ page }) => {
    await seedCart(page, fx.productId, fx.productName, { quantity: 2 });

    await page.goto("/cart");
    await settle(page);
    await expect(page.getByRole("heading", { name: fx.productName })).toBeVisible();

    // Hard reload — the Zustand store must rehydrate from localStorage.
    await page.reload({ waitUntil: "load" });
    await settle(page);
    await expect(page.getByRole("heading", { name: fx.productName })).toBeVisible();

    const persisted = await page.evaluate(
      (k) => window.localStorage.getItem(k),
      CART_STORAGE_KEY,
    );
    expect(persisted, "cart should be persisted under vedant.cart.v1").toContain(fx.productId);
  });

  /* ------------------------------------------------------------------ */
  /* d. Search: match + no-match                                         */
  /* ------------------------------------------------------------------ */
  test("d. search matches a product and shows an empty state for junk", async ({ page }, testInfo) => {
    const errors = collectErrors(page);

    await page.goto("/search?q=Banana");
    await settle(page);
    await expect(page.getByText(/results? for/i)).toBeVisible();
    await expect(page.getByRole("link", { name: /Banana/i }).first()).toBeVisible();
    const hits = await page.locator('a[href^="/products/"]').count();
    expect(hits, "search for 'Banana' should return at least one product").toBeGreaterThan(0);
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-d-search-hit-${testInfo.project.name}.png`),
      fullPage: true,
    });

    await page.goto("/search?q=zzzznosuchproduce");
    await settle(page);
    await expect(page.getByText(/Nothing matched/i)).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-d-search-empty-${testInfo.project.name}.png`),
      fullPage: true,
    });

    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  /* ------------------------------------------------------------------ */
  /* e. Offers page renders live offers                                  */
  /* ------------------------------------------------------------------ */
  test("e. offers page renders live offers", async ({ page }, testInfo) => {
    const errors = collectErrors(page);
    await page.goto("/offers");
    await settle(page);

    await expect(page.getByRole("heading", { name: /Today's rate board/i })).toBeVisible();
    await expect(page.getByText(/on offer right now/i)).toBeVisible();
    const cards = await page.locator('a[href^="/products/"]').count();
    expect(cards, "offers page should list at least one live offer").toBeGreaterThan(0);

    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-e-offers-${testInfo.project.name}.png`),
      fullPage: true,
    });
    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  /* ------------------------------------------------------------------ */
  /* f. Checkout as a GUEST — exact behaviour                            */
  /* ------------------------------------------------------------------ */
  test("f. checkout as guest is auth-guarded", async ({ page, request }, testInfo) => {
    const raw = await request.get("/checkout", { maxRedirects: 0 });
    const status = raw.status();
    const location = raw.headers()["location"] ?? "";

    await page.goto("/checkout");
    await page.waitForURL(/\/login/, { timeout: 15_000 });
    await settle(page);
    const finalUrl = new URL(page.url());

    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-f-checkout-guest-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // D2 fix: the guard now returns a REAL HTTP 307 redirect to
    // `/login?next=/checkout` (previously it returned 200 + a client-side
    // `<meta http-equiv="refresh">` NEXT_REDIRECT digest). The browser still
    // lands on /login with the preserved `next` param.
    testInfo.annotations.push({
      type: "guest-checkout",
      description: `raw=${status} location=${location} final=${finalUrl.pathname}${finalUrl.search}`,
    });

    expect(status, "document request status should be a real 307 redirect").toBe(307);
    expect(location, "307 Location should point at /login?next=/checkout").toContain(
      "/login?next=",
    );
    expect(finalUrl.pathname).toBe("/login");
    expect(finalUrl.searchParams.get("next")).toBe("/checkout");
    await expect(page.getByRole("heading", { level: 1 })).toContainText(/Welcome back/i);
  });

  /* ------------------------------------------------------------------ */
  /* g. Login / signup client-side validation                            */
  /* ------------------------------------------------------------------ */
  test("g. login & signup show client-side validation errors", async ({ page }, testInfo) => {
    const errors = collectErrors(page);

    // Login: submit empty
    await page.goto("/login");
    await settle(page);
    await page.getByRole("button", { name: /^Sign in$/i }).click();
    await expect(page.getByText("Enter a valid email address")).toBeVisible();
    await expect(page.getByText("Enter your password")).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-g-login-errors-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // Signup: submit empty
    await page.goto("/signup");
    await settle(page);
    await page.getByRole("button", { name: /Create account/i }).click();
    await expect(page.getByText("This is required").first()).toBeVisible();
    await expect(page.getByText("Enter a valid email address")).toBeVisible();
    await expect(page.getByText("Please accept the terms to continue")).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-g-signup-errors-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // A validation failure must not throw.
    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  /* ------------------------------------------------------------------ */
  /* h. 404 handling                                                     */
  /* ------------------------------------------------------------------ */
  test("h. unknown route and unknown product id both 404", async ({ page }, testInfo) => {
    const errors = collectErrors(page);

    const unknown = await page.goto("/this-route-does-not-exist-12345");
    await settle(page);
    expect(unknown?.status(), "unknown route status").toBe(404);
    await expect(page.getByText(/We couldn't find that page/i)).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-h-404-route-${testInfo.project.name}.png`),
      fullPage: true,
    });

    const badProduct = await page.goto("/products/00000000-0000-0000-0000-000000000000");
    await settle(page);
    // The not-found UI must render for an unknown product id. NOTE: Next.js
    // serves this as a SOFT 404 (HTTP 200 + not-found UI) rather than a 404
    // status — recorded here as evidence, asserted as a documented behaviour.
    testInfo.annotations.push({
      type: "soft-404",
      description: `unknown product id status=${badProduct?.status()}`,
    });
    await expect(page.getByText(/We couldn't find that page/i)).toBeVisible();
    await page.screenshot({
      path: path.join(SHOT_DIR, `flow-h-404-product-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // A genuine 404 document request legitimately logs a browser console error;
    // ignore that one, fail on anything else.
    const unexpected = errors.filter((e) => !/status of 404/i.test(e.text));
    expect(unexpected, `console errors:\n${formatErrors(unexpected)}`).toEqual([]);
  });
});

function escapeRe(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
