import path from "node:path";
import type { Page } from "@playwright/test";
import { test, expect } from "./fixtures";
import { collectErrors, formatErrors, settle } from "./helpers";

/**
 * T06 signed-in walk — the evidence a signed-out run cannot produce.
 *
 * Signs in as the confirmed fixture customer (`customer@vedant.local`, who owns
 * one real order) and drives the four surfaces T06 added:
 *   1. `/orders`            — the order history list actually renders the order
 *                             (the `orders.created_at` → `placed_at` fix);
 *   2. `/orders/[id]`       — the detail page renders its lines;
 *   3. `/` "Buy again"      — history resolved against **today's** catalogue,
 *                             then add-one / add-all (merge + quantity clamp);
 *   4. `/checkout`          — server-verified serviceability: in-area enables
 *                             "Place order", out-of-area genuinely disables it.
 *
 * The password is supplied via env (`T06_PASSWORD`) so it never lives in a file.
 */

const SHOT_DIR = path.resolve(process.cwd(), ".logs", "verify");
const EMAIL = process.env.T06_EMAIL ?? "customer@vedant.local";
const PASSWORD = process.env.T06_PASSWORD ?? "";
const APPLE = "Apple (Shimla)";
const BANANA = "Banana (Robusta)";
const ORDER_ID = "810c2daa-a39a-472f-b7c1-d68e50710f24";
const WORK_ADDRESS = "aaaa1111-0000-4000-8000-000000000002"; // Mumbai 400001 (out of area)

async function signIn(page: Page): Promise<void> {
  await page.goto("/login", { waitUntil: "load" });
  await settle(page, 800);
  await page.fill("#email", EMAIL);
  await page.fill("#password", PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((url) => new URL(url).pathname === "/", {
    timeout: 30_000,
  });
  await settle(page, 1500);
}

/** The "Buy again" region (the home page has other Apple cards in rails/grids). */
function buyAgain(page: Page) {
  return page.getByRole("region", { name: "Buy again" });
}

/** Log any non-2xx response so handled races are visible in the walk output. */
function logBadResponses(page: Page): void {
  page.on("response", (res) => {
    if (res.status() >= 400) {
      console.log(
        `[http ${res.status()}] ${res.request().method()} ${res.url()}`,
      );
    }
  });
}

/** The Buy again card for a product, by its visible name. */
function cardFor(page: Page, name: string) {
  return buyAgain(page).locator("article").filter({ hasText: name });
}

test.describe("T06 signed-in walk", () => {
  test("1 — /orders lists the real order", async ({ page }, testInfo) => {
    const errors = collectErrors(page);
    await signIn(page);

    await page.goto("/orders", { waitUntil: "load" });
    await settle(page, 1200);

    await expect(
      page.getByRole("heading", { name: "Your orders", level: 1 }),
    ).toBeVisible();
    // The bug produced the empty state for every customer.
    await expect(page.getByText(/No orders yet/i)).toHaveCount(0);
    await expect(page.getByText(/1 order placed with us/i)).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Past orders" }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: /View details/i })).toBeVisible();

    console.log("[1] /orders body:\n" + (await page.locator("body").innerText()));

    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-orders-${testInfo.project.name}.png`),
      fullPage: true,
    });
    console.log("[1] console errors:", formatErrors(errors) || "none");
  });

  test("2 — /orders/[id] renders the order lines", async ({ page }, testInfo) => {
    const errors = collectErrors(page);
    await signIn(page);

    await page.goto(`/orders/${ORDER_ID}`, { waitUntil: "load" });
    await settle(page, 1200);

    await expect(page.getByText(APPLE, { exact: false }).first()).toBeVisible();
    await expect(page.getByText(BANANA, { exact: false }).first()).toBeVisible();
    // A missing `created_at` rendered as "Invalid Date".
    await expect(page.getByText(/Invalid Date/i)).toHaveCount(0);

    console.log(
      "[2] /orders/[id] body:\n" + (await page.locator("body").innerText()),
    );

    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-order-detail-${testInfo.project.name}.png`),
      fullPage: true,
    });
    console.log("[2] console errors:", formatErrors(errors) || "none");
  });

  test("3 — Buy again reprices history; add-one then add-all clamps", async ({
    page,
  }, testInfo) => {
    const errors = collectErrors(page);
    await signIn(page);

    await page.goto("/", { waitUntil: "load" });
    await settle(page, 1500);

    await expect(page.getByRole("heading", { name: "Buy again" })).toBeVisible();
    const apple = cardFor(page, APPLE);
    const banana = cardFor(page, BANANA);
    await expect(apple).toBeVisible();
    await expect(banana).toBeVisible();

    const appleText = await apple.innerText();
    const bananaText = await banana.innerText();
    console.log("[3] Apple card:\n" + appleText);
    console.log("[3] Banana card:\n" + bananaText);
    // Today's price (180 / 72), never the historical price paid (20 / 40).
    expect(appleText).toContain("180");
    expect(bananaText).toContain("72");
    expect(appleText).not.toContain("20.00");
    expect(bananaText).not.toContain("40.00");

    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-home-buyagain-${testInfo.project.name}.png`),
      fullPage: true,
    });
    // A focused shot of the rail itself (the full page is too tall to read).
    await buyAgain(page).screenshot({
      path: path.join(SHOT_DIR, `t06-buyagain-region-${testInfo.project.name}.png`),
    });

    // --- add one ---------------------------------------------------------
    await buyAgain(page)
      .getByRole("button", { name: `Add ${APPLE} to basket` })
      .click();
    await settle(page, 900);
    await expect(page.getByRole("link", { name: "Basket, 1 items" })).toBeVisible();

    // --- add all (remembered qty == quantity_limit == 5) ------------------
    // Apple: 1 + 5 = 6 → clamped to 5.  Banana: 0 + 5 = 5.  Total = 10.
    await buyAgain(page).getByRole("button", { name: "Add all" }).click();
    await settle(page, 1400);

    const appleQty = await apple.locator('span[aria-live="polite"]').innerText();
    const bananaQty = await banana.locator('span[aria-live="polite"]').innerText();
    console.log(`[3] after add-all → Apple=${appleQty} Banana=${bananaQty}`);
    await expect(page.getByRole("link", { name: "Basket, 10 items" })).toBeVisible();
    expect(appleQty, "Apple must clamp 6 → 5").toBe("5");
    expect(bananaQty).toBe("5");

    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-home-cart-${testInfo.project.name}.png`),
      fullPage: true,
    });
    console.log("[3] console errors:", formatErrors(errors) || "none");
  });

  test("4 — serviceability enables/disables Place order", async ({
    page,
  }, testInfo) => {
    const errors = collectErrors(page);
    logBadResponses(page);
    await signIn(page);

    await page.goto("/", { waitUntil: "load" });
    await settle(page, 1200);
    await buyAgain(page)
      .getByRole("button", { name: `Add ${APPLE} to basket` })
      .click();
    await settle(page, 900);

    await page.goto("/checkout", { waitUntil: "load" });
    await settle(page, 1500);

    // Default address is the in-area "Home" (Pune 411001).
    const inArea = page.getByText("We deliver to Pune, Maharashtra");
    await expect(inArea).toBeVisible({ timeout: 20_000 });

    // A slot is required for "Place order" to be enabled at all.
    await page.locator('[id^="slot-"]:not([disabled])').first().click();
    await settle(page, 900);

    const place = page.getByRole("button", { name: "Place order" });
    await expect(place).toBeEnabled({ timeout: 15_000 });
    console.log("[4] in-area: verdict shown, Place order ENABLED");
    await place.screenshot({
      path: path.join(SHOT_DIR, `t06-placeorder-inarea-${testInfo.project.name}.png`),
    });
    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-checkout-inarea-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // Switch to the out-of-area "Work" address (Mumbai 400001).
    await page.locator(`#address-${WORK_ADDRESS}`).click();
    const outArea = page.getByText("We deliver in Pune, Maharashtra only");
    await expect(outArea).toBeVisible({ timeout: 20_000 });
    await expect(place).toBeDisabled();
    console.log("[4] out-of-area: verdict shown, Place order DISABLED");
    await place.screenshot({
      path: path.join(SHOT_DIR, `t06-placeorder-outarea-${testInfo.project.name}.png`),
    });
    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-checkout-outarea-${testInfo.project.name}.png`),
      fullPage: true,
    });

    // Proof the ADDRESS is the cause: switching back re-enables it.
    await page.locator("#address-aaaa1111-0000-4000-8000-000000000001").click();
    await expect(inArea).toBeVisible({ timeout: 20_000 });
    await expect(place).toBeEnabled({ timeout: 15_000 });
    console.log("[4] back to in-area: Place order re-ENABLED (address is the cause)");

    console.log("[4] console errors:", formatErrors(errors) || "none");
  });

  test("5 — 360px: populated home + checkout do not overflow", async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== "mobile", "360px is a mobile check");
    await signIn(page);
    await page.setViewportSize({ width: 360, height: 780 });

    await page.goto("/", { waitUntil: "load" });
    await settle(page, 1500);
    await expect(page.getByRole("heading", { name: "Buy again" })).toBeVisible();
    const homeOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    console.log("[5] home overflow @360 =", homeOverflow);
    expect(homeOverflow, "home horizontal overflow at 360px").toBeLessThanOrEqual(0);
    await page.screenshot({
      path: path.join(SHOT_DIR, "t06-home-360-populated.png"),
      fullPage: true,
    });

    await buyAgain(page)
      .getByRole("button", { name: `Add ${APPLE} to basket` })
      .click();
    await settle(page, 900);
    await page.goto("/checkout", { waitUntil: "load" });
    await settle(page, 1500);
    await expect(page.getByText("We deliver to Pune, Maharashtra")).toBeVisible({
      timeout: 20_000,
    });
    const checkoutOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    console.log("[5] checkout overflow @360 =", checkoutOverflow);
    expect(
      checkoutOverflow,
      "checkout horizontal overflow at 360px",
    ).toBeLessThanOrEqual(0);
    await page.screenshot({
      path: path.join(SHOT_DIR, "t06-checkout-360-inarea.png"),
      fullPage: true,
    });
  });
});
