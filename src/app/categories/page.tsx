import type { Metadata } from "next";
import Link from "next/link";
import { Sprout } from "lucide-react";

import {
  ActiveFilterSummary,
  CategoryChips,
  RefinementChips,
  SortControl,
} from "@/components/catalogue/browse-filters";
import { ProductGrid } from "@/components/products/product-grid";
import { Button } from "@/components/ui/button";
import { EmptyState, SectionHeading } from "@/components/ui/misc";
import { getCatalogue, getCategories } from "@/lib/catalogue";
import { applyBrowseFilters, parseBrowseParams } from "@/lib/browse";
import { site } from "@/config/site";

export const metadata: Metadata = {
  title: "Browse all produce",
  description:
    "Browse every fruit and vegetable currently in stock at Vedant Fruit & Vegetables. Filter by aisle, offer or availability.",
  alternates: { canonical: "/categories" },
};

type SearchParams = Record<string, string | string[] | undefined>;

/**
 * Browse page. All filtering and sorting is URL-driven and server-rendered
 * (see `browse-filters.tsx` for why).
 */
export default async function CategoriesPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const resolved = await searchParams;
  const params = parseBrowseParams(resolved);

  const [categories, catalogue] = await Promise.all([
    getCategories().catch(() => []),
    getCatalogue({ search: params.q }).catch(() => []),
  ]);

  const products = applyBrowseFilters(catalogue, params);

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <SectionHeading
        as="h1"
        title="Everything we have today"
        subtitle={
          params.q
            ? `Showing matches for “${params.q}”.`
            : "Filter by aisle, narrow to what is on offer, or sort by price."
        }
        action={
          <Button asChild variant="quiet" size="sm">
            <Link href="/search">Search instead</Link>
          </Button>
        }
      />

      <div className="flex flex-col gap-3">
        <CategoryChips
          categories={categories}
          base="/categories"
          params={params}
          totalCount={catalogue.length}
        />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <RefinementChips base="/categories" params={params} />
          <SortControl base="/categories" params={params} />
        </div>
        <ActiveFilterSummary
          params={params}
          categories={categories}
          resultCount={products.length}
        />
      </div>

      <div className="mt-1">
        {products.length > 0 ? (
          <ProductGrid products={products} />
        ) : (
          <EmptyState
            illustration={
              <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
                <Sprout className="size-7 text-leaf-500" aria-hidden="true" />
              </span>
            }
            title="Nothing matches those filters"
            description="Try removing a filter, or browse the full shelf — we restock every morning so the list changes daily."
            action={
              <Button asChild variant="secondary">
                <Link href="/categories">Clear all filters</Link>
              </Button>
            }
          />
        )}
      </div>

      <p className="text-small text-ink-subtle">
        Looking for something we do not list? {site.titleTemplate.split(" · ")[1]}{" "}
        can often source it —{" "}
        <Link
          href="/contact"
          className="text-leaf-700 underline underline-offset-2"
        >
          message the shop
        </Link>
        .
      </p>
    </div>
  );
}
