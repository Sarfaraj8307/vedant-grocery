# Production Readiness — Final Scorecard

> Verified 2026-10-22 against the live database, the live deployment, and recorded test output.

## Verdict

# NOT 100% READY — but the web app is live and usable

**One blocker is mine to fix and I did not finish it: no crash/error observability.**
**One is yours and cannot be automated: the Android release keystore.**

Everything else that was listed as blocking in the previous report is now done — the web app is
deployed, publicly reachable, and serving real data.

---

## Scorecard

| Area | Status | Evidence |
|---|---|---|
| CUSTOMER WEB | **PASS** | https://vedant-grocery.vercel.app — HTTP 200, 12 products from live DB |
| AUTH | **PASS** | login/signup/forgot/reset; `/account` `/orders` `/support` → 307 to login |
| SIGNUP | **PASS** | `/signup` 200; server-side validation |
| CUSTOMER MANAGEMENT | **FAIL** | Backend RPCs exist (`admin_search_users`, `admin_set_account_status`); **no Android UI** |
| CUSTOMER BLOCKING | **PASS** | backend: SUSPENDED → `42501`; no UI |
| OFFERS | **PASS** | 10 live product offers; model extended to 5 scopes |
| CUSTOM OFFERS | **PASS** | schema + CHECK constraints; **no UI** |
| WALLET FOUNDATION | **PASS** | `wallet_accounts` + append-only ledger + gated RPC. **No spending path by design** |
| PRODUCTS | **PASS** | 32 products / 24 weights / 10 categories, verified live |
| CART | **PASS** | `/cart` 200; server re-prices at checkout |
| CHECKOUT | **PASS** | 3-layer store gate; server-authoritative pricing; idempotent `request_id` |
| ORDER MANAGEMENT | **PASS** | Android: list, detail, reject item, adjust, status transitions |
| ORDER ADJUSTMENT | **PASS** | original/rejected/adjustment/final/reason all shown to the customer |
| COMPLAINTS | **PASS** | customer create + admin thread; attachments |
| GPS | **PASS** | browser geolocation, accuracy in metres, low-accuracy warning |
| MAP | **PASS** | OSM embed preview, no API key |
| COORDINATE ACCURACY | **PASS** | stored + returned; three-way fallback proven (live row / null / deleted) |
| PIN ADJUSTMENT | **FAIL** | preview only — no draggable pin (needs Leaflet) |
| DELIVERY | **PASS** | queue with CALL, NAVIGATE, Start, Mark delivered, **REPORT ISSUE** |
| NAVIGATION | **PASS** | `geo:` intent, coordinates preferred, text fallback |
| REALTIME | **PASS** | `orders` + history published, `REPLICA IDENTITY FULL`, filtered subscription |
| STORE OPEN/CLOSE | **PASS** | one-click dashboard control; enforced at UI, server action **and** RPC |
| STAFF MANAGEMENT | **FAIL** | RPCs exist (`owner_grant_role`, `admin_list_staff`); **UI removed at your request** |
| REPORTING | **FAIL** | `admin_dashboard_counts` / `admin_sales_today` exist; **no UI** |
| ANDROID UI | **PASS** | design system, skeletons, both themes, 459 tests |
| DARK MODE | **PASS** | scheme + persisted override |
| LIGHT MODE | **PASS** | scheme + persisted override |
| ACCESSIBILITY | **PARTIAL** | `role`/`aria-live` on web banners; content descriptions on Android. **No full WCAG audit** |
| PERFORMANCE | **PARTIAL** | Advisor 26/26 are `unused_index` on near-empty tables. **No load test, no CWV data** |
| SECURITY | **PASS** | 14 attack paths closed and re-probed; 0 tables without RLS; 0 unpinned DEFINER |
| RLS | **PASS** | 25/25 tables, 24-probe cross-customer matrix clean |
| OBSERVABILITY | **FAIL** | **no crash reporting, no error tracking.** `audit_logs` only |
| BACKUPS | **FAIL** | documented in `BACKUP_AND_RESTORE.md` but **never restore-tested** |
| WEB DEPLOYMENT | **PASS** | live, public (curl 200 without auth), 11 routes smoke-tested |
| ANDROID RELEASE BUILD | **FAIL** | debug only. **Needs a keystore you must create** |
| E2E TESTING | **FAIL** | 5 Playwright specs exist but predate GPS/acceptance/realtime |
| REGRESSION | **PASS** | 5 security invariants re-probed after every migration |
| ROLLBACK | **PASS** | documented in `ROLLBACK.md`; `vercel rollback` verified available |

**PASS 24 · FAIL 8 · PARTIAL 2**

---

## What I fixed this session

**Security — a hole I opened myself.** `profiles.internal_notes` was readable by any customer on
their own row. RLS is row-level; it cannot hide a column. My first fix (a column-level `REVOKE`)
was a **silent no-op** against a table-level grant. The working fix revokes table-level SELECT
and re-grants every column except that one — verified three ways (SQL allowed, SQL denied, REST
200 then `42501`).

**Deployment.** Web is live. Two bugs found only by testing the deployed site:
`NEXT_PUBLIC_SITE_URL` was unset so 12 canonical/OG URLs pointed at `localhost:3000`; and
`.vercelignore` used a bare `supabase` pattern which matched `src/lib/supabase/` at any depth and
broke the build — the same anchoring mistake as `.workbuddy-ai` earlier.

**Ledger integrity.** Migration 012's row had silently vanished from
`supabase_migrations.schema_migrations` and 016 was applied but never recorded. Both re-recorded.
Ledger now 18 files / 18 rows.

**Wallet + offers.** Offers extended to 5 scopes with DB-enforced integrity; wallet foundation
with an append-only ledger. All 10 existing offers verified untouched.

**Android.** Appearance persistence (System/Light/Dark), shimmer skeletons honouring reduce-motion,
`networkSecurityConfig` blocking cleartext, REPORT DELIVERY ISSUE. **Tests 427 → 459.**

## What I did NOT do, and why

| Item | Reason |
|---|---|
| Android release keystore | **Cannot be automated.** You must generate it and keep it secret. |
| Crash reporting | Needs a third-party account (Crashlytics/Sentry) — a credential I don't have. |
| Restore test | Needs a scratch Supabase project or a local Postgres. |
| Load testing | Would need seeded volume data; measuring on 0 orders is meaningless. |
| Customer-management / reporting / staff UI | Backend ready; UI is a substantial build I did not reach. |

## Honest statement on "100% ready"

I am **not** claiming 100%. The security work is done and proven, the web app is live and serving
real data, and the Android app is functionally complete for delivery work. What remains is
observability, release signing, three admin UIs, and a real backup test. Those are listed above
as FAIL rather than softened into "partial".
