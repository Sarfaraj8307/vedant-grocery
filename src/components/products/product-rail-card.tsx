import Link from "next/link";
import { PriceTag, SavingLabel } from "@/components/products/price-tag";
import { ProductImage } from "@/components/products/product-image";
import { QtyStepper } from "@/components/products/qty-stepper";
import { cheapestOption, priceFor } from "@/lib/pricing";
import { defaultWeight, unavailableReason } from "@/lib/cart-line";
import type { CatalogueProduct } from "@/types/database";
import { cn } from "@/lib/utils";

/**
 * The wider card used inside editorial rails (fresh arrivals, recommendations).
 *
 * Visually the same language as `ProductCard`, with room for the product
 * description — which is what makes a rail feel curated rather than merely a
 * narrower grid.
 */
export function ProductRailCard({
  product,
  className,
}: {
  product: CatalogueProduct;
  className?: string;
}) {
  const weight = defaultWeight(product);
  const breakdown = priceFor(product, weight);
  const cheapest = cheapestOption(product);
  const reason = unavailableReason(product, weight);
  const inStock = reason === null;

  const displayPrice = cheapest?.breakdown.unitPrice ?? breakdown.unitPrice;
  const displayCompare = cheapest?.breakdown.isDiscounted
    ? cheapest.breakdown.listPrice
    : undefined;
  const savingPercent = cheapest?.breakdown.savingPercent ?? 0;

  return (
    <article
      className={cn(
        "flex h-full flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-xs",
        className,
      )}
    >
      <div className="relative">
        <ProductImage
          source={{
            imageUrl: product.image_url,
            glyph: product.glyph,
            accent: product.accent,
            name: product.name,
          }}
          size="rail"
        />
        {savingPercent > 0 ? (
          <span className="absolute left-2 top-2">
            <SavingLabel percent={savingPercent} />
          </span>
        ) : null}
        {!inStock ? (
          <div className="absolute inset-0 grid place-items-center bg-surface/72">
            <span className="rounded-pill border border-line-strong bg-surface px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-muted">
              Out of stock
            </span>
          </div>
        ) : null}
      </div>

      <div className="flex flex-1 flex-col gap-1.5 p-3">
        <h3 className="text-h3 text-ink">
          <Link href={`/products/${product.id}`} className="rounded-xs">
            {product.name}
          </Link>
        </h3>
        <p className="line-clamp-2 text-small text-ink-muted">
          {product.description}
        </p>
        <p className="text-caption normal-case text-ink-subtle">
          {weight ? weight.label : `per ${product.unit_label}`}
        </p>

        <div className="mt-auto flex items-end justify-between gap-x-1.5 pt-2.5">
          <PriceTag
            amount={displayPrice}
            compareAt={displayCompare}
            className="flex-wrap"
          />
          <QtyStepper
            product={product}
            weight={weight}
            size="sm"
            className="shrink-0"
            disabled={!inStock}
            disabledLabel="Sold out"
          />
        </div>
      </div>
    </article>
  );
}
