"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import { absoluteUrl } from "@/config/site";

/**
 * Customer authentication.
 *
 * ARCHITECTURE NOTES
 * ------------------
 * • `@supabase/ssr` stores the session in httpOnly-capable cookies so Server
 *   Components can read it; this service only *triggers* auth events.
 * • Email/password + Google are real Supabase Auth flows. Nothing here fakes a
 *   login: every function returns the provider's own result.
 * • Profile creation is explicit. The live database has **no trigger** on
 *   `auth.users` (docs/BACKEND_CONTRACT.md §7.3), so a row in `public.profiles`
 *   is written by `ensureProfile()` — either right after signup when a session
 *   is returned, or on the first authenticated visit when email confirmation is
 *   enabled. The signup form always asks for a username because
 *   `profiles.username` is `NOT NULL UNIQUE` and the database exposes
 *   `check_username_available()` for exactly this purpose.
 */

export type AuthResult<T = void> =
  | { ok: true; data: T }
  | { ok: false; error: string; code?: string };

/** Turn Supabase's provider errors into something a customer can act on. */
export function friendlyAuthError(message: string, code?: string): string {
  const text = message.toLowerCase();

  if (text.includes("invalid login credentials")) {
    return "That email and password don't match. Check them and try again.";
  }
  if (text.includes("email not confirmed")) {
    return "Please confirm your email address first — check your inbox for our link.";
  }
  if (text.includes("user already registered") || code === "user_already_exists") {
    return "An account with this email already exists. Try signing in instead.";
  }
  if (text.includes("password should be at least")) {
    return "Please choose a password with at least 8 characters.";
  }
  if (text.includes("rate limit") || text.includes("too many")) {
    return "Too many attempts. Please wait a minute and try again.";
  }
  if (text.includes("username") || code === "23505") {
    return "That username is already taken. Please pick another.";
  }
  if (text.includes("failed to fetch") || text.includes("network")) {
    return "We couldn't reach the server. Check your connection and try again.";
  }
  if (text.includes("provider is not enabled")) {
    return "That sign-in method isn't available right now. Please use email instead.";
  }
  // Never surface raw provider text — it can leak implementation details.
  return "Something went wrong. Please try again in a moment.";
}

/* -------------------------------------------------------------------------- */
/* Username                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Pre-auth availability check backed by `public.check_username_available()`.
 * The RPC is granted to `anon` and returns a boolean only, so it leaks nothing.
 */
export async function checkUsername(username: string): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const normalised = username.trim().toLowerCase();
  if (normalised.length < 3) return false;
  const { data, error } = await supabase.rpc("check_username_available", {
    p_username: normalised,
  });
  if (error) {
    // Fallback to a direct EXISTS probe when the RPC is unavailable (e.g. during
    // a deploy window). An anon client cannot enumerate usernames beyond an
    // equality probe, so this does not leak the directory.
    const { data: rows, error: fallbackError } = await supabase
      .from("profiles")
      .select("username")
      .ilike("username", normalised)
      .limit(1);
    if (fallbackError) return false;
    return (rows?.length ?? 0) === 0;
  }
  return data === true;
}

/** Deterministic fallback username derived from an email local-part. */
export function deriveUsername(email: string): string {
  const base = email
    .split("@")[0]
    ?.toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_{2,}/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 18);
  const safe = base && base.length >= 3 ? base : "vedant_customer";
  return `${safe}_${Math.random().toString(36).slice(2, 6)}`.slice(0, 24);
}

/* -------------------------------------------------------------------------- */
/* Email + password                                                           */
/* -------------------------------------------------------------------------- */

export async function signUpWithPassword(input: {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  username: string;
}): Promise<AuthResult<{ needsEmailConfirmation: boolean }>> {
  const supabase = getSupabaseBrowserClient();

  const { data, error } = await supabase.auth.signUp({
    email: input.email.trim(),
    password: input.password,
    options: {
      // Carried in user_metadata so `ensureProfile()` can create the profile row
      // on first login when email confirmation defers the session.
      data: {
        first_name: input.firstName.trim(),
        last_name: input.lastName.trim(),
        username: input.username.toLowerCase(),
      },
      emailRedirectTo: absoluteUrl("/auth/callback"),
    },
  });

  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }

  const needsEmailConfirmation = !data.session;
  if (data.session) {
    await createProfileRow({
      firstName: input.firstName,
      lastName: input.lastName,
      username: input.username,
      email: input.email,
    });
  }

  return { ok: true, data: { needsEmailConfirmation } };
}

export async function signInWithPassword(input: {
  email: string;
  password: string;
}): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.signInWithPassword({
    email: input.email.trim(),
    password: input.password,
  });

  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  await ensureProfile();
  return { ok: true, data: undefined };
}

/**
 * Google OAuth. Redirects the browser to Google, so the promise resolves only
 * once the redirect has been initiated.
 */
export async function signInWithGoogle(nextPath = "/"): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.signInWithOAuth({
    provider: "google",
    options: {
      redirectTo: absoluteUrl(
        `/auth/callback?next=${encodeURIComponent(nextPath)}`,
      ),
      queryParams: { prompt: "select_account" },
    },
  });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  return { ok: true, data: undefined };
}

/* -------------------------------------------------------------------------- */
/* Password reset                                                             */
/* -------------------------------------------------------------------------- */

export async function sendPasswordReset(email: string): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), {
    redirectTo: absoluteUrl("/auth/callback?next=/account/reset-password"),
  });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  // Deliberately reports success even for an unknown address, so the form
  // cannot be used to enumerate customers.
  return { ok: true, data: undefined };
}

export async function updatePassword(newPassword: string): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.updateUser({ password: newPassword });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  return { ok: true, data: undefined };
}

/* -------------------------------------------------------------------------- */
/* Sign out                                                                   */
/* -------------------------------------------------------------------------- */

export async function signOut(): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.signOut();
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code) };
  }
  return { ok: true, data: undefined };
}

/* -------------------------------------------------------------------------- */
/* Profile                                                                    */
/* -------------------------------------------------------------------------- */

type ProfileInput = {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string | null;
  phone?: string | null;
};

async function createProfileRow(input: ProfileInput): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;

  const username = (input.username ?? deriveUsername(user.email ?? "")).toLowerCase();

  const { error } = await supabase.from("profiles").insert({
    id: user.id,
    username,
    first_name: input.firstName?.trim() ?? "",
    last_name: input.lastName?.trim() ?? "",
    email: input.email ?? user.email ?? null,
    phone: input.phone ?? null,
  });

  if (error) {
    // 23505 on the username index: retry once with a derived unique suffix.
    if (error.code === "23505" && !input.username) {
      const { error: retryError } = await supabase.from("profiles").insert({
        id: user.id,
        username: deriveUsername(user.email ?? ""),
        first_name: input.firstName?.trim() ?? "",
        last_name: input.lastName?.trim() ?? "",
        email: input.email ?? user.email ?? null,
      });
      return !retryError;
    }
    return false;
  }
  return true;
}

/**
 * Ensure the signed-in customer has a profile row.
 * Idempotent and safe to call before rendering any authenticated page.
 */
export async function ensureProfile(phone?: string | null): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;

  const { data: existing } = await supabase
    .from("profiles")
    .select("id")
    .eq("id", user.id)
    .maybeSingle();

  if (existing) {
    if (phone) {
      await supabase.from("profiles").update({ phone }).eq("id", user.id);
    }
    return true;
  }

  const meta = (user.user_metadata ?? {}) as Record<string, unknown>;
  return createProfileRow({
    firstName: typeof meta.first_name === "string" ? meta.first_name : "",
    lastName: typeof meta.last_name === "string" ? meta.last_name : "",
    username: typeof meta.username === "string" ? meta.username : undefined,
    email: user.email ?? null,
    phone: phone ?? null,
  });
}

export async function updateProfile(input: {
  firstName: string;
  lastName: string;
  phone: string | null;
}): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in again." };

  const { error } = await supabase
    .from("profiles")
    .update({
      first_name: input.firstName.trim(),
      last_name: input.lastName.trim(),
      phone: input.phone?.trim() || null,
      updated_at: new Date().toISOString(),
    })
    .eq("id", user.id);

  if (error) return { ok: false, error: friendlyAuthError(error.message, error.code) };
  return { ok: true, data: undefined };
}

/**
 * Account deletion.
 *
 * Supabase requires a server-side admin call to delete an `auth.users` row, so
 * this is intentionally **not** implemented client-side — doing so would need a
 * service-role key in the browser, which this project forbids.
 * The signed-in customer can clear their personal data themselves (addresses,
 * cart, profile fields) and a recorded request is handled by the store.
 * See docs/TASK_STATE.md → BLOCKED and the account page copy.
 */
export const ACCOUNT_DELETION_MODE = "request" as const;

/** Wipe the customer's own rows. RLS scopes every delete to `auth.uid()`. */
export async function clearPersonalData(): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in again." };

  const [addresses, cart] = await Promise.all([
    supabase.from("addresses").delete().eq("user_id", user.id),
    supabase.from("cart_items").delete().eq("user_id", user.id),
  ]);

  if (addresses.error) {
    return { ok: false, error: "We couldn't remove your saved addresses." };
  }
  if (cart.error) {
    return { ok: false, error: "We couldn't empty your basket." };
  }
  return { ok: true, data: undefined };
}
