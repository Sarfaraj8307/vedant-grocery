/**
 * Whether checkout should offer the "Instant Delivery" option.
 *
 * The option is visible only when the operator has enabled instant delivery
 * (`store_settings.instant_delivery_enabled`) AND the store is currently
 * accepting orders. Everything else hides it. A transient read error on the
 * store-settings flag fails *closed* (instantEnabled = false), so instant
 * delivery is never wrongly offered.
 */
export function shouldOfferInstantDelivery(
  instantEnabled: boolean,
  acceptingOrders: boolean,
): boolean {
  return instantEnabled && acceptingOrders;
}

/* -------------------------------------------------------------------------- */
/* Admin-editable Instant Delivery ETA (minutes)                              */
/* -------------------------------------------------------------------------- */

/** Safe fail-closed defaults, used when store_settings has no usable ETA. */
export const INSTANT_DELIVERY_ETA_MIN_DEFAULT = 30;
export const INSTANT_DELIVERY_ETA_MAX_DEFAULT = 45;

/** Inclusive bounds matching the DB CHECK constraint. */
const ETA_LOWER_BOUND = 1;
const ETA_UPPER_BOUND = 1440;

export type InstantDeliveryEta = {
  readonly min: number;
  readonly max: number;
};

/**
 * Validate + normalise the admin-editable ETA range read from `store_settings`.
 *
 * Anything invalid — missing, non-finite, non-integer, outside 1..1440, or a
 * min greater than the max — falls back to the safe 30 / 45 defaults, so the
 * checkout can never render a nonsensical or attacker-supplied ETA.
 */
export function normaliseInstantDeliveryEta(
  min: number | null | undefined,
  max: number | null | undefined,
): InstantDeliveryEta {
  if (
    typeof min !== "number" ||
    typeof max !== "number" ||
    !Number.isFinite(min) ||
    !Number.isFinite(max) ||
    !Number.isInteger(min) ||
    !Number.isInteger(max) ||
    min < ETA_LOWER_BOUND ||
    min > ETA_UPPER_BOUND ||
    max < ETA_LOWER_BOUND ||
    max > ETA_UPPER_BOUND ||
    min > max
  ) {
    return {
      min: INSTANT_DELIVERY_ETA_MIN_DEFAULT,
      max: INSTANT_DELIVERY_ETA_MAX_DEFAULT,
    };
  }
  return { min, max };
}

/**
 * Customer-facing subtitle for the Instant Delivery option.
 *
 * Derived entirely from the (normalised) admin values — no hard-coded ETA in
 * the JSX. An en-dash separates a true range; equal bounds render as a single
 * value.
 */
export function formatInstantDeliveryEta(min: number, max: number): string {
  const eta = normaliseInstantDeliveryEta(min, max);
  return eta.min === eta.max
    ? `Delivered in ${eta.min} mins`
    : `Delivered in ${eta.min}–${eta.max} mins`;
}
