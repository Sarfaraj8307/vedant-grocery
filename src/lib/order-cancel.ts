import type { OrderStatus } from "@/types/database";

/**
 * Statuses in which a customer may still cancel their own order.
 *
 * Mirrors `private.order_customer_may_cancel` in migration 022 — **the database
 * is the authority**, and `customer_cancel_order` re-checks it. This copy exists
 * only so the UI can hide the action instead of offering one that would be
 * refused. If the cutoff changes, change both.
 *
 * Deliberately in a plain module rather than the server-action file: a
 * `"use server"` module may only export async functions.
 */
export const CANCELLABLE_BY_CUSTOMER: readonly OrderStatus[] = [
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
];

export function customerMayCancel(status: OrderStatus): boolean {
  return CANCELLABLE_BY_CUSTOMER.includes(status);
}
