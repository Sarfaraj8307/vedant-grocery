/**
 * Mapping from `public.place_order` failure signals to copy a customer can act
 * on.
 *
 * Extracted from `services/order-service.ts` so the **same** mapping is used
 * whether the order is submitted from the browser or from the Server Action
 * (`src/app/checkout/actions.ts`). Two mappings would drift, and a raw database
 * error must never reach a customer (DESIGN brief §31).
 *
 * The codes are the ones the function actually raises (verified from its live
 * body):
 *   42501 — authentication / ownership
 *   22023 — validation (slot, cart, item availability, request id, address)
 */

export type PlaceOrderErrorInfo = {
  /** Customer-safe sentence. */
  message: string;
  /** Postgres SQLSTATE, when the failure came from the database. */
  sqlState?: string;
};

export function describePlaceOrderError(error: {
  code?: string;
  message?: string;
}): PlaceOrderErrorInfo {
  const message = error.message ?? "";
  const sqlState = error.code;

  if (message.includes("cart item unavailable")) {
    return {
      message:
        "One of your items just went out of stock or changed pack size. Please check your basket and try again.",
      sqlState,
    };
  }
  if (message.includes("cart is empty")) {
    return {
      message: "Your basket is empty. Add something and try again.",
      sqlState,
    };
  }
  if (message.includes("delivery slot full")) {
    return {
      message: "That delivery slot just filled up. Please choose another one.",
      sqlState,
    };
  }
  if (message.includes("delivery slot unavailable")) {
    return {
      message: "That delivery slot is no longer available. Please pick another.",
      sqlState,
    };
  }
  if (message.includes("address unavailable")) {
    return {
      message:
        "We could not verify that delivery address. Please choose or add another.",
      sqlState,
    };
  }
  if (message.includes("below minimum order")) {
    return {
      message:
        "Your basket is below our minimum order value. Please add a little more.",
      sqlState,
    };
  }
  if (message.includes("authentication required")) {
    return {
      message: "Your session expired. Please sign in again to place the order.",
      sqlState,
    };
  }
  if (message.includes("invalid request id")) {
    return {
      message: "Something went wrong submitting this order. Please try again.",
      sqlState,
    };
  }

  // Never surface raw database text to a customer.
  return {
    message:
      "We could not place your order just now. Please try again in a moment.",
    sqlState,
  };
}
