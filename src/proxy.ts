import { NextResponse, type NextRequest } from "next/server";
import { createServerClient } from "@supabase/ssr";
import { publicEnv } from "@/lib/env";
import { clientIp as getClientIp, rateLimit } from "@/lib/rate-limit";

/**
 * Next.js 16 request proxy (the successor to `middleware.ts`).
 *
 * Responsibilities — deliberately narrow:
 *  1. Refresh the Supabase auth session cookies on every navigation, so a
 *     customer who leaves a tab open stays signed in.
 *  2. Add baseline security headers.
 *  3. Issue a real HTTP 307 for signed-out guests on guarded routes (so crawlers
 *     and uptime checks see the correct status instead of a soft 200 +
 *     meta-refresh). This is a UX/observability convenience only — the actual
 *     security boundary remains Postgres RLS plus the per-route `requireUser()`
 *     server checks. Fail open if Supabase is unreachable.
 *
 * It does **not** replace authorization. Authorization lives in Postgres RLS
 * and in per-route server checks; this guard is a thin, fail-open edge redirect
 * that keeps monitoring/SEO honest. See docs/BACKEND_CONTRACT.md §4.
 */

const PUBLIC_FILE = /\.(?:svg|png|jpg|jpeg|gif|webp|avif|ico|txt|xml|json|webmanifest|js|css|woff2?)$/i;

export function proxy(request: NextRequest) {
  return handle(request);
}

async function handle(request: NextRequest) {
  // Rate limit sensitive routes.
  //
  // IMPORTANT: these limits gate **page loads only**. Every auth action (sign
  // in, sign up, social OAuth) is performed client-side directly against
  // Supabase Auth, so it never transits this proxy. This limiter therefore
  // cannot stop brute-forcing — it only stops someone hammering the page
  // itself. The thresholds are kept generous so normal navigation/refresh
  // never locks a genuine user out.
  const { pathname: reqPath } = request.nextUrl;
  const ip = getClientIp(request);
  const limits: Record<string, { limit: number; windowMs: number }> = {
    "/login": { limit: 120, windowMs: 60_000 },
    "/signup": { limit: 60, windowMs: 60_000 },
    "/auth/callback": { limit: 240, windowMs: 60_000 },
    "/checkout": { limit: 240, windowMs: 60_000 },
  };
  for (const [prefix, cfg] of Object.entries(limits)) {
    if (reqPath.startsWith(prefix)) {
      const r = rateLimit(ip, prefix, cfg.limit, cfg.windowMs);
      if (!r.allowed) {
        const res = new NextResponse("Too many requests. Please slow down.", {
          status: 429,
          headers: {
            "Retry-After": Math.ceil((r.resetAt - Date.now()) / 1000).toString(),
            "X-RateLimit-Remaining": "0",
          },
        });
        applySecurityHeaders(res);
        return res;
      }
      break;
    }
  }

  const response = NextResponse.next({ request });

  // Skip auth work for static assets — it would be pure overhead.
  const { pathname } = request.nextUrl;
  const isAsset =
    pathname.startsWith("/_next") ||
    pathname.startsWith("/images") ||
    pathname.startsWith("/icons") ||
    PUBLIC_FILE.test(pathname);

  // --------------------------------------------------------------------------
  // Authorization guard — issue a *real* 307 for signed-out guests.
  //
  // Guarded routes used to rely on a server-component `redirect()` in the page,
  // which under App Router streaming degrades into a soft HTTP 200 +
  // <meta http-equiv="refresh">. That is bad for monitoring/SEO. We bounce
  // guests here, before the page renders, with a proper 307 so crawlers and
  // uptime checks see the correct status.
  //
  // Fail open: if the Supabase user lookup throws (unreachable/misconfigured),
  // we return `null` and let the request through, so the page's own
  // `requireUser()` still handles it. The app's principle is "Misconfigured or
  // unreachable Supabase must not break every page."
  // --------------------------------------------------------------------------
  if (!isAsset && shouldGuard(pathname)) {
    const user = await getUserOrNull(request);
    if (user === null) {
      const loginUrl = new URL("/login", request.url);
      loginUrl.searchParams.set("next", pathname);
      return NextResponse.redirect(loginUrl, 307);
    }
  }

  if (!isAsset) {
    try {
      const { supabaseUrl, supabaseKey } = publicEnv();
      const supabase = createServerClient(supabaseUrl, supabaseKey, {
        cookies: {
          getAll() {
            return request.cookies.getAll();
          },
          setAll(cookiesToSet) {
            for (const { name, value, options } of cookiesToSet) {
              response.cookies.set(name, value, options);
            }
          },
        },
      });
      // Touching getUser() is what triggers the refresh-and-rotate cycle.
      await supabase.auth.getUser();
    } catch {
      // Misconfigured or unreachable Supabase must not break every page —
      // pages surface their own configuration error where it matters.
    }
  }

  applySecurityHeaders(response);
  return response;
}

/* -------------------------------------------------------------------------- */
/* Route guard configuration + helpers                                         */
/* -------------------------------------------------------------------------- */

/**
 * Prefixes that require an authenticated session. A request whose pathname
 * equals a prefix or starts with `prefix + "/"` is guarded (this also covers
 * dynamic segments such as `/orders/[id]` and `/support/[id]`).
 */
const GUARDED_PREFIXES: readonly string[] = [
  "/checkout",
  "/orders",
  "/notifications",
  "/support",
  "/account",
];

/**
 * Paths that must stay reachable while signed OUT. `/account/reset-password`
 * intentionally renders a page that redirects to `/forgot-password`, so it must
 * never be intercepted by the guard.
 */
const EXEMPT_PREFIXES: readonly string[] = ["/account/reset-password"];

/** True when `pathname` equals a prefix or sits beneath it. */
function matchesPrefix(pathname: string, prefixes: readonly string[]): boolean {
  return prefixes.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

/**
 * Should the request be bounced to sign-in? Exempt paths are never guarded;
 * everything else matching a guarded prefix is.
 */
function shouldGuard(pathname: string): boolean {
  if (matchesPrefix(pathname, EXEMPT_PREFIXES)) return false;
  return matchesPrefix(pathname, GUARDED_PREFIXES);
}

type GuardUser = { id: string; email: string | null; phone: string | null };

/**
 * Read-only lookup of the signed-in user for the guard decision.
 *
 * Uses a `createServerClient` that only supplies `getAll` — it must NOT write
 * cookies. The session-refresh flow in `handle()` owns `setAll` and runs only
 * on requests that are *not* redirected. Fail open: any Supabase error (network
 * or misconfiguration) yields `null`, letting the page's own `requireUser()`
 * guard handle the request instead of bricking the route.
 */
async function getUserOrNull(request: NextRequest): Promise<GuardUser | null> {
  try {
    const { supabaseUrl, supabaseKey } = publicEnv();
    const supabase = createServerClient(supabaseUrl, supabaseKey, {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
      },
    });
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
  } catch {
    // Misconfigured or unreachable Supabase must not break the guard.
    return null;
  }
}

function applySecurityHeaders(response: NextResponse) {
  const headers = response.headers;

  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("X-Frame-Options", "DENY");
  headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  headers.set(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=(self), payment=(), usb=()",
  );
  headers.set("Cross-Origin-Opener-Policy", "same-origin");
  headers.set("X-DNS-Prefetch-Control", "on");

  // HSTS is only meaningful (and only safe) over TLS.
  if (process.env.NODE_ENV === "production") {
    headers.set(
      "Strict-Transport-Security",
      "max-age=31536000; includeSubDomains; preload",
    );
  }
}

export const config = {
  matcher: [
    /*
     * Run on everything except Next internals and the service worker.
     * `sw.js` must be served untouched so the PWA update flow works.
     */
    "/((?!_next/static|_next/image|sw.js|favicon.ico).*)",
  ],
};
