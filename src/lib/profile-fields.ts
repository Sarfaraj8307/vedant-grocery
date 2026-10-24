import { USERNAME_PATTERN } from "@/lib/validation";

/**
 * Turn an `auth.users.user_metadata` blob into profile fields.
 *
 * Shared by the browser (`services/auth-service`) and the server
 * (`lib/auth`) so both agree on how a customer's name is derived. It lives here
 * rather than in either of them because `auth-service` is a client module and
 * cannot be imported by a Server Component.
 *
 * Two very different shapes arrive:
 *  • email/password signup — `first_name`, `last_name`, `username`, written by
 *    `signUpWithPassword()`.
 *  • Google OAuth — **no** `first_name` at all. Google supplies `given_name` /
 *    `family_name`, or only `full_name` / `name`, plus `user_name` /
 *    `preferred_username` for the handle.
 *
 * Reading only the email-signup keys is why a Google customer previously landed
 * in `profiles` with a blank name on their account page.
 */
export function profileFieldsFromMetadata(meta: Record<string, unknown>): {
  firstName: string;
  lastName: string;
  username?: string;
} {
  const text = (value: unknown) => (typeof value === "string" ? value.trim() : "");

  let firstName = text(meta.first_name) || text(meta.given_name);
  let lastName = text(meta.last_name) || text(meta.family_name);

  // Some Google accounts expose neither given/family name — only a display
  // name. Split on the first space; the remainder is the surname.
  if (!firstName) {
    const full = text(meta.full_name) || text(meta.name);
    if (full) {
      const [head, ...rest] = full.split(/\s+/);
      firstName = head ?? "";
      if (!lastName) lastName = rest.join(" ");
    }
  }

  // Only a handle that already satisfies the database rule is offered. Google
  // allows dots and uppercase (`john.doe`), which `profiles_username_check`
  // rejects — those are left for `deriveUsername()`.
  const handle = (
    text(meta.username) ||
    text(meta.user_name) ||
    text(meta.preferred_username)
  ).toLowerCase();

  return {
    firstName,
    lastName,
    username: USERNAME_PATTERN.test(handle) ? handle : undefined,
  };
}

/**
 * The profile picture a provider gave us, if any.
 *
 * Google returns `avatar_url` (and `picture`); other providers vary. Only an
 * absolute https URL is accepted, so a malformed or relative value cannot be
 * rendered as an image.
 */
export function avatarUrlFromMetadata(meta: Record<string, unknown>): string | null {
  for (const key of ["avatar_url", "picture"] as const) {
    const value = meta[key];
    if (typeof value === "string" && /^https:\/\//.test(value)) return value;
  }
  return null;
}
