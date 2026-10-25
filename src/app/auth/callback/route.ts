import { NextResponse, type NextRequest } from "next/server";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { absoluteUrl } from "@/config/site";

/**
 * Auth callback.
 *
 * Supabase sends the customer here from three flows:
 *  • email confirmation after signup            `?code=…`
 *  • password recovery                          `?code=…&next=/account/reset-password`
 *  • OAuth return (Google, when enabled)
 *
 * Both the modern PKCE flow (`code`, exchanged server-side) and the legacy
 * `token_hash` + `type` OTP link are handled, because the email template the
 * project uses determines which one arrives and the app must work with either.
 *
 * `next` is sanitised to a same-origin relative path: an attacker-controlled
 * `?next=` on an auth callback is the classic open-redirect vector.
 */
/**
 * Turn a Supabase auth failure into copy a customer can act on.
 *
 * WHY: these messages were being shown verbatim. A customer who clicked a
 * confirmation link on their phone after signing up on a laptop saw
 * "PKCE code verifier not found in storage…" — accurate, and useless.
 *
 * The important fact in that case is that **the account is already confirmed**:
 * Supabase's `/auth/v1/verify` endpoint validates the token and sets
 * `email_confirmed_at` before it redirects here. Only the automatic sign-in
 * fails, because a PKCE verifier is bound to the browser that started the flow.
 * So the honest message tells them to sign in, not that something broke.
 *
 * Anything unrecognised is passed through unchanged — guessing at an unknown
 * failure would hide a real one.
 */
function friendlyAuthError(raw: string): string {
  const m = raw.toLowerCase();

  if (m.includes("code verifier") || m.includes("pkce")) {
    return "Your email is confirmed. This link was opened in a different browser or device, so we could not sign you in automatically — please sign in with your email and password.";
  }
  if (m.includes("expired") || m.includes("otp_expired")) {
    return "That link has expired or has already been used. Request a fresh one from the sign-in page.";
  }
  if (m.includes("invalid") && (m.includes("token") || m.includes("link"))) {
    return "That link is no longer valid. Request a fresh one from the sign-in page.";
  }
  return raw;
}

export async function GET(request: NextRequest) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");
  const tokenHash = searchParams.get("token_hash");
  const type = searchParams.get("type");
  const next = sanitiseNext(searchParams.get("next"));
  const errorDescription = searchParams.get("error_description");

  // A provider-side failure is reported back to the customer, not swallowed.
  if (errorDescription) {
    return NextResponse.redirect(
      `${origin}/login?error=${encodeURIComponent(friendlyAuthError(errorDescription))}`,
    );
  }

  const supabase = await getSupabaseServerClient();

  try {
    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) {
        return NextResponse.redirect(
          `${origin}/login?error=${encodeURIComponent(friendlyAuthError(error.message))}`,
        );
      }
    } else if (tokenHash && type) {
      const { error } = await supabase.auth.verifyOtp({
        type: type as "signup" | "recovery" | "email" | "invite" | "email_change",
        token_hash: tokenHash,
      });
      if (error) {
        return NextResponse.redirect(
          `${origin}/login?error=${encodeURIComponent(friendlyAuthError(error.message))}`,
        );
      }
    } else {
      // Neither a PKCE `code` nor a `token_hash` reached us. The one legitimate
      // way that happens is the implicit flow, which carries the session in the
      // URL *fragment* — fragments are never sent to the server, so this route
      // cannot see it. Tell the customer what to do rather than showing a bare
      // form with no explanation.
      return NextResponse.redirect(
        `${origin}/login?error=${encodeURIComponent(
          "If you just confirmed your email, it worked — please sign in. We could not sign you in automatically from that link.",
        )}`,
      );
    }
  } catch {
    return NextResponse.redirect(
      `${origin}/login?error=${encodeURIComponent("We could not complete that sign-in link.")}`,
    );
  }

  // Password recovery lands on the reset form; everything else on `next`.
  const destination =
    type === "recovery" ? "/account/reset-password" : next;

  // Newly confirmed signups are told what happened rather than being dropped
  // into an empty app with no explanation.
  const suffix =
    type === "signup" || type === "email" ? "?confirmed=1" : "";

  return NextResponse.redirect(absoluteUrl(`${destination}${suffix}`));
}

/** Only same-origin relative paths are ever honoured. */
function sanitiseNext(raw: string | null): string {
  if (!raw) return "/";
  if (!raw.startsWith("/") || raw.startsWith("//")) return "/";
  return raw;
}
