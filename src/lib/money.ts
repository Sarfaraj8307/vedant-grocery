/**
 * Money helpers.
 *
 * The database stores money as Postgres `numeric(12,2)` and `public.place_order`
 * performs every authoritative calculation there, using `round(..., 2)`.
 * The client therefore only ever needs to (a) format, and (b) reproduce the
 * *same* arithmetic for an explicitly-labelled preview.
 *
 * To avoid binary-floating-point drift (0.1 + 0.2 !== 0.3) all arithmetic here
 * runs on **integer paise**. Nothing in this module adds raw floats.
 */

/** Money is always Indian rupees. */
export const CURRENCY = "INR" as const;

/**
 * Convert a database numeric (already 2dp) to integer paise.
 * `Math.round` guards against the last-bit representation error PostgREST
 * can introduce for values such as 166.65.
 */
export function toPaise(amount: number): number {
  if (!Number.isFinite(amount)) return 0;
  return Math.round(amount * 100);
}

/** Convert integer paise back to a 2dp rupee number. */
export function fromPaise(paise: number): number {
  return Math.round(paise) / 100;
}

/** Add money safely. */
export function addMoney(...amounts: number[]): number {
  return fromPaise(amounts.reduce((sum, a) => sum + toPaise(a), 0));
}

/** Multiply a unit price by an integer quantity without float drift. */
export function multiplyMoney(amount: number, quantity: number): number {
  return fromPaise(toPaise(amount) * Math.trunc(quantity));
}

/**
 * Reproduce `round(base * (1 - percent/100), 2)` from `place_order`.
 * Postgres `round()` on numeric is half-away-from-zero; `Math.round` is
 * half-up, which matches for the non-negative values in this domain.
 */
export function applyPercentDiscount(base: number, percent: number): number {
  const paise = toPaise(base);
  const discounted = (paise * (100 - percent)) / 100;
  return fromPaise(Math.round(discounted));
}

/** The better (lower) of two prices, using integer comparisons. */
export function minMoney(a: number, b: number): number {
  return toPaise(a) <= toPaise(b) ? a : b;
}

const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const inrCompactFormatter = new Intl.NumberFormat("en-IN", {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/**
 * Format rupees for display: `₹1,240` and `₹24.50`.
 *
 * Always pass through this function rather than interpolating a number, so
 * Indian lakh grouping and the 2dp rule stay consistent everywhere.
 */
export function formatMoney(amount: number): string {
  if (!Number.isFinite(amount)) return "₹0";
  return inrFormatter.format(fromPaise(toPaise(amount)));
}

/** Format rupees without the `₹` glyph (for places that draw it separately). */
export function formatAmount(amount: number): string {
  if (!Number.isFinite(amount)) return "0";
  return inrCompactFormatter.format(fromPaise(toPaise(amount)));
}

/**
 * Split a price so the `₹` glyph can be typographically smaller than the
 * amount (DESIGN_SYSTEM §3.2).
 */
export function splitMoney(amount: number): { symbol: string; value: string } {
  const formatted = formatMoney(amount);
  return { symbol: "₹", value: formatted.replace(/^₹\s?/, "") };
}
