"use client";

import Link from "next/link";
import { Trash2 } from "lucide-react";
import { ProductImage } from "@/components/products/product-image";
import { PriceTag } from "@/components/products/price-tag";
import { useCartSession } from "@/components/cart/cart-provider";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import type { CartLine } from "@/stores/cart-store";

/**
 * One line in the basket.
 *
 * The layout is optimised for the two questions a customer actually asks:
 * "what is this and how much is it" (left + price) and "how do I change it"
 * (the stepper, right, in the thumb zone).
 *
 * The remove button is a separate, clearly-labelled control rather than a
 * destructive swipe: swipe-to-delete is invisible, hard to discover and
 * impossible with a keyboard or screen reader.
 */
export function CartLineRow({ line }: { line: CartLine }) {
  const { increment, decrement, remove } = useCartSession();
  const lineTotal = Math.round(line.unitPrice * 100) * line.quantity / 100;
  const showCompare =
    line.listPrice > line.unitPrice
      ? Math.round(line.listPrice * 100) * line.quantity / 100
      : undefined;

  return (
    <li className="flex gap-3 py-3.5">
      <Link
        href={`/products/${line.productId}`}
        className="shrink-0 rounded-md focus-visible:outline-none"
        tabIndex={-1}
        aria-hidden="true"
      >
        <ProductImage
          source={{
            imageUrl: line.imageUrl,
            glyph: line.glyph,
            accent: line.accent,
            name: line.name,
          }}
          size="thumb"
          className="size-16 overflow-hidden rounded-md border border-line"
        />
      </Link>

      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-body font-medium text-ink">
              <Link
                href={`/products/${line.productId}`}
                className="rounded-xs hover:underline"
              >
                {line.name}
              </Link>
            </h3>
            <p className="mt-0.5 text-small text-ink-subtle">
              {line.weightLabel} · {formatMoney(line.unitPrice)} each
            </p>
          </div>

          <button
            type="button"
            onClick={() => remove(line.productId, line.weightId)}
            className="grid size-8 shrink-0 place-items-center rounded-md text-ink-subtle transition-colors hover:bg-danger-bg hover:text-danger focus-visible:outline-none"
          >
            <Trash2 className="size-4" aria-hidden="true" />
            <span className="sr-only">Remove {line.name} from basket</span>
          </button>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <PriceTag amount={lineTotal} compareAt={showCompare} size="md" />

          <div className="inline-flex items-center rounded-pill border border-line-strong bg-surface">
            <button
              type="button"
              onClick={() => decrement(line.productId, line.weightId)}
              className={cn(
                "grid size-9 place-items-center rounded-l-pill text-ink transition-colors hover:bg-surface-muted",
                "focus-visible:outline-none",
              )}
              aria-label={
                line.quantity === 1
                  ? `Remove ${line.name} from basket`
                  : `Reduce ${line.name} quantity`
              }
            >
              <span aria-hidden="true" className="text-lg leading-none">
                −
              </span>
            </button>
            <span
              className="min-w-8 text-center text-body font-semibold tabular-nums text-ink"
              data-numeric
              aria-live="polite"
            >
              {line.quantity}
            </span>
            <button
              type="button"
              disabled={line.quantity >= line.quantityLimit}
              onClick={() => increment(line.productId, line.weightId)}
              className="grid size-9 place-items-center rounded-r-pill text-ink transition-colors hover:bg-surface-muted disabled:cursor-not-allowed disabled:text-ink-subtle focus-visible:outline-none"
              aria-label={
                line.quantity >= line.quantityLimit
                  ? `Maximum ${line.quantityLimit} of ${line.name} in basket`
                  : `Increase ${line.name} quantity`
              }
            >
              <span aria-hidden="true" className="text-lg leading-none">
                +
              </span>
            </button>
          </div>
        </div>

        {line.quantity >= line.quantityLimit ? (
          <p className="text-[10px] font-medium text-marigold-600">
            Maximum {line.quantityLimit} per order
          </p>
        ) : null}
      </div>
    </li>
  );
}
