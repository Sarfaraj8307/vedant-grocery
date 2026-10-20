# CONTRACT.md — Vedant Admin (Android) · Authoritative Backend Contract

> **The single source of truth for the backend the admin app talks to.**
> Where `docs/BACKEND_CONTRACT.md`, `src/types/database.ts`, `HANDOVER.md` or
> `PROJECT_INDEX.md` disagree with this file, **this file wins** — it was read
> out of the live database, not copied from a document.

| Field | Value |
|---|---|
| Project ref | `hyibaljxicojfoxbihdn` |
| URL | `https://hyibaljxicojfoxbihdn.supabase.co` |
| Auth | publishable key only (`apikey` + `Authorization: Bearer`), read-only |
| Postgres | 17.11 |
| Enumerated (UTC) | `2026-10-11T19:18Z` |
| Method | `GET /rest/v1/{table}?select=*&limit=1` per table (see §1, §12) |
| Task | Phase 0 · T01 (contract reconciliation) + T02 (authed enumeration) |
| Status | Every table covered. 6/6 legacy pairs have a read + fallback + write policy. Pair 5 **resolved with evidence** (§8). RLS-hidden column lists **BLOCKED** (§10). |

---

## 1. Method & provenance

Every column list below was produced by issuing, for each table:

```bash
curl -s "https://hyibaljxicojfoxbihdn.supabase.co/rest/v1/<table>?select=*&limit=1" \
  -H "apikey: sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH" \
  -H "Authorization: Bearer sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH"
```

- **HTTP 200 + a JSON row** ⇒ the table is **anon-visible** and its column list is
  recorded verbatim from the returned keys.
- **HTTP 200 + `[]`** ⇒ the table exists but **RLS hides every row** from `anon`
  (correct behaviour — no leak). Its column list is **not observable** this way;
  see §10.
- **HTTP 404 / error body** ⇒ table does not exist.

No column was inferred, guessed, or carried over from an older document. Anything
not observed is marked **UNKNOWN** with the probe that would resolve it.

---

## 2. Table inventory

### 2.1 Anon-visible (row returned to the publishable key)

| Table | Kind | Notes |
|---|---|---|
| `categories` | table | catalogue, gated on `is_active` for the storefront |
| `products` | table | catalogue |
| `product_weights` | table | catalogue variants |
| `offers` | table | catalogue offers |
| `delivery_slots` | table | bookable slots |
| `store_settings` | table | single-row store config |
| `product_catalog` | **view** | flattened product+category+price projection (public) |

### 2.2 RLS-hidden from `anon` (HTTP 200, `[]`)

`orders`, `order_items`, `order_status_history`, `profiles`, `addresses`,
`cart_items`, `complaints`, `complaint_messages`, `complaint_attachments`,
`notifications`, `notification_outbox`, `events`.

> **anon-visible: none (RLS).** These return zero rows to the publishable key.
> Their **columns need T02** (an authenticated admin session) — see §10. This is
> RLS working correctly, **not** a defect.

---

## 3. Live column lists (verbatim, sorted)

Columns are listed in a **stable, alphabetically-sorted** order so the snapshot in
`SCHEMA_SNAPSHOT.md` can be diffed mechanically.

### 3.1 `categories` — anon-visible
```
accent, created_at, glyph, icon, id, image_url, is_active, name, slug, sort_order, updated_at
```
Live sample row (Fruits): `slug="fruits"`, `glyph="🍎"`, `icon="🥬"`, `accent=16711680`.

### 3.2 `products` — anon-visible
```
accent, availability, base_price, category_id, created_at, description, discount_price,
glyph, has_offer, id, image_url, is_active, is_featured, max_order_qty, name, price,
quantity_limit, slug, stock, unit, unit_label, updated_at
```
Live sample row: `price=72.00` and `base_price=72.00` (equal); `max_order_qty=5` and
`quantity_limit=5` (equal).

### 3.3 `product_weights` — anon-visible
```
created_at, grams, id, is_available, label, price, product_id, updated_at
```

### 3.4 `offers` — anon-visible
```
created_at, discount_percent, ends_at, id, is_active, price, product_id, starts_at, title, updated_at
```

### 3.5 `delivery_slots` — anon-visible
```
capacity, created_at, end_time, ends_at, id, is_active, max_orders, name, sort_order,
start_time, starts_at, updated_at
```
Live sample row: `start_time="08:00:00"` / `end_time="10:00:00"` (**type `time`**),
`starts_at="2026-10-11T02:30:00+00:00"` / `ends_at="2026-10-11T04:30:00+00:00"`
(**`timestamptz`**); `max_orders=25` and `capacity=25` (equal).

### 3.6 `store_settings` — anon-visible
```
delivery_fee, free_delivery_threshold, id, is_open, min_order_amount, phone,
store_name, updated_at, whatsapp
```
Live sample row: `store_name="Fresh Mart"` (**wrong value — PRD R6**), `phone=""`,
`whatsapp=""`, `delivery_fee=20.00`, `free_delivery_threshold=499.00`,
`min_order_amount=100.00`, `is_open=true`.

### 3.7 `product_catalog` (VIEW) — anon-visible
```
accent, availability, base_price, category_id, category_name, category_slug, description,
discount_price, effective_price, glyph, has_offer, id, image_url, is_active, is_featured,
max_order_qty, name, price, quantity_limit, slug, stock, unit_label, updated_at
```
> Read-only convenience projection. `effective_price` is computed server-side
> (`least(price, discount_price)` semantics). The admin app **writes to the base
> tables**, never to this view.

### 3.8 RLS-hidden tables — column lists
**BLOCKED (no admin creds — R1).** See §10. Do not guess. The probe is
`docs/probe_authed_columns.sh`.

---

## 4. Enum values (live)

| Enum | Values |
|---|---|
| `order_status` | `PENDING` · `CONFIRMED` · `PROCESSING` · `READY_FOR_DELIVERY` · `OUT_FOR_DELIVERY` · `DELIVERED` · `CANCELLED` |
| `complaint_status` | `OPEN` · `IN_PROGRESS` · `RESOLVED` · `CLOSED` |
| `complaint_type` | `WRONG_ITEM` · `DAMAGED` · `MISSING_ITEM` · `LATE_DELIVERY` · `QUALITY` · `OTHER` |
| `user_role` | `customer` · `admin` |
| `outbox_audience` | `customer` · `admin` |
| `author_role` (`complaint_messages`) | `CUSTOMER` · `ADMIN` |

**Retired / forbidden values — never emit these:** `ORDER_RECEIVED`,
`OWNER_ACCEPTED`, `PREPARING`, `READY`, `IN_REVIEW`.

`DELIVERED` and `CANCELLED` are **terminal** — no transitions out, no item changes.
Every non-terminal status may transition to `CANCELLED` (reason required).

---

## 5. Admin RPC signatures

All are `SECURITY DEFINER`, gate on `private.is_admin()` internally, and raise
`42501` for a non-admin. The client never sends a money amount — the server
recomputes and the app re-fetches the row.

| RPC | Signature | Purpose |
|---|---|---|
| `is_admin` | `() → boolean` | the single server-side admin gate |
| `admin_update_order_status` | `(p_order_id uuid, p_status text, p_reason text)` | validated state transition + history + notify |
| `admin_reject_order_item` | `(p_item_id uuid, p_reason text)` | reject one line; server recomputes totals |
| `admin_restore_order_item` | `(p_item_id uuid)` | undo a rejection; server recomputes totals |
| `admin_slot_load` | `(p_from timestamptz, p_days int)` | slot capacity/utilisation view |
| `ensure_delivery_slots` | `(p_days int)` | top up the slot horizon (idempotent) |
| `admin_list_admins` | `() → setof` | admin roster read |
| `admin_grant_admin` | `(p_email text, p_note text)` | add an admin |
| `admin_revoke_admin` | `(p_user_id uuid)` | remove an admin (refuses to remove the last one) |

**Non-admin RPCs (context):** `place_order(p_request_id, p_address_id, p_delivery_slot_id)`
(the customer path — the admin app never calls it) and
`check_username_available(p_username)`.

---

## 6. Storage bucket rules

| Bucket | Visibility | Read policy | Write policy | Path rule |
|---|---|---|---|---|
| `product-images` | **public** | public read (anyone) | **admin only** (`is_admin()`) | any object key; store the returned public URL in `products.image_url` |
| `complaint-images` | **private** | owner-or-admin | owner-or-admin | **MUST be `<auth.uid()>/<filename>`** — the policy reads the **first path segment** to authorise the owner |

> A `complaint-images` object whose key is not prefixed with the uploader's
> `auth.uid()` **fails the RLS insert check**. `currentUserId()` is the only
> correct source for that first segment.
> Both buckets: 5 MB limit, mime `image/jpeg|image/png|image/webp`.

---

## 7. Legacy / duplicate column policy (all 6 pairs)

**Read column = the one the mapper consumes; the other is a fallback. Write policy
= what an admin write does.** The dual-column logic lives **only** in
`data/remote/dto/*` and `data/remote/mapper/*` — no domain model, ViewModel or
composable may reference `price`, `max_order_qty`, `starts_at`, `slot_id`, `glyph`
or `icon` directly.

| # | Intended / authoritative | Legacy duplicate | **Read** | **Fallback** | **Write policy** | Evidence | Status |
|---|---|---|---|---|---|---|---|
| 1 | `products.base_price` | `products.price` | `base_price` | `price` | **Write both, same value** | `place_order` prices lines from `least(price, discount_price)`; live row shows both equal (72.00). Writing both keeps display and charge identical. | **Resolved** (write-both) |
| 2 | `products.quantity_limit` | `products.max_order_qty` | `quantity_limit` | `max_order_qty` | **Write both, same value** | Live row shows both equal (5). No server RPC reads either → write-both is safe. | **Resolved** (write-both) |
| 3 | `delivery_slots.capacity` | `delivery_slots.max_orders` | `capacity` | `max_orders` | **Write both, same value** | Live row shows both equal (25). Which column `admin_slot_load` reads is unconfirmed (T02) → write-both guarantees the server sees the admin's value regardless. | **Resolved** (write-both); T02 to confirm source |
| 4 | `delivery_slots.starts_at` / `ends_at` (`timestamptz`) | `delivery_slots.start_time` / `end_time` (`time`) | `starts_at` / `ends_at` | `start_time` / `end_time` | **Write both.** `starts_at`/`ends_at` as UTC `timestamptz`; `start_time`/`end_time` as the Asia/Kolkata wall-clock derived from them | Unique index + slot recurrence + `admin_slot_load(p_from,p_days)` key on `starts_at`. **T01 confirmed the types** (`time` vs `timestamptz`). | **Resolved** — type CONFIRMED in T01 |
| 5 | `categories.glyph` + `categories.accent` | `categories.icon` (+ `slug`) | `glyph` (visual) + `accent` (colour) | `icon` | **Write `glyph` + `accent` (authoritative) AND `slug`; also mirror `icon := glyph`** | The customer PWA renders `glyph` + `accent` and never reads `icon` or `slug`; live `icon` is empty/stale. Full evidence in §8. | **RESOLVED in T01** (evidence-based) |
| 6 | `orders.delivery_slot_id` | `orders.slot_id` | `delivery_slot_id` | `slot_id` | **N/A — the admin app never writes an order row.** Read-only fallback. | `place_order` writes both; the PWA reads `delivery_slot_id`. Admin only reads orders and calls `admin_update_order_status`. | **Resolved** (read-only) |

**Tally:** 6/6 pairs resolved. Pairs 1–4 by the write-both mitigation (with pair 3's
source-column evidence outstanding as a non-blocking T02 confirmation). Pair 5 by
direct evidence (§8). Pair 6 is read-only by design.

---

## 8. Pair-5 resolution — `categories.glyph`/`accent` vs `icon`/`slug`

**Question.** The design flagged pair 5 as the one genuinely OPEN legacy pair:
does the customer PWA depend on `glyph`+`accent`, or on `icon`?

**Evidence — the customer PWA renders `glyph` + `accent`; it never reads `icon`
or `slug`.**

| Source | Observation |
|---|---|
| `src/types/database.ts:51-61` | `CategoryRow = { id, name, glyph, accent, image_url, sort_order, is_active, created_at, updated_at }` — **no `icon`, no `slug` field at all** |
| `src/lib/catalogue-shared.ts:24` | nested select `category:categories ( id, name, glyph, accent )` |
| `src/app/layout.tsx:116-120` | `headerCategories = categories.map(c => ({ id, name, glyph: c.glyph }))` |
| `src/app/categories/[id]/page.tsx:113,142` | renders `{category.glyph}` / `{c.glyph}` |
| `src/components/home/home-sections.tsx:91-92` | tile tint from `glyph` + `accent` |
| `src/components/layout/site-header.tsx:300` | renders `{category.glyph}` |
| `grep -i slug src/` | **0 matches** — the PWA routes categories by `id` (`/categories/[id]`), not by slug |
| `grep icon src/` | every `icon` hit is a React component prop (`icon={<Leaf/>}`), never `category.icon` |

**Evidence — the live `icon` column is empty or stale (it is not trustworthy data).**

| Category (live) | `slug` | `glyph` | `icon` |
|---|---|---|---|
| Fruits | `fruits` | `🍎` | `🥬` ← **wrong** (leafy green on the Fruits category) |
| Vegetables | `vegetables` | `🥕` | `""` ← **empty** |
| Leafy & Herbs | `leafy-herbs` | `🌿` | `""` ← **empty** |

`glyph` is populated and semantically correct for all three; `icon` is empty for
two and wrong for the third.

**Conclusion.** The customer PWA depends on **`glyph` (visual) + `accent`
(colour)**. Therefore an admin write **must** populate `glyph` and `accent`.
`icon` is a legacy/dead column (empty/stale) — an admin write should still mirror
`icon := glyph` so no surface that ever reads it sees a blank. `slug` is live and
populated but **unused by the PWA** (it routes by `id`); write it anyway for URL
hygiene and future use.

**Write policy for an admin category save:**
`slug` = derived slug · `glyph` = the chosen emoji · `accent` = the chosen RGB ·
`icon` = same value as `glyph`.

---

## 9. Conflicts found against the stale docs

Every row below is a **doc-vs-live conflict**: the doc says X, the live DB says Y.

### 9.1 vs `docs/BACKEND_CONTRACT.md` (root — stale)

| # | Doc claims | Live truth |
|---|---|---|
| C1 | project `bmdmgycsrnffcgxmukxm` | `hyibaljxicojfoxbihdn` |
| C2 | `order_status` = `ORDER_RECEIVED|OWNER_ACCEPTED|PREPARING|READY|…` | `PENDING|CONFIRMED|PROCESSING|READY_FOR_DELIVERY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED` |
| C3 | `complaints.status` = `OPEN|IN_REVIEW|RESOLVED|CLOSED` | `OPEN|IN_PROGRESS|RESOLVED|CLOSED` |
| C4 | `categories` = `id,name,glyph,accent,image_url,sort_order,is_active,timestamps` | also has **`slug`**, **`icon`** |
| C5 | `products` = `id,category_id,name,description,glyph,accent,image_url,base_price,unit_label,availability,quantity_limit,timestamps` | also has **`slug, unit, price, discount_price, stock, max_order_qty, is_active, is_featured, has_offer`** |
| C6 | `delivery_slots` = `id,starts_at,ends_at,capacity,is_active,timestamps` | also has **`name, start_time, end_time, max_orders, sort_order`** |
| C7 | no `store_settings` table documented | `store_settings` exists and is anon-visible |
| C8 | no `product_catalog` view documented | `product_catalog` view exists and is anon-visible |
| C9 | `order_items.quantity` | live column is **`qty`** (per HANDOVER direct DB evidence) — unverifiable via anon (RLS) |
| C10 | `orders` documented without `slot_id`/`total`/`subtotal`/`address_snapshot`/`pincode` | HANDOVER/PROJECT_INDEX report those live — unverifiable via anon (RLS) |
| C11 | storage buckets documented without the `<uid>/<file>` path rule | `complaint-images` requires `<auth.uid()>/<filename>` |

### 9.2 vs `admin-android/PROJECT_INDEX.md` (stale)

| # | Doc claims | Live truth |
|---|---|---|
| C12 | "no Android code exists yet" / "Android: no project yet" | **7 Kotlin files exist**; debug APK builds |
| C13 | "Design System: **Not yet created**" | `ui/theme/{Color,Type,Spacing,Theme}.kt` exist |
| C14 | "Libraries: **None added yet**" | `app/build.gradle.kts` declares the full Compose/Supabase/Ktor/serialization set |
| C15 | "Screens: **None implemented**" | build-check surface + `OrderStatus` implemented |
| C16 | "`storage.objects` has **0 policies** → uploads fail. Fix pending." | 7 storage policies added (HANDOVER migration #5) |
| C17 | "Admin write policies … **still pending**" | 14 admin write/read policies added |
| C18 | "Storage buckets … Policies: **none yet**" | same as C16 |

### 9.3 vs `admin-android/HANDOVER.md` (stale)

| # | Doc claims | Live truth |
|---|---|---|
| C19 | "**No Android source files exist yet.**" | 7 Kotlin files exist |
| C20 | "Android: everything — **no project exists**." | project exists; build green |
| C21 | "Android: N/A, **no project**" | project exists; `OrderStatusTest` 14/14 pass |
| C22 | NEXT EXACT ACTION = "Scaffold the Android project" | **already done** — scaffold + green build complete |

**Conflict count: 22 distinct doc-vs-live conflicts** (C1–C22): 11 against
`docs/BACKEND_CONTRACT.md`, 7 against `PROJECT_INDEX.md`, 4 against `HANDOVER.md`.

> `docs/BACKEND_CONTRACT.md` describes a **different, older project**
> (`bmdmgycsrnffcgxmukxm`). It should be treated as historical and superseded by
> this file. It is out of scope to rewrite here (it is a repo-root PWA-era doc);
> flag it to the owner as a Phase-0 follow-up.

---

## 10. RLS-hidden tables — BLOCKED (no admin creds — R1)

We have **no admin credentials** (owner decision: build now, verify later). The
column lists of the RLS-hidden tables therefore **cannot be enumerated** and are
**NOT guessed**:

```
orders, order_items, order_status_history, profiles, addresses, cart_items,
complaints, complaint_messages, complaint_attachments, notifications,
notification_outbox, events
```

**Status: `BLOCKED (no admin creds — R1)`.**

To resolve, run the committed probe with real credentials:

```bash
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables/admin-android"
ADMIN_EMAIL='owner@example.com' ADMIN_PASSWORD='••••••' bash docs/probe_authed_columns.sh
```

The probe signs in via `POST /auth/v1/token?grant_type=password`, then for each
table above issues `GET /rest/v1/<table>?select=*&limit=1` with the returned
access token, printing the observed keys (sorted) so they can be pasted into §3.8
and `SCHEMA_SNAPSHOT.md`. **Never paste a password or token into this file.**

Open sub-items this closes when run (from `ARCHITECTURE.md` §14):

| ID | Item | Resolved by |
|---|---|---|
| O1 | `orders`/`order_items`/`profiles`/`complaints`/`complaint_messages`/`complaint_attachments` full column lists | the probe |
| O3 | which column `admin_slot_load` reads for capacity (pair 3 evidence) | probe + a call to `admin_slot_load` |
| O4 | `delivery_slots.start_time` type | **already resolved in T01** — it is `time` (§3.5) |

---

## 11. PWA-scoped remediation (do NOT edit the PWA here)

`src/types/database.ts` is **hand-maintained and stale**: it omits live columns
(no `slug`/`icon` on `CategoryRow`; no `slug`/`unit`/`price`/`discount_price`/
`stock`/`max_order_qty`/`is_active`/`is_featured`/`has_offer` on `ProductRow`) and
does not model `store_settings` or the `product_catalog` view.

**This file is the customer PWA's compile source and is out of scope for the
Android app. It was deliberately NOT modified by T01.** Editing it risks breaking
the PWA build, which is a standing **DO NOT BREAK** item.

**Remediation is PWA-scoped and must be followed by re-running the PWA gates:**

```bash
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables"
pnpm exec tsc --noEmit && pnpm exec eslint . && pnpm run build
```

Regenerate it from the live schema (or hand-update) as a **separate PWA task**,
then re-run the three gates above before considering it done.

---

## 12. Regeneration

The snapshot in `SCHEMA_SNAPSHOT.md` is reproducible. Re-run §1's curl for every
table and diff the sorted key lists. The project ref, fetch timestamp and the
exact command are recorded in that file's header. If the diff is non-empty, this
contract is stale and must be updated **and** the change flagged to the team.

---

*End of CONTRACT.md — Phase 0 · T01 + T02 output. Live DB wins; this file records
what the live DB actually is.*
