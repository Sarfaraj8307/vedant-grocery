import type { Metadata } from "next";
import Link from "next/link";
import { Sparkles } from "lucide-react";

import { ProductGrid } from "@/components/products/product-grid";
import { Button } from "@/components/ui/button";
import { EmptyState, SectionHeading } from "@/components/ui/misc";
import { getLiveOffers } from "@/lib/catalogue";
import { SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import { formatMoney } from "@/lib/money";
import { store } from "@/config/store";
import { absoluteUrl } from "@/config/site";
import type { CatalogueProduct } from "@/types/database";

export const metadata: Metadata = {
  title: "Today's offers",
  description: `Live discounts on fresh fruit and vegetables at ${store.name}. Updated as prices change at the market.`,
  alternates: { canonical: "/offers" },
};

/**
 * Offers page.
 *
 * Only products with a **live** offer appear: the same `is_active` +
 * `starts_at <= now()` + `ends_at > now()` predicate that `place_order` uses to
 * decide what to charge. A customer can therefore never tap a deal that the
 * server then refuses to honour.
 */
export default async function OffersPage() {
  const offers = await getLiveOffers().catch(() => []);
  const products: CatalogueProduct[] = offers.map((item) => item.product);

  const biggest = offers.reduce((max, item) => {
    const percent = item.offer.discount_percent ?? 0;
    return percent > max ? percent : max;
  }, 0);

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <SectionHeading
        as="h1"
        title="Today's rate board"
        subtitle={`Prices we set this morning at the market.${
          biggest > 0 ? ` Biggest saving today: ${biggest}% off.` : ""
        }`}
      />

      {/* The rate-board motif carries onto the page it belongs to. */}
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-lg border border-marigold-200 bg-marigold-50 px-4 py-3">
        <p className="text-small text-marigold-700">
          <span className="font-semibold tabular-nums" data-numeric>
            {products.length}
          </span>{" "}
          {products.length === 1 ? "item is" : "items are"} on offer right now
        </p>
        <p className="text-small text-marigold-600">
          Free delivery over{" "}
          {formatMoney(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold)}
        </p>
      </div>

      {products.length > 0 ? (
        <ProductGrid products={products} />
      ) : (
        <EmptyState
          illustration={
            <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
              <Sparkles className="size-7 text-marigold-500" aria-hidden="true" />
            </span>
          }
          title="No offers running right now"
          description="We set our rates from the morning market. When something is cheaper than usual it appears here first."
          action={
            <Button asChild variant="secondary">
              <Link href="/categories">Browse all produce</Link>
            </Button>
          }
        />
      )}

      {/*
        ItemList structured data for the offers page. Emitting it helps a
        "vedant fruit and vegetables offers" query surface real prices in the
        result snippet rather than a generic description.
      */}
      {products.length > 0 ? (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify({
              "@context": "https://schema.org",
              "@type": "ItemList",
              name: `Today's offers at ${store.name}`,
              numberOfItems: products.length,
              itemListElement: products.map((product, index) => ({
                "@type": "ListItem",
                position: index + 1,
                name: product.name,
                url: absoluteUrl(`/products/${product.id}`),
              })),
            }),
          }}
        />
      ) : null}
    </div>
  );
}
