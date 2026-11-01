# Email OTP authentication

How a customer gets from the signup form to a verified session on
`https://vedantfreshmart.in`, what lives in this repository, and the handful of
settings that live **only** in the Supabase dashboard.

Supabase Auth generates and validates every code. There is no OTP table, no
custom validation, and nothing is stored in `localStorage`.

## The flow

```
/signup  (name, email, username, password, terms)
   │  auth.signInWithOtp({ shouldCreateUser: true, data: {...profile} })
   ▼
Supabase creates an unconfirmed user, emails a 6-digit code
   │
   ▼
/signup  step 2 — "Verify your email"  (6 boxes, stays on vedantfreshmart.in)
   │  auth.verifyOtp({ email, token, type: "email" })  →  falls back to type "signup"
   ▼
Session cookies written by @supabase/ssr
   │  auth.updateUser({ password })   ← only for a genuinely new account
   ▼
Redirect to `next` (default `/`)
```

`verifyOtp` creates the session; `proxy.ts` refreshes it on every navigation and
Server Components read it with `getUser()`. Nothing about the existing session
architecture changed.

## Files

| File | Role |
|---|---|
| `src/lib/otp.ts` | Pure digit arithmetic for the six boxes — paste, backspace, auto-advance. Unit-tested. |
| `src/components/auth/otp-input.tsx` | The six-box input: autofocus, paste, arrow keys, numeric keypad. |
| `src/components/auth/auth-forms.tsx` | `OtpStep` (shared by signup and password reset) plus both forms. |
| `src/services/auth-service.ts` | `sendSignupOtp`, `resendSignupOtp`, `verifySignupOtp`, `sendRecoveryOtp`, `verifyRecoveryOtp`, `friendlyAuthError`. |
| `src/app/auth/callback/route.ts` | Still used for Google OAuth and for a link-based email template, if one is ever configured. |

### Two decisions worth knowing about

**`resendSignupOtp` uses `auth.resend({ type: "signup" })`, not `signInWithOtp`.**
The first `signInWithOtp` already created the account, so calling it again would
issue a *magic-link* token for that now-existing user, and a `signup`
verification would reject the replacement code. `resend` re-sends the same
token type as the original.

**An existing address is not allowed to overwrite a password.**
`signInWithOtp({ shouldCreateUser: true })` succeeds for an email that already
has an account — it simply sends a sign-in code. `verifySignupOtp` therefore
compares the account's `created_at` with the moment the code was requested
(`isExistingAccount`). If the account pre-dates the request, the customer is
signed in and the password field from the signup form is **ignored**; otherwise
it is applied. Without this, signing up with an address that already existed
would silently change that customer's password.

## Repository-side configuration

The deployment environment needs:

| Name | Production value |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://hyibaljxicojfoxbihdn.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_…` (publishable key only) |
| `NEXT_PUBLIC_SITE_URL` | `https://vedantfreshmart.in` |

`NEXT_PUBLIC_SITE_URL` is what `absoluteUrl()` builds every auth redirect from.
If it is wrong, `emailRedirectTo` and the post-callback redirect point at the
wrong host — that is how a `*.vercel.app` or `localhost:3000` URL would leak into
an email.

## Supabase configuration — current state

Applied via the Management API and read back to confirm each value.

| Setting | Before | Now |
|---|---|---|
| **Site URL** | `https://vedant-grocery.vercel.app` | `https://vedantfreshmart.in` |
| **Redirect URLs** | only `*.vercel.app` + localhost | `https://vedantfreshmart.in/**`, `https://vedantfreshmart.in`, `http://localhost:3000/**` |
| **OTP length** | `8` | `6` (matches the six boxes) |
| **Verify rate limit** | `30`/hour | `10`/hour |
| **SMTP** | none (built-in mailer) | Brevo relay, `smtp-relay.brevo.com:587` |
| **Sender** | — | `Vedant Fresh Mart` |
| **Emails per hour** | `2` | `30` |
| **Templates** | link only | `{{ .Token }}` + link fallback |

The Site URL was the "Vercel redirect" bug: every `emailRedirectTo` was silently
rewritten to the old Vercel host because the apex domain was not in the allowlist.
The OTP length was the other half — Supabase was minting **8-digit** codes for a UI
that accepts six.

Adding the Brevo mailer unlocked the template editor, which the free tier blocks
while the built-in mailer is in use.

**Verified end to end on 2026-11-01**: a real `signInWithOtp` request produced
Brevo events `requests → delivered → opened` for the subject
`Your Vedant Fresh Mart verification code`.

### One setting still needs a Pro plan

| Setting | Error |
|---|---|
| **Time-box user sessions** (the one-month cap) | *"User sessions can only be configured on Pro Plans and up."* |

Everything else in this document is live.

### Sender address — worth fixing

No domain is authenticated in Brevo, so the only verified sender is a personal
Gmail address and Brevo rewrites the envelope to
`…@<account>.brevosend.com`. Customers see the display name **Vedant Fresh Mart**,
which is what matters most, but the underlying address is not the store's.

To send as `orders@vedantfreshmart.in`: add the domain in Brevo → Senders,
Domains & IPs → Domains, publish the DKIM/SPF records it generates at the DNS
provider, then change `smtp_admin_email` to the new address. Until then, expect
some spam-folder placement — an unauthenticated domain is a spam signal.

### Providers → Email

Email is enabled and **"Confirm email" is on** (`mailer_autoconfirm = false`,
confirmed via `/auth/v1/settings`). Turning it off would make `signInWithOtp`
skip verification entirely.

## How to verify the flow end to end

You need an inbox you can read.

1. Sign up at `https://vedantfreshmart.in/signup` with that address.
2. The email must show **six digits** under "Your verification code is:". If it
   shows only a link, the template did not take — check
   `mailer_templates_confirmation_content`.
3. Enter the digits. A wrong code shows "That code is invalid or has expired";
   five wrong codes burn the code and ask for a new one.
4. After verifying, the browser lands on `/` signed in; reload and the session
   survives.
5. Sign out, then sign in with the same email and password.
6. Repeat with the *same* email from `/signup`: it must sign the customer in and
   must **not** change their password.
7. Any link in the email must land on `vedantfreshmart.in` — never `*.vercel.app`.

## Attempt limiting — what it does and does not protect

`src/lib/otp-attempts.ts` burns the **code**, not the account: after five wrong
codes the current code is unusable and the customer requests a new one, which
resets the budget. There is also a one-hour ceiling so someone who cannot receive
email is never stuck behind a dead form. The record is per email (hashed), kept in
`localStorage`, and read through `useSyncExternalStore` so a reload shows the real
state without a hydration mismatch.

**This is not the security boundary, and it should not be sold as one.** The
browser calls Supabase Auth directly, so an attacker who skips this page can call
the same endpoint with `curl` and never touch this counter. It exists so honest
customers get a clear stop instead of hammering a dead code, and so the easy path
is also the bounded one.

The control that actually bounds a scripted attack is Supabase's own per-user
verification rate limit plus the one-hour code expiry. Those live in the
dashboard, not in this repository:

| Setting | Where | Suggested value |
|---|---|---|
| OTP expiry | Authentication → Providers → Email | 3600 s (the UI copy says "about an hour") |
| Email OTP verification attempts | Authentication → Rate Limits | 5–10 per hour per user |
| Email send rate | Authentication → Rate Limits | as low as the mailer tolerates |
| JWT expiry | Authentication → Sessions | 3600 s |

With six digits and a five-attempt ceiling per code, guessing inside one code's
lifetime is a ~0.0005% chance; the limiter is what keeps it there.

## Sessions

`@supabase/ssr` already writes cookies with a 400-day lifetime and `proxy.ts`
refreshes the session on every navigation, so a customer stays signed in without
being asked for a password again — that part needed no change.

The account page (`/account` → "Your session") shows how the account signs in,
when it last signed in, and offers **Sign out of all devices**, which calls
`signOut({ scope: "global" })` and revokes every refresh token.

It deliberately does **not** list individual devices. Supabase does not expose a
per-device session list to a publishable key, and a security panel showing
plausible but invented sessions is worse than no panel.

To cap a session at one month — so a customer is asked for credentials again after
30 days — set **Authentication → Sessions → Time-box user sessions** to 30 days.
That is a dashboard setting, and it is **Pro-plan only** (the API rejected it on
this project with *"User sessions can only be configured on Pro Plans and up."*),
so nothing here can enforce it today.

## Known limitations

- **The sender is not the store's domain yet.** No domain is authenticated in
  Brevo, so mail leaves from a personal Gmail address via `*.brevosend.com` (the
  display name is still "Vedant Fresh Mart"). Authenticate `vedantfreshmart.in`
  to fix delivery rates as well as appearances.
- **Sessions cannot be time-boxed on the free tier**, so the one-month cap is not
  active. Sessions otherwise persist and refresh correctly.
- **The OTP screen still offers the email link** as a fallback. That is now a
  convenience rather than a necessity — the code works — but the copy stays until
  someone confirms the code path on a real device.
- **Recovery codes reuse the OTP screen** but not the signup guard: a recovery
  code always resolves to an existing account, which is the point.
- If `updateUser({ password })` fails after a successful verification the
  customer is still signed in and is sent into the app; the account page and the
  forgot-password flow both cover the missing password.
