import { test, expect } from "./fixtures";
import type { Page } from "@playwright/test";
import path from "node:path";
import { availableWeightIds, CART_STORAGE_KEY } from "./catalogue";

/**
 * HYDRATION SUSPECT — confirm or clear.
 *
 * The lead flagged a React hydration mismatch on the HOME page traced to
 * `src/components/products/qty-stepper.tsx` (rendered via `RateBoard` in
 * `src/components/home/home-sections.tsx`).
 *
 * Root-cause hypothesis: the Zustand cart is persisted to `localStorage`
 * (`vedant.cart.v1`) and rehydrates synchronously on the client. If a returning
 * customer already has a basket line for a product shown on the RateBoard, the
 * SERVER renders the `ADD` pill (quantity 0, no localStorage) while the CLIENT
 * renders the `− n +` stepper (quantity > 0) → the two trees disagree.
 *
 * These tests assert the CORRECT behaviour (a clean hydration). A failure here
 * IS the defect evidence — see the captured component stack in the report.
 */

const SHOT_DIR = path.resolve(process.cwd(), ".logs", "verify");

type SeedLine = { productId: string; weightId: string | null; name: string };

async function seedLines(page: Page, lines: SeedLine[]) {
  const record: Record<string, unknown> = {};
  for (const l of lines) {
    const key = `${l.productId}::${l.weightId ?? "base"}`;
    record[key] = {
      productId: l.productId,
      weightId: l.weightId,
      quantity: 2,
      name: l.name,
      glyph: "🍅",
      accent: 12,
      imageUrl: null,
      unitLabel: "kg",
      weightLabel: "500g",
      unitPrice: 30,
      listPrice: 40,
      quantityLimit: 10,
      addedAt: 1_700_000_000_000,
    };
  }
  const state = JSON.stringify({ state: { lines: record }, version: 1 });
  await page.addInitScript(
    ([k, v]) => window.localStorage.setItem(k as string, v as string),
    [CART_STORAGE_KEY, state] as [string, string],
  );
}

test.describe("Home page hydration", () => {
  test("returning customer with a RateBoard item in the basket hydrates cleanly", async ({
    page,
  }, testInfo) => {
    const hydrationErrors: string[] = [];
    page.on("pageerror", (err) => {
      if (/Hydration failed|hydration-mismatch|did not match/i.test(err.message)) {
        hydrationErrors.push(err.message);
      }
    });
    page.on("console", (msg) => {
      if (
        msg.type() === "error" &&
        /Hydration failed|hydration-mismatch|did not match/i.test(msg.text())
      ) {
        hydrationErrors.push(msg.text());
      }
    });

    // 1. Learn which product the RateBoard shows first (clean load).
    await page.goto("/");
    await page.waitForLoadState("load");
    const rateBoard = page.locator('section[aria-labelledby="rate-board-heading"]');
    await expect(rateBoard).toBeVisible();
    const href = await rateBoard
      .locator('a[href^="/products/"]')
      .first()
      .getAttribute("href");
    const productId = href?.split("/").pop();
    expect(productId, "could not resolve a RateBoard product id").toBeTruthy();

    // 2. Seed a basket line for every available weight of that product so the
    //    server's defaultWeight() choice is guaranteed to match.
    const weightIds = await availableWeightIds(productId!);
    const seed: SeedLine[] = weightIds.length
      ? weightIds.map((weightId) => ({ productId: productId!, weightId, name: "RateBoard item" }))
      : [{ productId: productId!, weightId: null, name: "RateBoard item" }];
    await seedLines(page, seed);

    // 3. Reload — server HTML (quantity 0) vs client rehydration (quantity 2).
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(3000);

    await page.screenshot({
      path: path.join(SHOT_DIR, `hydration-home-${testInfo.project.name}.png`),
      fullPage: true,
    });

    expect(
      hydrationErrors,
      `Hydration mismatch on / with a pre-seeded cart:\n${hydrationErrors.join("\n\n")}`,
    ).toEqual([]);
  });

  test("RateBoard stepper stays interactive after hydration (desktop)", async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== "desktop", "RateBoard stepper is hidden below sm");

    await page.goto("/");
    await page.waitForLoadState("load");
    const rateBoard = page.locator('section[aria-labelledby="rate-board-heading"]');
    const href = await rateBoard.locator('a[href^="/products/"]').first().getAttribute("href");
    const productId = href!.split("/").pop()!;
    const weightIds = await availableWeightIds(productId);
    const seed: SeedLine[] = weightIds.length
      ? weightIds.map((weightId) => ({ productId, weightId, name: "RateBoard item" }))
      : [{ productId, weightId: null, name: "RateBoard item" }];
    await seedLines(page, seed);
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(2500);

    // The stepper inside the RateBoard should show the seeded quantity and
    // respond to a tap (i.e. the mismatch is cosmetic, not fatal).
    const stepper = rateBoard
      .locator('span.hidden.sm\\:block')
      .first()
      .getByRole("button", { name: /Increase .* quantity/i });
    await expect(stepper).toBeVisible();
    await stepper.click();
    await page.waitForTimeout(400);
    await expect(
      rateBoard.locator('span.hidden.sm\\:block').first().getByText("3", { exact: true }),
    ).toBeVisible();
  });
});
