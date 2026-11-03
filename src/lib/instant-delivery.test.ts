import { describe, expect, it } from "vitest";

import { shouldOfferInstantDelivery } from "@/lib/instant-delivery";

/**
 * The Instant Delivery option must be shown only when the operator has enabled
 * it AND the store is accepting orders. Any other combination hides it.
 */
describe("shouldOfferInstantDelivery", () => {
  it("offers instant delivery only when enabled and the store is open", () => {
    expect(shouldOfferInstantDelivery(true, true)).toBe(true);
  });

  it("hides instant delivery when the operator has not enabled it", () => {
    expect(shouldOfferInstantDelivery(false, true)).toBe(false);
  });

  it("hides instant delivery when the store is closed", () => {
    expect(shouldOfferInstantDelivery(true, false)).toBe(false);
  });

  it("hides instant delivery when disabled and closed", () => {
    expect(shouldOfferInstantDelivery(false, false)).toBe(false);
  });
});
