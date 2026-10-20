import Link from "next/link";
import { PriceTag, SavingLabel } from "@/components/products/price-tag";
import { ProductImage } from "@/components/products/product-image";
import { QtyStepper } from "@/components/products/qty-stepper";
import { Badge } from "@/components/ui/badge";
import { cheapestOption, priceFor } from "@/lib/pricing";
import { defaultWeight, unavailableReason } from "@/lib/cart-line";
import type { CatalogueProduct } from "@/types/database";
import { cn } from "@/lib/utils";

/**
 * ProductCard — DESIGN_SYSTEM §7.2.
 *
 * Deliberately a **Server Component**: the grid's HTML, prices and links arrive
 * in the first response. Only the `QtyStepper` is a client island, so a
 * 40-product catalogue ships 40 tiny interactive components instead of 40 full
 * React subtrees.
 *
 * Layout guarantees:
 *  • the media well is always 1:1, so glyph and photo states are identical;
 *  • the name is clamped to two lines, so a long name cannot desynchronise the
 *    price row across a grid row;
 *  • the stepper's width is fixed, so tapping ADD never reflows the grid.
 */
export function ProductCard({
  product,
  priority = false,
  className,
}: {
  product: CatalogueProduct;
  priority?: boolean;
  className?: string;
}) {
  const weight = defaultWeight(product);
  const breakdown = priceFor(product, weight);
  const cheapest = cheapestOption(product);
  const reason = unavailableReason(product, weight);
  const inStock = reason === null;

  // Show the cheapest available price so the grid never advertises a price the
  // customer cannot actually buy at.
  const displayPrice = cheapest?.breakdown.unitPrice ?? breakdown.unitPrice;
  const displayCompare = cheapest?.breakdown.isDiscounted
    ? cheapest.breakdown.listPrice
    : undefined;
  const savingPercent = cheapest?.breakdown.savingPercent ?? 0;

  return (
    <article
      className={cn(
        "group relative flex flex-col overflow-hidden rounded-xl border border-line bg-surface",
        "shadow-xs transition-[box-shadow,border-color,transform] duration-[var(--motion-base)] ease-[var(--ease-standard)]",
        inStock &&
          "hover:-translate-y-0.5 hover:border-line-strong hover:shadow-produce motion-reduce:hover:translate-y-0",
        className,
      )}
    >
      <Link
        href={`/products/${product.id}`}
        className="relative block rounded-t-xl focus-visible:ring-2 focus-visible:ring-leaf-600/25 focus-visible:outline-none"
        tabIndex={-1}
        aria-hidden="true"
      >
        <ProductImage
          source={{
            imageUrl: product.image_url,
            glyph: product.glyph,
            accent: product.accent,
            name: product.name,
          }}
          size="card"
          priority={priority}
        />

        <div className="absolute left-2 top-2 flex flex-col items-start gap-1">
          {savingPercent > 0 ? (
            <SavingLabel percent={savingPercent} />
          ) : product.activeOffer ? (
            <Badge variant="offer" size="sm">
              {product.activeOffer.title}
            </Badge>
          ) : null}
        </div>

        {!inStock ? (
          <div className="absolute inset-0 grid place-items-center bg-surface/72">
            <span className="rounded-pill border border-line-strong bg-surface px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-muted">
              Out of stock
            </span>
          </div>
        ) : null}
      </Link>

      <div className="flex flex-1 flex-col gap-1 p-2.5 pt-2">
        <h3 className="text-h3 text-ink">
          <Link
            href={`/products/${product.id}`}
            className="line-clamp-2 rounded-xs focus-visible:ring-2 focus-visible:ring-leaf-600/25 focus-visible:outline-none"
          >
            {product.name}
          </Link>
        </h3>

        <p className="text-caption normal-case text-ink-subtle">
          {weight ? weight.label : `per ${product.unit_label}`}
          {weight ? (
            <span className="text-ink-subtle/70"> · {product.unit_label}</span>
          ) : null}
        </p>

        {/*
         * Price row budget. On a 360–390px phone a card's content box is
         * ~144–154px, and `₹104  ₹130  [ADD]` needs ~165px. So this row is the
         * one place where a compact grid can silently clip: the stepper has a
         * hard `min-width` (its two states must be the same width), which used
         * to push it 11px past the card's `overflow-hidden` edge — the ADD label
         * was sliced off. The fallback is to let the *price* wrap and the stepper
         * stay put: `flex-wrap` on the PriceTag lets the struck-through compare
         * price drop to a second line instead of squeezing the control the
         * customer is trying to hit. Nothing is hidden and nothing overlaps.
         */}
        <div className="mt-auto flex items-end justify-between gap-x-1.5 pt-2">
          <PriceTag
            amount={displayPrice}
            compareAt={displayCompare}
            size="sm"
            className="flex-wrap gap-x-1.5"
          />
          <QtyStepper
            product={product}
            weight={weight}
            size="sm"
            className="shrink-0"
            disabled={!inStock}
            disabledLabel={
              reason === "Out of stock" ? "Sold out" : "Unavailable"
            }
          />
        </div>
      </div>
    </article>
  );
}
