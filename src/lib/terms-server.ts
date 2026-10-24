import "server-only";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import { TERMS_VERSION, type TermsAcceptanceSource } from "@/lib/terms";

/**
 * Server-side reads of the consent record.
 *
 * Kept separate from `lib/terms.ts` because that module is imported by client
 * components — pulling `server-only` (or a Supabase server client) into it
 * would break the client bundle.
 */

export type TermsAcceptance = {
  terms_version: string;
  accepted_at: string;
  source: TermsAcceptanceSource;
};

export type ConsentState = {
  /** True when the customer has accepted the *current* version. */
  accepted: boolean;
  /** The row for the current version, if any. */
  current: TermsAcceptance | null;
  /** The most recent acceptance of any version — shown on the account page. */
  latest: TermsAcceptance | null;
  /**
   * False when the lookup itself failed.
   *
   * The gate fails **open** on a failed lookup. An unapplied migration or a
   * transient database error is a deployment fault; locking every customer out
   * of the shop behind a dialog whose accept button also cannot write is a
   * worse outcome than a missing consent row for a few minutes.
   */
  checked: boolean;
};

const NOTHING: ConsentState = {
  accepted: false,
  current: null,
  latest: null,
  checked: false,
};

export async function getConsentState(userId: string): Promise<ConsentState> {
  const supabase = await getSupabaseServerClient();

  // One round trip: the table holds at most one row per version, so reading the
  // recent history and deciding in JS is cheaper than two separate queries.
  const { data, error } = await supabase
    .from("terms_acceptances")
    .select("terms_version, accepted_at, source")
    .eq("user_id", userId)
    .order("accepted_at", { ascending: false })
    .limit(10);

  if (error) {
    console.error("[terms] could not read consent state:", error.message);
    return NOTHING;
  }

  const rows = (data ?? []) as TermsAcceptance[];
  const current = rows.find((row) => row.terms_version === TERMS_VERSION) ?? null;

  return {
    accepted: Boolean(current),
    current,
    latest: rows[0] ?? null,
    checked: true,
  };
}
