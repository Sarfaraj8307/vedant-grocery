"use client";

import Link from "next/link";
import { ShoppingBag } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { SectionHeading } from "@/components/ui/misc";
import { ProductImage } from "@/components/products/product-image";
import { PriceTag } from "@/components/products/price-tag";
import { QtyStepper } from "@/components/products/qty-stepper";
import { useCartSession } from "@/components/cart/cart-provider";
import { buildCartLine } from "@/lib/cart-line";
import { priceFor } from "@/lib/pricing";
import { pluralise } from "@/lib/format";
import type { BuyAgainItem } from "@/lib/buy-again";

/**
 * "Buy again" — the retention rail for returning customers.
 *
 * The items come from the customer's own order history (read on the server, see
 * `lib/buy-again.server.ts`) but every price shown and added is **today's**, via
 * the same `buildCartLine` the product grid uses. Nothing here replays a price
 * the customer paid weeks ago.
 *
 * The whole section renders `null` when there is no history, so a first-time or
 * signed-out visitor sees an unchanged home page.
 */
export function BuyAgainSection({
  items,
  unavailableCount = 0,
}: {
  items: BuyAgainItem[];
  /** Items from history that are unavailable today and were left out. */
  unavailableCount?: number;
}) {
  const { add } = useCartSession();

  if (items.length === 0) return null;

  /**
   * "Add all" respects the cart's merge + clamp semantics: it calls `add` per
   * line with `silent`, so a line already in the basket is *merged* and capped at
   * `products.quantity_limit` exactly as a manual add would be — never a
   * surprise overwrite.
   */
  function addAll() {
    for (const item of items) {
      add(buildCartLine(item.product, item.weight, item.qty), { silent: true });
    }
    toast.success(`${pluralise(items.length, "item")} added to your basket`, {
      description: "We repriced everything at today's rates.",
      duration: 4000,
    });
  }

  return (
    <section aria-labelledby="buy-again-heading">
      <SectionHeading
        id="buy-again-heading"
        title="Buy again"
        subtitle="Your usuals, repriced at today's rates."
        action={
          <Button type="button" variant="secondary" size="sm" onClick={addAll}>
            <ShoppingBag className="size-4" aria-hidden="true" />
            Add all
          </Button>
        }
      />

      <ul className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
        {items.map((item) => (
          <li key={item.product.id} className="flex">
            <BuyAgainCard item={item} />
          </li>
        ))}
      </ul>

      {unavailableCount > 0 ? (
        <p className="mt-3 text-small text-ink-subtle">
          {pluralise(unavailableCount, "item")} from your history{" "}
          {unavailableCount === 1 ? "is" : "are"} not available today and{" "}
          {unavailableCount === 1 ? "was" : "were"} left out.
        </p>
      ) : null}
    </section>
  );
}

function BuyAgainCard({ item }: { item: BuyAgainItem }) {
  const breakdown = priceFor(item.product, item.weight);

  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-xs transition-[box-shadow,border-color,transform] duration-[var(--motion-base)] ease-[var(--ease-standard)] hover:-translate-y-0.5 hover:border-line-strong hover:shadow-produce motion-reduce:hover:translate-y-0">
      <Link
        href={`/products/${item.product.id}`}
        className="relative block rounded-t-xl"
        tabIndex={-1}
        aria-hidden="true"
      >
        <ProductImage
          source={{
            imageUrl: item.product.image_url,
            glyph: item.product.glyph,
            accent: item.product.accent,
            name: item.product.name,
          }}
          size="card"
        />
      </Link>

      <div className="flex flex-1 flex-col gap-1 p-2.5 pt-2">
        <h3 className="text-h3 text-ink">
          <Link
            href={`/products/${item.product.id}`}
            className="line-clamp-2 rounded-xs focus-visible:ring-2 focus-visible:ring-leaf-600/25 focus-visible:outline-none"
          >
            {item.product.name}
          </Link>
        </h3>

        <p className="text-caption normal-case text-ink-subtle">
          {item.weight ? item.weight.label : `per ${item.product.unit_label}`}
          {item.substituted ? (
            <span className="text-marigold-600"> · pack changed</span>
          ) : null}
        </p>

        {/*
         * Same price-row contract as `ProductCard`: `flex-wrap` on the PriceTag
         * lets a struck-through compare price drop to its own line rather than
         * squeezing the stepper out of the card's `overflow-hidden` edge (the
         * 11px ADD-clipping regression).
         */}
        <div className="mt-auto flex items-end justify-between gap-x-1.5 pt-2">
          <PriceTag
            amount={breakdown.unitPrice}
            compareAt={breakdown.isDiscounted ? breakdown.listPrice : undefined}
            size="sm"
            className="flex-wrap gap-x-1.5"
          />
          <QtyStepper
            product={item.product}
            weight={item.weight}
            size="sm"
            className="shrink-0"
          />
        </div>
      </div>
    </article>
  );
}
