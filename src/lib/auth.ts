import "server-only";

import { redirect } from "next/navigation";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import type { ProfileRow } from "@/types/database";

/**
 * Server-side auth helpers.
 *
 * `getUser()` (not `getSession()`) is used everywhere: it validates the access
 * token against the Supabase auth server instead of trusting the cookie, which
 * is the only safe way to gate a server render.
 *
 * IMPORTANT: None of these functions is the security boundary. Every table is
 * protected by RLS, so even if a check here were wrong the database would still
 * refuse another customer's rows. These helpers exist to produce good UX
 * (redirects, personalised headers) — never to enforce authorization.
 */

export type SessionUser = {
  id: string;
  email: string | null;
  phone: string | null;
};

export async function getSessionUser(): Promise<SessionUser | null> {
  const supabase = await getSupabaseServerClient();
  const {
    data: { user },
    error,
  } = await supabase.auth.getUser();

  if (error || !user) return null;
  return {
    id: user.id,
    email: user.email ?? null,
    phone: user.phone ?? null,
  };
}

/**
 * The signed-in customer's profile row.
 *
 * There is **no trigger** on `auth.users` creating this row (verified against
 * the live database — see docs/BACKEND_CONTRACT.md §7.3), so a profile can
 * legitimately be missing. Callers must handle `null`.
 */
export async function getProfile(): Promise<ProfileRow | null> {
  const supabase = await getSupabaseServerClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return null;

  const { data } = await supabase
    .from("profiles")
    .select("*")
    .eq("id", user.id)
    .maybeSingle();

  return (data as ProfileRow | null) ?? null;
}

/** Profile with a graceful fallback when the row has not been created yet. */
export async function getProfileOrDefault(): Promise<{
  user: SessionUser | null;
  profile: ProfileRow | null;
  displayName: string;
  firstName: string;
}> {
  const user = await getSessionUser();
  if (!user) {
    return { user: null, profile: null, displayName: "", firstName: "" };
  }
  const profile = await getProfile();
  const firstName = profile?.first_name?.trim() || "";
  const lastName = profile?.last_name?.trim() || "";
  const displayName =
    [firstName, lastName].filter(Boolean).join(" ") ||
    user.email?.split("@")[0] ||
    "there";

  return { user, profile, displayName, firstName };
}

/** Redirects to sign-in, preserving where the customer was heading. */
export async function requireUser(nextPath: string): Promise<SessionUser> {
  const user = await getSessionUser();
  if (!user) {
    redirect(`/login?next=${encodeURIComponent(nextPath)}`);
  }
  return user;
}
