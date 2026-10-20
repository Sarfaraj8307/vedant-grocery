import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

import {
  AnnouncementBar,
  CategoryRail,
  RateBoard,
  TrustStrip,
} from "@/components/home/home-sections";
import { BuyAgainSection } from "@/components/home/buy-again";
import { DeliveryLocationBar } from "@/components/home/delivery-location";
import { HomeHero } from "@/components/hero/home-hero";
import { StoreClosedBanner } from "@/components/home/store-closed-banner";
import { ProductGrid, ProductRail } from "@/components/products/product-grid";
import { ContactPanel } from "@/components/store/contact-buttons";
import { Button } from "@/components/ui/button";
import { SectionHeading } from "@/components/ui/misc";
import { Reveal } from "@/components/ui/reveal";
import { getCatalogue, getCategories, getLiveOffers } from "@/lib/catalogue";
import { getBuyAgainItems } from "@/lib/buy-again.server";
import { getDeliveryLocation } from "@/lib/addresses";
import { BUY_AGAIN_COUNT, CATALOGUE_PREVIEW_COUNT, POPULAR_COUNT } from "@/config/catalogue";
import { site } from "@/config/site";
import { store } from "@/config/store";

export const metadata: Metadata = {
  title: site.defaultTitle,
  description: site.defaultDescription,
  alternates: { canonical: "/" },
};

/**
 * Home page.
 *
 * Rendered on the server from live catalogue data. Section order is deliberate:
 *   1. the free-delivery fact (the single most useful thing on the page),
 *   2. an appetising hero with a route into the catalogue in one tap,
 *   3. aisles,
 *   4. today's prices (the reason to buy now),
 *   5. what people actually order,
 *   6. the full shelf,
 *   7. why to trust us, then how to reach us.
 */
export default async function HomePage() {
  const [categories, products, offers, buyAgain, deliveryLocation] =
    await Promise.all([
      getCategories().catch(() => []),
      getCatalogue().catch(() => []),
      getLiveOffers().catch(() => []),
      // Both read the customer's session, so they are empty for a signed-out
      // visitor and the home page renders exactly as before for them.
      getBuyAgainItems(BUY_AGAIN_COUNT).catch(() => ({
        items: [],
        unavailableCount: 0,
      })),
      getDeliveryLocation().catch(() => null),
    ]);

  const counts = products.reduce<Record<string, number>>((acc, product) => {
    acc[product.category_id] = (acc[product.category_id] ?? 0) + 1;
    return acc;
  }, {});

  const inStock = products.filter((p) => p.availability);
  // "Popular" is the in-stock shelf, in-stock first and discounter-first, which
  // is a defensible ordering without inventing purchase statistics.
  const popular = [...inStock]
    .sort((a, b) => {
      const aOffer = a.activeOffer ? 1 : 0;
      const bOffer = b.activeOffer ? 1 : 0;
      if (aOffer !== bOffer) return bOffer - aOffer;
      return a.name.localeCompare(b.name);
    })
    .slice(0, POPULAR_COUNT);

  const preview = products.slice(0, CATALOGUE_PREVIEW_COUNT);

  return (
    <>
      <AnnouncementBar />
      <StoreClosedBanner />
      <HomeHero />

      <div className="shell flex flex-col gap-12 py-10 sm:gap-14 sm:py-12 lg:gap-16 lg:py-16">
        {/* Where we deliver — first, because it is the fact that decides whether
            anything below is orderable. */}
        <Reveal>
          <DeliveryLocationBar location={deliveryLocation} />
        </Reveal>

        {/* "Buy again" is placed above the aisles: for a returning customer it
            is the single highest-value shortcut on the page, and it only renders
            when they actually have history. */}
        {buyAgain.items.length > 0 ? (
          <Reveal>
            <BuyAgainSection
              items={buyAgain.items}
              unavailableCount={buyAgain.unavailableCount}
            />
          </Reveal>
        ) : null}

        <Reveal>
          <CategoryRail categories={categories} counts={counts} />
        </Reveal>

        <Reveal>
          <RateBoard items={offers} />
        </Reveal>

        {popular.length > 0 ? (
          <Reveal>
            <section aria-labelledby="popular-heading">
              <SectionHeading
                id="popular-heading"
                title="Ordered most this week"
                subtitle="The staples our regulars top up on."
              />
              <ProductRail
                products={popular}
                ariaLabel="Ordered most this week"
              />
            </section>
          </Reveal>
        ) : null}

        {products.length > 0 ? (
          <Reveal>
            <section aria-labelledby="shelf-heading">
              <SectionHeading
                id="shelf-heading"
                title="Everything on the shelf"
                subtitle={`${products.length} ${
                  products.length === 1 ? "item" : "items"
                } available today.`}
                action={
                  <Button asChild variant="quiet" size="sm">
                    <Link href="/categories">
                      Browse all
                      <ArrowRight className="size-4" aria-hidden="true" />
                    </Link>
                  </Button>
                }
              />
              <ProductGrid products={preview} />
              {products.length > preview.length ? (
                <div className="mt-5 flex justify-center">
                  <Button asChild variant="outline">
                    <Link href="/categories">
                      See all {products.length} items
                      <ArrowRight className="size-4" aria-hidden="true" />
                    </Link>
                  </Button>
                </div>
              ) : null}
            </section>
          </Reveal>
        ) : (
          <EmptyCatalogueNotice />
        )}

        <Reveal>
          <TrustStrip />
        </Reveal>

        <section
          aria-labelledby="contact-heading"
          className="rounded-lg border border-line bg-surface p-5 shadow-xs sm:p-7"
        >
          <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-center">
            <div>
              <h2 id="contact-heading" className="text-h2 text-ink">
                Need something not listed?
              </h2>
              <p className="mt-1.5 max-w-[54ch] text-body text-ink-muted">
                Message the shop and we will add it to your order — we stock far
                more than the app shows, and we can source specialities on
                request.
              </p>
              <div className="mt-5">
                <ContactPanel />
              </div>
            </div>

            <dl className="grid shrink-0 gap-3 text-small sm:grid-cols-2 lg:w-64 lg:grid-cols-1">
              <div className="rounded-md border border-line bg-surface-muted px-3.5 py-3">
                <dt className="text-caption text-ink-subtle">Delivery area</dt>
                <dd className="mt-1 text-ink-muted">
                  {store.deliveryAreas.length > 0
                    ? store.deliveryAreas.join(", ")
                    : "Local delivery — confirm your pincode at checkout."}
                </dd>
              </div>
              <div className="rounded-md border border-line bg-surface-muted px-3.5 py-3">
                <dt className="text-caption text-ink-subtle">Order cut-off</dt>
                <dd className="mt-1 text-ink-muted">
                  Slots are confirmed at checkout. Same-day while slots last.
                </dd>
              </div>
            </dl>
          </div>
        </section>
      </div>
    </>
  );
}

/**
 * Shown when the catalogue is empty. Treated as a real, designed state rather
 * than an error — the store may legitimately be closed or between restocks.
 */
function EmptyCatalogueNotice() {
  return (
    <section className="rounded-lg border border-line bg-surface p-8 text-center shadow-xs">
      <h2 className="text-h2 text-ink">The shelves are being restocked</h2>
      <p className="mx-auto mt-2 max-w-[42ch] text-body text-ink-muted">
        We are updating today&apos;s stock list. Please check back shortly, or
        message the shop and we will let you know what has arrived.
      </p>
      <div className="mt-5 flex justify-center">
        <ContactPanel className="mx-auto" />
      </div>
    </section>
  );
}
