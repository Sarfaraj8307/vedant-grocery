import Link from "next/link";
import { ArrowRight, BadgeCheck, Leaf, Snowflake, Truck } from "lucide-react";
import { PriceTag, SavingLabel } from "@/components/products/price-tag";
import { ProductImage } from "@/components/products/product-image";
import { QtyStepper } from "@/components/products/qty-stepper";
import { SectionHeading } from "@/components/ui/misc";
import { Button } from "@/components/ui/button";
import { formatMoney } from "@/lib/money";
import { cheapestOption } from "@/lib/pricing";
import { defaultWeight } from "@/lib/cart-line";
import { SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import type { CatalogueProduct, CategoryRow, OfferRow } from "@/types/database";

/* -------------------------------------------------------------------------- */
/* Announcement bar                                                           */
/* -------------------------------------------------------------------------- */

/**
 * A single slim informational strip. It carries one real fact (the free-delivery
 * threshold) rather than a rotating carousel of marketing copy.
 */
export function AnnouncementBar() {
  return (
    <div className="border-b border-marigold-200 bg-marigold-50">
      <div className="shell flex min-h-8 items-center justify-center gap-2 py-1.5 text-center">
        <Truck
          className="size-3.5 shrink-0 text-marigold-600"
          aria-hidden="true"
        />
        <p className="text-small text-marigold-700">
          Free delivery on orders over{" "}
          <strong className="font-semibold">
            {formatMoney(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold)}
          </strong>
          <span className="hidden sm:inline">
            {" "}
            · otherwise {formatMoney(SHOP_SETTINGS_PREVIEW.deliveryCharge)}
          </span>
        </p>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Category rail                                                              */
/* -------------------------------------------------------------------------- */

/**
 * Category tiles. The tile tint comes from the category's own `accent` RGB, so
 * adding a category in the admin app immediately gets a correctly-tinted tile.
 */
export function CategoryRail({
  categories,
  counts,
}: {
  categories: readonly CategoryRow[];
  /** Products per category, so the tile can say how much is behind it. */
  counts?: Record<string, number>;
}) {
  if (categories.length === 0) return null;

  return (
    <section aria-labelledby="categories-heading">
      <SectionHeading
        id="categories-heading"
        title="What are you cooking?"
        subtitle="Four aisles, restocked every morning."
        action={
          <Button asChild variant="quiet" size="sm">
            <Link href="/categories">
              All categories
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </Button>
        }
      />

      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {categories.map((category) => {
          const count = counts?.[category.id];
          return (
            <li key={category.id}>
              <Link
                href={`/categories/${category.id}`}
                className="group flex h-full flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-xs transition-[box-shadow,border-color,transform] duration-[var(--motion-base)] ease-[var(--ease-standard)] hover:-translate-y-0.5 hover:border-line-strong hover:shadow-produce motion-reduce:hover:translate-y-0"
              >
                <ProductImage
                  source={{
                    imageUrl: category.image_url,
                    glyph: category.glyph,
                    accent: category.accent,
                    name: category.name,
                  }}
                  size="tile"
                />
                <div className="flex flex-1 flex-col gap-0.5 p-3">
                  <span className="text-h3 text-ink">{category.name}</span>
                  <span className="text-caption normal-case text-ink-subtle">
                    {count !== undefined
                      ? `${count} ${count === 1 ? "item" : "items"}`
                      : "Shop now"}
                  </span>
                </div>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Rate board — the signature section                                         */
/* -------------------------------------------------------------------------- */

/**
 * "Today's rate board".
 *
 * The one place the design borrows a genuinely Indian market motif: a mandi
 * price board — warm paper, dashed hairlines, right-aligned numerals, a small
 * unit suffix. It is deliberately *not* a carousel of big discount cards, which
 * is what every other grocery app does.
 *
 * Only products with a live offer appear, and the "was" price is the real
 * `list_price` the customer would otherwise pay — computed by the same function
 * `place_order` uses, so the saving shown is the saving charged.
 */
export function RateBoard({
  items,
}: {
  items: readonly { offer: OfferRow; product: CatalogueProduct }[];
}) {
  if (items.length === 0) return null;
  const shown = items.slice(0, 6);

  return (
    <section aria-labelledby="rate-board-heading" className="pt-1">
      <SectionHeading
        id="rate-board-heading"
        title="Today's rate board"
        subtitle="Live prices on the produce we bought fresh this morning."
        action={
          <Button asChild variant="quiet" size="sm">
            <Link href="/offers">
              All offers
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </Button>
        }
      />

      <div className="overflow-hidden rounded-lg border border-marigold-200 bg-marigold-50">
        <div className="flex items-center justify-between gap-3 border-b border-marigold-200 px-4 py-2.5">
          <span className="text-caption text-marigold-700">
            Today&apos;s rate — per pack
          </span>
          <span className="text-caption text-marigold-600">
            {shown.length} {shown.length === 1 ? "deal" : "deals"}
          </span>
        </div>

        <ul className="divide-y divide-marigold-200">
          {shown.map(({ offer, product }) => {
            const weight = defaultWeight(product);
            const cheapest = cheapestOption(product);
            if (!cheapest) return null;
            const { breakdown } = cheapest;

            return (
              <li
                key={offer.id}
                className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-marigold-100/60"
              >
                <span className="grid size-11 shrink-0 place-items-center rounded-md border border-marigold-200 bg-surface text-xl">
                  <span aria-hidden="true">{product.glyph}</span>
                </span>

                <div className="min-w-0 flex-1">
                  <Link
                    href={`/products/${product.id}`}
                    className="block truncate text-body font-medium text-ink underline-offset-2 hover:underline"
                  >
                    {product.name}
                  </Link>
                  <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-small text-ink-muted">
                    {offer.title}
                    <SavingLabel
                      percent={breakdown.savingPercent}
                      saving={breakdown.savingPerUnit}
                    />
                  </span>
                </div>

                <div className="shrink-0 text-right">
                  <PriceTag
                    amount={breakdown.unitPrice}
                    compareAt={
                      breakdown.isDiscounted ? breakdown.listPrice : undefined
                    }
                    size="md"
                    className="justify-end"
                  />
                  <span className="mt-0.5 block text-[10px] text-ink-subtle">
                    {weight ? weight.label : `per ${product.unit_label}`}
                  </span>
                </div>

                <span className="hidden shrink-0 sm:block">
                  <QtyStepper product={product} weight={weight} size="sm" />
                </span>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Trust strip                                                                */
/* -------------------------------------------------------------------------- */

const TRUST = [
  {
    icon: Leaf,
    title: "Picked this morning",
    body: "We buy daily from the wholesale market — nothing sits in a cold room for a week.",
  },
  {
    icon: BadgeCheck,
    title: "Weighed in front of you",
    body: "Your pack is weighed on the scale and the bill reflects exactly that weight.",
  },
  {
    icon: Snowflake,
    title: "Packed to travel",
    body: "Leafy greens are cushioned and everything is sealed before it leaves the shop.",
  },
] as const;

export function TrustStrip() {
  return (
    <section aria-label="Why customers order from Vedant">
      <ul className="grid gap-3 sm:grid-cols-3">
        {TRUST.map(({ icon: Icon, title, body }) => (
          <li
            key={title}
            className="rounded-lg border border-line bg-surface p-4 shadow-xs"
          >
            <span className="grid size-8 place-items-center rounded-full bg-leaf-100 text-leaf-700">
              <Icon className="size-4" aria-hidden="true" />
            </span>
            <h3 className="mt-3 text-h3 text-ink">{title}</h3>
            <p className="mt-1 text-small text-ink-muted">{body}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
