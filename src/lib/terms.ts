/**
 * The terms version customers are currently asked to accept.
 *
 * Bump this whenever the published terms change in a way that needs fresh
 * consent. Every stored acceptance records the version it was given for, so a
 * bump means existing customers are asked once more and their earlier
 * acceptance stays on record.
 *
 * This module is deliberately free of `server-only` and of any Supabase import:
 * the client consent dialog and the server gate must agree on the same string,
 * and the only way to guarantee that is to share one constant.
 *
 * Keep in sync with:
 *  • the `updated` date on /terms
 *  • the `terms_version` values already stored in `public.terms_acceptances`
 */
export const TERMS_VERSION = "2026-10-25";

/** Human-readable date shown on the terms page and in the account section. */
export const TERMS_UPDATED = "25 October 2026";

/** Where an acceptance came from — mirrors the CHECK on `source`. */
export type TermsAcceptanceSource = "signup" | "google" | "login" | "account";

/**
 * Label for a stored source value.
 *
 * Shown to the customer rather than the raw enum, so the account page reads
 * "when you created your account" instead of "signup".
 */
export const TERMS_SOURCE_LABEL: Record<TermsAcceptanceSource, string> = {
  signup: "when you created your account",
  google: "when you signed in with Google",
  login: "when you signed in",
  account: "from your account page",
};
