# Remediation Summary — 2026-10-17

> Supabase project `hyibaljxicojfoxbihdn` · Next.js 16 customer PWA · Kotlin admin/delivery app
> Team: `software-vedant-platform` (齐活林 lead · 高见远 architect · 寇豆码 engineer · 严过关 QA)

## Advisor findings: 92 → 39

| | BEFORE | FINAL |
|---|---|---|
| Security | 26 | 20 |
| Performance | 66 | 19 |
| **Total** | **92** | **39** |

Security: `anon_security_definer_function_executable` **7 → 1**, `authenticated_…` **18 → 17**,
`auth_leaked_password_protection` 1 → 1 (ACCEPTED — Pro-plan only, HTTP 402).
New: `rls_enabled_no_policy` 1 = `public.events` (benign: table has 0 rows and no writer).

Performance: `auth_rls_initplan` **18 → 0**, `multiple_permissive_policies` **26 → 0**,
`unindexed_foreign_keys` **5 → 0**, `unused_index` 17 → 19 (new FK indexes on near-empty
tables; every one reviewed individually, none blindly dropped).

Evidence: `docs/supabase-lint/advisor-BEFORE-*.json` → `advisor-FINAL-*.json`.

## Migrations (all 8 in `supabase_migrations.schema_migrations`)

| # | File | What it did |
|---|---|---|
| 001 | `p0_security_hardening` | Dropped `events_insert_any` (`WITH CHECK (true)`) and `product_images_public_read` (bucket listing); revoked `EXECUTE` on 7 trigger-only `SECURITY DEFINER` fns + `private.*` helpers |
| 002 | `roles_and_account_status` | `staff_role` enum + `private.user_roles`; 12 `can_*` capability helpers; `owner_grant_role`/`owner_revoke_role` with last-OWNER guard; `account_status` + `account_status_history` + `audit_logs`; status gate in `place_order_app`; retired 3 superseded roster RPCs |
| 003 | `rls_restructure` | Rewrote all 18 initplan policies to `(select auth.uid())`; merged/split the 26 permissive overlaps; **dropped `orders_update_own`** (a customer could rewrite their own order's `status` and `total`) and `profiles_admin_all` |
| 004 | `indexes_and_reporting` | 5 FK covering indexes; 3 dead index drops; `admin_dashboard_counts`, `admin_sales_today`, `admin_search_users` |
| 005 | `definer_hardening` | Every `public`/`private` `SECURITY DEFINER` fn pinned `search_path=''`; `check_username_available` input-validated + boolean-only |
| 006 | `close_order_insert` | Dropped `orders_insert_own` + `order_items_insert` — a customer could mint an order with an arbitrary `total` |
| 007 | `serviceability_and_close_item_mutation` | `public.service_areas` (Pune/Maharashtra/`411`) + serviceability guard inside `place_order_app`; dropped `order_items_update`/`order_items_delete` |
| 008 | `delivery_assignment_and_rpc_gates` | `orders.assigned_to` + `delivery_orders()` + `admin_assign_delivery()`; **re-gated 5 RPCs** off the coarse `is_admin()` shim so SUPPORT can no longer change order status, reject items, or view slot load |

## Real defects found by verification (none were on the original 98)

1. **Customer could rewrite their own order's `status` and `total`** (`orders_update_own`) — closed.
2. **Customer could mint an order with `total = 0.01`** via direct table INSERT — closed, proven both ways in one transaction.
3. **Customer could alter/delete line items of a placed order** — closed.
4. **SUPPORT could change order status / reject items / view slot load** — the design's matrix forbids it; closed.
5. **`orders.created_at` does not exist** (it is `placed_at`) — referenced at 8 call sites, so `/orders` rendered "No orders yet" for *every* customer and Buy Again never rendered. Fixed.
6. **`/orders/[id]` 404'd for every customer** — `delivery_slot:delivery_slots(*)` is ambiguous because `orders` has two FKs to `delivery_slots`; PostgREST returns `PGRST201`. Fixed with an explicit FK hint.
7. **`admin_sales_today.refunds` was structurally always 0** (CTE excluded CANCELLED then summed them) and **`delivered_today` keyed on `placed_at`** — both fixed.
8. **The architect's own design would have caused `42P17 infinite recursion`** — a self-referential subquery on `profiles` inside a policy on `profiles`, made fatal by the InitPlan form. Caught by the engineer before it shipped; fixed via `private.current_profile_role()`.

## Website

The storefront was already well-built (responsive grid, search, cart, checkout, selective
glassmorphism) — a rewrite would have destroyed verified work. The genuine gaps were built:

- **Buy Again** — sourced from the customer's own order history, **re-priced against today's
  catalogue** (verified: historical ₹20/₹40 → displayed ₹149.40/₹61.20), add-one and add-all
  with `quantity_limit` clamping.
- **Delivery location + Pune service area** — `config/service-area.ts` (env-overridable, so a
  second city is a config change), Server Actions that re-read the address server-side, and a
  DB-level guard so a hand-crafted RPC call cannot bypass it.
- Address prefill (Maharashtra / Pune). The brief's claimed address-creation bug was
  **investigated and found not to exist** — proven by POSTing the exact payload as `anon` and
  getting `401 / 42501` (a schema fault would have returned PGRST204/23502/23514).

## Android

Baseline verified green (425 unit tests, `assembleDebug` successful). Added:

- **Explicit connection states (§52)** — `ConnectionState` (Online/Offline/Syncing/Synced/Error)
  and `ConnectionMonitor` that folds real request outcomes into connectivity, so **a backend
  failure no longer reads as "offline"**. `ConnectionBanner` gives each state honest copy.
- **Delivery assignment (DB side)** — `orders.assigned_to`, `delivery_orders()` returning
  assigned orders with customer name/phone/address, `admin_assign_delivery()`. Verified live:
  DELIVERY sees its assignment with contact; SUPPORT is refused `42501`.

**Still to do on Android:** welcome screen (§48), square 2-column grid (§49), animations (§50),
the DELIVERY UI screen + CALL CUSTOMER button (§47/§55), multi-staff creation UI (§54).

## Verification

24 adversarial probes (QA, independent) all passed: cross-account isolation, privilege
escalation blocked, anon cannot read private tables or insert `events`, storage hardened,
suspended customers cannot order, money is server-authoritative.

## Outstanding — must be resolved before launch

1. **The two fixture accounts carry a known test password**, and `owner@vedant.local` is an
   **ADMIN** in `private.user_roles`. This is a live-database credential exposure. Remove those
   accounts or reset them to strong random passwords, then confirm the app still works with the
   real owner account (`khansarfaraj.khan@gmail.com`).
2. **Two test addresses** exist on `customer@vedant.local` (Pune 411001 + Mumbai 400001).
3. **Rotate the Supabase personal access token** used for this work.
4. Confirm the OWNER/ADMIN split is correct (currently OWNER = `khansarfaraj.khan@gmail.com`).
