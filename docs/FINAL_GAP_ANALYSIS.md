# Final Gap Analysis — 2026-10-21

> Verified against the live database, the working tree and recorded test output. Supersedes
> `APPLICATION_REPORT_2026-10-20.md` where they differ.

## Inventory

**Surfaces (frozen per the brief):**

| Surface | Tech | State |
|---|---|---|
| Customer | Next.js 16.3.8 / React 19 PWA | 25 routes, production build green, 36 unit tests |
| Admin + Delivery | Kotlin, `com.vedant.admin` | 156 Kotlin files, **427 tests / 0 failures**, `assembleDebug` green |
| Backend | Supabase Postgres + Auth + Storage + Realtime | 13 migrations, all recorded |

**Git:** initialised, 6 commits, clean tree, no secrets in any commit.
**Vercel:** CLI **authenticated as `khansarfarajkhan-8498`**. 9 existing projects; **none belongs to
this repo**, and there is no `.vercel/project.json` link. Deployment is therefore available now
and was previously listed as human-blocked — that entry is obsolete.

---

## WHAT EXISTS — and works, verified

### Backend
- RLS on every table; 0 `SECURITY DEFINER` functions with an unpinned `search_path`
- Customer data isolation (24-adversarial-probe matrix)
- Server-authoritative pricing and totals; client sends ids/quantities only
- Order INSERT / status UPDATE / line-item UPDATE+DELETE surfaces all closed for customers
- Serviceability enforced in `place_order_app` (`22023: delivery area not served`)
- Role authorization: SUPPORT blocked from order status, item rejection, slot load, roster
- Delivery assignment + `delivery_orders()` with customer name/phone/coordinates
- Order acceptance control with reason, history and audit
- Realtime on `orders` + `order_status_history`, `REPLICA IDENTITY FULL`
- **Reporting RPCs**: `admin_dashboard_counts`, `admin_sales_today`, `admin_search_users`,
  `admin_set_account_status`, `admin_slot_load` — *backend complete, no UI*
- **Staff RPCs**: `owner_grant_role`, `owner_revoke_role`, `admin_list_staff` — *backend complete, no UI*

### Customer web
Home, categories, search, product detail, offers, cart, checkout (server-priced, idempotent
`request_id`), addresses, GPS capture with accuracy + map preview + Nominatim reverse geocoding
(**no API key**), orders, order detail with adjustments, Buy Again re-priced against today's
catalogue, complaints with attachments, auth (login/signup/forgot/reset), realtime order
updates, closed-store banner.

### Android
Splash → SignIn → role gate; dashboard with **one-click order acceptance**; orders list/detail
with reject-item + adjust + status transitions; catalogue (products/categories/weights/offers) +
editor; slots; complaints list/thread; store settings; profile; **delivery queue** with CALL
(`ACTION_DIAL`), NAVIGATE (`geo:`), state-gated Start/Mark-delivered; explicit connection
states; **light and dark colour schemes both implemented**.

---

## WHAT PARTIALLY WORKS

| Item | State | Gap |
|---|---|---|
| Android dark mode | Both schemes exist and `isSystemInDarkTheme()` is used | **No persisted appearance choice** — no System/Light/Dark override, nothing stored |
| Android appearance | `LocalReduceMotion` exists and is persisted-ish | No appearance store at all |
| Offers | `offers(product_id, discount_percent, starts_at, ends_at, is_active)` | **Product-only.** No min-order, max-discount, usage limit, per-customer limit, customer-targeting or segment-targeting. §20/§21 not possible without extension |
| Reporting | 3 RPCs return counts/sales | No Android screen consuming them |
| Customer management | `admin_search_users` + `admin_set_account_status` exist | No Android screen |
| Complaints | Full customer + admin flow | No `internal_notes` anywhere |

## WHAT IS MISSING

| § | Item | Type |
|---|---|---|
| 22 | Wallet foundation (`wallet_accounts`, `wallet_transactions`) — **0 tables today** | Backend |
| 20/21 | Custom + general offers extension | Backend |
| 17/18 | Android customer management + profile + internal notes | Android UI |
| 19 | Customer segmentation (configurable thresholds) | Backend + UI |
| 28 | Android reporting dashboard | Android UI |
| 25 | Delivery: REPORT ISSUE, Failed state | Android + backend |
| 27 | Customer delivery tracking page (map + progress) | Web |
| 36 | Security headers — `headers()` exists but only for `sw.js`/`manifest` | Web config |
| 37 | Android `networkSecurityConfig` + cleartext block | Android config |
| 38 | Crash reporting (Android), error boundary + reporting (web) | Both |
| 39 | Backup/restore procedure | Docs |
| 43 | **Vercel deployment** | **Now unblocked** |
| 42 | Android release signing | Human |
| — | Playwright specs for GPS / closed-store / realtime | Tests |
| — | `androidTest` refresh for delivery + acceptance | Tests |

## WHAT IS UNSAFE
Nothing outstanding. The 14 attack paths closed in migration 001–013 remain closed and were
re-probed after every change. The one accepted risk is `auth_leaked_password_protection`
(Pro-plan, HTTP 402).

## WHAT IS SLOW
Not measured. No profiling has been run on either app; no Core Web Vitals data exists. I will
not claim a performance result I have not measured.

## WHAT IS UGLY
Judgement, not defect: Android screens use a clean card system but no elevation hierarchy, no
shimmer skeletons, and the dashboard is a plain button stack. Web is deliberately restrained.

## WHAT BLOCKS RELEASE

| # | Blocker | Owner | Status |
|---|---|---|---|
| 1 | Android release keystore | **Human** | Not started — cannot be done autonomously |
| 2 | Crash/error observability | Agent | Not started |
| 3 | Web deployment | Agent | **Unblocked — Vercel credentials available** |
| 4 | Backup/restore documented + tested | Agent | Not started |
| 5 | Security headers | Agent | Not started |
| 6 | Delivery tracking page | Agent | Not started |
| 7 | Android appearance persistence | Agent | Not started |

---

## Execution plan (this session)

Delegated in parallel; each worker verifies before reporting:

- **Backend** — wallet foundation + offers extension + segmented offers, as migrations with live probes.
- **Android** — appearance persistence (System/Light/Dark), `networkSecurityConfig`, skeleton loading, REPORT ISSUE.
- **Web** — security headers, delivery tracking page, error boundary, Playwright specs.
- **Lead** — `FINAL_GAP_ANALYSIS` (this file), production docs, and the Vercel deployment.
