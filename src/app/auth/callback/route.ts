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
      `${origin}/login?error=${encodeURIComponent(errorDescription)}`,
    );
  }

  const supabase = await getSupabaseServerClient();

  try {
    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) {
        return NextResponse.redirect(
          `${origin}/login?error=${encodeURIComponent(error.message)}`,
        );
      }
    } else if (tokenHash && type) {
      const { error } = await supabase.auth.verifyOtp({
        type: type as "signup" | "recovery" | "email" | "invite" | "email_change",
        token_hash: tokenHash,
      });
      if (error) {
        return NextResponse.redirect(
          `${origin}/login?error=${encodeURIComponent(error.message)}`,
        );
      }
    } else {
      return NextResponse.redirect(`${origin}/login`);
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
  if (!raw) return "/account";
  if (!raw.startsWith("/") || raw.startsWith("//")) return "/account";
  return raw;
}
