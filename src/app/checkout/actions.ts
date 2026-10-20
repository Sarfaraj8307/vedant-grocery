"use server";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import {
  checkServiceability,
  type ServiceabilityVerdict,
} from "@/lib/serviceability";
import { describePlaceOrderError } from "@/lib/order-errors";
import { getStoreStatus, ORDERS_CLOSED_MESSAGE } from "@/lib/store-status";

/**
 * Checkout Server Actions — the **authoritative** half of the checkout flow.
 *
 * WHY SERVER ACTIONS
 * The browser already knows the address it selected, but it must not be trusted
 * to decide whether we deliver there: a tampered client could claim a Pune
 * pincode for a Mumbai address. Both actions below therefore re-read the address
 * from the database with the customer's own session, so the decision is made
 * from stored data, not from anything the client sent.
 *
 * No privileged client is used — `getSupabaseServerClient()` carries the
 * customer's cookies, so the same RLS policies apply as in the browser. This is
 * a *placement* boundary, not a privilege escalation.
 */

/** Verify a saved address is inside our delivery area. */
export async function verifyAddressServiceability(
  addressId: string,
): Promise<ServiceabilityVerdict> {
  if (!addressId) {
    return {
      serviceable: false,
      code: "MISSING_CITY",
      reason: "Choose a delivery address to continue.",
    };
  }

  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase
    .from("addresses")
    .select("city, state, postal_code")
    .eq("id", addressId)
    .maybeSingle();

  if (error || !data) {
    return {
      serviceable: false,
      code: "OUT_OF_AREA",
      reason: "We could not verify that address. Please choose another.",
    };
  }

  const row = data as {
    city: string | null;
    state: string | null;
    postal_code: string | null;
  };

  return checkServiceability({
    city: row.city,
    state: row.state,
    postalCode: row.postal_code,
  });
}

export type SubmitOrderResult =
  | { ok: true; orderId: string }
  | { ok: false; error: string };

/**
 * Place an order, gated by the serviceability check.
 *
 * Order of operations (the serviceability gate is *before* the write, so an
 * out-of-area order can never reach `place_order`):
 *   1. verify the address is serviceable (re-read server-side);
 *   2. call `public.place_order` with the customer's session;
 *   3. map any database failure to customer-safe copy.
 *
 * `requestId` is supplied by the client and must stay stable across retries of
 * the *same* intended order — `place_order` takes an advisory lock on
 * `(user_id, request_id)` and returns the existing order id, so a lost response
 * cannot create a duplicate.
 */
export async function submitOrder(input: {
  requestId: string;
  addressId: string;
  deliverySlotId: string;
}): Promise<SubmitOrderResult> {
  // Order acceptance (§9/§36). `place_order` enforces this itself with
  // STORE_CLOSED; checking here first only exists so the customer gets the
  // honest "we're not accepting orders" message instead of a generic rejection.
  // The database remains the authority — this is not the gate.
  const store = await getStoreStatus();
  if (!store.acceptingOrders) {
    return {
      ok: false,
      error: store.closedReason
        ? `${ORDERS_CLOSED_MESSAGE} (${store.closedReason})`
        : ORDERS_CLOSED_MESSAGE,
    };
  }

  const verdict = await verifyAddressServiceability(input.addressId);
  if (!verdict.serviceable) {
    return { ok: false, error: verdict.reason };
  }

  const supabase = await getSupabaseServerClient();
  const { data, error } = await supabase.rpc("place_order", {
    p_request_id: input.requestId,
    p_address_id: input.addressId,
    p_delivery_slot_id: input.deliverySlotId,
  });

  if (error) {
    return { ok: false, error: describePlaceOrderError(error).message };
  }
  if (!data) {
    return {
      ok: false,
      error:
        "We could not confirm your order. Please check your order history before retrying.",
    };
  }

  return { ok: true, orderId: data as string };
}
