import "server-only";

import { redirect } from "next/navigation";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { avatarUrlFromMetadata, profileFieldsFromMetadata } from "@/lib/profile-fields";
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
  /** From `user_metadata` — available before `profiles` is read. */
  firstName: string;
  lastName: string;
  /** Provider picture (Google), when the provider supplied one. */
  avatarUrl: string | null;
};

export async function getSessionUser(): Promise<SessionUser | null> {
  const supabase = await getSupabaseServerClient();
  const {
    data: { user },
    error,
  } = await supabase.auth.getUser();

  if (error || !user) return null;

  const meta = (user.user_metadata ?? {}) as Record<string, unknown>;
  const fields = profileFieldsFromMetadata(meta);

  return {
    id: user.id,
    email: user.email ?? null,
    phone: user.phone ?? null,
    firstName: fields.firstName,
    lastName: fields.lastName,
    avatarUrl: avatarUrlFromMetadata(meta),
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
  avatarUrl: string | null;
}> {
  const user = await getSessionUser();
  if (!user) {
    return {
      user: null,
      profile: null,
      displayName: "",
      firstName: "",
      avatarUrl: null,
    };
  }
  const profile = await getProfile();

  // The `profiles` row can legitimately be missing (no trigger on auth.users).
  // Falling back to the signup/OAuth metadata means the header still greets the
  // customer by their real name instead of their email local-part.
  const firstName = profile?.first_name?.trim() || user.firstName;
  const lastName = profile?.last_name?.trim() || user.lastName;
  const displayName =
    [firstName, lastName].filter(Boolean).join(" ") ||
    user.email?.split("@")[0] ||
    "there";

  return { user, profile, displayName, firstName, avatarUrl: user.avatarUrl };
}

/** Redirects to sign-in, preserving where the customer was heading. */
export async function requireUser(nextPath: string): Promise<SessionUser> {
  const user = await getSessionUser();
  if (!user) {
    redirect(`/login?next=${encodeURIComponent(nextPath)}`);
  }
  return user;
}
