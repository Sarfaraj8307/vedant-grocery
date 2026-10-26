import { getSupabaseServerClient } from "@/lib/supabase/server";
import { normaliseDial, type StoreContactChannel } from "@/config/store";

/**
 * Store contact channels resolved from the live `store_settings` row.
 *
 * The store's numbers are maintained by the owner from the Android admin app
 * (`store_settings.phone` / `store_settings.whatsapp`). This is the server-side
 * lookup that surfaces those numbers to customers, so the call / WhatsApp
 * buttons render from what the owner actually published rather than only from
 * the `NEXT_PUBLIC_STORE_*` environment fallback.
 *
 * Columns are anon-readable (see admin `docs/CONTRACT.md` §3.6), so the anon
 * server client can read them without any privileged key. Any failure degrades
 * to "no contact channels" and the UI falls back to env config / explanatory
 * copy — never crashes the page.
 */
export type StoreContact = {
  phone: StoreContactChannel | null;
  whatsapp: StoreContactChannel | null;
};

export async function getStoreContact(): Promise<StoreContact> {
  try {
    const supabase = await getSupabaseServerClient();
    const { data } = await supabase
      .from("store_settings")
      .select("phone, whatsapp")
      .limit(1)
      .maybeSingle();

    if (!data) return { phone: null, whatsapp: null };

    const row = data as { phone?: string | null; whatsapp?: string | null };
    return {
      phone: normaliseDial(row.phone ?? undefined),
      whatsapp: normaliseDial(row.whatsapp ?? undefined),
    };
  } catch {
    return { phone: null, whatsapp: null };
  }
}
