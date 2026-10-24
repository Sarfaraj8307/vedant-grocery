# Deployment

## Live

| | |
|---|---|
| **Production URL** | https://vedant-grocery.vercel.app |
| **Vercel project** | `khansarfarajkhan-8498/vedant-grocery` |
| **Region** | Vercel default (functions run closest to the visitor) |
| **Supabase project** | `hyibaljxicojfoxbihdn`, region `ap-south-1` (Mumbai) |

Verified 2026-10-22 by direct `curl` **without** a Vercel auth token (HTTP 200), so the
production alias is genuinely public. `Deployment Protection` is enabled on the project but
does not gate the production alias — only preview deployments.

## Environment variables (Vercel → Production)

| Name | Value | Secret? |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://hyibaljxicojfoxbihdn.supabase.co` | No — ships in the browser bundle |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | `sb_publishable__…` | No — same reason |
| `NEXT_PUBLIC_SITE_URL` | `https://vedant-grocery.vercel.app` | No |

`NEXT_PUBLIC_SITE_URL` is **not optional**. Without it, canonical links, OpenGraph URLs and the
`GroceryStore` JSON-LD all point at `http://localhost:3000` — which shipped in the first deploy
and had to be corrected. If the domain changes, update this variable and redeploy.

**Never** set a service-role key or a PAT as a `NEXT_PUBLIC_*` variable. Anything prefixed
`NEXT_PUBLIC_` is readable by every visitor.

## Deploying

```bash
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables"
vercel deploy --prod --archive=tgz --name=vedant-grocery
```

`--name` is required: the directory name contains spaces, and Vercel rejects a derived project
name (`Project names … must be lowercase`).

### `.vercelignore` matters

Without it the CLI archived **1.8 GB** — `node_modules` plus a stale 2.2 GB `.next-old`.
The file excludes those. **Its patterns are root-anchored on purpose**: a bare `supabase`
matches at any depth and also excluded `src/lib/supabase/`, which broke the build with
`Can't resolve '@/lib/supabase/client'`.

## Post-deploy verification

```bash
# public
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app/categories
curl -s -o /dev/null -w "%{http_code}\n" "https://vedant-grocery.vercel.app/search?q=tomato"

# auth-gated routes must 307 to /login, not 200
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app/orders
```

Expected, measured on 2026-10-22:

| Route | Code |
|---|---|
| `/`, `/categories`, `/offers`, `/search?q=tomato`, `/login`, `/signup`, `/cart`, `/products/<id>` | 200 |
| `/account`, `/orders`, `/support` | **307** (to login — correct) |

Then confirm the data layer actually reached Supabase, not a cached shell:

```bash
curl -s https://vedant-grocery.vercel.app | grep -o '/products/[a-f0-9-]\{36\}' | sort -u | wc -l
# expected: 12
curl -s https://vedant-grocery.vercel.app | grep -c 'localhost:3000'
# expected: 0
```

## Rollback

See `ROLLBACK.md`. Short version: `vercel rollback` to the previous deployment, or redeploy any
earlier commit — the app is stateless apart from Supabase.

## Known deployment limitations

- No custom domain. `vedant-grocery.vercel.app` is the production URL. Adding a domain is a
  dashboard step plus a `NEXT_PUBLIC_SITE_URL` change.
- No CI gate on deploy. `.github/workflows/ci.yml` exists but there is no remote, so it has never
  run. Deploys are manual and currently unverified by CI.
- Preview deployments require Vercel authentication.

## Supabase Auth URL configuration (a trap that will recur)

Supabase sends confirmation / recovery emails using **its own** URL settings, not the app's. If
`Site URL` is left at `http://localhost:3000` — the default — every link points at a machine that
does not exist for the customer, and the app's `emailRedirectTo` is **silently overridden** unless
the target is in the allowlist.

This actually happened: a real signup produced
`http://localhost:3000/?error=access_denied&error_code=otp_expired…`.

### Correct settings

Dashboard → Authentication → URL Configuration:

| Field | Value |
|---|---|
| **Site URL** | `https://vedant-grocery.vercel.app` |
| **Redirect URLs** | `https://vedant-grocery.vercel.app/**`, `https://vedant-grocery.vercel.app`, `http://localhost:3000/**` |

The trailing `/**` matters — `emailRedirectTo` targets `/auth/callback`, and a bare
`https://host` entry does not match a path.

Programmatically:

```bash
curl -X PATCH "https://api.supabase.com/v1/projects/$REF/config/auth" \
  -H "Authorization: Bearer $PAT" -H "Content-Type: application/json" \
  -d '{"site_url":"https://vedant-grocery.vercel.app",
       "uri_allow_list":"https://vedant-grocery.vercel.app/**,https://vedant-grocery.vercel.app,http://localhost:3000/**"}'
```

**Verify the PATCH actually took.** It returns `200` even when a field is ignored; re-`GET` and
read `site_url` / `uri_allow_list` back. A first read can be stale — check twice before believing
it failed.

### How to prove it works without reading an inbox

```bash
curl -X POST "https://$REF.supabase.co/auth/v1/admin/generate_link" \
  -H "apikey: $SERVICE_ROLE" -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "Content-Type: application/json" \
  -d '{"type":"signup","email":"probe@example.com","password":"…",
       "redirect_to":"https://vedant-grocery.vercel.app/auth/callback"}'
```

Read `redirect_to` from the response. It must come back as the `/auth/callback` URL — if Supabase
has overridden it with the Site URL, the allowlist is still wrong. **Delete the probe user
afterwards**; `generate_link` creates a real account.

### If a link still says `otp_expired`

That is separate from the URL bug: the token was already used, expired (default 24 h), or
superseded by a newer signup for the same address. Signing up again issues a fresh one.

## Terms consent

`supabase/migrations/20261025_001_terms_acceptance.sql` creates
`public.terms_acceptances` — one immutable row per customer per terms version
(`user_id`, `terms_version`, `accepted_at`, `source`).

**This migration must be applied before the consent gate will do anything.** Until then
`getConsentState()` cannot read the table, so it reports `checked: false` and the gate stays
closed. That is deliberate: an unapplied migration is a deployment fault, and locking every
customer out behind a modal whose accept button also cannot write would be worse than a missing
consent row.

Why the row is immutable: there is no UPDATE or DELETE policy, and the table privileges are
`SELECT, INSERT` only for `authenticated`. Consent is evidence — a customer who could edit the
row could rewrite what they agreed to. Deleting the account cascades the row away, which is the
intended, deliberate path.

### Changing the terms

1. Edit the copy on `/terms`.
2. Bump `TERMS_VERSION` (and `TERMS_UPDATED`) in `src/lib/terms.ts`.
3. Deploy.

Existing customers are then asked once more on their next sign-in. Their earlier acceptance stays
in the table with its own timestamp and version — that is the point of versioning rather than a
boolean on `profiles`.

### Auditing

```sql
-- Who has accepted the current version, and who is outstanding
select p.email, a.terms_version, a.accepted_at, a.source
from public.profiles p
left join public.terms_acceptances a
  on a.user_id = p.id and a.terms_version = '2026-10-25'
order by a.accepted_at desc nulls last;
```

`source` records where consent was given: `signup` (the form's checkbox, written immediately or
on first sign-in when email confirmation deferred the session), `google`, `login` (the gate) or
`account`.

## Google sign-in (Gmail)

**The code is already shipped.** `signInWithGoogle()` performs a real Supabase OAuth call, the
callback route exchanges the code, and both `/login` and `/signup` render a "Continue with Google"
button — but only when the live project reports the provider as enabled
(`src/lib/auth-providers.ts`). A button that always errors would be a fake control, so it is
hidden instead.

Probed on 2026-10-24: `GET /auth/v1/settings` returns `external.google = false`. The button is
therefore absent from the live site, and **no code change is needed to turn it on** — only
configuration.

### Enabling it

1. **Google Cloud Console** → APIs & Services → OAuth consent screen → External → fill in app name,
   support email, developer email. Publishing status can stay in *Testing* while you verify, but add
   your own Google account under *Test users*, otherwise only that list can sign in.
2. **Credentials** → Create credentials → OAuth client ID → **Web application**.
   Authorised redirect URI (exactly this, no trailing slash):

   ```
   https://hyibaljxicojfoxbihdn.supabase.co/auth/v1/callback
   ```

   This is Supabase's callback, **not** the app's `/auth/callback`. Getting this wrong produces
   `redirect_uri_mismatch` at Google, before the app is ever reached.
3. **Supabase dashboard** → Authentication → Providers → Google → paste Client ID and Client
   Secret → Enable → Save.
4. Confirm the app URLs from the section above are still in the redirect allowlist
   (`https://vedant-grocery.vercel.app/**`), because the OAuth return path lands on
   `/auth/callback`.
5. Verify:

   ```bash
   npm run auth:check     # exits 0 once google is true
   ```

   Then reload `/login`. The button appears on the next render; the capability probe is cached for
   5 minutes (`revalidate: 300`), so give it that long or restart the dev server.

### Why not Firebase Auth

Firebase would be a **second identity system**, and every RLS policy in this database keys on
`auth.uid()` from the Supabase JWT. A customer signed in through Firebase would carry a Firebase ID
token, so `supabase.auth.getUser()` would return null, requests would run as `anon`, and RLS would
deny their own orders, addresses, cart and profile. Bridging the two needs a token-exchange function
that verifies the Firebase token and mints a Supabase JWT signed with the project's JWT secret — a
security-critical component that duplicates, rather than adds, what Supabase already does natively.
The admin Android app authenticates against Supabase too, so it would split the identity model in
two. Supabase's Google provider gives the same "Sign in with Google" experience with one console
setting and zero new moving parts.

