"use server";

import { revalidatePath } from "next/cache";

import { requireUser } from "@/lib/auth";
import { getSupabaseServerClient } from "@/lib/supabase/server";

export type CancelOrderResult = { ok: true } | { ok: false; error: string };

/**
 * Cancel the signed-in customer's own order (migration 022).
 *
 * Authorization, ownership, the status window and stock restoration all happen
 * inside `customer_cancel_order` — this action does not decide any of them. It
 * only validates the reason shape and turns the RPC's error into copy the
 * customer can act on.
 */
export async function cancelOrder(
  orderId: string,
  reason: string,
): Promise<CancelOrderResult> {
  await requireUser(`/orders/${orderId}`);

  const trimmed = reason.trim();
  if (trimmed.length < 3) {
    return { ok: false, error: "Tell us briefly why you want to cancel." };
  }

  const supabase = await getSupabaseServerClient();
  const { error } = await supabase.rpc("customer_cancel_order", {
    p_order_id: orderId,
    p_reason: trimmed,
  });

  if (error) {
    return { ok: false, error: describeCancelError(error.message) };
  }

  // The order page renders the cancelled state; the list must stop showing it
  // as active.
  revalidatePath(`/orders/${orderId}`);
  revalidatePath("/orders");
  return { ok: true };
}

/**
 * The RPC's messages are already close to customer-facing, so they are mapped
 * rather than replaced — except the cutoff case, which needs to tell the
 * customer what to do next (contact the store), not just what failed.
 */
function describeCancelError(message: string): string {
  const m = message.toLowerCase();

  if (m.includes("no longer be cancelled")) {
    return "This order is already being packed, so it can't be cancelled here. Please contact the store and we'll help.";
  }
  if (m.includes("reason is required")) {
    return "Tell us briefly why you want to cancel.";
  }
  if (m.includes("not found")) {
    return "We couldn't find that order. Refresh the page and try again.";
  }
  if (m.includes("not authorized") || m.includes("authentication")) {
    return "Your session has expired. Please sign in again.";
  }
  return "We couldn't cancel that order. Please try again or contact the store.";
}
