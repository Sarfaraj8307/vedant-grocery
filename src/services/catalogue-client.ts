"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import { PRODUCT_SELECT, toCatalogueProduct, type ProductNestedRow } from "@/lib/catalogue-shared";
import type { CatalogueProduct } from "@/types/database";

/**
 * Client-side catalogue reads.
 *
 * Only used where a *user gesture* needs fresh data that the server render did
 * not have — currently just "order again", which must price today's catalogue
 * rather than the prices the customer paid weeks ago.
 *
 * The catalogue is public data (`products_read_public` with `using (true)`), so
 * this read is safe for a signed-in or signed-out client and leaks nothing.
 */
export async function getProductsByIds(
  ids: string[],
): Promise<CatalogueProduct[]> {
  if (ids.length === 0) return [];

  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("products")
    .select(PRODUCT_SELECT)
    .in("id", ids);

  if (error) return [];

  const now = new Date();
  return ((data ?? []) as unknown as ProductNestedRow[]).map((row) =>
    toCatalogueProduct(row, now),
  );
}
