import { describe, expect, it } from "vitest";

import { describePlaceOrderError } from "./order-errors";

/**
 * Every branch here is a sentence a customer reads at the worst possible moment
 * — the moment they have decided to buy. Two rules:
 *
 *  1. never surface raw Postgres text (`record "new" has no field "customer_id"`
 *     is not an apology, it is a stack trace);
 *  2. never let a *specific* failure collapse into the generic "try again",
 *     because "try again" is what an outage looks like from the outside.
 */

const GENERIC = "We could not place your order just now. Please try again in a moment.";

describe("describePlaceOrderError — mapped failures", () => {
  it("explains an out-of-stock item", () => {
    expect(describePlaceOrderError({ message: "cart item unavailable" }).message).toContain(
      "out of stock",
    );
  });

  it("explains an empty basket", () => {
    expect(describePlaceOrderError({ message: "cart is empty" }).message).toContain(
      "basket is empty",
    );
  });

  it("distinguishes a full slot from an unavailable one", () => {
    const full = describePlaceOrderError({ message: "delivery slot full" }).message;
    const gone = describePlaceOrderError({ message: "delivery slot unavailable" }).message;
    expect(full).toContain("filled up");
    expect(gone).toContain("no longer available");
  });

  it("explains an expired session", () => {
    expect(
      describePlaceOrderError({ message: "authentication required", code: "42501" }).message,
    ).toContain("sign in again");
  });
});

describe("describePlaceOrderError — guards that used to fall through", () => {
  it("explains a blocked account instead of asking them to retry", () => {
    const blocked = describePlaceOrderError({
      message: "account is not permitted to place orders",
      code: "42501",
    }).message;
    expect(blocked).toContain("can't place orders");
    expect(blocked).not.toBe(GENERIC);
  });

  it("recognises the trigger's wording too", () => {
    expect(
      describePlaceOrderError({ message: "this customer is blocked from ordering", code: "23514" })
        .message,
    ).toContain("can't place orders");
  });

  it("explains an address outside the delivery area", () => {
    expect(
      describePlaceOrderError({ message: "delivery area not served" }).message,
    ).toContain("don't deliver to that address");
  });

  it("explains a closed store", () => {
    expect(describePlaceOrderError({ message: "store closed" }).message).toContain(
      "not taking orders",
    );
  });

  it("turns a duplicate retry into a check-your-history instruction", () => {
    expect(describePlaceOrderError({ message: "DUPLICATE_OR_RETRY" }).message).toContain(
      "already submitted",
    );
  });

  it("turns an inner VALIDATION code into something actionable", () => {
    expect(describePlaceOrderError({ message: "VALIDATION" }).message).toContain(
      "basket changed",
    );
  });

  it("keeps 'order failed' distinct from the generic sentence", () => {
    const message = describePlaceOrderError({ message: "order failed" }).message;
    expect(message).not.toBe(GENERIC);
    expect(message).toContain("basket is unchanged");
  });
});

describe("describePlaceOrderError — safety net", () => {
  it("never leaks raw Postgres text", () => {
    const leaked = describePlaceOrderError({
      message: 'record "new" has no field "customer_id"',
      code: "42703",
    });
    expect(leaked.message).toBe(GENERIC);
    expect(leaked.message).not.toContain("customer_id");
  });

  it("still reports the SQLSTATE for logs without showing it", () => {
    expect(describePlaceOrderError({ message: "something odd", code: "42703" }).sqlState).toBe(
      "42703",
    );
  });

  it("handles a missing message", () => {
    expect(describePlaceOrderError({}).message).toBe(GENERIC);
  });
});
