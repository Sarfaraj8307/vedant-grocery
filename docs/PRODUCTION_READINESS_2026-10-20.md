# Production Readiness Report — 2026-10-20

> Project `hyibaljxicojfoxbihdn` · Next.js 16 customer PWA · Kotlin `com.vedant.admin` app
> Verdict at the bottom. It is not the verdict anyone wants, but it is the accurate one.

## Final decision

# NOT PRODUCTION READY

The **backend and the customer website are production-grade**. The **Android application is
not**, because a large part of what the brief describes for Android does not exist — and one
item (token rotation) cannot be completed by me at all.

Everything below is measured, not asserted.

---

## A. Completed and verified

### Database security

| Check | Result |
|---|---|
| Advisor findings | **92 → 36** (security 26→20, performance 66→16) |
| Tables without RLS | **0** |
| `SECURITY DEFINER` with unpinned `search_path` | **0** |
| `anon`-executable `DEFINER` functions | **1** — `check_username_available` (intentional, validated, boolean-only) |
| Migrations applied + recorded | **8 / 8** in `supabase_migrations.schema_migrations` |

Remaining findings, all documented: 17 authenticated-`DEFINER` RPCs (the intentional,
internally-gated API surface — the linter cannot distinguish these from exposed privileged
functions), 1 `rls_enabled_no_policy` (`events`, 0 rows, no writer), 1 leaked-password
protection (**accepted**: HTTP 402, Pro-plan feature, no SQL path), 16 `unused_index` on
near-empty tables.

### Vulnerabilities confirmed closed (each proven by probe)

| # | Vulnerability | Proof |
|---|---|---|
| 1 | Customer modifies order status/total | `orders_update_own` dropped; UPDATE → 0 rows |
| 2 | Customer creates arbitrary orders via direct INSERT | `orders_insert_own` dropped; INSERT → `42501` |
| 3 | Customer modifies/deletes placed-order line items | `order_items_update/delete` dropped; → `42501` |
| 4 | SUPPORT performs ADMIN/DELIVERY operations | 5 RPCs re-gated; SUPPORT → `42501`, DELIVERY passes |
| 5 | Customer reads another customer's data | 24-probe matrix: 0 rows on every cross-account read |
| 6 | Suspended customer places orders | `42501` before business logic |
| 7 | Client trusted for pricing | prices read from `product_weights`/`offers` server-side only |
| 8 | Service area bypassable via direct RPC | `service_areas` table + guard inside `place_order_app` → `22023` |
| 9 | Privilege escalation to admin/owner | ADMIN → `owner_grant_role` = `42501`; self-`role` change blocked by policy guard |
| 10 | Bucket enumeration | `product_images_public_read` dropped; anon list → `[]`, object URL → 200 |

### Secrets

| Item | Status |
|---|---|
| Service-role key in browser/Android source | **none found** (`src/lib/env.ts` actively rejects it) |
| `.env.local` contents | publishable key only — correct |
| **Supabase PAT found in `session-ses_ed59.md`** | **redacted** (line 1482) |
| **Test password found in `.logs/db/*.js`** | **scrubbed** (hardcoded fallback removed) |
| `.gitignore` | covers `.env*`, `.next/`, `.next-old/`, `.logs/`, `.tmp/` |

### Test accounts and data

| Item | Status |
|---|---|
| `owner@vedant.local` (fixture ADMIN, known password) | **deleted** |
| `customer@vedant.local` (fixture customer, known password) | **deleted** |
| Test order `ORD-TEST-1001` + 2 items | **deleted** |
| Test addresses Pune 411001 / Mumbai 400001 | **deleted** |
| Test cart items | **deleted** |
| Pre-deletion backup | `.logs/production-cleanup-backup-2026-10-20.json` |
| Real owner `khansarfaraj.khan@gmail.com` | **verified**: confirmed, not banned, `admin/ACTIVE`, staff role `OWNER` |
| Remaining staff roster | `OWNER` only — no ADMIN left |

### Builds

| Target | Result |
|---|---|
| Web `tsc --noEmit` | **exit 0** |
| Web `eslint .` | **0 errors** (17 warnings, all vendored in `.agents/skills/`) |
| Web `next build` | **SUCCESS** — 26 routes + middleware |
| Android `testDebugUnitTest` | **428 tests pass** |
| Android `assembleDebug` | **SUCCESS** |

### Android delivered this pass

- **CALL CUSTOMER (§12)** — `ACTION_DIAL` (opens the dialer pre-filled; never places the call,
  needs no `CALL_PHONE` permission), hidden entirely when no phone exists.
- **Customer phone surfaced and separated from the address.** `toAddressLines()` previously
  flattened *every* snapshot value, so the customer's phone number was being rendered as if it
  were a street address. Now `phone`/`name` are excluded and `customerPhone` is extracted and
  normalised to a dialable form. Covered by 3 new mapper tests.

---

## B. NOT done — the blockers

### B1. Supabase PAT rotation — **cannot be done by me**

The development token is still valid and was exposed in chat and on disk. Rotating a personal
access token is a dashboard action with no Management API endpoint. **This is the single
highest-priority remaining item and it requires you.**

After rotating: re-issue the token, update any local tooling, and confirm the old token returns
401. Nothing in the application depends on it — it was only used for migrations and probes.

### B2. The customer Android app does not exist

The brief's §18–21 describe a **customer** Android app (welcome → login → home, 2-column product
grid, product detail, cart, checkout). The only Android project present is
`admin-android` / `com.vedant.admin` — an **admin/delivery** application. There is no cart, no
catalogue browsing, no checkout, no customer-facing code in it at all.

So:
- "Android welcome screen", "2-column grid", "product detail", "cart/checkout" — **not
  applicable to the existing app**; they would require building a second application.
- The customer experience today is the **web PWA**, which is mature and builds clean.

This is a scope decision, not a defect. It needs your call: build a customer Android app, or
treat the PWA as the customer surface.

### B3. Admin/delivery app gaps

| Feature | State |
|---|---|
| Delivery screen (assigned orders, list) | **not built** — DB side is complete (`delivery_orders()` verified returning customer name/phone/address/items/totals) |
| Multi-staff creation UI (§14) | **not built** — DB side is complete (`owner_grant_role`/`owner_revoke_role`, last-OWNER guard) |
| Customer search/profile/status UI (§15) | **not built** — `admin_search_users()` + `admin_set_account_status()` exist and are gated |
| Reporting UI (§27) | **not built** — `admin_dashboard_counts()`/`admin_sales_today()` exist and are verified |
| Welcome screen | **not built** (app opens on Splash → SignIn, which is functionally equivalent) |
| CALL CUSTOMER | **done** |

The backend for every one of these is built, tested and gated. The remaining work is Compose UI.

### B4. End-to-end test (§45) — not run

The 33-step E2E was **not** executed end-to-end in this pass. The signed-in web walk *was* run
previously (order history, Buy Again re-pricing, add-all clamping, serviceability gating, all
with screenshots), but the full admin-reject → adjust → delivery → complaint cycle was not.

Deleting the fixture accounts removed the ability to run it without a real account. **To run
it, the owner must sign in** (or a new confirmed staff account must be created via
`owner_grant_role`).

### B5. Release engineering

| Item | State |
|---|---|
| Android **release** build / AAB | **not produced** — only `assembleDebug` verified; no signing config inspected |
| Git repository | **none** — no version control, no history, no rollback |
| CI/CD | **none** |
| Backup / recovery procedure | **not documented** |
| Deployment procedure | **not documented** |
| Rollback procedure | **not documented** |
| Accessibility audit (§37) | not run this pass |
| Performance/load testing (§44) | not run |
| Payment abstraction (§33) | not implemented — no payment code exists (COD only) |
| Observability (§35) | not implemented — no error tracking / crash reporting |

### B6. Housekeeping

- `.next-old` — **2.2 GB** stale build cache left on disk; the sandbox bulk-delete guard blocks
  removal and the documented override was unavailable. Gitignored. **Delete it manually.**
- `vedant@gmail.com` — an **unconfirmed** account (created 2026-10-12, no role, no data). Not on
  the brief's removal list, so I did not delete it: it may be a real person's abandoned signup.
  **Needs your decision.**
- One cart item remains on the real OWNER's account (created 2026-10-16). It is the owner's own
  data, not fixture data, so I left it.

---

## C. Remaining risks

| # | Severity | Risk | Impact | Reason | Mitigation |
|---|---|---|---|---|---|
| 1 | **Critical** | Development PAT still valid | Full account control for anyone with the token | Cannot rotate via API | Rotate in dashboard now |
| 2 | **High** | No git repository | No history, no rollback, no review | Never initialised | `git init` + commit + remote |
| 3 | **High** | No customer Android app | Brief §18–21 unmet | App is admin-only | Decide scope (B2) |
| 4 | **High** | E2E not executed end-to-end | Order lifecycle unproven as a whole | Fixtures removed | Run with a real staff account |
| 5 | **Medium** | No release build / signing | Cannot ship to Play Store | Only debug built | Add signing config, build AAB |
| 6 | **Medium** | No backup/restore procedure | Data loss on incident | Undocumented | Enable PITR + document |
| 7 | **Medium** | No observability | Failures invisible in production | Not implemented | Sentry/Crashlytics + log drain |
| 8 | **Low** | 16 unused indexes | Minor write cost | New/empty tables | Revisit after real traffic |
| 9 | **Low** | Leaked-password protection off | Weaker password policy | Pro-plan only | Accept or upgrade |
| 10 | **Low** | `vedant@gmail.com` unconfirmed account | None today | Ownership unknown | Confirm and remove, or leave |

---

## D. What is genuinely safe to say

- The **database is secure**. 10 previously-live vulnerabilities are closed and each is proven
  by a probe, not by reading the code.
- **Customer data is isolated.** 24 adversarial probes across 8 identities all returned the
  correct deny.
- **Money cannot be manipulated** by a client — pricing, totals, discounts and delivery fees are
  all server-computed, and the three mutation paths that existed are gone.
- **The customer website builds and runs clean** and the storefront gaps (Buy Again, delivery
  location, serviceability) are implemented and verified.
- **No production secret remains in the repository.**

What stops it being production-ready is **not** the backend. It is: one token you must rotate,
a missing customer Android app, unfinished admin UI, and the absence of release engineering
(git, CI, backups, signing).
