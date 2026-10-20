# Product Gap Analysis — Vedant Fruit & Vegetables (Customer PWA)

> **Date:** 2026-10-10
> **Source:** `docs/PROJECT_AUDIT.md` + `docs/SUPABASE_AUDIT.md` + live `hyibaljxicojfoxbihdn` inspection + `docs/BACKEND_CONTRACT.md` (2026-10-07 contract on `bmdmgycsrnffcgxmukxm`)
> **Build at analysis time:** `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓` (7 workers). **Runtime:** catalogue is empty against live DB (every `PRODUCT_SELECT` column missing), see §2.

This document answers: *what does the customer think exists, what actually works against the live project, and what must change before launch* — no heuristics, every claim tied to an inspected artifact.

--------------------------------------------------------------------

## 0. Reading this document

* **Status vocabulary:** `IMPLEMENTED + WORKING`, `+ BUGGY`, `+ INCOMPLETE`, `MOCKED`, `PARTIAL`, `MISSING`, `UNKNOWN` — see `docs/PROJECT_AUDIT.md` §4 for the taxonomy.
* **Live project:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`), 3 migrations (`001_core_tables`, `002_view_triggers_rpc`, `003_fix_place_order`), 12 tables + 1 `SECURITY DEFINER` view, 0 rows in every domain table except `categories:8, delivery_slots:3, store_settings:1`. RLS is **off on all 12 tables, 0 policies** (P0, world-readable).
* **Application contract:** `src/types/database.ts` + `src/lib/catalogue-shared.ts:PRODUCT_SELECT` + `src/services/order-service.ts:placeOrder` etc. — written against `bmdmgycsrnffcgxmukxm` with `product_weights`, `offers`, `cart_items`, `order_status_history`, `complaint_messages`, `notifications`.

The **entire** gap below is that the two contracts are different products. The app is high-quality; it is pointed at the wrong database. Every row in §2 is a consequence of that single fact, plus the RLS exposure.

--------------------------------------------------------------------

## 1. Feature map — customer journey

| # | Step | Route / surface | Expected (contract) | Actual live (`hyibalj...`) | Gap class |
|---|---|---|---|---|---|
| 1 | **Home shelf** | `/` — `getCatalogue()` + `getLiveOffers()` + `getCategories()` | Single `PRODUCT_SELECT` → grid, rails, rate board (saving-sorted) | `PRODUCT_SELECT` selects `glyph, accent, base_price, unit_label, availability, quantity_limit, product_weights, offers` — none exist; live `products` is `price/discount_price/stock/max_order_qty/unit` | **IMPLEMENTED + BUGGY** (empty / error) |
| 2 | **Browse aisles** | `/categories`, `/categories/[id]` — `BrowseFilters` (category + offersOnly + inStockOnly + sort) | `parseBrowseParams → getCatalogue → applyBrowseFilters` time-aware offer predicate in memory | `offersOnly`/`inStockOnly` predicate touches nonexistent columns; `getCategoryById` over-selects `glyph/accent` | **IMPLEMENTED + INCOMPLETE** |
| 3 | **Search** | `/search?q=...` — `normaliseSearchTerm` + `ilike name,description` + recent + suggestions | Substring, wildcard-stripped, `SEARCH_SUGGESTIONS` derived from seed | Works on `name/description` but suggestions point at products that do not exist; no result explains the DB mismatch | **IMPLEMENTED + INCOMPLETE** |
| 4 | **Product detail** | `/products/[id]` — weight selector, `priceFor`, related `PRODUCT_SELECT` | Backend weight variants (100g→1kg, grams-sorted) priced via `least(price, discount)` per offer | No `product_weights` table; `defaultWeight`/`priceFor`/`cheapestOption` branch dead; related query columns wrong | **IMPLEMENTED + BUGGY** |
| 5 | **Offers** | `/offers`, `?offers=1` | `getLiveOffers()` `is_active && starts_at/ends_at` window, biggest saving first | No `offers` table; live discounts are inline `discount_price` with no window | **MOCKED** (page renders empty) |
| 6 | **Add to cart** | `ProductCard`/`QtyStepper` → `useCart` (Zustand + `CartProvider` merge) | Guest persisted locally, signed-in mirrored to `cart_items` via read-then-write (partial indexes) | Guest works; any signed-in `persistLine/fetchRemoteCart` errors (`cart_items` missing) | **IMPLEMENTED + BUGGY when signed in** |
| 7 | **Cart** | `/cart` — `CartLineRow`, `OrderSummary` | Qty inc/dec/remove/clear, weight-aware keys, optimistic rollback | Renders guest cart; totals previews use wrong offer model (inline vs time-windowed) | **PARTIAL** |
| 8 | **Checkout** | `/checkout` — `AddressBook` → `SlotPicker` → summary → `place_order` | Stable `requestIdRef`, server is authority, idempotent replay | **Double-broken:** `getDeliverySlots` filters `starts_at` (nonexistent; live is `start_time time`), so no slot id to place with; `placeOrder({p_request_id, p_delivery_slot_id})` 400 vs live `(p_idempotency_key,p_items,p_delivery_date,p_slot_id)` | **IMPLEMENTED + BUGGY** (blocked) |
| 9 | **Orders** | `/orders`, `/orders/[id]` — history, timeline, adjustments, rejected items, reorder | `order_status_history` + `adjustment_*` + `rejected` | Live `orders` is `status PENDING..` (vs `ORDER_RECEIVED..`), no `order_status_history`/`adjustment_*`/`rejected`; reads return wrong shape | **IMPLEMENTED + BUGGY** |
| 10 | **Profile** | `/account` — `getProfileOrDefault` | `username/first_name/last_name/email/phone` | Live `profiles` is `full_name/phone/role/is_active` (`handle_new_user` inserts that shape) | **PARTIAL** |
| 11 | **Addresses** | `/account/addresses` + checkout `AddressBook` | `full_name/phone/house_number/street/landmark/city/state/postal_code 5..10` + lat/long + `one_default_per_user` | Live `addresses` is minimal `label∈{Home,Work,Other} + line1/line2/city/pincode 6-digit` | **PARTIAL** |
| 12 | **Support** | `/support`, `/support/[id]` — `complaint_messages` threads + attachments | `complaint_messages(author_role CUSTOMER|ADMIN)` + `complaint-images` bucket | No `complaint_messages` table; attachments table exists but 0 buckets; `complaints.admin_reply` is inline | **PARTIAL** |
| 13 | **Notifications** | `/notifications` | `notifications` (SELECT/UPDATE owner, no INSERT) | Live is `notification_outbox(audience,type,title,body,data)` populated by triggers; read path hits wrong table | **MOCKED** |
| 14 | **Auth** | `/login`, `/signup`, `/forgot-password`, `/auth/callback`, `proxy.ts` | Email/password + gated Google (probe), email confirm | Auth itself is correct (`@supabase/ssr` + `proxy.ts` headers); Google gate depends on dashboard provider config (probe via `lib/auth-providers.ts`) | **IMPLEMENTED + WORKING** |
| 15 | **Delivery slots** | `getDeliverySlots` + `getSlotLoad` + `SlotPicker` grey-out | `starts_at/ends_at timestamptz, capacity`, horizon 7d, capacity locked server-side | Live `delivery_slots(name,start_time time,end_time time,max_orders,sort_order)` — time not timestamptz; horizon predicate invalid | **PARTIAL** |
| 16 | **Contact/legal/offline** | `/contact`, `/about`, `/privacy`, `/terms`, `/offline` | `config/store.ts` null-safe, SW precache | Independent of catalogue DB — **working even under mismatch** | **IMPLEMENTED + WORKING** |
| 17 | **PWA** | `manifest.ts`, `sw.js` (153 lines, never-cache Supabase), icons, `PwaProvider` | `standalone, start_url:/, maskable`, update-on-demand | Correct and **never caches private data** — essential given RLS-off | **IMPLEMENTED + WORKING** |
| 18 | **SEO** | `layout.tsx` JSON-LD, per-product Product+BreadcrumbList, sitemap/robots/opengraph | GroceryStore structured data | Shell sound; catalogue-driven sitemap empty until catalogue aligned | **IMPLEMENTED + INCOMPLETE** |
| 19 | **Money/pricing** | `lib/money.ts` (integer paise), `lib/pricing.ts` (`pickActiveOffer/priceFor/cheapestOption/previewTotals`) | `least(price, discount)` per time-windowed offers, `pickActiveOffer` = `place_order` predicate `is_active && starts_at/ends_at` `order by created_at desc limit 1` | Preview models a table (`offers`) that does not exist; live is `least(price, discount_price)` inline with no window — drift | **IMPLEMENTED + BUGGY live** |
| 20 | **Analytics** | `events(session_hash,event∈{page_view,search,add_to_cart,place_order})` | Analytic outbox | `events` table exists live and matches check constraint — but no client writes yet and no RLS insert policy documented | **MISSING (wiring)** |
| 21 | **Tests** | Vitest + Playwright installed | Unit + E2E for pricing/money/cart/store | **0 first-party specs** found; no CI/CD | **MISSING** |

--------------------------------------------------------------------

## 2. The failure chain (why everything is empty)

1. `PRODUCT_SELECT` is the single chokepoint: `products` query
   ```
   id, category_id, name, description, glyph, accent, image_url,
   base_price, unit_label, availability, quantity_limit,
   category:categories (id, name, glyph, accent),
   product_weights (…), offers (…)
   ```
   against live `hyibalj...` where `products` is `price/discount_price/stock/unit`, `categories` is `icon/slug`, and `product_weights`/`offers`/`order_status_history`/`notifications`/`complaint_messages` tables do not exist. PostgREST rejects the column list → `CatalogueError` → home/categories/search/product/offers render error/empty branches that are designed as "shelves being restocked" states — correct fallback, wrong cause.

2. `getDeliverySlots(horizonDays=7)` builds `.eq(is_active,true).gt("starts_at", now).lt("starts_at", horizon)`. Live column is `start_time time` (+ `name`), not `starts_at timestamptz`. Filter rejects or returns 0 rows → `SlotPicker` shows no slots → checkout cannot produce a `slot_id` satisfying `place_order`'s `FOR UPDATE … and capacity` guard.

3. `order-service.placeOrder({p_request_id, p_address_id, p_delivery_slot_id})` vs live `place_order(p_address_id, p_delivery_date, p_idempotency_key uuid, p_items jsonb, p_slot_id uuid)` — wrong names, wrong count, wrong shape (`p_items` is required). Pre-flight 400 before any item validation. Even after param fix, the app expects the server to read `cart_items`; live expects the client to send `p_items:[{product_id,qty}]` assembled from the Zustand snapshot.

These three points account for every "MOCKED/BUGGY" row above. The app shell (header, bottom nav, hero composition, legal, offline, PWA, SEO shell) is real and proven.

--------------------------------------------------------------------

## 3. Working features that must be preserved

A `MISSING → IMPLEMENTED` fix must not regress these (all are build-clean and, where domain-independent, live-verified 200):

* **Design tokens** (`src/app/globals.css @theme` warm paper/leaf/marigold/beet, no hex in components) — keep verbatim.
* **Money invariants** (`money.ts` integer paise, `pricing.ts` `least(...)` model) — retarget to `discount_price` inline if Branch B, or preserve if Branch A; either way keep the integer-paise discipline.
* **Cart state** (`stores/cart-store.ts` `cartKey(productId,weightId)` matching partial indexes; `cart-provider` `max` merge on sign-in) — retarget key to live `products(max_order_qty,stock)` if Branch B.
* **PWA** (`public/sw.js` never-cache Supabase/auth) — **keep as-is**; it is what contains the RLS-off exposure window.
* **Proxy/security headers** (`src/proxy.ts` `nosniff/DENY/strict-origin-when-cross-origin/COOP/HSTS`) + `lib/env.ts` `service_role` rejection — keep.
* **Proxy is not auth** — correct comment; keep. Real auth is `auth.ts:getUser()` + RLS (once enabled).
* **Catalogue shared mapper** (`catalogue-shared.ts:toCatalogueProduct` grams-sorted, `pickActiveOffer` `created_at desc`) — keep pattern, update columns.
* **SEO/structured data** (GroceryStore, Product+BreadcrumbList, sitemap/robots) — keep; just reseed.
* **One hero 3D scene** (`produce-scene.tsx` lathe basket + primitive produce, `use-device-capability` DPR/frameloop gating, `HeroComposition`→`HeroVisual` handoff) — keep scoped to hero, not cart/checkout.
* **A11y baseline** (`Skip to content`, `prefers-reduced-motion` kill switch, tabular numerals, focus `leaf-600`, never-blocking zoom) — keep.

--------------------------------------------------------------------

## 4. Gaps to close before launch — ordered

### P0 — must fix or cannot launch

| # | Gap | Evidence | Fix direction (non-destructive, additive) |
|---|---|---|---|
| 1 | **RLS disabled on all 12 public tables, 0 policies (world-readable)** | `list_tables rls_enabled:false` ×12 + `pg_policies:0` + advisor `rls_disabled_in_public ERROR ×12` | `ENABLE ROW LEVEL SECURITY` + least-privilege policies (catalogue public read `is_active`, owned `auth.uid()=user_id` for private tables). See `docs/SUPABASE_AUDIT.md` §8.1 for exact SQL. Revoke `anon/authenticated EXECUTE` on `SECURITY DEFINER` helpers; pin `search_path` on `set_updated_at/gen_order_number`. |
| 2 | **Catalogue reads empty against live** | `PRODUCT_SELECT` columns/tables missing (see §2.1) | Choose a branch **before any migration**: <br>**A — bring DB toward contract** (preserve authored app): add `product_weights`, `offers`, `cart_items` (2 partial unique indexes), `order_status_history`, `complaint_messages`, `notifications` + missing columns (`profiles.username/first_name/last_name`, `products.glyph/accent/base_price…`, `categories.glyph/accent…`, `delivery_slots.starts_at/ends_at/capacity`). Wrap/alias `place_order` to legacy `p_request_id` shape or version as `place_order_v2`. <br>**B — adapt app toward live** (smaller migration, rewrite types): regen `src/types/database.ts` from live, rewrite `PRODUCT_SELECT`, `pricing.ts` view of offers, `SlotPicker/cart-service/order-service/lib/catalogue.ts` to live `price/discount_price` + `p_items/delivery_date/slot_id/idempotency_key`. Map `delivery_slots(name,start_time,end_time)` → concrete `timestamptz` windows for picker respecting `today|tomorrow` guard. <br>For this workspace (`.env.local → hyibalj...`, 0 customer rows) **Branch B is lower risk** for fastest ship; recorded `supabase/migrations/*_*.sql` (no `DROP production`). |
| 3 | **Checkout is double-broken** | `getDeliverySlots` `starts_at` vs live `start_time time`; `placeOrder` param mismatch; `p_items` missing | As part of #2: slot bridge (`name + time → timestamptz` windows), error-contract alignment (`UNAUTHENTICATED/INVALID_DATE…` vs app `42501/22023` substrings), client builds `p_items:[{product_id,qty}]` from Zustand lines + `p_delivery_date = current_date\|+1` + `p_idempotency_key uuid`. |
| 4 | **No storage buckets** | `storage.buckets:0` (expected `complaint-images` + catalogue bucket) | Create `complaint-images` (private, owner-only RLS matching `complaint_attachments`), ensure allowlist in `next.config.ts:images.remotePatterns` stays host-derived. |
| 5 | **Project identity ambiguous** | `.env.local → hyibalj...` (3 migrations) vs contract `bmdmgycsrnffcgxmukxm` (8 migrations) | Lock decision before deploy; re-point `.env.local/NEXT_PUBLIC_SUPABASE_URL` and `project_ref` in `.mcp.json` to the chosen project and freeze. |

### P1 — must fix before handling real traffic

| # | Gap | Detail |
|---|---|---|
| 6 | **Profile/addresses/support column mismatch** | `profiles/addresses/complaints` types assume multi-column address and `complaint_messages`; live is minimal `addresses(label,line1,line2,city,pincode)` + `profiles(full_name,role)` + `complaints.admin_reply inline`. Remap forms and services to one or the other. |
| 7 | **Delivery-slot recurrence** | Seed slots expire; no recurring generation (noted as BLOCKED in `TASK_STATE.md`). Add a `supabase/migrations/*_delivery_slot_recurrence.sql` (cron/`pg_cron`) after branch choice. |
| 8 | **Pricing preview drift** | `pricing.ts` models `offers` (time-windowed) vs live inline `discount_price` (no window). Reconcile so `priceFor`/`cheapestOption`/`previewTotals` match whatever `place_order` actually computes. |
| 9 | **Analytics wiring** | `events(session_hash,event,path,meta)` exists but no client write yet; add insert policy + flush (respect privacy, no PII in `meta`). |
| 10 | **Tests** | `vitest`/`playwright` installed, **0 first-party specs**. Priority: `money.ts` paise rounding + `pricing.ts:priceFor` parity + `cart-store` reducers + `catalogue-shared` mapper, then a smoke `search → add → cart → checkout` (auth-guarded) E2E. |
| 11 | **Indexes/trust** | Linter flags `unindexed FKs` `complaints(order_id)` + `notification_outbox(user_id)` and `security_definer_view product_catalog` — fix in the same RLS migration. |

### P2 — needed for the "premium grocery PWA" bar

| # | Gap | Detail |
|---|---|---|
| 12 | **Catalogue imagery** | No real photography via `storage.buckets`; `ProductImage` glyph plinth is correct fallback but real imagery optimised as AVIF/WebP via `next/image` allowlist must be sourced before marketing. |
| 13 | **Store contact** | `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` unset (persistently `needsOwnerConfiguration` per `config/store.ts`); Call/WhatsApp deep links hidden by design — owner must supply. |
| 14 | **Admin** | `private.admin_users` empty in contract sense; live has `profiles.role enum` + `admin@freshmart.local → admin` in `handle_new_user` but no dashboard. `FUTURE_ADMIN_APP.md` / `DATABASE_ARCHITECTURE.md` must stay consistent with chosen branch. |
| 15 | **PWA installability** | Managed to pass RLS hardening before real install; on-device verify: icons maskable + `theme_color`, offline shell, update prompt before fix is pointless — defer to post-remediation smoke. |

--------------------------------------------------------------------

## 5. Acceptance criteria for "gap closed"

* [`] `ENABLE RLS` on all 12 tables + policies proven: signed-out anon cannot select/update private tables; `categories/products/delivery_slots` public reads succeed when `is_active`; owned data round-trips.
* [`] Catalogue reads against live: `getCatalogue`/`getProductById`/`getRelatedProducts`/`getCategories`/`getLiveOffers` return real rows with images (or glyph fallbacks) — proven by `GET /`, `/categories`, `/search`, `/products/[id]`, `/offers` showing populated grids not empty states.
* [`] `place_order` end-to-end: guest cart + authenticated cart, slot capacity + `today|tomorrow` guard, `min_order_amount`, stock/max-order checks, idempotent replay via stable `p_idempotency_key` → `string order_id` persisted and visible in `/orders/[id]` timeline.
* [`] Complaint attachment through storage bucket + success UX.
* [`] `tsc --noEmit ✓` + `eslint ✓` + `pnpm build 22/22 ✓` + headless `curl` smoke on all routes + on-device PWA install + Lighthouse pass on paper/leaf palette (contrast, tabular prices, motion-reduce).

--------------------------------------------------------------------

## 6. Open questions (not blockers, but record now)

* **Seed catalogue source:** who supplies real photography and pack sizes? `product_weights.grams` vs live `products.unit+max_order_qty` — the owner must pick the unit model.
* **Offers model:** time-windowed `offers` table (authored) vs inline `discount_price` (live) — one of them must be chosen; the other removed from docs so `pickActiveOffer` never drifts again.
* **Delivery-slot timezone:** live `delivery_slots` is `time` without `timestamptz`; the picker currently assumes `timestamptz` — the bridge must fix `Asia/Kolkata` vs UTC before `today|tomorrow` comparisons mean anything.

Every feature in §1 was classified from an inspected file or a live query, not a heuristic — where evidence was absent the entry is marked `PARTIAL`/`MOCKED` and the missing source is named. No error was suppressed to obtain a green build.
