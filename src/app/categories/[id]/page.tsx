import type { Metadata } from "next";
import { notFound } from "next/navigation";

import {
  ActiveFilterSummary,
  CategoryChips,
  RefinementChips,
  SortControl,
} from "@/components/catalogue/browse-filters";
import { ProductGrid } from "@/components/products/product-grid";
import { ProductImage } from "@/components/products/product-image";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { getCatalogue, getCategories, getCategoryById } from "@/lib/catalogue";
import { applyBrowseFilters, parseBrowseParams } from "@/lib/browse";
import { store } from "@/config/store";
import { site } from "@/config/site";

type Params = { id: string };
type SearchParams = Record<string, string | string[] | undefined>;

export async function generateMetadata({
  params,
}: {
  params: Promise<Params>;
}): Promise<Metadata> {
  const { id } = await params;
  const category = await getCategoryById(id).catch(() => null);
  // Throw here (not just a metadata title) so the 404 status is committed
  // *before* the async page body streams under the root `loading.tsx`
  // Suspense — otherwise the shell is sent with a 200 and the not-found UI is
  // injected as a soft 404. The page body re-checks below as defence in depth.
  if (!category) notFound();

  return {
    title: `${category.name} delivery`,
    description: `Buy fresh ${category.name.toLowerCase()} online from ${store.name}. Choose your exact pack size and get same-day local delivery.`,
    alternates: { canonical: `/categories/${category.id}` },
    openGraph: {
      title: `${category.name} — ${store.name}`,
      description: `Fresh ${category.name.toLowerCase()}, weighed and packed to order.`,
    },
  };
}

export default async function CategoryPage({
  params,
  searchParams,
}: {
  params: Promise<Params>;
  searchParams: Promise<SearchParams>;
}) {
  const [{ id }, resolved] = await Promise.all([params, searchParams]);
  const browseParams = { ...parseBrowseParams(resolved), categoryId: id };

  const category = await getCategoryById(id).catch(() => null);
  if (!category) notFound();

  const [categories, catalogue] = await Promise.all([
    getCategories().catch(() => []),
    getCatalogue({ categoryId: id }).catch(() => []),
  ]);

  const products = applyBrowseFilters(catalogue, browseParams);
  const base = `/categories/${id}`;

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      {/* Category header: a tinted banner using the category's own accent. */}
      <header className="flex items-center gap-4 rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <ProductImage
          source={{
            imageUrl: category.image_url,
            glyph: category.glyph,
            accent: category.accent,
            name: category.name,
          }}
          size="thumb"
          className="size-16 shrink-0 overflow-hidden rounded-md sm:size-20"
        />
        <div className="min-w-0">
          <h1 className="font-display text-h1 text-ink">{category.name}</h1>
          <p className="mt-1 max-w-[52ch] text-small text-ink-muted">
            {catalogue.length > 0
              ? `${catalogue.length} ${
                  catalogue.length === 1 ? "item" : "items"
                } in this aisle today. Pick your pack size — we weigh it fresh.`
              : "This aisle is being restocked. Check back shortly."}
          </p>
        </div>
      </header>

      <div className="flex flex-col gap-3">
        <CategoryChips
          categories={categories}
          base={base}
          params={browseParams}
          totalCount={catalogue.length}
        />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <RefinementChips base={base} params={browseParams} />
          <SortControl base={base} params={browseParams} />
        </div>
        <ActiveFilterSummary
          params={browseParams}
          categories={categories}
          resultCount={products.length}
        />
      </div>

      {products.length > 0 ? (
        <ProductGrid products={products} />
      ) : (
        <EmptyState
          illustration={
            <span aria-hidden="true" className="text-4xl">
              {category.glyph}
            </span>
          }
          title={`No ${category.name.toLowerCase()} match those filters`}
          description="Clear the filters to see the whole aisle, or message the shop if you need something specific."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button asChild variant="secondary">
                <a href={base}>Clear filters</a>
              </Button>
              <Button asChild variant="outline">
                <a href="/contact">Message the shop</a>
              </Button>
            </div>
          }
        />
      )}

      <nav aria-label="Other aisles" className="border-t border-line pt-5">
        <p className="text-caption text-ink-subtle">
          More from {site.defaultTitle.split(" — ")[0]}
        </p>
        <ul className="mt-2.5 flex flex-wrap gap-2">
          {categories
            .filter((c) => c.id !== category.id)
            .map((c) => (
              <li key={c.id}>
                <Button asChild variant="outline" size="sm">
                  <a href={`/categories/${c.id}`}>
                    <span aria-hidden="true">{c.glyph}</span>
                    {c.name}
                  </a>
                </Button>
              </li>
            ))}
        </ul>
      </nav>
    </div>
  );
}
