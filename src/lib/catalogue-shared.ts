import { pickActiveOffer } from "@/lib/pricing";
import type {
  CatalogueProduct,
  CategoryRow,
  OfferRow,
  ProductRow,
  ProductWeightRow,
} from "@/types/database";

/**
 * Catalogue projection shared by the server (`lib/catalogue.ts`) and the browser
 * (`services/catalogue-client.ts`).
 *
 * This module deliberately has **no** `server-only` import and **no** I/O. Both
 * environments must map a PostgREST row into a `CatalogueProduct` in exactly the
 * same way — if the two drifted, "reorder" or a client refresh could price an
 * item differently from the server-rendered card, which is precisely the class of
 * bug that erodes trust in a grocery app.
 */

export const PRODUCT_SELECT = `
  id, category_id, name, description, glyph, accent, image_url,
  base_price, unit_label, availability, quantity_limit, created_at, updated_at,
  category:categories ( id, name, glyph, accent ),
  product_weights ( id, product_id, label, grams, price, is_available, created_at, updated_at ),
  offers ( id, product_id, title, price, discount_percent, starts_at, ends_at, is_active, created_at, updated_at )
` as const;

export type ProductNestedRow = ProductRow & {
  category: Pick<CategoryRow, "id" | "name" | "glyph" | "accent"> | null;
  product_weights: ProductWeightRow[] | null;
  offers: OfferRow[] | null;
};

/** Map a nested PostgREST row into the shape the UI consumes. */
export function toCatalogueProduct(
  row: ProductNestedRow,
  now: Date = new Date(),
): CatalogueProduct {
  const { category, product_weights, offers, ...product } = row;

  // Weight variants are always ordered by size so the picker reads
  // 100 g → 250 g → 500 g → 1 kg rather than in insertion order.
  const weights = (product_weights ?? [])
    .slice()
    .sort((a, b) => a.grams - b.grams);

  return {
    ...product,
    category,
    weights,
    activeOffer: pickActiveOffer(offers ?? [], now),
  };
}
