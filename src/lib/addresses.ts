import "server-only";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import { isSupabaseConfigured } from "@/lib/env";
import type { AddressRow } from "@/types/database";

/**
 * Server-side address read.
 *
 * No `user_id` filter is applied: the `addresses_read_self` policy
 * (`user_id = auth.uid()`) is what scopes the result, and it cannot be
 * forgotten or mis-typed. Default first, then oldest first, so the ordering is
 * stable and matches what the checkout picker expects.
 */
export async function listAddressesForUser(): Promise<AddressRow[]> {
  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("addresses")
    .select("*")
    .order("is_default", { ascending: false })
    .order("created_at", { ascending: true });

  return (data ?? []) as AddressRow[];
}

/**
 * The customer's current delivery location, for the header chip and the home
 * page. Prefers the default address, then the oldest — the same ordering the
 * checkout picker uses, so what the header shows is what checkout selects.
 *
 * Returns `null` for a signed-out customer or one with no saved address; the UI
 * renders a "set your location" state rather than inventing a place.
 */
export type DeliveryLocation = {
  label: string;
  city: string;
  postalCode: string;
};

export async function getDeliveryLocation(): Promise<DeliveryLocation | null> {
  if (!isSupabaseConfigured()) return null;
  try {
    const supabase = await getSupabaseServerClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) return null;

    const { data } = await supabase
      .from("addresses")
      .select("label, city, postal_code, is_default")
      .order("is_default", { ascending: false })
      .order("created_at", { ascending: true })
      .limit(1)
      .maybeSingle();

    if (!data) return null;
    const row = data as {
      label: string;
      city: string | null;
      postal_code: string | null;
    };
    return {
      label: row.label,
      city: row.city ?? "",
      postalCode: row.postal_code ?? "",
    };
  } catch {
    return null;
  }
}
