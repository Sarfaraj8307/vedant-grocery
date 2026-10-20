/**
 * Environment validation.
 *
 * The customer app is allowed exactly two Supabase values, both of which are
 * intentionally public. There is deliberately **no** slot for a service-role
 * key or any admin secret — see docs/BACKEND_CONTRACT.md §3 and the security
 * rules in `docs/FINAL_IMPLEMENTATION_REPORT.md`.
 *
 * Validation is lazy (called on first client construction) so that a missing
 * variable produces one clear, actionable error at the point of use instead of
 * crashing every import graph at build time.
 */

type PublicEnv = {
  supabaseUrl: string;
  supabaseKey: string;
  siteUrl: string;
};

let cached: PublicEnv | null = null;

function readPublicEnv(): PublicEnv {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL?.trim();
  const supabaseKey = (
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ??
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
  )?.trim();
  const siteUrl = (
    process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
  ).trim();

  const missing: string[] = [];
  if (!supabaseUrl) missing.push("NEXT_PUBLIC_SUPABASE_URL");
  if (!supabaseKey) {
    missing.push(
      "NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY (or NEXT_PUBLIC_SUPABASE_ANON_KEY)",
    );
  }

  if (missing.length > 0) {
    throw new Error(
      `Missing Supabase configuration: ${missing.join(", ")}. ` +
        `Copy .env.example to .env.local and fill in the values from ` +
        `Supabase → Project Settings → API.`,
    );
  }

  // Guard against the classic and dangerous misconfiguration of shipping a
  // secret key to the browser. A service-role JWT decodes to role=service_role.
  if (looksLikeServiceRoleKey(supabaseKey!)) {
    throw new Error(
      "NEXT_PUBLIC_SUPABASE_* must never contain a service_role key. " +
        "This project is customer-only and must not be able to bypass RLS. " +
        "Use the publishable (anon) key instead.",
    );
  }

  if (!/^https:\/\/[a-z0-9-]+\.supabase\.(co|in)$/i.test(supabaseUrl!)) {
    throw new Error(
      `NEXT_PUBLIC_SUPABASE_URL does not look like a Supabase project URL: ${supabaseUrl}`,
    );
  }

  return { supabaseUrl: supabaseUrl!, supabaseKey: supabaseKey!, siteUrl };
}

function looksLikeServiceRoleKey(key: string): boolean {
  if (key.startsWith("sb_secret_")) return true;
  const parts = key.split(".");
  if (parts.length !== 3) return false;
  try {
    const payload = JSON.parse(
      Buffer.from(parts[1]!, "base64url").toString("utf8"),
    ) as { role?: string };
    return payload.role === "service_role";
  } catch {
    return false;
  }
}

/** Validated public environment. Cached after the first successful read. */
export function publicEnv(): PublicEnv {
  cached ??= readPublicEnv();
  return cached;
}

/** True when Supabase is configured — lets pages degrade instead of throwing. */
export function isSupabaseConfigured(): boolean {
  try {
    publicEnv();
    return true;
  } catch {
    return false;
  }
}
