"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import { describePlaceOrderError } from "@/lib/order-errors";
import type {
  OrderItemRow,
  OrderRow,
  OrderStatusHistoryRow,
} from "@/types/database";

/**
 * Order operations.
 *
 * Order creation goes through `public.place_order` — there is no INSERT policy on
 * `orders`, `order_items` or `order_status_history` for customers (verified:
 * docs/BACKEND_CONTRACT.md §4). That is the correct architecture: the client
 * *cannot* write an order even if it tried, and pricing/availability/limits are
 * re-validated inside the function's transaction.
 */

export class OrderError extends Error {
  constructor(
    message: string,
    /** Postgres SQLSTATE, when the failure came from the database. */
    readonly sqlState?: string,
  ) {
    super(message);
    this.name = "OrderError";
  }
}

/**
 * Map the `place_order` error codes to copy a customer can act on.
 *
 * The mapping itself lives in `lib/order-errors.ts` so the browser path and the
 * Server Action (`app/checkout/actions.ts`) cannot drift apart.
 */
function mapPlaceOrderError(error: { code?: string; message?: string }): OrderError {
  const { message, sqlState } = describePlaceOrderError(error);
  return new OrderError(message, sqlState);
}

/**
 * Place the order from the server-side basket.
 *
 * `requestId` must be stable across retries of the *same* intended order:
 * `place_order` takes an advisory lock on `(user_id, request_id)` and returns the
 * existing order id if one already exists. That makes "the response was lost,
 * tap again" safe instead of creating a duplicate order.
 */
export async function placeOrder(input: {
  requestId: string;
  addressId: string;
  deliverySlotId: string;
}): Promise<string> {
  const supabase = getSupabaseBrowserClient();

  const { data, error } = await supabase.rpc("place_order", {
    p_request_id: input.requestId,
    p_address_id: input.addressId,
    p_delivery_slot_id: input.deliverySlotId,
  });

  if (error) {
    throw mapPlaceOrderError(error);
  }
  if (!data) {
    throw new OrderError(
      "We could not confirm your order. Please check your order history before retrying.",
    );
  }

  return data as string;
}

/** A generated request id that satisfies the 8–128 character CHECK. */
export function newRequestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `ord_${crypto.randomUUID()}`;
  }
  return `ord_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

/* -------------------------------------------------------------------------- */
/* Reads                                                                      */
/* -------------------------------------------------------------------------- */

export type OrderWithItems = OrderRow & {
  order_items: OrderItemRow[];
  order_status_history: OrderStatusHistoryRow[];
};

const ORDER_DETAIL_SELECT = `
  *,
  order_items ( id, order_id, product_id, product_name, weight_label, qty, unit_price, line_total, rejected, rejection_reason, created_at ),
  order_status_history ( id, order_id, old_status, new_status, changed_by, reason, created_at )
`;

/**
 * The signed-in customer's orders, newest first.
 * RLS scopes this to their own rows, so no explicit user filter is needed — and
 * adding one would be misleading because it would suggest the filter is the
 * protection.
 */
export async function listOrders(limit = 50): Promise<OrderRow[]> {
  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("orders")
    .select("*")
    .order("placed_at", { ascending: false })
    .limit(limit);

  if (error) return [];
  return (data ?? []) as OrderRow[];
}

export async function getOrder(orderId: string): Promise<OrderWithItems | null> {
  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("orders")
    .select(ORDER_DETAIL_SELECT)
    .eq("id", orderId)
    .maybeSingle();

  if (error || !data) return null;

  const order = data as unknown as OrderWithItems;
  return {
    ...order,
    order_items: [...(order.order_items ?? [])].sort((a, b) =>
      a.created_at.localeCompare(b.created_at),
    ),
    order_status_history: [...(order.order_status_history ?? [])].sort((a, b) =>
      a.created_at.localeCompare(b.created_at),
    ),
  };
}

/* -------------------------------------------------------------------------- */
/* Support                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * Create a support message.
 *
 * The INSERT policy enforces `user_id = auth.uid()`, `status = 'OPEN'`,
 * `resolved_* IS NULL`, and that any referenced order belongs to the caller — so
 * "attach this to someone else's order" is rejected by the database, not by the
 * form (docs/BACKEND_CONTRACT.md §4).
 */
export async function createComplaint(input: {
  subject: string;
  description: string;
  orderId?: string | null;
}): Promise<{ ok: true; id: string } | { ok: false; error: string }> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in to send us a message." };

  const { data, error } = await supabase
    .from("complaints")
    .insert({
      user_id: user.id,
      subject: input.subject.trim(),
      description: input.description.trim(),
      order_id: input.orderId || null,
    })
    .select("id")
    .single();

  if (error || !data) {
    return {
      ok: false,
      error: "We could not send that message. Please try again.",
    };
  }
  return { ok: true, id: (data as { id: string }).id };
}
