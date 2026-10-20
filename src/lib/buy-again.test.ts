import { describe, expect, it } from "vitest";

import {
  pickRecentPurchases,
  type BuyAgainOrder,
  type BuyAgainOrderItem,
} from "@/lib/buy-again";

/**
 * "Buy again" derives a de-duplicated, most-recent-first set of products from
 * order history. These cases pin the two rules that matter: newest wins, and
 * lines that were never delivered (rejected) are never offered again.
 */

function line(over: Partial<BuyAgainOrderItem> = {}): BuyAgainOrderItem {
  return {
    product_id: "p1",
    product_name: "Apple",
    weight_label: "1 kg",
    qty: 2,
    rejected: false,
    ...over,
  };
}

function order(...items: BuyAgainOrderItem[]): BuyAgainOrder {
  return { order_items: items };
}

describe("pickRecentPurchases", () => {
  it("returns nothing for empty history", () => {
    expect(pickRecentPurchases([], 8)).toEqual([]);
    expect(pickRecentPurchases([order()], 8)).toEqual([]);
  });

  it("keeps the most recent purchase of each product (orders are newest-first)", () => {
    const picks = pickRecentPurchases(
      [
        order(line({ product_id: "p1", weight_label: "500 g", qty: 3 })),
        order(line({ product_id: "p1", weight_label: "1 kg", qty: 1 })),
      ],
      8,
    );
    expect(picks).toHaveLength(1);
    expect(picks[0]).toMatchObject({ productId: "p1", weightLabel: "500 g", qty: 3 });
  });

  it("skips rejected lines — they were never delivered", () => {
    const picks = pickRecentPurchases(
      [order(line({ product_id: "p1", rejected: true }), line({ product_id: "p2" }))],
      8,
    );
    expect(picks.map((p) => p.productId)).toEqual(["p2"]);
  });

  it("skips lines whose product has been deleted (null product_id)", () => {
    const picks = pickRecentPurchases(
      [order(line({ product_id: null }), line({ product_id: "p2" }))],
      8,
    );
    expect(picks.map((p) => p.productId)).toEqual(["p2"]);
  });

  it("respects the limit", () => {
    const picks = pickRecentPurchases(
      [order(line({ product_id: "p1" }), line({ product_id: "p2" }), line({ product_id: "p3" }))],
      2,
    );
    expect(picks).toHaveLength(2);
    expect(picks.map((p) => p.productId)).toEqual(["p1", "p2"]);
  });

  it("normalises a missing weight label and a non-positive quantity", () => {
    const picks = pickRecentPurchases(
      [order(line({ weight_label: null, qty: 0 }))],
      8,
    );
    expect(picks[0]).toMatchObject({ weightLabel: "", qty: 1 });
  });
});
