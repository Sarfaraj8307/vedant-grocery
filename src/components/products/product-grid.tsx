import { ProductCard } from "@/components/products/product-card";
import { ProductRailCard } from "@/components/products/product-rail-card";
import type { CatalogueProduct } from "@/types/database";
import { cn } from "@/lib/utils";

/**
 * Responsive product grid — 2 / 3 / 4 columns (5 on very wide screens).
 *
 * Columns are decided by the *container* (`auto-fill` with a min track) as well
 * as the viewport, so the grid behaves correctly inside a narrower checkout
 * column as well as across the full page width.
 */
export function ProductGrid({
  products,
  priorityCount = 4,
  className,
}: {
  products: readonly CatalogueProduct[];
  /** How many leading cards get `priority` — the first visible row. */
  priorityCount?: number;
  className?: string;
}) {
  return (
    <ul
      className={cn(
        "grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5",
        className,
      )}
    >
      {products.map((product, index) => (
        <li key={product.id} className="flex">
          <ProductCard
            product={product}
            priority={index < priorityCount}
            className="w-full"
          />
        </li>
      ))}
    </ul>
  );
}

/**
 * Horizontally scrolling rail. Used for "popular", "fresh arrivals" and related
 * products, where a rail keeps the page short and signals "there's more".
 *
 * A rail is an **intentional** horizontal scroll region: it uses scroll-snap,
 * contains overscroll so it never hijacks the page, and carries
 * `role="list"` semantics for screen readers.
 */
export function ProductRail({
  products,
  ariaLabel,
  className,
}: {
  products: readonly CatalogueProduct[];
  ariaLabel: string;
  className?: string;
}) {
  return (
    <ul
      aria-label={ariaLabel}
      className={cn(
        "rail-snap -mx-4 gap-3 px-4 pb-1 sm:-mx-6 sm:px-6 lg:mx-0 lg:px-0",
        className,
      )}
    >
      {products.map((product, index) => (
        <li
          key={product.id}
          className="w-[46vw] max-w-[200px] shrink-0 snap-start sm:w-[190px]"
        >
          <ProductCard product={product} priority={index < 2} />
        </li>
      ))}
    </ul>
  );
}

export { ProductRailCard };
