import path from "node:path";
import { test, expect } from "./fixtures";
import { collectErrors, formatErrors, settle } from "./helpers";

/**
 * T06 home-page gaps — driven-app evidence.
 *
 * Covers the three surfaces added in T06 that a signed-out visitor can reach:
 *   • the "Delivery location" section (invitation state when there is no address);
 *   • the mobile header delivery row;
 *   • the absence of "Buy again" for a customer with no order history.
 *
 * The signed-in states (a real saved address, a populated Buy again rail) cannot
 * be exercised here — sign-in requires an email-confirmed account and this run
 * has no session. The pure logic behind both is pinned by unit tests
 * (`src/lib/serviceability.test.ts`, `src/lib/buy-again.test.ts`).
 *
 * Screenshots are written to `.logs/verify/` for review.
 */

const SHOT_DIR = path.resolve(process.cwd(), ".logs", "verify");

test.describe("T06 home gaps", () => {
  test("signed out: delivery location is offered, Buy again is absent", async ({
    page,
  }, testInfo) => {
    const errors = collectErrors(page);

    await page.goto("/", { waitUntil: "load" });
    await settle(page);

    // The delivery-location section renders its honest "no address yet" state.
    const delivery = page.getByRole("region", { name: "Delivery location" });
    await expect(delivery).toBeVisible();
    await expect(delivery).toContainText("Set your delivery location");
    await expect(delivery).toContainText("Pune, Maharashtra");

    // It offers a real, working way to add one.
    const addAddress = delivery.getByRole("link", { name: /Add an address/i });
    await expect(addAddress).toBeVisible();
    await expect(addAddress).toHaveAttribute("href", "/account/addresses");

    // No history ⇒ no Buy again rail at all.
    await expect(page.getByRole("heading", { name: "Buy again" })).toHaveCount(0);

    await page.screenshot({
      path: path.join(SHOT_DIR, `t06-home-${testInfo.project.name}.png`),
      fullPage: true,
    });

    expect(errors, `console errors:\n${formatErrors(errors)}`).toEqual([]);
  });

  test("mobile header carries a delivery row that links to the address book", async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== "mobile", "mobile-only row");

    await page.goto("/", { waitUntil: "load" });
    await settle(page);

    const row = page.getByRole("link", { name: "Set your delivery location" });
    await expect(row).toBeVisible();
    await expect(row).toHaveAttribute("href", "/account/addresses");

    // Tapping it lands on the (guarded) address book: signed out, the guard
    // sends the customer to sign-in and preserves where they were heading.
    await row.click();
    await page.waitForURL(/\/login(\?|$)/, { timeout: 15_000 });
    expect(new URL(page.url()).searchParams.get("next")).toBe(
      "/account/addresses",
    );
    await expect(page.getByRole("heading", { level: 1 })).toContainText(
      /Welcome back/i,
    );
  });

  test("360px: the home page does not overflow horizontally", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await page.goto("/", { waitUntil: "load" });
    await settle(page);

    // The 11px ADD-clipping class of regression shows up as a document wider
    // than the viewport. Assert there is none.
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow, "horizontal overflow at 360px").toBeLessThanOrEqual(0);

    await expect(
      page.getByRole("region", { name: "Delivery location" }),
    ).toBeVisible();

    await page.screenshot({
      path: path.join(SHOT_DIR, "t06-home-360.png"),
      fullPage: true,
    });
  });
});
