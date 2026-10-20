# Supabase Audit — Live Project `hyibaljxicojfoxbihdn`

> **Date:** 2026-10-10
> **Mode:** read-only inspection via Supabase MCP (MCP URL `https://mcp.supabase.com/mcp?project_ref=hyibaljxicojfoxbihdn`)
> **Project URL:** `https://hyibaljxicojfoxbihdn.supabase.co`
> **Previous contract project:** `bmdmgycsrnffcgxmukxm` (`kunal fruit mart`, 2026-10-07 contract) — **not** the project currently configured in `.env.local`. This audit covers the configured project only.
> **MCP auth:** successful

--------------------------------------------------------------------

## 0. Verdict

| Area | Status |
|---|---|
| **Schema vs. application contract** | **DIVERGED — P0**. Live schema is a different product from the types in `src/types/database.ts` / `src/lib/catalogue-shared.ts` / `src/services/order-service.ts`. Every catalogue read and the checkout write will fail live (see §7). |
| **Row Level Security** | **CRITICAL — RLS disabled on all 12 public tables, 0 policies.** Any anon key holder can read/write every customer row. Stop-ship before any real PII/orders land. |
| **Migrations** | 3 applied (`001_core_tables`, `002_view_triggers_rpc`, `003_fix_place_order`). No `supabase/migrations/` directory in repo — local↔remote cannot be diffed except via this audit. |
| **Storage** | 0 buckets (expected at least `complaint-images` / catalogue image bucket). |
| **Auth** | Read-only probes not in this pass; `handle_new_user`/`sync_profile_phone` triggers present. Google provider status to be probed via `auth-providers` capability check in app. |
| **Realtime/functions** | `place_order` live signature differs from client call (params & semantics). No edge functions listed via MCP. |
| **Advisors** | RLS disabled (ERROR ×12) + security_definer view + function search_path + SECURITY DEFINER anon/auth exposure. Performance: 2 unindexed FKs, 14 unused indexes. |

--------------------------------------------------------------------

## 1. Applied migrations (live)

Via `list_migrations`:

```
20261009203355  001_core_tables
20261009203704  002_view_triggers_rpc
20261009203847  003_fix_place_order
```

No local migration files exist in repo (`supabase/` missing). Therefore **no automated drift check** — this document is the drift record.

Historical contract (`docs/BACKEND_CONTRACT.md`) listed 8 migrations on `bmdmgycsrnffcgxmukxm` with `product_weights`, `offers`, `cart_items`, `order_status_history`, `complaint_messages`, `notifications`, `private.is_admin`, storage buckets, and 45 RLS policies. None of those structures exist on `hyibalj...`. The two projects are unrelated from a schema perspective.

--------------------------------------------------------------------

## 2. Tables (public) — live

`list_tables verbose=true` snapshot (12 tables + 1 view):

| Table | Rows | `rls_enabled` | PK | Notable columns / checks | FKs |
|---|---|---|---|---|---|
| `profiles` | 0 | false | `id uuid` | `full_name text default ''`, `phone text unique nullable`, `role user_role enum(customer,admin) default customer`, `is_active bool default true` | `id → auth.users(id)`; parents of `addresses`, `orders`, `complaints`, `notification_outbox` |
| `addresses` | 0 | false | `id uuid` | `label text check Home|Work|Other default Home`, `line1 text check 3..200`, `line2 text nullable check ≤200`, `city text 2..60`, `pincode bpchar check ^[0-9]{6}$`, `is_default bool default false` | `user_id → profiles(id)` |
| `categories` | 8 | false | `id uuid` | `name text unique`, `slug text unique`, `icon text default ''`, `sort_order int default 0`, `is_active bool default true` | parent of `products.category_id` |
| `products` | 0 | false | `id uuid` | `name text check 2..120`, `slug text unique`, `description text default ''`, `image_url text nullable`, `unit text default 'pc'`, `price numeric check >0`, `discount_price numeric nullable`, `stock int default 0 check ≥0`, `max_order_qty int default 10 check >0`, `is_active bool default true`, `is_featured bool default false`, `has_offer bool default false` | `category_id → categories`; parent of `order_items.product_id` |
| `delivery_slots` | 3 | false | `id uuid` | `name text check 2..60`, `start_time time`, `end_time time`, `max_orders int default 20 check >0`, `is_active bool default true`, `sort_order int default 0` | parent of `orders.slot_id` |
| `orders` | 0 | false | `id uuid` | `order_number text unique`, `status order_status enum(PENDING,CONFIRMED,PROCESSING,READY_FOR_DELIVERY,OUT_FOR_DELIVERY,DELIVERED,CANCELLED) default PENDING`, `delivery_date date`, `slot_id uuid nullable → delivery_slots`, `address_snapshot jsonb`, `pincode bpchar generated (address_snapshot->>'pincode')`, `subtotal numeric check ≥0`, `delivery_fee numeric default 0 check ≥0`, `total numeric check ≥0`, `idempotency_key uuid unique`, `placed_at timestamptz default now()`, `cancelled_at/cancel_reason nullable`, `updated_at` | `user_id → profiles`, `slot_id → delivery_slots`; parents `order_items`, `complaints` |
| `order_items` | 0 | false | `id uuid` | `product_id uuid nullable → products`, `product_name text`, `unit text default 'pc'`, `unit_price numeric check ≥0`, `qty int check >0`, `line_total numeric generated (unit_price*qty)` | `order_id → orders`, `product_id → products` |
| `complaints` | 0 | false | `id uuid` | `type complaint_type enum(WRONG_ITEM,DAMAGED,MISSING_ITEM,LATE_DELIVERY,QUALITY,OTHER)`, `description text check 5..2000`, `status complaint_status enum(OPEN,IN_PROGRESS,RESOLVED,CLOSED) default OPEN`, `admin_reply text nullable` | `user_id → profiles`, `order_id → orders nullable`; parent `complaint_attachments` |
| `complaint_attachments` | 0 | false | `id uuid` | `storage_path text`, `content_type text check image/%`, `size int check 0< size <5MiB` | `complaint_id → complaints` |
| `events` | 0 | false | `id bigint identity` | `session_hash text check 8..64`, `event text check page_view|search|add_to_cart|place_order`, `path text nullable check ≤300`, `meta jsonb nullable` | — |
| `notification_outbox` | 0 | false | `id bigint identity` | `audience outbox_audience enum(customer,admin)`, `user_id uuid nullable → profiles`, `type text`, `title text`, `body text default ''`, `data jsonb default {}`, `read_at timestamptz nullable` | `user_id → profiles` |
| `store_settings` | 1 | false | `id int check =1` | `store_name text default 'Fresh Mart'`, `phone text default ''`, `whatsapp text default ''`, `delivery_fee numeric default 20 check ≥0`, `free_delivery_threshold numeric default 499`, `min_order_amount numeric default 100`, `is_open bool default true` | — |
| `product_catalog` | — | — | **VIEW** | `SECURITY DEFINER` (advisor flagged). Columns mirror products+category join + computed `effective_price`. | — |

**Missing vs. application contract** (`src/types/database.ts` / `PRODUCT_SELECT`):

- No `product_weights` (weight variants), no `offers` (time-windowed discounts), no `cart_items`, no `order_status_history`, no `complaint_messages`, no `notifications` (there is `notification_outbox` instead), no `private.shop_settings` / `private.is_admin` surface.
- `profiles` mismatch: expects `username, first_name, last_name, email`; live has `full_name, role, is_active`.
- `categories` mismatch: expects `glyph, accent, image_url`; live has `icon, slug`.
- `products` mismatch: expects `glyph, accent, base_price, unit_label, availability, quantity_limit`; live has `slug, price, discount_price, stock, max_order_qty, unit, is_active, has_offer`.
- `delivery_slots` mismatch: expects `starts_at/ends_at timestamptz + capacity`; live has `name + start_time/end_time time + max_orders`.
- `addresses` mismatch: expects `full_name, phone, house_number, street, landmark, state, postal_code (5..10), latitude, longitude, label 1..40`; live is minimal `label∈{Home,Work,Other}, line1, line2, city, pincode(6-digit), is_default`.
- `orders/order_items` mismatch: app types expect `request_id, address_id, delivery_slot_id, original_total, delivery_charge, adjustment_*, final_total` + `order_status enum ORDER_RECEIVED..` and `order_items(weight_label, rejected, rejection_reason)`; live uses `delivery_date, slot_id, subtotal/delivery_fee/total, idempotency_key, status PENDING..` and `order_items(unit, line_total generated)`.

Row counts confirm greenfield: categories 8, delivery_slots 3, store_settings 1, all others 0.

--------------------------------------------------------------------

## 3. RLS and policies

* `list_tables verbose=true`: `rls_enabled: false` for **all 12** public tables.
* `pg_policies where schemaname='public'`: **0 rows**.
* Advisor `rls_disabled_in_public`: 12 tables listed as `level: ERROR` (`profiles, addresses, categories, products, delivery_slots, orders, order_items, complaints, complaint_attachments, events, notification_outbox, store_settings`).
* Remediation provided inline: `ALTER TABLE "public"."<table>" ENABLE ROW LEVEL SECURITY;` per table — **do not apply without adding policies** (would lock out all reads). Full policy set is in §9 of this document (to be applied in the remediation migration).

**Impact today:** any holder of the publishable anon key can `select/insert/update/delete` every row via PostgREST. Customer-data isolation across `profiles/addresses/orders/complaints` is **not enforced**. Treat as stop-ship before ingesting PII.

--------------------------------------------------------------------

## 4. Routines and triggers

### Routines (`information_schema.routines`)

| Name | Type | `security_type` | Notes |
|---|---|---|---|
| `gen_order_number()` | FUNCTION | INVOKER | `select 'ORD-' || to_char(now(),'YYMMDD') || '-' || upper(substr(md5(random()::text||clock_timestamp()::text),1,5))` |
| `handle_new_user()` | FUNCTION | DEFINER | `insert into profiles(id, full_name, phone, role) values(new.id, coalesce(new.raw_user_meta_data->>'full_name',''), nullif(new.phone,''), case email='admin@freshmart.local' then admin else customer) on conflict do nothing; return new` |
| `notify_complaint_event()` | FUNCTION | DEFINER | AFTER INSERT: outbox admin `new_complaint`; AFTER UPDATE status change: outbox customer `complaint_update` |
| `notify_order_event()` | FUNCTION | DEFINER | AFTER INSERT: outbox admin `new_order` + customer `order_placed`; AFTER UPDATE status change: outbox customer `order_status` |
| `place_order(p_address_id uuid, p_delivery_date date, p_idempotency_key uuid, p_items jsonb, p_slot_id uuid) returns jsonb` | FUNCTION | INVOKER | §5 |
| `set_updated_at()` | FUNCTION | INVOKER | `new.updated_at = now(); return new` |
| `sync_profile_phone()` | FUNCTION | DEFINER | If `new.phone` + `raw_user_meta_data->>'phone'` present, `update profiles set phone=... where id=new.id and phone is null` |

### Triggers (`information_schema.triggers`)

| Table | Trigger | Timing | Event | Statement |
|---|---|---|---|---|
| `addresses` | `addresses_updated` | BEFORE | UPDATE | `set_updated_at()` |
| `complaints` | `complaints_notify` | AFTER | INSERT, UPDATE | `notify_complaint_event()` |
| `complaints` | `complaints_updated` | BEFORE | UPDATE | `set_updated_at()` |
| `orders` | `orders_notify` | AFTER | INSERT, UPDATE | `notify_order_event()` |
| `orders` | `orders_updated` | BEFORE | UPDATE | `set_updated_at()` |
| `products` | `products_updated` | BEFORE | UPDATE | `set_updated_at()` |
| `profiles` | `profiles_updated` | BEFORE | UPDATE | `set_updated_at()` |
| `store_settings` | `settings_updated` | BEFORE | UPDATE | `set_updated_at()` |

No trigger on `auth.users` enumerated via `information_schema.triggers` (auth schema not in scope), but `handle_new_user`/`sync_profile_phone` imply a trigger to be verified via `pg_trigger`.

### `place_order` live semantics (full body in audit log, summarised)

* `auth.uid()` required else `{error:'UNAUTHENTICATED'}`; `p_idempotency_key` required else `VALIDATION`.
* Idempotent replay: if `orders.idempotency_key` exists, return `{order_id, order_number, replay:true}` immediately + also via `on conflict do nothing` path for racing inserts.
* `store_settings id=1` guard: `is_open` must be true else `STORE_CLOSED`.
* Date guard: `p_delivery_date ∈ {current_date, current_date+1}` else `INVALID_DATE`.
* Address ownership: `addresses where id=p_address_id and user_id=auth.uid()` else `INVALID_ADDRESS`.
* Slot guard: `delivery_slots where id=p_slot_id and is_active FOR UPDATE` else `INVALID_SLOT`; capacity: `count orders where slot_id=p_slot_id and delivery_date=p_delivery_date and status<>'CANCELLED' >= max_orders` → `SLOT_FULL`.
* Items: `p_items` must be `jsonb array 1..50`; each element `qty` 1..50 int, `product_id` must be `is_active` product `FOR UPDATE`; `qty > max_order_qty → QTY_EXCEEDED`; `qty > stock → OUT_OF_STOCK`; otherwise `PRODUCT_UNAVAILABLE`. Pricing: `least(price, coalesce(discount_price, price))`; `v_subtotal += price*qty` buffered into `v_lines` with `(product_id, product_name, unit, unit_price, qty)`.
* `v_subtotal < min_order_amount → BELOW_MIN_ORDER`; delivery fee: `subtotal >= free_delivery_threshold ? 0 : delivery_fee`.
* All-or-nothing insert: `orders(order_number user_id status PENDING delivery_date slot_id address_snapshot{label,line1,line2,city,pincode} subtotal delivery_fee total idempotency_key)`, `order_items` from `v_lines`, `products.stock` decrement per `qty`.
* Exception `unique_violation` → `{error:'DUPLICATE_OR_RETRY'}` (order_number collision ~1/9000).

**Client mismatch:** `src/services/order-service.ts` calls
```ts
supabase.rpc("place_order", { p_request_id, p_address_id, p_delivery_slot_id })
```
and maps `42501/22023` plus `cart item unavailable/slot full/cart empty/address unavailable` substrings. Live function uses different param names (`p_idempotency_key`, `p_delivery_date`, `p_items`, `p_slot_id`), different error strings (`UNAUTHENTICATED`, `VALIDATION`, `INVALID_DATE/ADDRESS/SLOT`, `SLOT_FULL`, ...), and returns JSONB not a string. **Current client call cannot succeed** until params/error mapping are bridged.

--------------------------------------------------------------------

## 5. Indexes, constraints, storage, auth

### Indexes (inferred from `verbose` FKs + performance advisor)

* Performance advisor `unindexed_foreign_keys` (INFO): `complaints.complaints_order_id_fkey`, `notification_outbox.notification_outbox_user_id_fkey` — missing covering index.
* `unused_index` (INFO ×14): `addresses_user_idx`, `products_category_idx`, `orders_*` (status, user, slot_date, pincode), `order_items_*`, `complaints_*`, `complaint_attachments_cid_idx`, `events_*`, `notification_outbox outbox_unread_idx`. Expected pre-launch signal (zero rows).

### Constraints

* Verified via columns output: `products.price >0`, `stock ≥0`, `max_order_qty >0`, `delivery_slots.max_orders >0`, `orders.subtotal/delivery_fee/total ≥0`, `order_items.qty>0 unit_price≥0`, `complaint_attachments content_type image/% size<5MiB`, `events.event ∈ {page_view,search,add_to_cart,place_order}`, `store_settings id=1 + thresholds ≥0`.

### Storage

* `storage.buckets`: **0 rows**. Contract expected `complaint-images` (and potentially catalogue bucket). All product images go through `next/image` allowlist tied to `*.supabase.co/storage/v1/object/public/**` — allowlist entry is host-derived from `NEXT_PUBLIC_SUPABASE_URL`; no bucket to host against live.

### Auth

* Not enumerated in this pass beyond `handle_new_user`/`sync_profile_phone`. `lib/auth-providers.ts` does a live capability probe to decide whether to show Google — that probe is the intended dashboard truth for provider on/off. `lib/env.ts` rejects `service_role` keys client-side; `.env.local` holds publishable key.

### Advisors (full)

**Security:**

| Lint | Level | Facing | Finding |
|---|---|---|---|
| `rls_disabled_in_public` | ERROR | EXTERNAL | 12 tables (all public) RLS not enabled — details §3 |
| `security_definer_view` | ERROR | EXTERNAL | `public.product_catalog` is SECURITY DEFINER |
| `function_search_path_mutable` | WARN | EXTERNAL | `set_updated_at`, `gen_order_number` — search_path not set |
| `anon_security_definer_function_executable` | WARN | EXTERNAL | `handle_new_user`, `notify_complaint_event`, `notify_order_event`, `sync_profile_phone` callable by `anon` as SECURITY DEFINER via `/rest/v1/rpc/*` |
| `authenticated_security_definer_function_executable` | WARN | EXTERNAL | same 4 callable by `authenticated` |

**Performance:** as §5 indexes above. `unused_index` is expected on greenfield; `unindexed_foreign_keys` should be fixed in the remediation migration.

--------------------------------------------------------------------

## 6. Sample live data

| Table | Query | Result |
|---|---|---|
| `categories` | `select * limit 5` | 8 rows exist (not enumerated individually this pass) |
| `delivery_slots` | `select * from delivery_slots` | 3 rows: `Morning 08:00-10:00 max 25 sort 1 active`, `Afternoon 12:00-14:00 max 25 sort 2`, `Evening 17:00-19:00 max 25 sort 3` — **time not timestamptz** |
| `products` | `select * limit 5` | 0 rows |
| `product_catalog` | `select * limit 5` | 0 rows |
| `store_settings` | `select * where id=1` | `store_name='Fresh Mart', delivery_fee 20, free_delivery_threshold 499, min_order_amount 100, is_open true` |

--------------------------------------------------------------------

## 7. What breaks today (production-readiness gaps)

### P0 — RLS

Already §3. Fix: `ENABLE ROW LEVEL SECURITY` + policies (catalogue public read, owned reads/writes where relevant, deny on `events` except insert own, outbox read-own). Storage policies when buckets created.

### P0 — catalogue & product reads

* `src/lib/catalogue-shared.ts: PRODUCT_SELECT` selects `glyph, accent, base_price, unit_label, availability, quantity_limit, category(glyph,accent), product_weights(...), offers(...)` — none of these columns/tables exist live → every `getCatalogue`/`getProductById`/`getRelatedProducts`/`getLiveOffers` errors before rendering.
* `src/lib/catalogue.ts: getCategories .eq("is_active",true).order(sort_order)` matches live columns but app types expect `glyph/accent/image_url`; OK on read but type drift on render.
* `getLiveOffers` expects `offers` table with `is_active/starts_at/ends_at`; live pricing is `products.discount_price` with no window.

### P0 — checkout write path

* Client `place_order(p_request_id, p_address_id, p_delivery_slot_id)` vs live `(p_address_id, p_delivery_date, p_idempotency_key, p_items, p_slot_id)` → pre-flight 400 before business logic.
* Even with param mapping, client sends no `p_items` (it expects server to read `cart_items`); live requires `p_items jsonb`. Client must assemble `p_items:[{product_id,qty}]` from Zustand `cart_lines` and send `p_delivery_date = current_date or current_date+1` + `p_idempotency_key` (UUID).
* `getDeliverySlots(horizonDays=7).gt("starts_at",now).lt("starts_at",horizon)` filters a column that does not exist (`delivery_slots.start_time is time`). Slot picker will return 0 or error → no slot id to place order with.

### P1 — cart persistence

* `services/cart-service.ts` speaks to `cart_items` (missing) with partial unique indexes vs read-then-write. Guest Zustand persisted cart survives; any signed-in `persistLine`/`fetchRemoteCart`/`replaceRemoteCart` errors.

### P1 — profile / addresses / support / notifications

* Types vs live: `profiles(username/first_name/last_name/email)` vs live `full_name/phone/role`; `addresses(full_name/phone/house_number/street/landmark/state/latitude/longitude)` vs live minimal; `complaints/complaint_messages/notifications` tables mismatch.
* Storage: 0 buckets; complaint attachments cannot be persisted as designed.

### P1 — views / trust / security hardening

* `product_catalog SECURITY DEFINER` to revisit; `anon/authenticated` EXECUTE on DEFINER helpers to revoke; `function_search_path` to pin; missing indexes on FKs.

--------------------------------------------------------------------

## 8. Remediation plan (forward migrations, no DROP production)

> **Safety:** zero customer rows exist today (counts above), so additive fixes are safe. No `DROP TABLE` / no `TRUNCATE` after launch.

### 8.1 Enable RLS + least-privilege policies

```sql
-- Enable (repeat for all 12)
alter table public.profiles               enable row level security;
alter table public.addresses              enable row level security;
alter table public.categories             enable row level security;
alter table public.products               enable row level security;
alter table public.delivery_slots         enable row level security;
alter table public.orders                 enable row level security;
alter table public.order_items            enable row level security;
alter table public.complaints             enable row level security;
alter table public.complaint_attachments  enable row level security;
alter table public.events                 enable row level security;
alter table public.notification_outbox    enable row level security;
alter table public.store_settings         enable row level security;

-- Example least-privilege set (to be refined in the remediation migration):
-- catalogue: public read for active rows
create policy categories_read_public on public.categories for select using (is_active);
create policy products_read_public   on public.products   for select using (is_active);
create policy slots_read_public      on public.delivery_slots for select using (is_active);
create policy store_settings_read    on public.store_settings for select using (true);

-- owned private data: auth.uid() ownership
create policy profiles_self_rw   on public.profiles for all using (auth.uid()=id) with check (auth.uid()=id);
create policy addresses_owner     on public.addresses for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
create policy orders_owner        on public.orders for select using (auth.uid()=user_id);
create policy order_items_owner   on public.order_items for select using (
  exists (select 1 from public.orders o where o.id=order_id and o.user_id=auth.uid())
);
create policy complaints_owner    on public.complaints for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
create policy complaint_att_owner on public.complaint_attachments for all using (
  exists (select 1 from public.complaints c where c.id=complaint_id and c.user_id=auth.uid())
);
create policy events_insert_own   on public.events for insert with check (true); -- optionally rate-limited
create policy outbox_read_own     on public.notification_outbox for select using (user_id=auth.uid() or audience='admin' and false);
-- (admin policies via `public.is_admin()` / `private.is_admin()` only after that helper exists)

-- Revoke anon/authenticated execute on DEFINER helpers; keep only trigger-internal / service path
revoke execute on function public.handle_new_user() from anon, authenticated;
revoke execute on function public.notify_complaint_event() from anon, authenticated;
revoke execute on function public.notify_order_event() from anon, authenticated;
revoke execute on function public.sync_profile_phone() from anon, authenticated;

-- Pin search_path
alter function public.set_updated_at() set search_path = public, pg_temp;
alter function public.gen_order_number() set search_path = public, pg_temp;
```

### 8.2 Bridge the catalogue vs live

Two viable branches — choose one before launch:

* **Branch A — bring DB toward contract (preserve authored app):** add `product_weights`, `offers`, `cart_items` (partial unique indexes), `order_status_history`, `complaint_messages`, `notifications` + missing columns (`profiles.username/first_name/last_name`, `products.glyph/accent/base_price/...`, `categories.glyph/accent/image_url`, `delivery_slots.starts_at/ends_at/capacity`), adapt `place_order` to `p_request_id + cart_items` or version as `place_order_v2`; add storage buckets + RLS on them.
* **Branch B — adapt app toward live (smaller migration, rewrite app types):** regenerate `src/types/database.ts` from live, rewrite `PRODUCT_SELECT`, `pricing.ts` view of offers, `SlotPicker`/`cart-service`/`order-service`/`lib/catalogue.ts` to match live columns and `p_items/delivery_date/slot_id/idempotency_key` RPC shape; map `delivery_slots(name,start_time,end_time)` to concrete `timestamptz` windows for the picker respecting the `today|tomorrow` guard.

For this workspace (`.env.local` already points at `hyibalj...`), **Branch B** is the lower-risk path to shipped quickly; the companion `docs/DATABASE_ARCHITECTURE.md` will record the chosen branch and the forward-migration SQL that was actually applied.

### 8.3 Remaining gaps to close

* Create storage buckets: `complaint-images` (private, owner-only policies matching the attach flow).
* Create missing FK covering indexes: `complaints(order_id)`, `notification_outbox(user_id)`; review `product_catalog` DEFINER view (switch to INVOKER if RLS must gate it).
* Align `place_order` error contract between client `mapPlaceOrderError` and live JSONB error strings; include `SLOTS→slot full→choose another` etc.

--------------------------------------------------------------------

## 9. Appendix — evidence (queries)

* `get_project_url` → `https://hyibaljxicojfoxbihdn.supabase.co`
* `list_migrations` → 3 rows (001/002/003)
* `list_tables verbose=true` → 12 tables `rls_enabled:false`, 1 view, counts as §2
* `pg_policies where schemaname='public'` → 0 rows
* `information_schema.columns where table_schema='public'` → column sets as §2
* `information_schema.routines where routine_schema='public'` → 7 routines (place_order body confirms jsonb semantics + error set)
* `information_schema.triggers where trigger_schema='public'` → 10 triggers
* `storage.buckets` → 0 rows
* Advisors `security` / `performance` → as §5

Live data probes:

* `select * from delivery_slots` → 3 rows time-based (08-10, 12-14, 17-19 max 25)
* `select * from categories/products/product_catalog/store_settings` → as §6

--------------------------------------------------------------------

## 10. Checklist before accepting real traffic

- [ ] Decide project identity (stay on `hyibalj...` vs revert to `bmdmgycsrnffcgxmukxm`) and lock `.env.local`/`NEXT_PUBLIC_SUPABASE_URL` to it.
- [ ] Either add missing DB structures (Branch A) or adapt app types/services to live (Branch B) and prove with `npx tsc --noEmit && npm run build && npm run lint`.
- [ ] Apply `ENABLE RLS` + per-table policies (no anon bypass), revoke DEFINER EXECUTE, pin `search_path`.
- [ ] Create storage buckets + storage RLS.
- [ ] Prove catalogue reads (`getCatalogue`, `getProductById`, `getRelatedProducts`, `getCategories`, `getDeliverySlots`, `getLiveOffers`) against live.
- [ ] Prove `place_order` end-to-end (guest cart + authenticated cart + slot capacity + idempotency replay → string id).
- [ ] Re-run Playwright smoke (home→search→cart→checkout→orders) + measure PWA install, image allowlist, SEO sitemap contents.
