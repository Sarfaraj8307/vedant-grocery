import "server-only";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import {
  PRODUCT_SELECT,
  toCatalogueProduct,
  type ProductNestedRow,
} from "@/lib/catalogue-shared";
import { defaultWeight, matchWeightByLabel, unavailableReason } from "@/lib/cart-line";
import {
  pickRecentPurchases,
  type BuyAgainItem,
  type BuyAgainOrder,
} from "@/lib/buy-again";

/**
 * Server read for the "Buy again" rail.
 *
 * Reads the customer's **own** order history (RLS scopes `orders` /
 * `order_items` to `user_id = auth.uid()`, so no explicit filter is needed and
 * none is added — adding one would imply the filter is the protection), then
 * resolves each remembered purchase against **today's** catalogue so the price
 * and availability are live.
 *
 * Never throws: the home page must render even if this read fails, so every
 * failure path returns an empty result. That keeps a database hiccup from
 * turning the storefront into an error boundary.
 */

/** How many recent orders to scan for a de-duplicated product set. */
const ORDER_SCAN_LIMIT = 25;

export type BuyAgainResult = {
  items: BuyAgainItem[];
  /** Products the customer bought that are unavailable today (left out). */
  unavailableCount: number;
};

const EMPTY: BuyAgainResult = { items: [], unavailableCount: 0 };

export async function getBuyAgainItems(limit = 8): Promise<BuyAgainResult> {
  try {
    const supabase = await getSupabaseServerClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) return EMPTY;

    const { data, error } = await supabase
      .from("orders")
      .select(
        "placed_at, order_items ( product_id, product_name, weight_label, qty, rejected )",
      )
      .order("placed_at", { ascending: false })
      .limit(ORDER_SCAN_LIMIT);

    if (error) return EMPTY;

    // Over-fetch candidates so that dropping today's unavailable items still
    // leaves a full rail.
    const picks = pickRecentPurchases(
      (data ?? []) as unknown as BuyAgainOrder[],
      limit * 3,
    );
    if (picks.length === 0) return EMPTY;

    const ids = [...new Set(picks.map((pick) => pick.productId))];
    const { data: rows, error: productsError } = await supabase
      .from("products")
      .select(PRODUCT_SELECT)
      .in("id", ids);

    if (productsError) return EMPTY;

    const now = new Date();
    const byId = new Map<string, ReturnType<typeof toCatalogueProduct>>(
      ((rows ?? []) as unknown as ProductNestedRow[]).map((row) => {
        const product = toCatalogueProduct(row, now);
        return [product.id, product] as const;
      }),
    );

    const items: BuyAgainItem[] = [];
    let unavailableCount = 0;

    for (const pick of picks) {
      const product = byId.get(pick.productId);
      if (!product) {
        unavailableCount += 1;
        continue;
      }

      // Canonical "can I buy this today?" test — the same one the product card
      // uses, so the rail can never advertise something the grid greys out.
      if (unavailableReason(product, defaultWeight(product)) !== null) {
        unavailableCount += 1;
        continue;
      }

      const { weight, substituted } = matchWeightByLabel(
        product,
        pick.weightLabel,
      );

      items.push({
        product,
        weight,
        purchasedWeightLabel: pick.weightLabel || (weight?.label ?? ""),
        substituted,
        qty: pick.qty,
      });

      if (items.length >= limit) break;
    }

    return { items, unavailableCount };
  } catch {
    return EMPTY;
  }
}
