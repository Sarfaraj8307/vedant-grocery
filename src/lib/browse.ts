import { cheapestOption } from "@/lib/pricing";
import type { CatalogueProduct } from "@/types/database";
import type { BrowseParams, BrowseSort } from "@/components/catalogue/browse-filters";

/**
 * Shared filtering/sorting for the browse and search grids.
 *
 * Filtering runs **in memory** on the already-fetched catalogue rather than as
 * extra SQL predicates. Rationale: the live catalogue is small (tens of rows),
 * the `offersOnly` / `inStockOnly` predicates depend on the same time-aware
 * offer logic that `lib/pricing.ts` defines, and duplicating that logic in
 * PostgREST filters is exactly how a client and a server end up disagreeing
 * about a price. One definition, one behaviour.
 *
 * If the catalogue grows past a few hundred products this becomes a real
 * `products_search` SQL view with a `tsvector` and keyset pagination — noted in
 * docs/FINAL_IMPLEMENTATION_REPORT.md as a scaling step, not a current problem.
 */

export function parseBrowseParams(
  searchParams: Record<string, string | string[] | undefined>,
): BrowseParams {
  const first = (value: string | string[] | undefined): string | undefined =>
    Array.isArray(value) ? value[0] : value;

  const sortRaw = first(searchParams.sort);
  const sort: BrowseSort | undefined =
    sortRaw === "price-asc" || sortRaw === "price-desc" || sortRaw === "name"
      ? sortRaw
      : undefined;

  return {
    q: first(searchParams.q)?.trim() || undefined,
    categoryId: first(searchParams.category) || undefined,
    offersOnly: first(searchParams.offers) === "1",
    inStockOnly: first(searchParams.stock) === "1",
    sort,
  };
}

export function applyBrowseFilters(
  products: readonly CatalogueProduct[],
  params: BrowseParams,
): CatalogueProduct[] {
  let result = [...products];

  if (params.categoryId) {
    result = result.filter((p) => p.category_id === params.categoryId);
  }
  if (params.offersOnly) {
    result = result.filter((p) => p.activeOffer !== null);
  }
  if (params.inStockOnly) {
    result = result.filter((p) => p.availability);
  }

  return sortCatalogue(result, params.sort ?? "name");
}

export function sortCatalogue(
  products: CatalogueProduct[],
  sort: BrowseSort,
): CatalogueProduct[] {
  // In-stock products always lead, whatever the sort. A customer scanning a
  // sorted list should never have to step over items they cannot buy.
  const byStock = (a: CatalogueProduct, b: CatalogueProduct) =>
    Number(b.availability) - Number(a.availability);

  switch (sort) {
    case "price-asc":
      return products.sort((a, b) => {
        const stock = byStock(a, b);
        if (stock !== 0) return stock;
        return priceOf(a) - priceOf(b) || a.name.localeCompare(b.name);
      });
    case "price-desc":
      return products.sort((a, b) => {
        const stock = byStock(a, b);
        if (stock !== 0) return stock;
        return priceOf(b) - priceOf(a) || a.name.localeCompare(b.name);
      });
    case "name":
    default:
      return products.sort((a, b) => {
        const stock = byStock(a, b);
        if (stock !== 0) return stock;
        return a.name.localeCompare(b.name);
      });
  }
}

/** Cheapest purchasable price in paise, so sorting never compares floats. */
function priceOf(product: CatalogueProduct): number {
  const cheapest = cheapestOption(product);
  return Math.round((cheapest?.breakdown.unitPrice ?? product.base_price) * 100);
}

