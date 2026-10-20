import type { Metadata } from "next";
import Link from "next/link";
import { SearchX } from "lucide-react";

import { SearchField } from "@/components/search/search-field";
import { RecentSearches } from "@/components/search/recent-searches";
import { ProductGrid } from "@/components/products/product-grid";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { RefinementChips } from "@/components/catalogue/browse-filters";
import { getCatalogue } from "@/lib/catalogue";
import { applyBrowseFilters, parseBrowseParams } from "@/lib/browse";
import { SEARCH_SUGGESTIONS } from "@/config/catalogue";

export const metadata: Metadata = {
  title: "Search",
  description:
    "Search the Vedant Fruit & Vegetables catalogue by name — apples, tomatoes, palak, coriander and more.",
  // A results page must never be indexed: it is infinite low-value duplicate
  // content and would compete with the real category pages.
  robots: { index: false, follow: true },
};

type SearchParams = Record<string, string | string[] | undefined>;

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const resolved = await searchParams;
  const params = parseBrowseParams(resolved);
  const query = params.q ?? "";

  // The database does the matching (`name`/`description` ilike) so search stays
  // fast as the catalogue grows; refinements are applied on top.
  const catalogue = query
    ? await getCatalogue({ search: query }).catch(() => [])
    : [];
  const results = applyBrowseFilters(catalogue, params);

  return (
    <div className="shell flex flex-col gap-5 py-6 sm:py-8">
      <div className="mx-auto w-full max-w-2xl">
        <h1 className="sr-only">Search products</h1>
        {/* Keyed on the query so the field re-seeds when the URL changes
            (back button, tapping a suggestion) without an effect. */}
        <SearchField
          key={query}
          defaultValue={query}
          size="lg"
          autoFocus={!query}
          placeholder="Search apples, tomatoes, palak…"
          className="w-full"
        />
      </div>

      {query ? (
        <>
          <RecentSearches currentQuery={query} className="mx-auto w-full max-w-2xl" />

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-small text-ink-muted" aria-live="polite">
              {results.length} {results.length === 1 ? "result" : "results"} for{" "}
              <span className="text-ink">“{query}”</span>
            </p>
            <RefinementChips base="/search" params={params} />
          </div>

          {results.length > 0 ? (
            <ProductGrid products={results} />
          ) : (
            <EmptyState
              illustration={
                <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
                  <SearchX className="size-7 text-ink-subtle" aria-hidden="true" />
                </span>
              }
              title={`Nothing matched “${query}”`}
              description="We may still stock it — try a shorter word, or ask the shop directly and we will tell you what arrived today."
              action={
                <div className="flex flex-col items-center gap-4">
                  <div className="flex flex-wrap justify-center gap-2">
                    {SEARCH_SUGGESTIONS.slice(0, 6).map((term) => (
                      <Link
                        key={term}
                        href={`/search?q=${encodeURIComponent(term)}`}
                        className="inline-flex h-8 items-center rounded-pill border border-line-strong bg-surface px-3 text-small text-ink-muted transition-colors hover:border-ink-subtle hover:text-ink"
                      >
                        {term}
                      </Link>
                    ))}
                  </div>
                  <div className="flex flex-wrap justify-center gap-2">
                    <Button asChild variant="secondary">
                      <Link href="/categories">Browse everything</Link>
                    </Button>
                    <Button asChild variant="outline">
                      <Link href="/contact">Ask the shop</Link>
                    </Button>
                  </div>
                </div>
              }
            />
          )}
        </>
      ) : (
        <>
          <RecentSearches className="mx-auto w-full max-w-2xl" />

          <section className="mx-auto w-full max-w-2xl">
            <p className="text-caption text-ink-subtle">Popular searches</p>
            <ul className="mt-2 flex flex-wrap gap-2">
              {SEARCH_SUGGESTIONS.map((term) => (
                <li key={term}>
                  <Link
                    href={`/search?q=${encodeURIComponent(term)}`}
                    className="inline-flex h-8 items-center rounded-pill border border-line-strong bg-surface px-3 text-small text-ink-muted transition-colors hover:border-ink-subtle hover:text-ink"
                  >
                    {term}
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <p className="mx-auto max-w-2xl text-small text-ink-subtle">
            Prefer to look around?{" "}
            <Link
              href="/categories"
              className="text-leaf-700 underline underline-offset-2"
            >
              Browse all produce
            </Link>
            .
          </p>
        </>
      )}
    </div>
  );
}
