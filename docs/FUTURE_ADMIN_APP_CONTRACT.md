# Future Admin App Contract — Vedant Fruit & Vegetables

> **Date:** 2026-10-10 · **Customer PWA:** this repo (no admin UI, no service-role key) · **Live project:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`) · **Build:** `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓`
> **Purpose:** Backend contract for the **separate Android admin app** that will share the same Supabase project. This PWA deliberately ships no admin screens and no admin secret — `lib/env.ts` rejects `sb_secret_*`, `proxy.ts` is not auth, authorization is RLS + `SECURITY DEFINER` RPCs. See `docs/DATABASE_ARCHITECTURE.md` + `docs/API_CONTRACT.md` for the full schema/transport; `docs/BACKEND_CONTRACT.md` (2026-10-07, `bmdmgycsrnffcgxmukxm`) is the historical source of truth.
> **Live divergence:** `hyibalj...` has 3 migrations, 12 tables + `product_catalog SECURITY DEFINER` view, `storage.buckets:0`, RLS **OFF on all 12, 0 policies** (P0), `product_weights/offers/cart_items/order_status_history/complaint_messages/notifications` missing. This contract describes the **target** the Android app must be written against; forward `supabase/migrations/*.sql` reconciles `hyibalj...` additively (no `DROP production`). Branch A (DB→app) vs Branch B (app→live) is decided in `docs/PRODUCT_GAP_ANALYSIS.md` §4 / `docs/DATABASE_ARCHITECTURE.md` §7.

## 1. Architecture

```
Supabase (hyibaljxicojfoxbihdn)
├── auth.users ── profiles (role customer|admin / private.admin_users)
├── Shared catalogue (RLS: public read where is_active)
│   ├── categories (glyph, accent, image_url) / products (base_price, unit_label, availability, quantity_limit, glyph, accent)
│   ├── product_weights (backend variants, grams-sorted) / offers (time-windowed)
│   └── delivery_slots (target: starts_at/ends_at timestamptz, capacity) vs live: name + start_time/end_time time
├── Customer-private (RLS: auth.uid()=user_id)
│   ├── addresses / cart_items (2 partial uniques on weight_id) / orders (+ order_items, order_status_history)
│   ├── complaints (+ complaint_messages, complaint_attachments → storage complaint-images)
│   └── notifications / notification_outbox (populated by notify_* triggers) / events (analytics)
├── Admin-private (RLS: is_admin())
│   └── writes via SECURITY DEFINER RPCs + private.is_admin()
└── Store singleton (store_settings id=1: delivery_fee 20, free 499, min 100, is_open)
     Customer PWA ──reads catalogue, writes via place_order──┘
     Admin Android ──CRUD catalogue/slots, order state machine, support──┘
```

Customer PWA and Admin Android share **one** Supabase project (same `NEXT_PUBLIC_SUPABASE_URL` host for `images.remotePatterns allowlist`). Service-role key never ships to either client.

## 2. Auth & authorization

### 2.1 Roster

Target (once `private` schema exists):
```sql
create schema if not exists private;
create table private.admin_users (user_id uuid primary key references auth.users(id) on delete cascade, created_at timestamptz not null default now());
create or replace function private.is_admin() returns boolean
  security definer set search_path = '' stable language sql as $$
    select exists (select 1 from private.admin_users a where a.user_id = (select auth.uid()));
  $$;
-- revoke anon/authenticated execute on definer helpers until private exists
```
Live `hyibalj...` variant: `profiles.role enum(customer,admin)` + `handle_new_user() case email='admin@freshmart.local' then admin else customer` — either model satisfies this contract if RLS gates on it. Pick one before launch and freeze `docs/SUPABASE_AUDIT.md` §8.

Grant: `insert into private.admin_users (user_id) values ('<auth.uid>');` via Edge Function with service-role (never from client). Verify: `select private.is_admin();` in that user's session.

### 2.2 Rules

* Admin app authenticates via Supabase Auth (same project) — never assumes admin client-side, always handles `403 Permission Denied` from RLS/RPC.
* `private` schema is not accessible from client SDKs; roster management via Edge Function.
* Never ship service-role key in Android APK; anon/publishable key is safe (RLS stands).

## 3. Admin RPCs (SECURITY DEFINER, is_admin-gated)

### 3.1 `admin_update_order_status(p_order_id uuid, p_status text, p_reason text default null) returns uuid`

State machine (target): `ORDER_RECEIVED → OWNER_ACCEPTED → PREPARING → READY → OUT_FOR_DELIVERY → DELIVERED`, any → `CANCELLED` (requires `p_reason`). Inserts `order_status_history(old_status,new_status,changed_by default auth.uid()::text, reason)` and updates `orders.status`. Live `hyibalj...` enum is `PENDING,CONFIRMED,PROCESSING,READY_FOR_DELIVERY,OUT_FOR_DELIVERY,DELIVERED,CANCELLED` — map `ORDER_RECEIVED↔PENDING` in the migration or in Android's display layer; contract stays target-named.

```kotlin
supabase.rpc("admin_update_order_status") {
  parameter("p_order_id", orderId)
  parameter("p_status", "OWNER_ACCEPTED")
  parameter("p_reason", null)
}
```

### 3.2 `admin_reject_order_item(p_item_id uuid, p_reason text) returns void`

Sets `order_items.rejected=true, rejection_reason=p_reason`, recalculates `orders.adjustment_amount (≤0)` and `final_total = original_total + adjustment_amount` (checked), triggers `notification_outbox` for customer. Live `order_items` lacks `rejected/rejection_reason` — add forward.

### 3.3 `place_order` — not admin, but Android may place on behalf

Live sig: `place_order(p_address_id uuid, p_delivery_date date, p_idempotency_key uuid, p_items jsonb, p_slot_id uuid) returns jsonb` vs app sig `place_order(p_request_id, p_address_id, p_delivery_slot_id) returns string`. Admin wrapper should normalize to live sig or version as `place_order_v2` — see `docs/API_CONTRACT.md` §1.2.

## 4. Tables the admin app owns

### 4.1 Catalogue (admin CRUD, public read where is_active)

| Table | Admin write | Customer read | Notes |
|---|---|---|---|
| `categories` (name unique, glyph, accent, image_url, sort_order, is_active) | `is_admin()` | `select where is_active order sort_order` | accent tint 4–8% only |
| `products` (category_id RESTRICT, base_price numeric(12,2), unit_label, availability, quantity_limit) | `is_admin()` | `PRODUCT_SELECT` nested | live has `price/discount_price/stock/max_order_qty` — reconcile |
| `product_weights` (product_id CASCADE, unique(product_id,grams), label, grams, price, is_available) | `is_admin()` | nested, grams-sorted | **missing live** — create |
| `offers` (product_id CASCADE, price/discount_percent, starts_at/ends_at, is_active, checks) `pickActiveOffer = is_active && (starts_at null||≤now) && (ends_at null||>now) order created_at desc limit 1` | `is_admin()` | nested | **missing live** — create; vs live inline discount |
| `delivery_slots` (target starts_at/ends_at timestamptz, capacity) vs live (name, start_time/end_time time, max_orders) | `is_admin()` | public read, horizon 7d, capacity grey-out via `getSlotLoad()` count(status<>'CANCELLED') | Recurrence: generate next 90d 9:00–20:00 30-min capacity 20 — see §9 latest |

### 4.2 Orders & support (admin read all, customer read own)

| Surface | Admin | Customer | Contract |
|---|---|---|---|
| `orders` (+ `order_items` snapshot + `order_status_history` timeline) | `select all` via `orders_read_self_or_admin`, update via `admin_update_order_status` | `select where auth.uid()=user_id`, `place_order` only write path, no direct INSERT | `adjustment_amount ≤0`, `final_total` generated, `pincode` generated from address_snapshot (live), `idempotency_key uuid unique` |
| `complaints` + `complaint_messages(author_role CUSTOMER\|ADMIN)` + `complaint_attachments(media_type image/jpeg|png|webp)` | `select all`, reply as `ADMIN`, `update status RESOLVED` + `resolved_at/resolved_by` | `select where user_id`, `insert {subject,description,order_id}` ownership-checked | live missing `complaint_messages` (has `admin_reply inline`) + `type/status` enum diff — create messages table |
| `notifications` vs `notification_outbox(audience, type,title,body,data)` | inserts trigger notify_* on `orders/complaints` | `select where user_id`, `mark read via read_at` (no INSERT) | triggers `notify_order_event/notify_complaint_event` → outbox admin/customer |

### 4.3 Storage

* `product-images` (public) for `products.image_url`/`categories.image_url` — `next.config.ts:images.remotePatterns` allowlist is host-derived from `NEXT_PUBLIC_SUPABASE_URL`.
* `complaint-images` (private, owner-or-admin RLS) for `complaint_attachments.storage_path` — owner-or-admin RLS, `content_type image/%`, `size <5MiB`. Live `storage.buckets:0` — create both.

## 5. Analytics the admin app can answer (from orders + events)

Events append-only: `events(session_hash 8..64, event page_view|search|add_to_cart|place_order, path ≤300, meta jsonb)`. No PII in meta. Admin queries:

* customers/day, new registrations (auth.users + profiles), orders/day, revenue (`final_total`), popular products/categories (order_items join), conversion `place_order / add_to_cart`, cart abandonment, repeat customers, delivery performance per slot.

Respect privacy; `events` insert-only, no customer select.

## 6. Recommended Android stack

Kotlin, MVVM + Compose, `supabase-kt`, Room + DataStore (offline-first order list), Compose Navigation, Hilt, Ktor (via supabase-kt), Coil, WorkManager (periodic poll + slot generation). Edge Function with service-role for roster.

## 7. Security

* Service-role bypasses RLS — never in APK.
* RLS is boundary — revoke `anon/authenticated EXECUTE` on `handle_new_user/notify_*/sync_profile_phone` SECURITY DEFINER helpers; pin `search_path = public, pg_temp` on `set_updated_at/gen_order_number`; revisit `product_catalog SECURITY DEFINER` (consider `INVOKER`).
* Storage RLS matches `complaint_attachments` ownership.

## 8. Current status & owner actions (as of 2026-10-10)

| Component | Status | Action |
|---|---|---|
| Migrations (hyibalj...) | 3 applied | Apply forward reconciliation (Branch A/B) — no DROP |
| RLS | OFF ×12, 0 policies — **P0 stop-ship** | `ENABLE RLS` + policies per `docs/SUPABASE_AUDIT.md` §8.1 |
| Admin roster | empty (target `private.admin_users`; live `profiles.role` seeded) | Grant first admin via Edge Function, verify `is_admin()` |
| Delivery slots | 3 time-based rows — stale after 2026-10-10 | Slot recurrence migration (§9) |
| Catalogue images | `NULL` (glyph plinth fallback) | Upload to `product-images` bucket, set `image_url` |
| Store contact | `NEXT_PUBLIC_STORE_*` unset → `needsOwnerConfiguration` | Set phone/whatsapp/address |
| Google OAuth | probe-gated (`lib/auth-providers.ts`) | Enable in Supabase dashboard if needed |
| Account deletion | request mode (`auth-service:clearPersonalData mode=request`) | No service-role in customer app by design |

First-time admin setup: create user in Auth → grant (`insert private.admin_users` or set `profiles.role='admin'`) → verify `select private.is_admin()` → generate slots → set store contact → upload catalogue imagery → test RLS (non-admin 403 on admin RPCs/tables).

## 9. Slot recurrence (target — apply as migration when Branch chosen)

```sql
-- supabase/migrations/*_delivery_slot_recurrence.sql  (target shape: starts_at/ends_at)
do $$ declare s date := current_date + 1; e date := current_date + 90; d date := s; st timestamptz; en timestamptz; begin while d <= e loop for h in 9..19 loop for m in array[0,30] loop if not (h=19 and m=30) then st := (d || ' ' || h || ':' || m || ':00')::timestamptz; en := st + interval '30 minutes'; insert into delivery_slots (starts_at, ends_at, capacity, is_active) values (st, en, 20, true) on conflict do nothing; end if; end loop; end loop; d := d + 1; end loop; end $$;
-- For live hyibalj... time-based variant: adapt to (name, start_time, end_time, max_orders) and bridge to timestamptz in SlotPicker respecting Asia/Kolkata today|tomorrow.
```

## 10. Handoff checklist

- [ ] Read `docs/BACKEND_CONTRACT.md` + this contract + `docs/DATABASE_ARCHITECTURE.md` + `docs/API_CONTRACT.md`
- [ ] Obtain `hyibaljxicojfoxbihdn` credentials, freeze `NEXT_PUBLIC_SUPABASE_URL` / `.mcp.json project_ref`
- [ ] Apply reconciliation (Branch A/B) + `ENABLE RLS` + storage buckets + slot recurrence + admin grant
- [ ] `supabase-kt` setup, order list→detail→`admin_update_order_status`→`admin_reject_order_item`, catalogue CRUD (products/weights/offers/categories/slots), complaint threads, `notification_outbox` display, RLS 403 tests, analytics, image upload via Camera
- [ ] Play Store internal track

## 11. Versioning

This contract is versioned with the customer PWA. Any schema change affecting `is_admin()`/catalogue/slots/orders/support/storage must be reflected here and in `docs/DATABASE_ARCHITECTURE.md` / `docs/API_CONTRACT.md` in the same `supabase/migrations/*.sql` change. Customer PWA contains **no** admin screens and **no** admin secrets — ever.
