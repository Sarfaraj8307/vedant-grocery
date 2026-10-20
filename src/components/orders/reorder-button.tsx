"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { RotateCcw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useCartSession } from "@/components/cart/cart-provider";
import { getProductsByIds } from "@/services/catalogue-client";
import { buildCartLine, matchWeightByLabel } from "@/lib/cart-line";
import type { OrderItemRow } from "@/types/database";

/**
 * "Order again".
 *
 * Reprices against **today's** catalogue rather than replaying the prices the
 * customer paid previously — which is the only honest behaviour, since market
 * rates move daily and the server would reject a stale price anyway.
 *
 * Weight variants are matched by label (`order_items.weight_label` is the
 * snapshot column; `order_items` deliberately does not store `weight_id`, because
 * a weight row can be deleted while the order history must survive). When the
 * label no longer exists, the product's default available pack is used and the
 * customer is told which substitution happened rather than being silently
 * switched.
 */
export function ReorderButton({
  items,
  className,
  redirectToCart = true,
}: {
  items: Pick<
    OrderItemRow,
    "product_id" | "product_name" | "weight_label" | "qty" | "rejected"
  >[];
  className?: string;
  redirectToCart?: boolean;
}) {
  const router = useRouter();
  const { add } = useCartSession();
  const [working, setWorking] = React.useState(false);

  async function reorder() {
    setWorking(true);
    try {
      // Rejected lines were never delivered, so offering them again is wrong.
      const wanted = items.filter((item) => !item.rejected && item.product_id);
      const ids = [...new Set(wanted.map((item) => item.product_id!))];

      if (ids.length === 0) {
        toast.error("Nothing in this order can be reordered.");
        return;
      }

      const products = await getProductsByIds(ids);
      const byId = new Map(products.map((product) => [product.id, product]));

      const substitutions: string[] = [];
      const missing: string[] = [];
      let added = 0;

      for (const item of wanted) {
        const product = byId.get(item.product_id!);
        if (!product || !product.availability) {
          missing.push(item.product_name);
          continue;
        }

        // Shared with "buy again" so both entry points substitute identically.
        const { weight: matched, substituted } = matchWeightByLabel(
          product,
          item.weight_label,
        );

        if (substituted && matched) {
          substitutions.push(`${item.product_name} → ${matched.label}`);
        }

        add(buildCartLine(product, matched, item.qty), { silent: true });
        added += 1;
      }

      if (added === 0) {
        toast.error(
          missing.length > 0
            ? `${missing.join(", ")} ${missing.length === 1 ? "is" : "are"} not available today.`
            : "We could not add those items. Please browse the catalogue.",
        );
        return;
      }

      toast.success(
        `${added} ${added === 1 ? "item" : "items"} added to your basket`,
        {
          description:
            substitutions.length > 0
              ? `Pack size changed for: ${substitutions.join("; ")}`
              : missing.length > 0
                ? `Not available today: ${missing.join(", ")}`
                : undefined,
          duration: 5000,
        },
      );

      if (redirectToCart) router.push("/cart");
    } catch {
      toast.error("We could not rebuild that basket. Please try again.");
    } finally {
      setWorking(false);
    }
  }

  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      className={className}
      loading={working}
      loadingLabel="Adding to basket…"
      onClick={() => void reorder()}
    >
      <RotateCcw className="size-3.5" aria-hidden="true" />
      Order again
    </Button>
  );
}
