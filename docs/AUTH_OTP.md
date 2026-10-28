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

## Supabase dashboard — required, cannot be done from this repository

There is no management token in this workspace, so these three steps must be
done by the project owner.

### 1. Authentication → URL Configuration

| Field | Value |
|---|---|
| **Site URL** | `https://vedantfreshmart.in` |
| **Redirect URLs** | `https://vedantfreshmart.in/**`<br>`https://vedantfreshmart.in`<br>`http://localhost:3000/**` (local development only) |

The `/**` suffix matters: `emailRedirectTo` targets `/auth/callback`, and a bare
host entry does not match a path.

`www.vedantfreshmart.in` redirects to the apex domain, so the apex is the only
host that needs to be listed.

### 2. Authentication → Email Templates — **this is the step that turns the flow on**

By default Supabase's templates contain `{{ .ConfirmationURL }}` and no code. If
these are left alone, the customer receives a link, the six boxes never have
anything to verify, and the flow is dead.

**Confirm signup** — subject `Your Vedant Fresh Mart verification code`:

```html
<h2>Vedant Fresh Mart</h2>
<p>Your verification code is:</p>
<p style="font-size:28px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>Enter this code on Vedant Fresh Mart to verify your email.</p>
```

**Magic Link** — same body. This is the template used when an address already has
an account, so it must also carry the code.

**Reset Password** — subject `Your Vedant Fresh Mart password reset code`:

```html
<h2>Vedant Fresh Mart</h2>
<p>Your password reset code is:</p>
<p style="font-size:28px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>Enter this code on Vedant Fresh Mart to choose a new password.</p>
<p>If you did not ask for this, you can ignore this email.</p>
```

`{{ .Token }}` is the six-digit code. `{{ .ConfirmationURL }}` is the clickable
link — it must **not** be the only thing in the email.

### 3. Authentication → Providers → Email

Email must be enabled and **"Confirm email" must stay on**. Turning it off would
make `signInWithOtp` skip the code entirely.

### Optional: custom SMTP

The built-in Supabase mailer is heavily rate-limited and is intended for testing.
For real customer volume, configure Authentication → Emails → SMTP Settings with
a transactional provider, or OTP delivery will start failing under load with
`email rate limit exceeded`.

## How to verify the flow end to end

You need an inbox you can read.

1. Sign up at `https://vedantfreshmart.in/signup` with that address.
2. The email must show **six digits**, not a link. If it shows a link, step 2
   above has not been applied.
3. Enter the digits — a wrong code shows "That code is invalid or has expired".
4. After verifying, the browser lands on `/` signed in; reload and the session
   survives.
5. Sign out, then sign in with the same email and password.
6. Repeat with the *same* email from `/signup`: it must sign the customer in and
   must **not** change their password.

## Known limitations

- **Email delivery is not observable from here.** `mailer_autoconfirm` is
  `false` and the email provider is enabled (confirmed via
  `/auth/v1/settings`), but whether the template contains `{{ .Token }}` can only
  be checked in the dashboard.
- **Recovery codes reuse the OTP screen** but not the signup guard: a recovery
  code always resolves to an existing account, which is the point.
- If `updateUser({ password })` fails after a successful verification the
  customer is still signed in and is sent into the app; the account page and the
  forgot-password flow both cover the missing password.
