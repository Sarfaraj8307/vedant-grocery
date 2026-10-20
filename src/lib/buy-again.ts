import type { CatalogueProduct, ProductWeightRow } from "@/types/database";

/**
 * "Buy again" — the retention surface on the home page.
 *
 * This module holds only the **pure** shape and helpers so it can be imported by
 * both the Server Component that reads history (`buy-again.server.ts`) and the
 * Client island that renders it (`components/home/buy-again.tsx`) without
 * dragging `server-only` into the browser bundle.
 *
 * The one rule that matters: a purchase is a *hint*, never a price. The stored
 * `order_items.weight_label` identifies the pack the customer bought; the price
 * always comes from today's catalogue (see `buildCartLine`). Replaying a stale
 * price would be dishonest and the server would reject it anyway.
 */

/** The minimal order-items projection "buy again" needs. */
export type BuyAgainOrderItem = {
  product_id: string | null;
  product_name: string;
  weight_label: string | null;
  qty: number;
  rejected: boolean;
};

/** An order with just enough of its lines to rebuild a basket. */
export type BuyAgainOrder = {
  order_items: BuyAgainOrderItem[] | null;
};

/** One de-duplicated purchase, before it is matched to the live catalogue. */
export type BuyAgainSourceItem = {
  productId: string;
  productName: string;
  /** The pack label the customer bought; `""` when the line had none. */
  weightLabel: string;
  qty: number;
};

/** One resolved "buy again" row, ready to render and add to the basket. */
export type BuyAgainItem = {
  product: CatalogueProduct;
  /** The matched weight variant, or `null` for a base-price product. */
  weight: ProductWeightRow | null;
  /** The pack the customer bought last time (for the caption). */
  purchasedWeightLabel: string;
  /** True when that exact pack no longer exists and the default was used. */
  substituted: boolean;
  /** The quantity bought last time — used by "add all". */
  qty: number;
};

/**
 * Walk orders **newest-first** and keep the most recent purchase of each
 * product, de-duplicated, up to `limit`.
 *
 * Rejected lines were never delivered, so they are skipped — offering them again
 * would be wrong. A line whose product has since been deleted (`product_id` is
 * null) is skipped too.
 */
export function pickRecentPurchases(
  orders: readonly BuyAgainOrder[],
  limit: number,
): BuyAgainSourceItem[] {
  const seen = new Set<string>();
  const picks: BuyAgainSourceItem[] = [];

  for (const order of orders) {
    for (const item of order.order_items ?? []) {
      if (item.rejected) continue;
      if (!item.product_id) continue;
      if (seen.has(item.product_id)) continue;

      seen.add(item.product_id);
      picks.push({
        productId: item.product_id,
        productName: item.product_name,
        weightLabel: item.weight_label ?? "",
        qty: Math.max(1, Math.trunc(item.qty) || 1),
      });

      if (picks.length >= limit) return picks;
    }
  }

  return picks;
}
