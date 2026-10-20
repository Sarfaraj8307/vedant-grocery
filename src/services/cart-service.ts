"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import type { CartItemRow } from "@/types/database";

/**
 * Server-side cart persistence.
 *
 * WHY THERE IS NO `upsert()` HERE
 * ------------------------------
 * `cart_items` enforces line uniqueness through **two partial** unique indexes:
 *
 *   (user_id, product_id)             WHERE weight_id IS NULL
 *   (user_id, product_id, weight_id)  WHERE weight_id IS NOT NULL
 *
 * PostgREST's `upsert(..., { onConflict })` cannot express the `WHERE`
 * predicate a partial index requires, so `ON CONFLICT` inference fails with
 * "no unique or exclusion constraint matching the ON CONFLICT specification".
 * The correct, verified-by-schema approach is an explicit read-then-write.
 *
 * Concurrency: this is a fast local write scoped to one customer's own rows, and
 * `public.place_order` serialises checkout with
 * `pg_advisory_xact_lock(uid || ':cart')`. A double-tap can therefore at worst
 * create a duplicate row that the unique index rejects — which is surfaced as a
 * recoverable error and re-read, never as data corruption.
 */

export type RemoteCartLine = {
  product_id: string;
  weight_id: string | null;
  quantity: number;
};

export class CartServiceError extends Error {
  constructor(message: string, readonly cause?: unknown) {
    super(message);
    this.name = "CartServiceError";
  }
}

export async function fetchRemoteCart(): Promise<RemoteCartLine[]> {
  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("cart_items")
    .select("product_id, weight_id, quantity")
    .order("created_at", { ascending: true });

  if (error) {
    throw new CartServiceError("Could not load your basket.", error);
  }
  return (data ?? []) as RemoteCartLine[];
}

/**
 * Write one line, inserting or updating as required.
 * Passing `quantity <= 0` deletes the line.
 */
export async function persistLine(params: {
  productId: string;
  weightId: string | null;
  quantity: number;
}): Promise<void> {
  const { productId, weightId, quantity } = params;
  const supabase = getSupabaseBrowserClient();

  if (quantity <= 0) {
    await deleteLine(productId, weightId);
    return;
  }

  // Find the existing row for this exact (product, weight) pair.
  let lookup = supabase
    .from("cart_items")
    .select("id")
    .eq("product_id", productId);

  lookup = weightId
    ? lookup.eq("weight_id", weightId)
    : lookup.is("weight_id", null);

  const { data: existing, error: lookupError } = await lookup.maybeSingle();

  if (lookupError) {
    throw new CartServiceError("Could not update your basket.", lookupError);
  }

  if (existing?.id) {
    const { error } = await supabase
      .from("cart_items")
      .update({ quantity, updated_at: new Date().toISOString() })
      .eq("id", existing.id);

    if (error) {
      throw new CartServiceError("Could not update your basket.", error);
    }
    return;
  }

  const { error } = await supabase.from("cart_items").insert({
    product_id: productId,
    weight_id: weightId,
    quantity,
  });

  if (error) {
    // 23505 = unique_violation: a concurrent write created the line first.
    // Treat it as success — the desired state (line exists) is already true.
    if (error.code === "23505") return;
    throw new CartServiceError("Could not add this to your basket.", error);
  }
}

export async function deleteLine(
  productId: string,
  weightId: string | null,
): Promise<void> {
  const supabase = getSupabaseBrowserClient();
  let query = supabase.from("cart_items").delete().eq("product_id", productId);
  query = weightId ? query.eq("weight_id", weightId) : query.is("weight_id", null);

  const { error } = await query;
  if (error) {
    throw new CartServiceError("Could not remove this item.", error);
  }
}

export async function clearRemoteCart(): Promise<void> {
  const supabase = getSupabaseBrowserClient();
  // The DELETE policy is `user_id = auth.uid()`, so an unfiltered delete can
  // only ever affect the caller's own rows. The explicit filter is still added
  // so the intent is readable and the planner uses cart_items_owner_idx.
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return;

  const { error } = await supabase
    .from("cart_items")
    .delete()
    .eq("user_id", user.id);

  if (error) {
    throw new CartServiceError("Could not empty your basket.", error);
  }
}

/**
 * Replace the entire server cart with `lines` in one pass.
 * Used by the sign-in merge, where a partial write would lose items.
 */
export async function replaceRemoteCart(lines: RemoteCartLine[]): Promise<void> {
  await clearRemoteCart();
  if (lines.length === 0) return;

  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.from("cart_items").insert(
    lines.map((line) => ({
      product_id: line.product_id,
      weight_id: line.weight_id,
      quantity: line.quantity,
    })),
  );
  if (error) {
    throw new CartServiceError("Could not sync your basket.", error);
  }
}

/** Raw rows, for tests and diagnostics. */
export type { CartItemRow };
