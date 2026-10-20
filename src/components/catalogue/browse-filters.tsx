import Link from "next/link";
import type { CategoryRow } from "@/types/database";
import { cn } from "@/lib/utils";

/**
 * URL-driven filters.
 *
 * Every filter is an `<a>` to a new query string rather than a client-side state
 * toggle. That is a deliberate architectural choice:
 *   • the result set is rendered on the server, so a filtered grid ships zero
 *     product JavaScript;
 *   • the filtered view is shareable, bookmarkable and crawlable;
 *   • the browser back button works the way a customer expects it to;
 *   • it cannot desynchronise from the URL, because the URL *is* the state.
 *
 * Chips are 32px tall with pill radius; the active chip is leaf-filled and also
 * carries `aria-current`, so the state is not colour-only (WCAG 1.4.1).
 */

export type BrowseSort = "name" | "price-asc" | "price-desc";

export const SORT_LABELS: Record<BrowseSort, string> = {
  name: "A–Z",
  "price-asc": "Price: low to high",
  "price-desc": "Price: high to low",
};

export type BrowseParams = {
  categoryId?: string;
  offersOnly?: boolean;
  inStockOnly?: boolean;
  sort?: BrowseSort;
  q?: string;
};

/** Build a query string from the current params plus overrides. */
export function buildBrowseHref(
  base: string,
  current: BrowseParams,
  overrides: Partial<BrowseParams>,
): string {
  const next: BrowseParams = { ...current, ...overrides };
  const params = new URLSearchParams();

  if (next.q) params.set("q", next.q);

  if (!base.startsWith("/categories/") && next.categoryId) {
    params.set("category", next.categoryId);
  }
  if (next.offersOnly) params.set("offers", "1");
  if (next.inStockOnly) params.set("stock", "1");
  if (next.sort && next.sort !== "name") params.set("sort", next.sort);

  const query = params.toString();
  return query ? `${base}?${query}` : base;
}

export function FilterChip({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <li className="shrink-0 snap-start">
      <Link
        href={href}
        aria-current={active ? "true" : undefined}
        scroll={false}
        className={cn(
          "inline-flex h-8 items-center gap-1.5 rounded-pill border px-3 text-small font-medium transition-colors duration-[var(--motion-fast)]",
          active
            ? "border-leaf-600 bg-leaf-600 text-white"
            : "border-line-strong bg-surface text-ink-muted hover:border-ink-subtle hover:text-ink",
        )}
      >
        {children}
      </Link>
    </li>
  );
}

export function CategoryChips({
  categories,
  base,
  params,
  totalCount,
}: {
  categories: readonly CategoryRow[];
  base: string;
  params: BrowseParams;
  totalCount: number;
}) {
  return (
    <div className="-mx-4 sm:-mx-6 lg:mx-0">
      <ul className="rail-snap gap-2 px-4 pb-1 sm:px-6 lg:px-0" aria-label="Filter by category">
        <FilterChip
          href={buildBrowseHref(base, params, { categoryId: undefined })}
          active={!params.categoryId}
        >
          All
          <span className="text-[11px] opacity-70 tabular-nums" data-numeric>
            {totalCount}
          </span>
        </FilterChip>
        {categories.map((category) => (
          <FilterChip
            key={category.id}
            // A category page fixes its own category, so chips navigate to the
            // generic browse route with a category selected.
            href={buildBrowseHref("/categories", params, { categoryId: category.id })}
            active={params.categoryId === category.id}
          >
            <span aria-hidden="true">{category.glyph}</span>
            {category.name}
          </FilterChip>
        ))}
      </ul>
    </div>
  );
}

export function RefinementChips({
  base,
  params,
}: {
  base: string;
  params: BrowseParams;
}) {
  return (
    <ul className="flex flex-wrap items-center gap-2" aria-label="Refine results">
      <FilterChip
        href={buildBrowseHref(base, params, { offersOnly: !params.offersOnly })}
        active={Boolean(params.offersOnly)}
      >
        On offer
      </FilterChip>
      <FilterChip
        href={buildBrowseHref(base, params, { inStockOnly: !params.inStockOnly })}
        active={Boolean(params.inStockOnly)}
      >
        In stock
      </FilterChip>
    </ul>
  );
}

export function SortControl({
  base,
  params,
}: {
  base: string;
  params: BrowseParams;
}) {
  const current = params.sort ?? "name";
  const options: BrowseSort[] = ["name", "price-asc", "price-desc"];

  return (
    /**
     * `items-start` + `leading-8` (the chip height) keeps the "Sort" caption on
     * the first chip row. With `items-center` the caption drifted to the middle of
     * the wrapped block on a 360px phone, so it floated between two rows of
     * buttons instead of labelling them.
     */
    <div className="flex items-start gap-2">
      <span className="text-small leading-8 text-ink-subtle">Sort</span>
      <ul className="flex flex-wrap gap-1.5" aria-label="Sort products">
        {options.map((option) => (
          <FilterChip
            key={option}
            href={buildBrowseHref(base, params, { sort: option })}
            active={current === option}
          >
            {SORT_LABELS[option]}
          </FilterChip>
        ))}
      </ul>
    </div>
  );
}

/** Renders the active filters as a summary line, or nothing when unfiltered. */
export function ActiveFilterSummary({
  params,
  categories,
  resultCount,
}: {
  params: BrowseParams;
  categories: readonly CategoryRow[];
  resultCount: number;
}) {
  const category = params.categoryId
    ? categories.find((c) => c.id === params.categoryId)
    : undefined;

  const parts: string[] = [];
  if (params.q) parts.push(`“${params.q}”`);
  if (category) parts.push(category.name);
  if (params.offersOnly) parts.push("on offer");
  if (params.inStockOnly) parts.push("in stock");

  if (parts.length === 0) {
    return (
      <p className="text-small text-ink-muted" aria-live="polite">
        {resultCount} {resultCount === 1 ? "item" : "items"} available
      </p>
    );
  }

  return (
    <p className="text-small text-ink-muted" aria-live="polite">
      {resultCount} {resultCount === 1 ? "result" : "results"} for{" "}
      <span className="text-ink">{parts.join(" · ")}</span>
    </p>
  );
}
