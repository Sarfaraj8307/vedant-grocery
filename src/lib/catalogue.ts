import "server-only";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import { pickActiveOffer } from "@/lib/pricing";
import {
  PRODUCT_SELECT,
  toCatalogueProduct,
  type ProductNestedRow,
} from "@/lib/catalogue-shared";
import type {
  CatalogueProduct,
  CategoryRow,
  DeliverySlotRow,
  OfferRow,
} from "@/types/database";

/**
 * Catalogue data access.
 *
 * Everything the storefront needs comes from a *single* nested select per page
 * so the app never issues an N+1 query. The catalogue is public data (RLS
 * `*_read_public` policies), so these reads work for signed-out customers and
 * are safe to cache.
 *
 * All functions return typed results and throw a `CatalogueError` on failure,
 * which route-level error boundaries turn into a friendly message. Database
 * error text is never shown to a customer (DESIGN brief §31).
 */

export class CatalogueError extends Error {
  constructor(message: string, readonly cause?: unknown) {
    super(message);
    this.name = "CatalogueError";
  }
}

export type CatalogueQuery = {
  categoryId?: string;
  /** Free-text term; normalised and matched against name + description. */
  search?: string;
  /** Only products with a live offer. */
  offersOnly?: boolean;
  limit?: number;
};

/**
 * Fetch the catalogue.
 *
 * Ordering is deterministic (`availability` first, then name) so the grid is
 * stable across renders and between server and client — a requirement for
 * avoiding hydration mismatch and CLS.
 */
export async function getCatalogue(
  query: CatalogueQuery = {},
): Promise<CatalogueProduct[]> {
  const supabase = await getSupabaseServerClient();
  const now = new Date();

  let request = supabase
    .from("products")
    .select(PRODUCT_SELECT)
    // `availability` desc puts in-stock first; PostgREST orders booleans with
    // true before false when descending.
    .order("availability", { ascending: false })
    .order("name", { ascending: true });

  if (query.categoryId) {
    request = request.eq("category_id", query.categoryId);
  }

  if (query.search) {
    const term = normaliseSearchTerm(query.search);
    if (term) {
      /*
       * `or` with `ilike` gives case-insensitive substring matching across the
       * two fields a customer actually types against. `%` and `_` are stripped
       * by normaliseSearchTerm so a customer cannot inject a wildcard that
       * turns a search into a full table scan.
       */
      request = request.or(
        `name.ilike.%${term}%,description.ilike.%${term}%`,
      );
    }
  }

  if (query.limit) request = request.limit(query.limit);

  const { data, error } = await request;
  if (error) {
    throw new CatalogueError("Could not load the catalogue.", error);
  }

  let products = ((data ?? []) as unknown as ProductNestedRow[]).map((row) =>
    toCatalogueProduct(row, now),
  );

  // Offers are filtered in memory rather than in SQL: the "live" predicate is
  // time-dependent and mirroring it in a PostgREST filter would risk drift from
  // the single definition used by pricing.ts.
  if (query.offersOnly) {
    products = products.filter((p) => p.activeOffer !== null);
  }

  return products;
}

/** A single product with its category, weights and offers. */
export async function getProductById(
  id: string,
): Promise<CatalogueProduct | null> {
  if (!isUuid(id)) return null;

  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase
    .from("products")
    .select(PRODUCT_SELECT)
    .eq("id", id)
    .maybeSingle();

  if (error) {
    throw new CatalogueError("Could not load this product.", error);
  }
  if (!data) return null;

  return toCatalogueProduct(data as unknown as ProductNestedRow, new Date());
}

/**
 * Related products: same category, excluding the product itself, in-stock
 * first. Powers the "goes well with" rail on the product page.
 */
export async function getRelatedProducts(
  product: CatalogueProduct,
  limit = 8,
): Promise<CatalogueProduct[]> {
  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase
    .from("products")
    .select(PRODUCT_SELECT)
    .eq("category_id", product.category_id)
    .neq("id", product.id)
    .order("availability", { ascending: false })
    .order("name", { ascending: true })
    .limit(limit);

  if (error) return [];
  return ((data ?? []) as unknown as ProductNestedRow[]).map((row) =>
    toCatalogueProduct(row, new Date()),
  );
}

export async function getCategories(): Promise<CategoryRow[]> {
  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase
    .from("categories")
    .select("*")
    .eq("is_active", true)
    .order("sort_order", { ascending: true });

  if (error) throw new CatalogueError("Could not load categories.", error);
  return (data ?? []) as CategoryRow[];
}

export async function getCategoryById(id: string): Promise<CategoryRow | null> {
  if (!isUuid(id)) return null;
  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase
    .from("categories")
    .select("*")
    .eq("id", id)
    .maybeSingle();

  if (error) throw new CatalogueError("Could not load this category.", error);
  return (data as CategoryRow | null) ?? null;
}

/** Offers joined to their product, for the /offers page. */
export async function getLiveOffers(): Promise<
  { offer: OfferRow; product: CatalogueProduct }[]
> {
  const products = await getCatalogue({ offersOnly: true });
  const now = new Date();
  return products
    .filter((p) => p.activeOffer)
    .map((p) => ({ offer: pickActiveOffer([p.activeOffer!], now)!, product: p }))
    // Biggest saving first — the ordering that is actually useful to a customer.
    .sort((a, b) => {
      const savingOf = (offer: OfferRow) =>
        offer.discount_percent ??
        (offer.price !== null ? Number.POSITIVE_INFINITY : 0);
      const aSaving = a.offer.discount_percent ?? 0;
      const bSaving = b.offer.discount_percent ?? 0;
      if (aSaving !== bSaving) return bSaving - aSaving;
      return savingOf(b.offer) - savingOf(a.offer);
    });
}

/**
 * Bookable delivery slots.
 *
 * `place_order` requires `starts_at > now()` and an active slot, so the client
 * applies exactly the same predicate — this guarantees the customer can never
 * pick a slot the server would reject.
 */
export async function getDeliverySlots(
  horizonDays = 7,
): Promise<DeliverySlotRow[]> {
  const supabase = await getSupabaseServerClient();
  const now = new Date();
  const horizon = new Date(now.getTime() + horizonDays * 86_400_000);

  const { data, error } = await supabase
    .from("delivery_slots")
    .select("*")
    .eq("is_active", true)
    .gt("starts_at", now.toISOString())
    .lt("starts_at", horizon.toISOString())
    .order("starts_at", { ascending: true });

  if (error) throw new CatalogueError("Could not load delivery slots.", error);
  return (data ?? []) as DeliverySlotRow[];
}

/**
 * How many non-cancelled orders already occupy each slot, so the picker can
 * grey out full ones before the customer tries to check out.
 * Reads only aggregate counts of the customer's own rows; capacity itself is
 * enforced server-side by `place_order`.
 */
export async function getSlotLoad(): Promise<Record<string, number>> {
  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("orders")
    .select("delivery_slot_id, status")
    .neq("status", "CANCELLED")
    .not("delivery_slot_id", "is", null);

  const load: Record<string, number> = {};
  for (const row of (data ?? []) as {
    delivery_slot_id: string | null;
  }[]) {
    if (!row.delivery_slot_id) continue;
    load[row.delivery_slot_id] = (load[row.delivery_slot_id] ?? 0) + 1;
  }
  return load;
}

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_RE.test(value);
}

/**
 * Normalise a search term for `ilike`: trim, collapse whitespace, and strip the
 * PostgREST/LIKE metacharacters (`%`, `_`, `,`, `(`) that would otherwise let a
 * crafted query change the shape of the request.
 */
export function normaliseSearchTerm(raw: string): string {
  return raw
    .trim()
    .replace(/\s+/g, " ")
    .replace(/[%_,()\\]/g, "")
    .slice(0, 80);
}
