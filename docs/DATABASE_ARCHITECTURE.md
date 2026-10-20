# Database Architecture — Vedant Fruit & Vegetables

> **Date:** 2026-10-10
> **Live project:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`) — 3 migrations (`001_core_tables`, `002_view_triggers_rpc`, `003_fix_place_order`)
> **App contract:** `src/types/database.ts` + `src/lib/catalogue-shared.ts:PRODUCT_SELECT` + `docs/BACKEND_CONTRACT.md` (2026-10-07 contract on `bmdmgycsrnffcgxmukxm`)
> **Build:** `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓`

## 0. Principle

The customer PWA is **customer-only** — no service-role key ships to the browser, authorization is Postgres RLS + `SECURITY DEFINER` RPCs. `lib/env.ts` rejects `sb_secret_*` / `role=service_role`. `proxy.ts` is not auth. Every table is RLS-gated (once hardened — see §7).

Live `hyibalj...` is diverged from the app contract (see `docs/SUPABASE_AUDIT.md`): `product_weights`, `offers`, `cart_items`, `order_status_history`, `complaint_messages`, `notifications` missing; `profiles/products/categories/delivery_slots/addresses/orders/order_items/complaints` columns differ; `product_catalog SECURITY DEFINER` view exists; `storage.buckets:0`; RLS **off on all 12 tables, 0 policies** (P0). This document describes the **target architecture the existing app was written against** and how `hyibalj...` is reconciled to it — forward additive migrations only, no `DROP production`.

## 1. Entity map

```
auth.users ──1:1── profiles ──1:n── addresses
                      │  1:n── orders ──1:n── order_items ──n:1── products
                      │         │  1:n── order_status_history
                      │         │  1:n── complaints ──1:n── complaint_messages
                      │         │                      └─1:n── complaint_attachments
                      │         └─notification_outbox / notifications
                      └─cart_items ──n:1── products (+ product_weights)

categories ──1:n── products ──1:n── product_weights
                          └─1:n── offers
delivery_slots ──1:n── orders
store_settings (singleton id=1)
events (analytics, no FK to profiles)
```

## 2. Tables — target (app contract)

### `profiles` — `id uuid PK → auth.users(id)`
`username text UNIQUE CHECK ^[a-z0-9_]{3,24}$`, `first_name text ≤60`, `last_name text ≤60`, `email text nullable`, `phone text nullable`, timestamps. No trigger creates this row at signup in the original contract; app calls `ProfileSync` on first authenticated visit. Live `hyibalj...` variant is `full_name/phone/role/is_active` via `handle_new_user()` — reconcile by adding missing columns or migrating app types to `full_name` (Branch choice in `docs/PRODUCT_GAP_ANALYSIS.md`).

### `categories` — `id uuid PK`
`name text UNIQUE 1..100`, `glyph text` (emoji glyph, rendered large as plinth), `accent bigint` (decimal RGB int for 4–8% tint, never text color), `image_url text nullable`, `sort_order int`, `is_active bool`. Live `hyibalj...` has `icon/slug` not `glyph/accent/image_url` — add columns forward.

### `products` — `id uuid PK → categories(id) RESTRICT`
`name 1..200`, `description`, `glyph`, `accent`, `image_url nullable`, `base_price numeric(12,2) ≥0`, `unit_label text (kg|dozen|bunch|pc…)`, `availability bool` (in-stock gate), `quantity_limit int >0`. Live has `slug/price/discount_price/stock/max_order_qty/unit/is_active/has_offer` — add `glyph/accent/base_price/unit_label/availability/quantity_limit` or adapt app to live shape.

### `product_weights` — `id uuid PK → products(id) CASCADE, UNIQUE(product_id, grams)`
`label 1..40`, `grams int >0`, `price numeric ≥0`, `is_available bool`. Backend-supplied variants (100g→1kg, grams-sorted in `toCatalogueProduct`). **Missing live** — create.

### `offers` — `id uuid PK → products(id) CASCADE`
`title 1..160`, `price numeric nullable`, `discount_percent 0..100 nullable`, `starts_at timestamptz nullable`, `ends_at nullable`, `is_active bool`, `CHECK (price IS NOT NULL OR discount_percent IS NOT NULL)`, `CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)`. Live when `is_active AND (starts_at IS NULL OR ≤now()) AND (ends_at IS NULL OR >now())` — `pickActiveOffer` = `order by created_at desc limit 1` matching `place_order`. **Missing live** — create. Live inline `discount_price` is not time-windowed; choose one model.

### `delivery_slots` — `id uuid PK`
Target: `starts_at timestamptz`, `ends_at >starts_at`, `capacity int >0`, `is_active bool`. Live: `name text, start_time time, end_time time, max_orders int, sort_order`. Bridge `name+time → timestamptz` for `today|tomorrow` predicate.

### `addresses` — `id uuid PK, user_id uuid NOT NULL DEFAULT auth.uid()`
`label 1..40`, `full_name 1..120`, `phone 10..15`, `house_number/street/landmark` (default ''), `city`, `state`, `postal_code CHECK ^[0-9]{5,10}$`, `latitude -90..90`, `longitude -180..180`, `is_default bool`. Partial unique `one_default_per_user WHERE is_default` → clear-then-set. Live is minimal `label∈{Home,Work,Other}, line1/line2/city/pincode 6-digit` — expand forward.

### `cart_items` — `id uuid PK, user_id DEFAULT auth.uid(), product_id → products CASCADE, weight_id → product_weights nullable, quantity 1..100`
Partial uniques: `(user_id,product_id) WHERE weight_id IS NULL` and `(user_id,product_id,weight_id) WHERE weight_id IS NOT NULL` — key is `cartKey(product,weight)`. PostgREST `upsert onConflict` cannot target partial indexes → app does read-then-write tolerating `23505`. **Missing live** — create.

### `orders` — `id uuid PK, user_id DEFAULT auth.uid(), request_id text 8..128 UNIQUE(user_id,request_id), address_id → addresses SET NULL, delivery_slot_id → slots SET NULL`
`status ORDER_RECEIVED|OWNER_ACCEPTED|PREPARING|READY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED default ORDER_RECEIVED`, `original_total ≥0`, `delivery_charge ≥0`, `adjustment_amount ≤0`, `final_total = original_total + adjustment_amount` (checked), `adjusted_by/adjusted_at` paired. Live: `order_number unique, delivery_date date, slot_id, address_snapshot jsonb, subtotal/delivery_fee/total, idempotency_key uuid unique, status PENDING…` and `place_order(p_delivery_date,p_idempotency_key,p_items,p_slot_id) returns jsonb` — reconcile via wrapper or Branch B.

### `order_items` — `id uuid PK → orders CASCADE, product_id → products SET NULL`
`product_name` snapshot, `weight_label`, `quantity 1..100`, `unit_price ≥0`, `line_total = unit_price*quantity`, `rejected bool`, `rejection_reason` (`CHECK NOT rejected OR reason IS NOT NULL`). Live has `unit/line_total generated(unit_price*qty)` without weight/rejected.

### `order_status_history` — `id uuid PK → orders CASCADE`
`old_status nullable`, `new_status` (same 7), `changed_by default auth.uid()::text`, `reason nullable`. **Missing live** — create.

### `complaints` — `id uuid PK, user_id DEFAULT auth.uid(), order_id → orders SET NULL`
`subject 1..160`, `description 1..10000`, `status OPEN|IN_REVIEW|RESOLVED|CLOSED default OPEN`, `resolution/resolved_by/resolved_at nullable`. Live similar but `type complaint_type enum` + `status OPEN/IN_PROGRESS/RESOLVED/CLOSED` + `admin_reply inline`.

### `complaint_messages` — `id uuid PK → complaints CASCADE, author_id, author_role CUSTOMER|ADMIN, body text`
**Missing live** — create (live has `admin_reply` inline, not threaded).

### `complaint_attachments` — `id uuid PK → complaints CASCADE, storage_path text, media_type image/jpeg|png|webp`
Live exists with `storage_path/content_type image/%/size <5MiB` — align `media_type` naming. Bucket `complaint-images` **missing live (0 buckets)** — create private.

### `notifications` / `notification_outbox`
App contract: `notifications(user_id,title,body,read_at)` — SELECT/UPDATE owner, no INSERT (admin/backend only). Live: `notification_outbox(audience customer|admin, user_id nullable, type, title, body, data jsonb, read_at)` populated by `notify_order_event`/`notify_complaint_event` triggers. Wire read path to `notification_outbox` or add view.

### `store_settings` — singleton `id=1 CHECK id=1`
`store_name`, `phone`, `whatsapp`, `delivery_fee 20`, `free_delivery_threshold 499`, `min_order_amount 100`, `is_open bool`. Live matches.

### `events` — `id bigint identity, session_hash 8..64, event page_view|search|add_to_cart|place_order, path ≤300, meta jsonb, created_at`
Live matches check `event IN (...)`. No FK to profiles; privacy: no PII in `meta`.

### `product_catalog` VIEW
Authored as convenience join; live is `SECURITY DEFINER` (flagged). Target is `INVOKER` or documented definer with RLS.

## 3. Keys, indexes, constraints

* **PKs:** all tables `id uuid` (or `bigint identity` for append-only `events/outbox/history`) — `gen_random_uuid()` default.
* **Uniques:** `profiles.username`, `categories.name`, `product_weights(product_id,grams)`, `orders(user_id,request_id)` / live `orders.idempotency_key` + `order_number`, `addresses one_default_per_user WHERE is_default`, `cart_items` two partial uniques above, `store_settings id=1`.
* **FKs:** `products.category_id RESTRICT`, `product_weights/offers → products CASCADE`, `cart_items → products/weights`, `orders → addresses/slots SET NULL`, `order_items/complaints → orders CASCADE/SET NULL`, `complaint_* → complaints CASCADE`. Missing covering indexes flagged live: `complaints.order_id`, `notification_outbox.user_id` — add.
* **Checks:** `numeric(12,2)` for money (exact, never float), `CHECK (final_total = original_total + adjustment_amount)`, `CHECK (NOT rejected OR rejection_reason IS NOT NULL)`, `line_total = unit_price*quantity`, phone `10..15`, postal `5..10` (live 6-digit), `quantity_limit >0`, `capacity/max_orders >0`, `availability/is_active` booleans.
* **Performance:** `unused_index` ×14 live is expected on greenfield; keep `products_category_idx`, `orders_status/user/slot_date/pincode`, `order_items_order/product`, `complaints_user/status`, `events_created/event`, `outbox_unread`.

## 4. Transactions & order integrity

* `place_order` is the **only** customer write path for orders (`INSERT` policy on `orders/order_items/history` is none for customer — verified `docs/BACKEND_CONTRACT.md` §4). Live `place_order` validates `auth.uid()`, `idempotency_key`, `is_open`, `delivery_date ∈ {today,tomorrow}`, address ownership, slot `FOR UPDATE` + `count(*) WHERE status<>'CANCELLED' < max_orders`, item loop `FOR UPDATE product WHERE is_active`, `qty 1..50`, `max_order_qty`, `stock`, `least(price,coalesce(discount_price,price))`, `min_order_amount`, delivery fee `subtotal≥free_threshold?0:fee`, then `INSERT orders + order_items + stock decrement` all-or-nothing, `on conflict idempotency_key do nothing` replay, `unique_violation → DUPLICATE_OR_RETRY`. Client `placeOrder({p_request_id,p_address_id,p_delivery_slot_id})` must be bridged to `(p_idempotency_key,p_items,p_delivery_date,p_slot_id)`.
* `cart_items` concurrency: `pg_advisory_xact_lock(uid||':cart')` in contract; double-tap at worst `23505` rejected — surfaced as recoverable.
* Pricing parity: `lib/pricing.ts:pickActiveOffer/priceFor/cheapestOption` reproduces `least(price,discount)` and `created_at desc limit 1` window; `lib/money.ts` integer paise avoids `0.1+0.2` drift (`toPaise/fromPaise/addMoney/multiplyMoney/applyPercentDiscount/minMoney`, `formatMoney` `en-IN`).

## 5. RLS — target (once hardened)

Enable on all public tables (live off ×12, 0 policies — P0). Example least-privilege:

```sql
alter table public.profiles enable row level security;
-- catalogue public read when is_active
create policy categories_read_public on public.categories for select using (is_active);
create policy products_read_public   on public.products   for select using (is_active);
create policy slots_read_public      on public.delivery_slots for select using (is_active);
create policy store_settings_read    on public.store_settings for select using (true);
-- owned private
create policy profiles_self_rw   on public.profiles for all using (auth.uid()=id) with check (auth.uid()=id);
create policy addresses_owner     on public.addresses for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
create policy cart_owner          on public.cart_items for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
create policy orders_owner_select on public.orders for select using (auth.uid()=user_id);
create policy order_items_owner   on public.order_items for select using (exists(select 1 from public.orders o where o.id=order_id and o.user_id=auth.uid()));
create policy complaints_owner    on public.complaints for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
-- admin gated via private.is_admin() (or profiles.role='admin' on live) — no USING(true) on private data
```

Revoke `anon/authenticated EXECUTE` on `SECURITY DEFINER` helpers (`handle_new_user`, `notify_*`, `sync_profile_phone`); pin `search_path = public, pg_temp` on `set_updated_at/gen_order_number`; revisit `product_catalog SECURITY DEFINER`; add storage RLS when buckets created.

## 6. Storage

* `product-images` (public) for `products.image_url`/`categories.image_url` — `next.config.ts:images.remotePatterns` allowlist tied to `NEXT_PUBLIC_SUPABASE_URL` storage host, `minimumCacheTTL 30d`, `formats avif→webp`, `sizes` by grid breakpoint.
* `complaint-images` (private) for `complaint_attachments` — owner-or-admin RLS, `image/%` + `<5MiB` checks. Live `storage.buckets:0` — create both forward.

## 7. Live divergence & remediation branches

* **Branch A — DB toward contract** (preserve authored app): add `product_weights, offers, cart_items (2 partial uniques), order_status_history, complaint_messages, notifications` + missing columns + storage + `place_order` wrapper for `p_request_id`. Larger migration, app unchanged.
* **Branch B — app toward live** (smaller migration, rewrite types): regen `src/types/database.ts` from live, rewrite `PRODUCT_SELECT`, `pricing.ts` inline `discount_price`, `SlotPicker/cart-service/order-service/lib/catalogue.ts` to `p_items/delivery_date/slot_id/idempotency_key`, bridge `delivery_slots name+time → timestamptz` respecting `today|tomorrow` and `Asia/Kolkata`. Faster ship for `hyibalj...` (0 customer rows).

For this workspace (`.env.local → hyibalj...`, 0 rows) Branch B is lower risk; `supabase/migrations/*_*.sql` forward additive (no `DROP production`). Decision must be frozen before deploy; `docs/SUPABASE_AUDIT.md` §8 has exact RLS + revoke SQL.

## 8. Verification before accepting traffic

* `ENABLE RLS` + policies proven: anon cannot enumerate private tables; catalogue public reads succeed.
* Catalogue reads (`getCatalogue/getProductById/getRelatedProducts/getCategories/getLiveOffers`) return real rows (or glyph fallbacks) — `GET /,/categories,/search,/products/[id],/offers` show populated grids.
* `place_order` e2e: guest + authenticated cart, slot capacity + `today|tomorrow`, `min_order_amount`, stock/max-order, idempotent replay via stable `p_idempotency_key` → `order_id` visible in `GET /orders/[id]` timeline + `notification_outbox`.
* `pnpm exec tsc --noEmit ✓` + `pnpm exec eslint . ✓` + `pnpm run build 22/22 ✓` + curl smoke.

Only features that actually exist are documented — `docs/PROJECT_AUDIT.md` §4 is the classified source of truth.
