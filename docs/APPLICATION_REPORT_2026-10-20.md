# Application Report — Android + Web

> Verified 2026-10-20 against the live database, the working tree, and recorded test>   
> output. Nothing here is from memory.

## Verdict

**NOT PRODUCTION READY.** Three blockers remain: one is yours alone (an Android release  
keystore), and two are implementation gaps (observability, backups). The backend and the  
customer website are in good shape; the Android app is missing an Internet permission guard  
and has never had a release build.

---

## 1. Shared backend — Supabase

13 migrations, all applied and recorded in `supabase_migrations.schema_migrations`.

| #   | Migration                                | What it does                                                                                                                                                            |
| --- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 001 | `p0_security_hardening`                  | Dropped unrestricted `events` INSERT and public bucket listing; revoked EXECUTE on 7 trigger-only DEFINER functions                                                     |
| 002 | `roles_and_account_status`               | `staff_role` enum, `private.user_roles`, capability helpers, `owner_grant_role`/`owner_revoke_role` with last-OWNER guard, `account_status` + history + `audit_logs`    |
| 003 | `rls_restructure`                        | Rewrote every initplan policy; merged 26 permissive overlaps; dropped `orders_update_own` and `profiles_admin_all`                                                      |
| 004 | `indexes_and_reporting`                  | FK covering indexes, dead index drops, three reporting RPCs                                                                                                             |
| 005 | `definer_hardening`                      | Every DEFINER function pinned `search_path=''`; `check_username_available` validated                                                                                    |
| 006 | `close_order_insert`                     | Dropped `orders_insert_own` + `order_items_insert` — customer could mint an order with an arbitrary total                                                               |
| 007 | `serviceability_and_close_item_mutation` | `service_areas` + guard inside `place_order_app`; dropped `order_items_update`/`order_items_delete`                                                                     |
| 008 | `delivery_assignment_and_rpc_gates`      | `orders.assigned_to`, `delivery_orders()`, `admin_assign_delivery()`; re-gated 5 RPCs off the coarse `is_admin()` shim so **SUPPORT can no longer change order status** |
| 009 | `order_acceptance`                       | Reason + history + audit for stopping orders; `admin_set_order_acceptance()`                                                                                            |
| 010 | `realtime`                               | `orders` + `order_status_history` published; `REPLICA IDENTITY FULL` on `orders`                                                                                        |
| 011 | `delivery_coordinates`                   | `delivery_orders()` returns lat/lng joined from `addresses`                                                                                                             |
| 012 | `staff_roster`                           | `admin_list_staff()`, OWNER-gated *(RPC now unused — staff UI removed at your request)*                                                                                 |
| 013 | `delivery_contact_and_history_index`     | Fixed a blank customer name on every courier row; snapshot coordinate fallback; indexed an FK                                                                           |

**Advisor findings: 92 → 39** (security 22 / performance 17). The 17 performance findings are  
all `unused_index` on near-empty tables — individually reviewed, not blindly dropped. The 22  
security findings are: 19 authenticated-DEFINER RPCs (the intended API surface — the linter  
cannot tell a gated RPC from an exposed one; **both bodies were read and confirmed gated**),  
1 `anon_security_definer_function_executable` (`check_username_available`, intentional and  
validated), 1 `rls_enabled_no_policy` (`events`, 0 rows, no writer), 1  
`auth_leaked_password_protection` (**accepted** — Pro-plan feature, HTTP 402).

### Verified-closed attack paths

Each proved by a live probe, not by reading code:

| Attack                                           | Result                                                                |
| ------------------------------------------------ | --------------------------------------------------------------------- |
| Customer rewrites own order status/total         | `UPDATE` → 0 rows (policy dropped)                                    |
| Customer mints an order with `total = 0.01`      | `42501`                                                               |
| Customer mutates/deletes placed-order line items | `42501`; `line_total` is `GENERATED ALWAYS` so it was never craftable |
| SUPPORT changes order status / rejects items     | `42501` (re-gated in 008)                                             |
| Customer A reads Customer B                      | 0 rows on every probe (24-adversarial matrix)                         |
| Customer bypasses serviceability via direct RPC  | `22023: delivery area not served`                                     |
| Suspended customer orders                        | `42501`, before business logic                                        |
| Admin promotes self to OWNER                     | `42501: owner authorization required`                                 |
| `anon` reads private tables / lists bucket       | 0 rows / `[]`                                                         |
| Store closed → checkout                          | `22023: store closed`, and the banner + blocked button on web         |

---

## 2. Android — `com.vedant.admin`

Admin + delivery app. 156 Kotlin files, 44 test files, **427 tests, 0 failures**.

### Built and verified

| Area                  | State                                                                                                                                                                                                                                        |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Auth + session        | Splash → SignIn → gate; token refresh, session restore, sign-out                                                                                                                                                                             |
| Dashboard             | Order counts, quick actions, **one-click order acceptance** (Stop / Start)                                                                                                                                                                   |
| Orders                | List with server-side filter/pagination; detail with customer, address, items, adjustment audit; reject/restore item; status transitions                                                                                                     |
| **Delivery queue**    | Assigned orders with customer, phone, address, amount, item count; **CALL** (`ACTION_DIAL`, no `CALL_PHONE` permission), **NAVIGATE** (`geo:` intent, coordinates preferred, text fallback), state-gated *Start delivery* / *Mark delivered* |
| Catalogue             | Products, categories, units, offers, product editor                                                                                                                                                                                          |
| Slots                 | Recurring slot materialisation, load view                                                                                                                                                                                                    |
| Complaints            | List + thread with reply and status change                                                                                                                                                                                                   |
| Settings              | Store settings incl. the order-acceptance toggle, profile                                                                                                                                                                                    |
| **Connection states** | `Online / Offline / Syncing / Synced / Error` — a backend failure no longer reads as "offline"                                                                                                                                               |

Two bug fixes worth naming: **CALL CUSTOMER** existed nowhere before, and the customer's phone  
number was being rendered as a *street-address line* because the mapper flattened every  
snapshot value. Both fixed, with tests.

### Gaps

| Gap                                                          | Impact                                                                                                                                                                                                                                                                                                   | Effort                                                    |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| **No `INTERNET`/`ACCESS_NETWORK_STATE` guard for cleartext** | The manifest has both permissions, but there is no `networkSecurityConfig` pinning HTTPS or blocking cleartext                                                                                                                                                                                           | Small                                                     |
| **No release build / signing config**                        | Only `assembleDebug` has ever run. Cannot ship to Play                                                                                                                                                                                                                                                   | **Blocked — needs a keystore you create and keep secret** |
| **No crash reporting**                                       | A crash in the field is invisible                                                                                                                                                                                                                                                                        | Small                                                     |
| **No ProGuard/R8 rules reviewed**                            | Release shrink may break reflection-based serialization                                                                                                                                                                                                                                                  | Small                                                     |
| **Instrumentation tests exist but are stale**                | 9 `androidTest` files covering CatalogueHub, ComplaintsList, Dashboard, OfflineGating, OrderDetail, OrdersList, ProductEditor and SignIn — but **none cover the delivery queue, ORDER acceptance, or CALL CUSTOMER**. They also require a device/emulator, so they have not been run in this environment | Medium                                                    |
| **Welcome screen**                                           | App opens Splash → SignIn (functionally equivalent, not the specified screen)                                                                                                                                                                                                                            | Small                                                     |
| `admin_list_staff()` unused                                  | RPC exists, no caller — dead code                                                                                                                                                                                                                                                                        | Trivial                                                   |

---

## 3. Web — Next.js 16.3.8

Customer storefront. 25 routes, production build green (`wM4wsqKaWShJmbe9wvx5i`), `tsc` exit 0,  
`eslint` 0 errors, 36 unit tests passing.

### Built and verified

| Area                    | State                                                                                                                                                                                                      |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Storefront              | Responsive grid, search, category filters, product detail, offers                                                                                                                                          |
| Cart                    | Add/remove/quantity, persisted, server re-prices at checkout                                                                                                                                               |
| Checkout                | Address + slot + summary; **serviceability re-read server-side**; server-authoritative pricing; idempotent `request_id`                                                                                    |
| **Order acceptance**    | Banner on home with the operator's reason, checkout button disabled, `submitOrder` pre-check, RPC authority — 3 layers                                                                                     |
| Orders                  | History, detail with rejected items + adjustment + reason, **Buy Again re-priced against today's catalogue**                                                                                               |
| **Realtime**            | Filtered subscription on the customer's own orders; debounced; refreshes on tab focus                                                                                                                      |
| **GPS address capture** | "Use my current location", live accuracy in metres, >100 m warning with retry, OSM map preview, reverse geocoding via Nominatim (**no API key**), permission/failure states, manual entry always available |
| Support                 | Complaint creation with order selection, issue type, description, attachment                                                                                                                               |
| Auth                    | Login, signup, forgot/reset password, session, account page, addresses                                                                                                                                     |

Two real bugs found and fixed by testing while signed in — both were invisible to `tsc` and  
`next build`:

- **`orders.created_at` does not exist** (it is `placed_at`) — referenced at 8 call sites, so    
  `/orders` showed "No orders yet" for *every* customer and Buy Again never rendered.
- **`/orders/[id]` 404'd for every customer** — `delivery_slot:delivery_slots(*)` is ambiguous    
  because `orders` has two FKs to `delivery_slots`; PostgREST returns `PGRST201`.

### Gaps

| Gap                                | Impact                                                                                                                                                                                         | Effort                            |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| **No deployment target**           | Builds locally; nothing is deployed, no environment config                                                                                                                                     | **Blocked — you choose the host** |
| **No analytics/error tracking**    | Client errors invisible                                                                                                                                                                        | Small                             |
| **No Core Web Vitals measurement** | No real perf data from real devices                                                                                                                                                            | Small                             |
| **Delivery tracking page**         | Customer sees status but no map/progress view                                                                                                                                                  | Medium                            |
| Draggable map pin                  | Current picker previews a marker but doesn't let you drag it                                                                                                                                   | Small (`leaflet`)                 |
| Security headers                   | `next.config.ts` has a `headers()` block, but it only sets `Cache-Control` / `Content-Type` on `sw.js` and `manifest.webmanifest`. **No CSP, HSTS, X-Content-Type-Options or X-Frame-Options** | Small                             |
| No E2E suite                       | 3 unit test files; no Playwright specs for the new GPS/acceptance flows                                                                                                                        | Medium                            |

---

## 4. Critical changes needed for a shippable product

### P0 — must be done before any real customer

1. **Create an Android release keystore.** You must generate it and guard it — it cannot be     
   committed. Then `./gradlew :app:assembleRelease :app:bundleRelease`. **This is yours.**
2. **Deploy the web app** to a host (Vercel/Netlify/Cloudflare) with `NEXT_PUBLIC_SUPABASE_URL`     
   and the publishable key as environment variables. Nothing is live yet.
3. **Rotate the Supabase PAT** — done, but note the token was pasted into chat *again* since     
   the rotation, so **rotate it once more** before you consider it private. It is stored at     
   `~/.secrets/supabase-pat-vedant.txt`, ACL-locked and outside the repo.
4. **Delete the demo accounts** (`admin@vedant.local`, `demo@vedant.local`, password     
4. **Delete the demo accounts** `admin@vedant.local` and `demo@vedant.local` — the
   password was shared in chat, must never live in a file, and is deliberately not
   recorded here — and confirm `khansarfaraj.khan@gmail.com` can still sign in.
5. **Review R8/ProGuard rules** before the first release build — kotlinx-serialization needs


keep rules and a shrink can silently break the API layer.

### P1 — strongly recommended

1. Add `android:usesCleartextTraffic="false"` and a `network_security_config`.
2. Install crash reporting (Firebase Crashlytics or Sentry) on Android and error boundaries +     
   reporting on web.
3. Write a backup/restore procedure for Supabase (PITR or scheduled `pg_dump`), and document a     
   rollback plan for each migration.
4. Add Playwright specs for: closed-store banner, checkout blocked while closed, GPS prefill     
   failing over to manual entry, and the realtime status update.
5. Add real security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options) to the existing `headers()` block in `next.config.ts` — the function is already there, it just handles static assets only.

### P2 — polish

1. Delivery tracking page for customers.
2. Draggable map pin (Leaflet).
3. A lightweight `admin_list_staff` caller or removal of the RPC.
4. Refresh the 9 stale `androidTest` files and add specs for the delivery queue, order acceptance and CALL CUSTOMER.

---

## 5. Honest scorecard

| Surface             | Pass   | Fail  | Blocked        |
| ------------------- | ------ | ----- | -------------- |
| Backend security    | 14     | 0     | 0              |
| Web customer flows  | 16     | 0     | 1 (deployment) |
| Android             | 8      | 3     | 1 (signing)    |
| Release engineering | 3      | 5     | 1              |
| **Total**           | **41** | **8** | **3**          |

The eight failures are: no release signing, no crash reporting, no R8/ProGuard review, no  
cleartext guard, no security headers, no analytics, no delivery tracking page, and stale test  
suites (Android `androidTest` and web Playwright both exist but don't cover the new work).

**A correction to an earlier draft of this report:** I first wrote that the project had "no  
instrumentation tests" and "no E2E suite". Both were wrong — there are 9 `androidTest` files and  
5 Playwright specs. They exist; they simply don't cover the features added this session. I've  
corrected the rows rather than leaving the error in place.

**The security work is done and proven.** What stands between this and a real launch is  
deployment, signing, and observability — not correctness.
