import { priceFor } from "@/lib/pricing";
import type { CatalogueProduct, ProductWeightRow } from "@/types/database";
import type { CartLine } from "@/stores/cart-store";

/**
 * Build the local cart-line snapshot from catalogue data.
 *
 * Centralised so the product card, product page, offers page and catalogue
 * browser all create byte-identical lines. If this logic were duplicated, two
 * entry points could produce different prices for the same product — the kind of
 * inconsistency that destroys trust at checkout.
 *
 * The snapshot is a **preview**: `public.place_order` recalculates every unit
 * price and line total server-side before writing the order.
 */
export function buildCartLine(
  product: CatalogueProduct,
  weight: ProductWeightRow | null,
  quantity = 1,
): Omit<CartLine, "addedAt"> {
  const breakdown = priceFor(product, weight);

  return {
    productId: product.id,
    weightId: weight?.id ?? null,
    quantity,
    name: product.name,
    glyph: product.glyph,
    accent: product.accent,
    imageUrl: product.image_url,
    unitLabel: product.unit_label,
    weightLabel: weight ? weight.label : `per ${product.unit_label}`,
    unitPrice: breakdown.unitPrice,
    listPrice: breakdown.listPrice,
    quantityLimit: product.quantity_limit,
  };
}

/**
 * The weight variant to default to on a product page: the first available one,
 * or `null` when the product is sold by its base price only.
 */
export function defaultWeight(
  product: CatalogueProduct,
): ProductWeightRow | null {
  if (product.weights.length === 0) return null;
  return product.weights.find((w) => w.is_available) ?? product.weights[0] ?? null;
}

/**
 * Match a remembered pack label (from `order_items.weight_label`) to a live
 * weight variant.
 *
 * Used by "order again" and "buy again" so both behave identically. The label is
 * the only durable key: `order_items` deliberately does not store `weight_id`,
 * because a weight row can be deleted while order history must survive.
 *
 * Returns the matched weight plus whether a substitution happened, so the caller
 * can *tell* the customer the pack changed rather than switching it silently.
 */
export function matchWeightByLabel(
  product: CatalogueProduct,
  label: string | null | undefined,
): { weight: ProductWeightRow | null; substituted: boolean } {
  if (product.weights.length === 0) {
    return { weight: null, substituted: false };
  }

  const wanted = label?.trim().toLowerCase() ?? "";

  if (wanted) {
    const exact = product.weights.find(
      (weight) =>
        weight.label.trim().toLowerCase() === wanted && weight.is_available,
    );
    if (exact) return { weight: exact, substituted: false };
  }

  const fallback = defaultWeight(product);
  const substituted =
    wanted !== "" &&
    fallback !== null &&
    fallback.label.trim().toLowerCase() !== wanted;

  return { weight: fallback, substituted };
}

/** Why an item cannot be added to the basket, or `null` when it can. */
export function unavailableReason(
  product: CatalogueProduct,
  weight: ProductWeightRow | null,
): string | null {
  if (!product.availability) return "Out of stock";
  if (weight && !weight.is_available) return "This pack size is out of stock";
  if (product.weights.length > 0 && !product.weights.some((w) => w.is_available)) {
    return "Out of stock";
  }
  return null;
}
