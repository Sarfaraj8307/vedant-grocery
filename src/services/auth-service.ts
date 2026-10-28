"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import { absoluteUrl } from "@/config/site";
import { USERNAME_PATTERN } from "@/lib/validation";
import { TERMS_VERSION, type TermsAcceptanceSource } from "@/lib/terms";
import { profileFieldsFromMetadata } from "@/lib/profile-fields";

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
    return "That email hasn't been verified yet. Sign up again to get a fresh 6-digit code, then sign in.";
  }
  if (text.includes("already confirmed")) {
    return "That email is already verified. Please sign in instead.";
  }
  if (text.includes("user already registered") || code === "user_already_exists") {
    return "An account with this email already exists. Try signing in instead.";
  }
  if (text.includes("password should be at least")) {
    return "Please choose a password with at least 8 characters.";
  }
  if (text.includes("email rate limit exceeded")) {
    return "We've sent several codes already. Please wait a few minutes before requesting another one.";
  }
  if (text.includes("for security purposes")) {
    return "Please wait a few seconds before requesting another code.";
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
  // Supabase deliberately cannot distinguish a wrong code from an expired one
  // ("Token has expired or is invalid"), so neither can we — guessing would
  // tell an attacker which of the two they hit.
  if (
    text.includes("token has expired") ||
    text.includes("invalid token") ||
    text.includes("code is invalid") ||
    text.includes("token is invalid") ||
    code === "otp_expired" ||
    code === "invalid_otp"
  ) {
    return "That code is invalid or has expired. Check the latest email and try again.";
  }
  if (code === "otp_disabled" || text.includes("otp is disabled")) {
    return "Code sign-in isn't available right now. Please use your password instead.";
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
        // The form has already required this tick. Recording the version here
        // means a customer whose confirmation email defers their session is not
        // asked the same question again on first sign-in — `syncSignupConsent()`
        // reads this and writes the real row.
        accepted_terms_version: TERMS_VERSION,
        accepted_terms_at: new Date().toISOString(),
      },
      emailRedirectTo: absoluteUrl("/auth/callback"),
    },
  });

  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }

  const needsEmailConfirmation = !data.session;
  if (data.session && data.user) {
    await createProfileRow({
      firstName: input.firstName,
      lastName: input.lastName,
      username: input.username,
      email: input.email,
    });
    // The box was ticked a moment ago and we have a session, so write the
    // consent row now rather than interrupting them with the gate.
    await insertAcceptance(data.user.id, "signup");
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
/* Email confirmation                                                         */
/* -------------------------------------------------------------------------- */

/**
 * True when a sign-in failed only because the address has not been confirmed.
 *
 * Supabase reports this as `email_not_confirmed`; older responses only carry the
 * message. Both are checked because the code field is not guaranteed on every
 * error shape.
 */
export function isEmailUnconfirmed(code?: string, message?: string): boolean {
  if (code === "email_not_confirmed") return true;
  // The message may already be the friendly copy, which talks about the address
  // not being verified rather than the provider's "email not confirmed". Both
  // wordings have to match or a real failure falls through to a bare error with
  // no way to request a fresh code.
  return /email not confirmed|confirm your email|been verified/.test(
    (message ?? "").toLowerCase(),
  );
}

/* -------------------------------------------------------------------------- */
/* Email OTP                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * Send a one-time password (OTP) code to an email address to verify a new
 * sign-up. `shouldCreateUser: true` creates an unconfirmed user carrying the
 * profile fields in `user_metadata`; the code itself confirms the account.
 *
 * The returned `sentAt` is the moment the request went out. It is handed back
 * to `verifySignupOtp` so the two can tell a brand-new account (created by this
 * very request) from an address that already had one.
 */
export async function sendSignupOtp(input: {
  email: string;
  firstName: string;
  lastName: string;
  username: string;
}): Promise<AuthResult<{ sentAt: number }>> {
  const supabase = getSupabaseBrowserClient();
  const sentAt = Date.now();

  const { error } = await supabase.auth.signInWithOtp({
    email: input.email.trim(),
    options: {
      shouldCreateUser: true,
      // Only consulted if the project's email template still renders a link
      // instead of a code. It keeps that fallback on the production domain
      // rather than Supabase's default Site URL.
      emailRedirectTo: absoluteUrl("/auth/callback"),
      data: {
        first_name: input.firstName.trim(),
        last_name: input.lastName.trim(),
        username: input.username.toLowerCase(),
        // The user has already ticked the terms on the form; record the version
        // so the acceptance row can be written once the OTP confirms them.
        accepted_terms_version: TERMS_VERSION,
        accepted_terms_at: new Date().toISOString(),
      },
    },
  });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  return { ok: true, data: { sentAt } };
}

/**
 * Send a replacement code for a sign-up that is still pending.
 *
 * Uses `resend({ type: "signup" })` rather than `signInWithOtp` again, so the
 * replacement carries the **same token type** as the first code. Re-requesting
 * through the OTP endpoint would issue a `magiclink` token for the account that
 * the first request already created, and a `signup` verification would then
 * reject it.
 */
export async function resendSignupOtp(input: {
  email: string;
  firstName: string;
  lastName: string;
  username: string;
}): Promise<AuthResult<{ sentAt: number }>> {
  const supabase = getSupabaseBrowserClient();
  const sentAt = Date.now();

  const { error } = await supabase.auth.resend({
    type: "signup",
    email: input.email.trim(),
    options: { emailRedirectTo: absoluteUrl("/auth/callback") },
  });

  if (!error) return { ok: true, data: { sentAt } };

  // "User not found" means the first request never created the account (for
  // example it failed after the rate limiter accepted it). Starting the signup
  // again is the only way forward, and it is safe: nothing was created.
  if (/user not found|no user/i.test(error.message)) {
    return sendSignupOtp(input);
  }

  return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
}

/**
 * Two minutes of slack when deciding whether an account pre-dates this sign-up.
 *
 * Absorbs clock skew between the browser and Supabase, and the round trip
 * itself. Anything newer than this was almost certainly created by the request
 * the customer just made.
 */
const EXISTING_ACCOUNT_SLACK_MS = 2 * 60_000;

/**
 * Was this account already on file before the customer asked for a code?
 *
 * WHY IT MATTERS: `signInWithOtp({ shouldCreateUser: true })` succeeds for an
 * address that already has an account — it sends a sign-in code instead of a
 * sign-up code. Without this check the signup form would happily take over an
 * existing customer's account and **overwrite their password** with whatever
 * was typed into the "password" box. Detecting it lets the caller sign them in
 * and leave their credentials alone.
 */
export function isExistingAccount(
  createdAt: string | null | undefined,
  requestedAt: number | undefined,
): boolean {
  if (!createdAt || !requestedAt) return false;
  const created = Date.parse(createdAt);
  if (Number.isNaN(created)) return false;
  return created < requestedAt - EXISTING_ACCOUNT_SLACK_MS;
}

/** True when a verify call failed because the code was wrong or too old. */
function isTokenRejection(error: { message: string; code?: string }): boolean {
  const text = error.message.toLowerCase();
  return (
    error.code === "otp_expired" ||
    error.code === "invalid_otp" ||
    text.includes("token has expired") ||
    text.includes("invalid token") ||
    text.includes("token is invalid") ||
    text.includes("code is invalid")
  );
}

/**
 * Verify the sign-up OTP. On success Supabase confirms the user and creates a
 * session; we then persist the profile row and the terms acceptance, mirroring
 * what `signUpWithPassword` does when signup returns a session.
 */
export async function verifySignupOtp(input: {
  email: string;
  token: string;
  /** `sentAt` from `sendSignupOtp`/`resendSignupOtp`. */
  requestedAt?: number;
}): Promise<AuthResult<{ userId: string; existingAccount: boolean }>> {
  const supabase = getSupabaseBrowserClient();
  const email = input.email.trim();
  const token = input.token.trim();

  // `signInWithOtp` issues a `signup` token for a brand-new address and a
  // `magiclink` one for an address that already has an account. GoTrue's
  // `email` type covers both, so it goes first; `signup` is retried only when
  // the code itself was rejected, which is the one case where the type might be
  // the reason. A rate-limit or network failure is never retried — that would
  // just burn the verification attempt budget.
  const primary = await supabase.auth.verifyOtp({ email, token, type: "email" });
  let data = primary.data;
  let error = primary.error;

  if (error && isTokenRejection(error)) {
    const fallback = await supabase.auth.verifyOtp({ email, token, type: "signup" });
    data = fallback.data;
    error = fallback.error;
  }

  if (error || !data.user) {
    const message = error
      ? friendlyAuthError(error.message, error.code)
      : "That code did not match.";
    return { ok: false, error: message, code: error?.code };
  }

  const meta = (data.user.user_metadata ?? {}) as Record<string, unknown>;
  await createProfileRow({
    firstName: String(meta.first_name ?? ""),
    lastName: String(meta.last_name ?? ""),
    username: String(meta.username ?? ""),
    email: data.user.email ?? null,
  });
  await insertAcceptance(data.user.id, "signup");

  return {
    ok: true,
    data: {
      userId: data.user.id,
      existingAccount: isExistingAccount(data.user.created_at, input.requestedAt),
    },
  };
}

/** Send a password-recovery OTP code. Does not create an account. */
export async function sendRecoveryOtp(email: string): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.signInWithOtp({
    email: email.trim(),
    options: { shouldCreateUser: false },
  });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code), code: error.code };
  }
  return { ok: true, data: undefined };
}

/**
 * Verify a password-recovery OTP. On success Supabase returns a recovery
 * session, which lets the caller call `updatePassword()` without being signed
 * in through the normal flow.
 */
export async function verifyRecoveryOtp(input: {
  email: string;
  token: string;
}): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.verifyOtp({
    email: input.email.trim(),
    token: input.token.trim(),
    type: "recovery",
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

/**
 * Change password from the account page (not the recovery flow).
 *
 * Requires the customer's current password to prove they own the account, then
 * sets the new one. Supabase stores passphrases hashed in `auth.users` — there
 * is no plain-text column to update, so the change is a single Auth API call.
 */
export async function changePassword(input: {
  currentPassword: string;
  newPassword: string;
}): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user?.email) return { ok: false, error: "Please sign in again." };

  // Verify the current password first — the customer must prove ownership before
  // we let them set a new one.
  const { error: signInError } = await supabase.auth.signInWithPassword({
    email: user.email,
    password: input.currentPassword,
  });
  if (signInError) {
    return {
      ok: false,
      error: friendlyAuthError(signInError.message, signInError.code),
      code: signInError.code,
    };
  }

  const { error } = await supabase.auth.updateUser({ password: input.newPassword });
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

/**
 * Sign out of every device, not just this one.
 *
 * `scope: "global"` revokes every refresh token issued for the account, so a
 * session left open on a shared or lost device stops renewing. The access token
 * already handed to that device stays valid until it expires (an hour), which
 * is a property of JWTs rather than of this call — the short lifetime is what
 * bounds it, and is why Supabase's own guidance is to keep JWT expiry low.
 */
export async function signOutEverywhere(): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.auth.signOut({ scope: "global" });
  if (error) {
    return { ok: false, error: friendlyAuthError(error.message, error.code) };
  }
  return { ok: true, data: undefined };
}

/* -------------------------------------------------------------------------- */
/* Terms consent                                                              */
/* -------------------------------------------------------------------------- */

/**
 * Write one acceptance row.
 *
 * `accepted_at` is deliberately **not** sent: the column defaults to `now()` on
 * the server, so the timestamp is the database's, not the browser's. A client
 * clock (or a curious customer) cannot back-date consent.
 *
 * A `23505` unique violation means this version was already accepted. That is
 * the outcome the caller wanted, so it counts as success.
 */
async function insertAcceptance(
  userId: string,
  source: TermsAcceptanceSource,
): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const { error } = await supabase.from("terms_acceptances").insert({
    user_id: userId,
    terms_version: TERMS_VERSION,
    source,
  });

  if (!error) return true;
  if (error.code === "23505") return true;

  console.error("[terms] could not record acceptance:", error.message);
  return false;
}

/**
 * Record the signed-in customer's acceptance of the current terms.
 *
 * Called by the consent gate. `source: "login"` is refined to `"google"` when
 * the account's provider is Google, so the audit row says how they signed in
 * without the caller needing to know.
 */
export async function recordTermsAcceptance(
  source: TermsAcceptanceSource,
): Promise<AuthResult> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in again." };

  const provider = user.app_metadata?.provider;
  const resolved: TermsAcceptanceSource =
    source === "login" && provider === "google" ? "google" : source;

  const saved = await insertAcceptance(user.id, resolved);
  if (!saved) {
    return {
      ok: false,
      error: "We couldn't save your acceptance. Please check your connection and try again.",
    };
  }
  return { ok: true, data: undefined };
}

/**
 * Persist a consent that was already given on the signup form.
 *
 * Email/password signup requires the terms tick, but with email confirmation
 * enabled the session only exists after the customer opens the link. In that
 * window the row cannot be written, so the version they agreed to travels in
 * `user_metadata` and is written here on first authenticated render. Without
 * this the gate would ask a customer to agree to something they had just
 * agreed to.
 *
 * Returns false when there is nothing to sync — the caller then shows the gate.
 */
export async function syncSignupConsent(): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;

  const meta = (user.user_metadata ?? {}) as Record<string, unknown>;
  if (meta.accepted_terms_version !== TERMS_VERSION) return false;

  return insertAcceptance(user.id, "signup");
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

// `profileFieldsFromMetadata` lives in `lib/profile-fields` so that the server
// (`lib/auth`) and this client service derive a customer's name the same way.
// A client module cannot be imported by a Server Component, hence the split.

async function createProfileRow(input: ProfileInput): Promise<boolean> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;

  // Single choke point for the username rule. A caller may hand us anything —
  // a Google handle like `john.doe` is perfectly normal on Google and illegal
  // in `profiles` — so an invalid candidate is discarded here rather than
  // being sent to Postgres to fail on `profiles_username_check`.
  const candidate = (input.username ?? "").trim().toLowerCase();
  const row = {
    id: user.id,
    username: USERNAME_PATTERN.test(candidate)
      ? candidate
      : deriveUsername(user.email ?? ""),
    first_name: input.firstName?.trim() ?? "",
    last_name: input.lastName?.trim() ?? "",
    email: input.email ?? user.email ?? null,
    phone: input.phone ?? null,
  };

  const { error } = await supabase.from("profiles").insert(row);
  if (!error) return true;

  if (error.code === "23505") {
    const message = error.message.toLowerCase();

    // Username collision. Reachable even when a username *was* supplied: the
    // signup form checks availability, but a Google handle is never checked,
    // so `john_doe` may already belong to someone else. Retrying with a
    // derived unique name is the difference between "profile exists" and
    // "customer signed in but has no profile at all".
    if (message.includes("username")) {
      const { error: retryError } = await supabase
        .from("profiles")
        .insert({ ...row, username: deriveUsername(user.email ?? "") });
      return !retryError;
    }

    // Primary-key collision: a second tab inserted the row between the
    // existence check in `ensureProfile()` and this INSERT. The row exists,
    // which is exactly the outcome the caller wanted — reporting failure here
    // would make ProfileSync retry forever against a row that is already fine.
    const { data: existing } = await supabase
      .from("profiles")
      .select("id")
      .eq("id", user.id)
      .maybeSingle();
    return Boolean(existing);
  }

  return false;
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
    ...profileFieldsFromMetadata(meta),
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

