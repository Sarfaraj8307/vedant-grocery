import {
  applyPercentDiscount,
  minMoney,
  multiplyMoney,
  toPaise,
} from "@/lib/money";
import type {
  CatalogueProduct,
  OfferRow,
  PriceBreakdown,
  ProductWeightRow,
} from "@/types/database";

/**
 * Client-side pricing **preview**.
 *
 * This module reproduces the arithmetic inside `public.place_order` so the cart
 * and checkout can show the customer a total before they commit. It is
 * deliberately a pure function of catalogue data with no I/O, so it can run in
 * a Server Component, a Client Component and a unit test unchanged.
 *
 * ⚠️  THIS IS NEVER AUTHORITATIVE.
 * `place_order` re-reads the current price, availability and offer state inside
 * a transaction and rejects the order if anything has changed. The UI must
 * always present the server's returned totals on the confirmation screen, and
 * must treat a `22023` "cart item unavailable" response as "your basket needs
 * attention", not as a bug.
 */

/**
 * Pick the live offer the same way `place_order` does:
 *   where is_active
 *     and (starts_at is null or starts_at <= now())
 *     and (ends_at is null or ends_at > now())
 *   order by created_at desc limit 1
 */
export function pickActiveOffer(
  offers: readonly OfferRow[],
  now: Date = new Date(),
): OfferRow | null {
  const t = now.getTime();
  const live = offers.filter((offer) => {
    if (!offer.is_active) return false;
    if (offer.starts_at && new Date(offer.starts_at).getTime() > t) return false;
    if (offer.ends_at && new Date(offer.ends_at).getTime() <= t) return false;
    return true;
  });
  if (live.length === 0) return null;
  return live.reduce((best, offer) =>
    new Date(offer.created_at).getTime() > new Date(best.created_at).getTime()
      ? offer
      : best,
  );
}

/** Whether an offer is live right now (used for the "offers" pages). */
export function isOfferLive(offer: OfferRow, now: Date = new Date()): boolean {
  const t = now.getTime();
  if (!offer.is_active) return false;
  if (offer.starts_at && new Date(offer.starts_at).getTime() > t) return false;
  if (offer.ends_at && new Date(offer.ends_at).getTime() <= t) return false;
  return true;
}

/**
 * The list price for a chosen weight, or the product's `base_price` when no
 * weight variant is selected — mirroring `coalesce(w.price, p.base_price)`.
 */
export function listPriceFor(
  product: Pick<CatalogueProduct, "base_price">,
  weight: ProductWeightRow | null,
): number {
  return weight ? weight.price : product.base_price;
}

/**
 * Full price breakdown for a product + optional weight.
 * Reproduces `place_order`'s nested `least(...)` rules:
 *   1. start from coalesce(weight.price, base_price)
 *   2. apply a fixed offer price with least()
 *   3. apply a percentage offer with least(), each independently
 */
export function priceFor(
  product: CatalogueProduct,
  weight: ProductWeightRow | null,
): PriceBreakdown {
  const listPrice = listPriceFor(product, weight);
  const offer = product.activeOffer;

  let unitPrice = listPrice;

  if (offer) {
    if (offer.price !== null) {
      unitPrice = minMoney(unitPrice, offer.price);
    }
    if (offer.discount_percent !== null) {
      unitPrice = minMoney(
        unitPrice,
        applyPercentDiscount(listPrice, offer.discount_percent),
      );
    }
  }

  const savingPaise = toPaise(listPrice) - toPaise(unitPrice);
  const savingPerUnit = savingPaise / 100;
  const isDiscounted = savingPaise > 0;

  return {
    listPrice,
    unitPrice,
    savingPerUnit,
    savingPercent: isDiscounted
      ? Math.round((savingPaise / toPaise(listPrice)) * 100)
      : 0,
    currency: "INR",
    isDiscounted,
  };
}

/**
 * The cheapest available option for a product — used for the "from ₹x" price
 * shown on a product card before a weight is chosen, and for sorting.
 * Returns `null` when nothing is purchasable.
 */
export function cheapestOption(
  product: CatalogueProduct,
): { weight: ProductWeightRow | null; breakdown: PriceBreakdown } | null {
  if (!product.availability) return null;

  const candidates: (ProductWeightRow | null)[] = product.weights.length
    ? product.weights.filter((w) => w.is_available)
    : [null];

  let best: { weight: ProductWeightRow | null; breakdown: PriceBreakdown } | null =
    null;

  for (const weight of candidates) {
    const breakdown = priceFor(product, weight);
    if (toPaise(breakdown.unitPrice) <= 0) continue;
    if (!best || toPaise(breakdown.unitPrice) < toPaise(best.breakdown.unitPrice)) {
      best = { weight, breakdown };
    }
  }

  return best;
}

/** A stable cart-line identity: a product plus an optional weight variant. */
export function cartKey(productId: string, weightId: string | null): string {
  return weightId ? `${productId}::${weightId}` : `${productId}::base`;
}

/* -------------------------------------------------------------------------- */
/* Order totals preview                                                       */
/* -------------------------------------------------------------------------- */

/**
 * Delivery-charge preview. The authoritative values live in
 * `private.shop_settings` (currently ₹20, free at ₹500) and are only applied
 * server-side. They are mirrored here purely so the checkout can show the
 * customer a realistic total; the confirmation screen always shows the number
 * `place_order` actually wrote.
 */
export const SHOP_SETTINGS_PREVIEW = {
  deliveryCharge: 20,
  freeDeliveryThreshold: 500,
} as const;

export function previewDeliveryCharge(subtotal: number): number {
  if (toPaise(subtotal) >= toPaise(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold)) {
    return 0;
  }
  return SHOP_SETTINGS_PREVIEW.deliveryCharge;
}

export type TotalsPreview = {
  subtotal: number;
  listSubtotal: number;
  savings: number;
  deliveryCharge: number;
  total: number;
  freeDeliveryThreshold: number;
  amountToFreeDelivery: number;
};

export function previewTotals(
  lines: readonly { unitPrice: number; listPrice: number; quantity: number }[],
): TotalsPreview {
  let subtotalPaise = 0;
  let listPaise = 0;
  for (const line of lines) {
    subtotalPaise += toPaise(line.unitPrice) * line.quantity;
    listPaise += toPaise(line.listPrice) * line.quantity;
  }
  const subtotal = subtotalPaise / 100;
  const deliveryCharge = previewDeliveryCharge(subtotal);
  return {
    subtotal,
    listSubtotal: listPaise / 100,
    savings: (listPaise - subtotalPaise) / 100,
    deliveryCharge,
    total: (subtotalPaise + toPaise(deliveryCharge)) / 100,
    freeDeliveryThreshold: SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold,
    amountToFreeDelivery: Math.max(
      0,
      (toPaise(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold) - subtotalPaise) / 100,
    ),
  };
}

/** Line total using the same integer path as the database. */
export function lineTotal(unitPrice: number, quantity: number): number {
  return multiplyMoney(unitPrice, quantity);
}
