# Security review — 2026-10-28

Live audit of project `hyibaljxicojfoxbihdn` (`vedantfreshmart.in`) through the
Supabase Management API, plus a read of the auth code in this repository.

Everything below was checked against the running database, not the migration
files. Where a check was inconclusive it says so.

## Verdict

The schema was already in good shape — better than most. The one real hole was in
the **staff-invite OTP**, and it is now closed. The rest of this document records
what was checked so the next review does not have to redo it.

## Database — RLS

| Check | Result |
|---|---|
| Public tables with RLS disabled | **0** — every table has it on |
| `SECURITY DEFINER` functions without a pinned `search_path` | **0** |
| Views bypassing RLS | **0** — the only view (`product_catalog`) has `security_invoker=true` |
| `INSERT`/`UPDATE` policies missing a `WITH CHECK` | **0** |

Customer-owned tables are scoped correctly: `addresses`, `cart_items`,
`complaints`, `notifications` and `orders` all resolve to
`auth.uid() = user_id`, with staff access routed through
`private.can_view_orders()` / `can_view_users()` rather than a blanket role check.

Two details worth keeping, because they are easy to lose in a refactor:

- `profiles_insert_own` has `WITH CHECK (auth.uid() = id)` — without it a customer
  could insert a profile row carrying someone else's id.
- `profiles_update_own` has `WITH CHECK (auth.uid() = id AND role =
  private.current_profile_role())` — this is what stops a customer promoting
  themselves to `admin`. Any future "let users edit their profile" migration must
  keep that clause.

Anonymous access is limited to six catalogue tables, all `SELECT`:
`categories`, `delivery_slots`, `products` (filtered on `is_active`),
`offers`, `product_weights`, `store_settings` (unfiltered, but that table is
public store configuration by design).

### Minor, not fixed

`events_insert_authenticated` allows any signed-in user to insert any row
(`WITH CHECK (true)`). That is an analytics table, so the exposure is forged
events rather than leaked data. Tightening it to `user_id IS NULL OR user_id =
auth.uid()` would remove the spoofing path; it was left alone because the write
shape is owned by the reporting code and changing it without tracing every caller
risked breaking analytics.

## The hole that was closed

`public.verify_staff_otp(email, otp)` is callable with the **publishable anon
key** — necessarily, because an invitee has no session yet. It grants a staff
role, up to `OWNER`.

It accepted unlimited guesses against a six-digit bcrypt-hashed code valid for 15
minutes. bcrypt is slow, but "slow" is ~100 ms per guess: a few thousand attempts
fit in the window, and nothing server-side recorded a failure, so an attacker
could parallelise freely. The prize was an admin account.

`supabase/migrations/20261028_001_staff_invite_bruteforce_guard.sql` adds
`failed_attempts` / `locked_until`, locks the invite for an hour after five wrong
codes, and makes "no such invite" and "wrong code" return the **same** message so
the function is no longer an address-enumeration oracle.

Verified live after applying: an anonymous call with an unknown address returns
`That code is invalid or has expired. Ask the owner for a new invite.` and the
function body contains the attempt counter.

Residual risk: the invite window is still 15 minutes and the code is generated
with `random()` rather than a CSPRNG. Five attempts against 1,000,000
combinations is a 0.0005% chance, which is acceptable; switching generation to
`gen_random_bytes` would be a tidy follow-up.

## Auth configuration

| Setting | Was | Now |
|---|---|---|
| Site URL | `https://vedant-grocery.vercel.app` | `https://vedantfreshmart.in` |
| Redirect allowlist | Vercel host only | apex `/**` + localhost |
| OTP length | 8 | 6 |
| Verify rate limit | 30/hour | 10/hour |
| JWT expiry | 3600 s | unchanged — correct |
| Email confirmation | required | unchanged — correct |
| Signup | enabled | unchanged — correct |

The Site URL mismatch was a live bug, not just a stale config: every
`emailRedirectTo` was silently rewritten to the old Vercel host because the apex
domain was not allowlisted.

## Code review notes

- No service-role key anywhere in the app; the browser holds only the publishable
  key and every query is subject to RLS.
- `NEXT_PUBLIC_SITE_URL` is the single source for auth redirects, and
  `lib/env.ts` refuses to boot if a `service_role` JWT is pasted into a
  `NEXT_PUBLIC_*` variable.
- OTP codes are never persisted, logged, or put in `localStorage`; only the
  *failure count* is stored, keyed by a hash of the address.
- Auth callback sanitises `?next=` to same-origin paths, so it is not an open
  redirect.

## What this review could not establish

- **Deliverability.** Whether a code actually reaches an inbox needs a mailbox;
  the template is currently locked by the plan, so today's email carries a link.
- **Rate limiting under load.** The app-level limiter in `src/lib/rate-limit.ts`
  is an in-memory `Map`, which on Cloudflare Workers is per-isolate. It slows a
  single hot instance and does nothing across them. Fine as a speed bump, not as
  a quota.
- **Android client.** `admin-android/` was read only where it consumes the RPCs;
  it was not audited.
