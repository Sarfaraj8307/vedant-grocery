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
