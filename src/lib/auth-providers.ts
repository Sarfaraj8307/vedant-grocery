import "server-only";

/**
 * Which authentication methods this Supabase project actually has enabled.
 *
 * WHY THIS EXISTS
 * ---------------
 * Verified against the live project on 2026-10-07: the `/auth/v1/settings`
 * endpoint reports `google: false` and `mailer_autoconfirm: false`. That means
 * Google sign-in *cannot* work yet, and signup *does* require email confirmation.
 *
 * The brief forbids buttons that do nothing (§47), so the Google button is
 * rendered **only** when the provider is genuinely enabled. The settings endpoint
 * is public and takes no secret — this is a capability probe, not a privilege
 * escalation. The moment the owner enables Google in the Supabase dashboard, the
 * button appears with no code change.
 */

export type AuthProviderCapabilities = {
  google: boolean;
  email: boolean;
  /** True when signup completes without clicking an emailed link. */
  emailAutoConfirm: boolean;
  /** True when the settings endpoint could not be reached. */
  unknown: boolean;
};

const FALLBACK: AuthProviderCapabilities = {
  google: false,
  email: true,
  emailAutoConfirm: false,
  unknown: true,
};

export async function getAuthProviderCapabilities(): Promise<AuthProviderCapabilities> {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  if (!url || !key) return FALLBACK;

  try {
    const response = await fetch(`${url}/auth/v1/settings`, {
      headers: { apikey: key },
      // Cached in the Next data cache: provider configuration changes rarely,
      // and this must never sit on the critical path of a page render.
      next: { revalidate: 300, tags: ["auth-providers"] },
    });

    if (!response.ok) return FALLBACK;

    const settings = (await response.json()) as {
      external?: Record<string, boolean>;
      mailer_autoconfirm?: boolean;
    };

    return {
      google: settings.external?.google === true,
      email: settings.external?.email !== false,
      emailAutoConfirm: settings.mailer_autoconfirm === true,
      unknown: false,
    };
  } catch {
    // Never let a probe failure break the sign-in page: fall back to the
    // conservative answer (email only), which is what the live project supports.
    return FALLBACK;
  }
}
