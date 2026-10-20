import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronRight, Leaf, ShieldCheck, Scale } from "lucide-react";

import { ProductImage } from "@/components/products/product-image";
import { ProductPurchase } from "@/components/products/product-purchase";
import { ProductRail } from "@/components/products/product-grid";
import { SectionHeading } from "@/components/ui/misc";
import { Badge } from "@/components/ui/badge";
import {
  getProductById,
  getRelatedProducts,
} from "@/lib/catalogue";
import { cheapestOption, priceFor } from "@/lib/pricing";
import { defaultWeight } from "@/lib/cart-line";
import { store } from "@/config/store";
import { site, absoluteUrl } from "@/config/site";
import { RELATED_COUNT } from "@/config/catalogue";

type Params = { id: string };

export async function generateMetadata({
  params,
}: {
  params: Promise<Params>;
}): Promise<Metadata> {
  const { id } = await params;
  const product = await getProductById(id).catch(() => null);
  // Throw here (not just a metadata title) so the 404 status is committed
  // *before* the async page body streams under the root `loading.tsx`
  // Suspense — otherwise the shell is sent with a 200 and the not-found UI is
  // injected as a soft 404. The page body re-checks below as defence in depth.
  if (!product) notFound();

  const cheapest = cheapestOption(product);
  const price = cheapest?.breakdown.unitPrice ?? product.base_price;
  const unit = cheapest?.weight?.label ?? `per ${product.unit_label}`;

  return {
    title: `${product.name} — ${unit}`,
    description:
      product.description ||
      `Buy ${product.name} online from ${store.name}. ${unit} at ₹${price}.`,
    alternates: { canonical: `/products/${product.id}` },
    openGraph: {
      type: "website",
      title: `${product.name} · ${store.name}`,
      description: product.description,
      url: absoluteUrl(`/products/${product.id}`),
    },
  };
}

/**
 * Product detail page.
 *
 * Layout: single column on mobile (image → purchase → detail), two-column from
 * `lg` with a sticky media column so the price and controls stay reachable while
 * the description scrolls. Breadcrumbs are real links and are also emitted as
 * `BreadcrumbList` structured data.
 */
export default async function ProductPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { id } = await params;
  const product = await getProductById(id).catch(() => null);
  if (!product) notFound();

  const related = await getRelatedProducts(product, RELATED_COUNT).catch(() => []);
  const defaultW = defaultWeight(product);
  const breakdown = priceFor(product, defaultW);
  const inStock = product.availability;

  /* --- Structured data ------------------------------------------------ */
  const productSchema = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    description: product.description || undefined,
    sku: product.id,
    category: product.category?.name,
    brand: { "@type": "Brand", name: store.name },
    offers: {
      "@type": "Offer",
      url: absoluteUrl(`/products/${product.id}`),
      priceCurrency: "INR",
      price: breakdown.unitPrice.toFixed(2),
      availability: inStock
        ? "https://schema.org/InStock"
        : "https://schema.org/OutOfStock",
      seller: { "@type": "Organization", name: store.name },
      eligibleQuantity: {
        "@type": "QuantitativeValue",
        unitText: defaultW ? defaultW.label : product.unit_label,
      },
    },
  };

  const breadcrumbSchema = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      { "@type": "ListItem", position: 1, name: "Home", item: site.url },
      {
        "@type": "ListItem",
        position: 2,
        name: "All produce",
        item: absoluteUrl("/categories"),
      },
      ...(product.category
        ? [
            {
              "@type": "ListItem",
              position: 3,
              name: product.category.name,
              item: absoluteUrl(`/categories/${product.category.id}`),
            },
          ]
        : []),
      {
        "@type": "ListItem",
        position: product.category ? 4 : 3,
        name: product.name,
        item: absoluteUrl(`/products/${product.id}`),
      },
    ],
  };

  return (
    <div className="shell py-5 sm:py-7">
      <script
        type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(productSchema) }}
      />
      <script
        type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbSchema) }}
      />

      <nav aria-label="Breadcrumb" className="mb-4">
        <ol className="flex flex-wrap items-center gap-1 text-small text-ink-subtle">
          <li>
            <Link href="/" className="rounded-xs hover:text-ink">
              Home
            </Link>
          </li>
          <ChevronRight className="size-3.5" aria-hidden="true" />
          <li>
            <Link href="/categories" className="rounded-xs hover:text-ink">
              All produce
            </Link>
          </li>
          {product.category ? (
            <>
              <ChevronRight className="size-3.5" aria-hidden="true" />
              <li>
                <Link
                  href={`/categories/${product.category.id}`}
                  className="rounded-xs hover:text-ink"
                >
                  {product.category.name}
                </Link>
              </li>
            </>
          ) : null}
          <ChevronRight className="size-3.5" aria-hidden="true" />
          <li aria-current="page" className="truncate text-ink">
            {product.name}
          </li>
        </ol>
      </nav>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-10 xl:gap-14">
        {/* Media */}
        <div className="lg:sticky lg:top-[calc(var(--header-h)+16px)] lg:self-start">
          <div className="overflow-hidden rounded-xl border border-line bg-surface shadow-sm">
            <ProductImage
              source={{
                imageUrl: product.image_url,
                glyph: product.glyph,
                accent: product.accent,
                name: product.name,
              }}
              size="detail"
              priority
            />
          </div>

          <ul className="mt-3 grid grid-cols-3 gap-2 text-center">
            <Assurance
              icon={<Leaf className="size-4" aria-hidden="true" />}
              label="Fresh today"
            />
            <Assurance
              icon={<Scale className="size-4" aria-hidden="true" />}
              label="Weighed to order"
            />
            <Assurance
              icon={<ShieldCheck className="size-4" aria-hidden="true" />}
              label="Quality checked"
            />
          </ul>
        </div>

        {/* Detail */}
        <div className="flex flex-col gap-5">
          <header className="flex flex-col gap-2">
            {product.category ? (
              <Link
                href={`/categories/${product.category.id}`}
                className="w-fit text-caption text-leaf-700"
              >
                {product.category.name}
              </Link>
            ) : null}
            <h1 className="font-display text-h1 text-ink">{product.name}</h1>
            <div className="flex flex-wrap items-center gap-2">
              {inStock ? (
                <Badge variant="success">In stock</Badge>
              ) : (
                <Badge variant="warning">Out of stock</Badge>
              )}
              {product.activeOffer ? (
                <Badge variant="offer">{product.activeOffer.title}</Badge>
              ) : null}
              <span className="text-small text-ink-subtle">
                Sold per {product.unit_label}
              </span>
            </div>
            {product.description ? (
              <p className="mt-1 max-w-[52ch] text-body text-ink-muted">
                {product.description}
              </p>
            ) : null}
          </header>

          <ProductPurchase product={product} />
        </div>
      </div>

      {related.length > 0 ? (
        <section aria-labelledby="related-heading" className="mt-12">
          <SectionHeading
            id="related-heading"
            title={`More ${product.category?.name.toLowerCase() ?? "produce"}`}
            subtitle="Customers usually add one of these too."
          />
          <ProductRail products={related} ariaLabel="Related products" />
        </section>
      ) : null}
    </div>
  );
}

function Assurance({
  icon,
  label,
}: {
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <li className="flex flex-col items-center gap-1.5 rounded-md border border-line bg-surface px-2 py-2.5">
      <span className="text-leaf-600">{icon}</span>
      <span className="text-[10px] font-medium uppercase tracking-[0.06em] text-ink-muted">
        {label}
      </span>
    </li>
  );
}
