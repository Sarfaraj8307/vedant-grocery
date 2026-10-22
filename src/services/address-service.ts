"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import type { AddressRow } from "@/types/database";
import type { AddressInput } from "@/lib/validation";

/**
 * Customer address book.
 *
 * All operations are scoped by RLS to `user_id = auth.uid()`. The `user_id`
 * column also has `DEFAULT auth.uid()`, so it is never sent by the client — an
 * address cannot be written on behalf of another customer even if the code tried.
 */

export type AddressResult<T = void> =
  | { ok: true; data: T }
  | { ok: false; error: string };

export async function listAddresses(): Promise<AddressRow[]> {
  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("addresses")
    .select("*")
    .order("is_default", { ascending: false })
    .order("created_at", { ascending: true });

  if (error) return [];
  return (data ?? []) as AddressRow[];
}

/**
 * Turn an address write failure into copy the customer can act on.
 *
 * WHY: this used to collapse every failure into "We could not save that address.
 * Please try again." A real 42501 RLS rejection — which retrying can never fix —
 * was presented as if it were transient. That is how a permanently broken
 * insert looked like a flaky one for so long.
 *
 * The raw error is logged so the next occurrence is diagnosable instead of
 * invisible, while the customer sees something useful.
 */
export function describeAddressError(error: unknown): string {
  const code = (error as { code?: string })?.code ?? "";
  const message = ((error as { message?: string })?.message ?? "").toLowerCase();

  // Always leave a trail — a swallowed error is how this bug hid.
  if (typeof console !== "undefined") {
    console.error("[address-service] save failed", error);
  }

  if (code === "42501" || message.includes("row-level security")) {
    return "Your session has expired, so we could not save this address. Please sign in again and retry.";
  }
  if (message.includes("pincode") || message.includes("postal_code")) {
    return "Enter a valid 6-digit pincode.";
  }
  if (message.includes("phone")) {
    return "Enter a valid 10–15 digit phone number.";
  }
  if (code === "23502" || message.includes("null value")) {
    return "Something required is missing. Check the address and phone number.";
  }
  if (code === "23514") {
    return "One of the details is not accepted. Check the pincode and phone number.";
  }
  if (code === "PGRST301" || message.includes("jwt")) {
    return "Your session has expired. Please sign in again.";
  }
  return "We could not save that address. Please try again.";
}

export async function createAddress(
  input: AddressInput,
  options: { makeDefault?: boolean } = {},
): Promise<AddressResult<AddressRow>> {
  const supabase = getSupabaseBrowserClient();

  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in to save an address." };

  const { count } = await supabase
    .from("addresses")
    .select("id", { count: "exact", head: true });

  // The first address a customer saves becomes their default automatically.
  const shouldBeDefault = options.makeDefault ?? (count ?? 0) === 0;
  if (shouldBeDefault) {
    await clearDefaultFlags();
  }

  // Live table has legacy NOT NULL columns line1/pincode/city; send them too
  // so the insert never fails on a DB-level null check even if the trigger
  // ordering differs. Keep postal_code as the app-facing column.
  const line1 = `${input.house_number ?? ""} ${input.street}`.trim() || input.label;
  const pincode6 = (() => {
    const digits = input.postal_code.replace(/\D/g, "");
    return digits.slice(0, 6).padEnd(6, "0");
  })();

  const { data, error } = await supabase
    .from("addresses")
    .insert({
      // Sent explicitly as well as defaulted in the database (migration 020).
      // The column is NOT NULL with no default historically, and the RLS policy
      // is `auth.uid() = user_id`, so omitting it produced a 42501 on every
      // insert. Relying on the DB default alone would work, but stating it here
      // means a future schema change cannot silently break address creation
      // again — and the intent is visible at the call site.
      user_id: user.id,
      label: input.label,
      line1,
      line2: input.landmark ?? "",
      city: input.city,
      pincode: pincode6,
      full_name: input.full_name,
      phone: input.phone,
      house_number: input.house_number ?? "",
      street: input.street,
      landmark: input.landmark ?? "",
      state: input.state,
      postal_code: input.postal_code,
      latitude: input.latitude ?? null,
      longitude: input.longitude ?? null,
      is_default: shouldBeDefault,
    })
    .select("*")
    .single();

  if (error || !data) {
    return { ok: false, error: describeAddressError(error) };
  }
  return { ok: true, data: data as AddressRow };
}

export async function updateAddress(
  id: string,
  input: AddressInput,
): Promise<AddressResult<AddressRow>> {
  const supabase = getSupabaseBrowserClient();
  const line1 = `${input.house_number ?? ""} ${input.street}`.trim() || input.label;
  const pincode6 = (() => {
    const digits = input.postal_code.replace(/\D/g, "");
    return digits.slice(0, 6).padEnd(6, "0");
  })();
  const { data, error } = await supabase
    .from("addresses")
    .update({
      label: input.label,
      line1,
      line2: input.landmark ?? "",
      city: input.city,
      pincode: pincode6,
      full_name: input.full_name,
      phone: input.phone,
      house_number: input.house_number ?? "",
      street: input.street,
      landmark: input.landmark ?? "",
      state: input.state,
      postal_code: input.postal_code,
      latitude: input.latitude ?? null,
      longitude: input.longitude ?? null,
      updated_at: new Date().toISOString(),
    })
    .eq("id", id)
    .select("*")
    .single();

  if (error || !data) {
    // Same reasoning as createAddress: a 42501 here means the session expired or
    // the row is not yours, and "try again" would send the customer in circles.
    return { ok: false, error: describeAddressError(error) };
  }
  return { ok: true, data: data as AddressRow };
}

export async function deleteAddress(id: string): Promise<AddressResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.from("addresses").delete().eq("id", id);
  if (error) {
    return {
      ok: false,
      // FK is ON DELETE SET NULL for orders, so history is preserved — worth
      // saying, because customers worry about losing past orders.
      error: "We could not remove that address. Past orders are unaffected — please try again.",
    };
  }
  return { ok: true, data: undefined };
}

/**
 * Promote an address to default.
 *
 * `addresses_one_default_per_user_idx` is a **partial unique index**
 * (`UNIQUE (user_id) WHERE is_default`), so two rows cannot both be default.
 * The flags must therefore be cleared first, then set — a single update that
 * flips both would violate the index mid-statement.
 */
export async function setDefaultAddress(id: string): Promise<AddressResult> {
  const supabase = getSupabaseBrowserClient();
  const cleared = await clearDefaultFlags();
  if (!cleared.ok) return cleared;

  const { error } = await supabase
    .from("addresses")
    .update({ is_default: true, updated_at: new Date().toISOString() })
    .eq("id", id);

  if (error) {
    return { ok: false, error: "We could not set that as your default address." };
  }
  return { ok: true, data: undefined };
}

async function clearDefaultFlags(): Promise<AddressResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase
    .from("addresses")
    .update({ is_default: false })
    .eq("is_default", true);

  if (error) {
    return { ok: false, error: "We could not update your default address." };
  }
  return { ok: true, data: undefined };
}

/** A single-line summary for the header chip. */
export function formatAddressLine(address: AddressRow): string {
  return [address.house_number, address.street, address.landmark]
    .filter(Boolean)
    .join(", ");
}

export function formatAddressCity(address: AddressRow): string {
  return [address.city, address.state, address.postal_code]
    .filter(Boolean)
    .join(" ");
}

/** Google Maps link for admin to navigate to a delivery address. */
export function addressMapsUrl(address: AddressRow): string | null {
  if (address.latitude != null && address.longitude != null) {
    return `https://www.google.com/maps/search/?api=1&query=${address.latitude},${address.longitude}`;
  }
  const q = [address.house_number, address.street, address.landmark, address.city, address.state, address.postal_code]
    .filter(Boolean)
    .join(", ");
  if (!q) return null;
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
}
