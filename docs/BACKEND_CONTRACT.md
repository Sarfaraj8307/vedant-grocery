# Backend Contract — Verified Live Schema

> **Status:** VERIFIED by direct inspection of the live Supabase project on
> 2026-10-07 (read-only introspection via Supabase MCP: `list_migrations`,
> `list_tables`, `information_schema.columns`, `pg_policy`, `pg_proc`,
> `pg_indexes`, `pg_constraint`).
>
> **Project:** `bmdmgycsrnffcgxmukxm` (region `ap-southeast-1`, Postgres 17.11)
> **API URL:** `https://bmdmgycsrnffcgxmukxm.supabase.co`
> **Note:** the project is currently *named* `kunal fruit mart` in the Supabase
> dashboard while this product is branded **Vedant Fruit & Vegetables**. This
> naming discrepancy is an owner-level item — see `FINAL_IMPLEMENTATION_REPORT.md`
> → Known Limitations. It does not affect the schema or the customer app.

This document is the **source of truth for the client**. The customer app is
written against exactly these tables, columns, policies and RPC signatures.
Nothing here was invented: every claim below was read out of the live database.

---

## 1. Applied migrations (live, in order)

| Version | Name | Purpose |
|---|---|---|
| `20261004130211` | `customer_core` | Core catalogue, cart, addresses, orders, complaints, notifications |
| `20261004130249` | `rls_storage` | RLS policies + storage buckets |
| `20261004130315` | `order_workflows` | `place_order()` RPC, order state machine |
| `20261004130408` | `admin_actions` | `admin_update_order_status`, `admin_reject_order_item` |
| `20261004131021` | `seed_demo_catalog` | 4 categories, 19 products, 7 offers, 14 slots |
| `20261004131319` | `admin_capability_grants` | `private.is_admin()` + grant surfaces |
| `20261004131550` | `fix_anon_is_admin_execute` | Removed anon EXECUTE on admin helpers |
| `20261004131808` | `advisor_performance_cleanup` | Index / lint cleanup |

**Local repository migrations:** none existed. The project directory contained
only `.freebuff/`. Therefore there was **no local↔remote divergence to resolve**:
remote is the single source of truth. The migrations above are mirrored into
`supabase/migrations/` in this repo as documentation of the contract, and this
document supersedes them for client work.

---

## 2. Tables (all in `public`, all with RLS enabled)

Currency is `numeric(12,2)` (Indian rupees, 2 decimals) — **exact numeric, never
float**. Does not match the "integer minor units" suggestion in the brief, but
it is exact and already enforced with `CHECK` constraints; the client must
therefore round to 2 decimals and never do float math.

### `profiles`
`id uuid PK` (default `auth.uid()`), `username text UNIQUE NOT NULL`
(`CHECK ^[a-z0-9_]{3,24}$`), `first_name text` (`<=60`), `last_name text`
(`<=60`), `email text NULL`, `phone text NULL`, `created_at`, `updated_at`.

### `categories`
`id uuid PK`, `name text UNIQUE` (`1..100`), `glyph text`, `accent bigint`
(decimal RGB int, e.g. `16639968` = `#FDE0C0`-ish), `image_url text NULL`,
`sort_order int`, `is_active bool`, timestamps.

### `products`
`id uuid PK`, `category_id uuid → categories(id) ON DELETE RESTRICT`,
`name text` (`1..200`), `description text`, `glyph text`, `accent bigint`,
`image_url text NULL`, `base_price numeric >= 0`, `unit_label text`
(`kg|dozen|bunch|…`), `availability bool`, `quantity_limit int > 0`, timestamps.

### `product_weights`
`id uuid PK`, `product_id uuid → products(id) ON DELETE CASCADE`
(`UNIQUE (product_id, grams)`), `label text` (`1..40`), `grams int > 0`,
`price numeric >= 0`, `is_available bool`, timestamps.

> Weight options are **backend-supplied variants**, exactly as the brief
> requires. The UI never hard-codes 100g/500g/1kg.

### `offers`
`id uuid PK`, `product_id uuid → products(id) ON DELETE CASCADE`,
`title text` (`1..160`), `price numeric NULL`, `discount_percent numeric NULL`
(`0..100`), `starts_at timestamptz NULL`, `ends_at timestamptz NULL`,
`is_active bool`, timestamps.
`CHECK (price IS NOT NULL OR discount_percent IS NOT NULL)`,
`CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)`.

An offer is "live" when `is_active AND (starts_at IS NULL OR starts_at <= now())
AND (ends_at IS NULL OR ends_at > now())`.

### `delivery_slots`
`id uuid PK`, `starts_at timestamptz`, `ends_at timestamptz` (`> starts_at`),
`capacity int > 0`, `is_active bool`, timestamps.

### `addresses`
`id uuid PK`, `user_id uuid NOT NULL DEFAULT auth.uid()`, `label text`
(default `'Home'`, `1..40`), `full_name text` (`1..120`), `phone text`
(`10..15`), `house_number`, `street`, `landmark` (all default `''`), `city text`,
`state text`, `postal_code text` (`CHECK ^[0-9]{5,10}$`), `latitude double NULL`
(`-90..90`), `longitude double NULL` (`-180..180`), `is_default bool`, timestamps.

> Partial unique index `addresses_one_default_per_user_idx` guarantees at most
> **one default address per user** — the client must clear-then-set, and a plain
> "set as default" update can raise a unique violation. Handled explicitly.

### `cart_items`
`id uuid PK`, `user_id uuid NOT NULL DEFAULT auth.uid()`, `product_id uuid →
products(id) ON DELETE CASCADE`, `weight_id uuid NULL → product_weights(id)`,
`quantity int` (`1..100`), timestamps.
Two partial unique indexes:
* `(user_id, product_id) WHERE weight_id IS NULL`
* `(user_id, product_id, weight_id) WHERE weight_id IS NOT NULL`

> A cart line is therefore keyed by *(product, weight-or-null)*. Upserts must
> target the matching partial index.

### `orders`
`id uuid PK`, `user_id uuid NOT NULL DEFAULT auth.uid()`, `request_id text`
(`8..128`, `UNIQUE (user_id, request_id)`), `address_id uuid NULL →
addresses(id) ON DELETE SET NULL`, `delivery_slot_id uuid NULL →
delivery_slots(id) ON DELETE SET NULL`, `status text`
(`ORDER_RECEIVED|OWNER_ACCEPTED|PREPARING|READY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED`,
default `ORDER_RECEIVED`), `original_total numeric >= 0`, `delivery_charge
numeric >= 0`, `adjustment_amount numeric <= 0`, `adjustment_reason text NULL`,
`adjusted_by text NULL`, `adjusted_at timestamptz NULL`, `final_total numeric >= 0`,
timestamps.
`CHECK (final_total = original_total + adjustment_amount)`.
`CHECK ((adjusted_by IS NULL) = (adjusted_at IS NULL))`.

> `adjustment_amount <= 0`: adjustments can only ever **reduce** the total
> (rejected items). `final_total` is a generated-by-formula value.

### `order_items`
`id uuid PK`, `order_id uuid → orders(id) ON DELETE CASCADE`, `product_id uuid
NULL → products(id) ON DELETE SET NULL`, `product_name text` (denormalised
snapshot), `weight_label text`, `quantity int 1..100`, `unit_price numeric >= 0`,
`line_total numeric >= 0`, `rejected bool`, `rejection_reason text NULL`,
`created_at`.
`CHECK (line_total = unit_price * quantity)`,
`CHECK (NOT rejected OR rejection_reason IS NOT NULL)`.

> Rejected-item + reason fields exist and are customer-visible by design, as the
> brief requires for the future admin app.

### `order_status_history`
`id uuid PK`, `order_id uuid → orders(id) ON DELETE CASCADE`, `old_status text
NULL`, `new_status text` (same 7 statuses), `changed_by text` (default
`auth.uid()::text`), `reason text NULL`, `created_at`.

### `complaints`
`id uuid PK`, `user_id uuid NOT NULL DEFAULT auth.uid()`, `order_id uuid NULL →
orders(id) ON DELETE SET NULL`, `subject text` (`1..160`), `description text`
(`1..10000`), `status text` (`OPEN|IN_REVIEW|RESOLVED|CLOSED`, default `OPEN`),
`resolution text NULL`, `resolved_by text NULL`, `resolved_at timestamptz NULL`,
timestamps.

### `complaint_messages`
`id uuid PK`, `complaint_id uuid → complaints(id) ON DELETE CASCADE`,
`author_id uuid NOT NULL DEFAULT auth.uid()`, `author_role text`
(`CUSTOMER|ADMIN`, default `CUSTOMER`), `body text` (`1..2000`), `created_at`.

### `complaint_attachments`
`id uuid PK`, `complaint_id uuid → complaints(id) ON DELETE CASCADE`,
`storage_path text UNIQUE`, `media_type text`
(`image/jpeg|image/png|image/webp`), `created_at`.

### `notifications`
`id uuid PK`, `user_id uuid NOT NULL DEFAULT auth.uid()`, `title text` (`1..160`),
`body text` (`1..2000`), `read_at timestamptz NULL`, `created_at`.

---

## 3. Private schema (NOT reachable by the customer app)

`private.admin_users(user_id uuid PK, created_at)` — the admin roster.
`private.shop_settings(singleton bool PK, delivery_charge numeric,
free_delivery_threshold numeric)` — current values:
`delivery_charge = 20`, `free_delivery_threshold = 500`.

Neither table is exposed in the API schema and neither is granted to `anon` or
`authenticated`. The client can only observe their *effects* (the delivery
charge applied by `place_order`).

`private.is_admin()` → `boolean`, `STABLE SECURITY DEFINER`,
`SET search_path = ''`, body:
```sql
select exists (select 1 from private.admin_users a where a.user_id = (select auth.uid()))
```
This is the **single database-level authorization boundary**. Every
admin-write policy calls it. The customer app has no equivalent and must not
try to replicate it client-side.

---

## 4. Row Level Security — verified policy inventory

Every table in `public` has `rls_enabled = true`. 45 policies exist. Summary:

| Table | CMD | Policy | Predicate (abridged) |
|---|---|---|---|
| profiles | SELECT | `profiles_read_self` | `id = auth.uid()` |
| profiles | INSERT | `profiles_insert_self` | `id = auth.uid()` |
| profiles | UPDATE | `profiles_update_self` | `id = auth.uid()` |
| addresses | SELECT/INS/UPD/DEL | `addresses_*_self` | `user_id = auth.uid()` |
| cart_items | SELECT/INS/UPD/DEL | `cart_*_self` | `user_id = auth.uid()` |
| orders | SELECT | `orders_read_self_or_admin` | `user_id = auth.uid() OR is_admin()` |
| orders | UPDATE | `orders_admin_update` | `is_admin()` |
| order_items | SELECT | `items_read_owner_or_admin` | parent order owned by `auth.uid()` or admin |
| order_items | UPDATE | `order_items_admin_update` | `is_admin()` |
| order_status_history | SELECT | `history_read_owner_or_admin` | parent order owned by `auth.uid()` or admin |
| order_status_history | INSERT | `history_admin_insert` | `is_admin()` |
| complaints | SELECT | `complaints_read_self_or_admin` | `user_id = auth.uid() OR is_admin()` |
| complaints | INSERT | `complaints_insert_self` | own + `status='OPEN'` + `resolved_*` NULL + order (if any) is own |
| complaints | UPDATE | `complaints_admin_update` | `is_admin()` |
| complaint_messages | SELECT | `complaint_messages_read_owner_or_admin` | own complaint or admin |
| complaint_messages | INSERT | `complaint_messages_insert` | `CUSTOMER` on own complaint, or `ADMIN` if admin |
| complaint_attachments | SELECT/DEL | `complaint_attachments_*` | own complaint or admin |
| complaint_attachments | INSERT | `complaint_attachments_insert_owner` | own complaint |
| notifications | SELECT/UPDATE | `notifications_*_self` | `user_id = auth.uid()` |
| categories / products / product_weights / offers / delivery_slots | SELECT | `*_read_public` | `anon, authenticated`; catalogue rows (`categories`, `delivery_slots`) gated on `is_active` |
| categories / products / product_weights / offers / delivery_slots | INS/UPD/DEL | `*_admin_*` | `is_admin()` |

Findings:

* **No `USING (true)` on private customer data.** The only `true` predicate is
  `offers_read_public` — public catalogue data, which is intentional.
* **Orders cannot be INSERTed by the client at all.** There is no INSERT policy
  on `orders`, `order_items` or `order_status_history` for customers. Order
  creation must go through `public.place_order()`. This is the correct design.
* `notifications` has **no INSERT policy** — only the backend/admin can create
  them. The client may only read and mark-as-read.
* Customers cannot UPDATE their own orders (no such policy); cancellation is an
  admin transition.

---

## 5. RPCs (the only write paths for orders)

### `public.place_order(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid) → uuid`
`SECURITY DEFINER`, `SET search_path = ''`. Granted to `authenticated` only.
Verified behaviour (from the live function body):

1. Requires `auth.uid()` **and** `auth.jwt()->>'role' = 'authenticated'`.
2. Validates `p_request_id` length 8–128.
3. Takes advisory locks on `(uid, request_id)` and `(uid, ':cart')`.
4. **Idempotent**: if an order already exists for `(user_id, request_id)`,
   returns its id immediately — safe to retry. This is exactly the
   dedupe key the client should use for "Order submission failed → retry".
5. Verifies the address belongs to the caller (`FOR KEY SHARE`).
6. Verifies the slot is active and `starts_at > now()`; locks it
   `FOR UPDATE`; rejects if `capacity` is reached by non-cancelled orders.
7. Requires a non-empty cart.
8. Locks cart rows, their weights, and the relevant offers.
9. Computes **the authoritative unit price server-side**:
   `coalesce(weight.price, product.base_price)`, then
   `least(unit, offer.price)` if an offer has a fixed price, then
   `least(unit, round(base * (1 - offer.discount_percent/100), 2))` if a
   percentage offer is live. Rejects the whole order if any line is
   unavailable, over `quantity_limit`, or has an unavailable/missing weight.
10. Reads `delivery_charge` / `free_delivery_threshold` from
    `private.shop_settings`; free delivery at `>= 500`.
11. Inserts the order with `final_total = original_total`, `adjustment_amount = 0`.
12. Inserts `order_items` snapshots (name + weight label + unit price + line total).
13. Inserts the first `order_status_history` row `null → ORDER_RECEIVED`.
14. **Deletes the caller's cart rows** and returns the new order id.

Error codes the client must map to friendly copy (Postgres `SQLSTATE`):
`42501` authentication/ownership (`authentication required`, `address unavailable`),
`22023` validation (`invalid request id`, `delivery slot unavailable`,
`delivery slot full`, `cart is empty`, `cart item unavailable`).

### `public.check_username_available(p_username text) → boolean`
`SECURITY DEFINER`, `STABLE`, granted to `anon` + `authenticated`.
Returns `true` only for a well-formed `^[a-z0-9_]{3,24}$` username that is not
already taken. Safe to call before authentication — this is the only
pre-auth RPC, and it leaks nothing beyond a boolean.

### Admin RPCs (present, **deliberately not callable by this app**)
`public.admin_update_order_status(p_order_id uuid, p_status text, p_reason text)`
and `public.admin_reject_order_item(p_item_id uuid, p_reason text)`.
Both are `SECURITY DEFINER` and gate on `is_admin()` internally. They are
documented in `FUTURE_ADMIN_APP.md` for the separate Android admin client and
are **never** referenced by customer code.

### Utility
`public.touch_updated_at()` — trigger helper, present but **no triggers are
currently attached to any `public` table**.

---

## 6. Storage buckets

| Bucket | Public | Purpose |
|---|---|---|
| `product-images` | **yes** | Catalogue imagery. Public read is intended. |
| `complaint-images` | no | Customer complaint evidence. Private + RLS-gated. |

---

## 7. Verified data-quality findings (action items for the owner)

1. **Delivery slots are stale.** The 14 seeded slots cover
   `2026-10-04 03:30 UTC` → `2026-10-10 13:30 UTC` with no recurrence. As of
   today (`2026-10-07`) only a handful remain in the future, and after
   `2026-10-10` **checkout will be impossible** because `place_order` requires
   an active slot with `starts_at > now()`. The store needs recurring slot
   generation. `supabase/migrations/…_delivery_slot_recurrence.sql` is provided
   as a **proposed, not yet applied** migration.
2. **No `updated_at` triggers exist.** `touch_updated_at()` is defined but
   attached to nothing, so `updated_at` only changes if a writer sets it. The
   client sets `updated_at` explicitly when it updates `addresses`, `cart_items`
   and `profiles` to keep the column meaningful.
3. **No profile auto-creation trigger.** `profiles` rows are *not* created by a
   trigger on `auth.users`. The client must insert its own profile row after
   signup (permitted by `profiles_insert_self`), and must tolerate a missing
   profile row for pre-existing accounts.
4. **`categories.image_url` and `products.image_url` are all `NULL`.** The
   catalogue currently has no real photography — only emoji `glyph` + `accent`
   RGB. See `docs/IMAGE_STRATEGY.md`; the client renders a designed
   glyph/accent plinth now and swaps to real images the moment
   `products.image_url` / `categories.image_url` are populated. This is a
   first-class, supported state — **not** a broken state.
5. **`auth.users` is empty** (0 rows) and no admin has been granted yet
   (`private.admin_users` is empty). A customer can sign up and order
   immediately, but nobody can accept the order until an admin is granted —
   orders would sit in `ORDER_RECEIVED` forever. Recorded as BLOCKED for the
   owner in `TASK_STATE.md`.
6. `products.availability = false` for **Broccoli** — this is the seed's
   deliberate out-of-stock case and the client renders a real out-of-stock
   treatment for it.
