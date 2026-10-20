# API Contract — Vedant Fruit & Vegetables (Customer PWA)

> **Date:** 2026-10-10 · **Live project:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`) · **Build:** `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓`
> **Source of truth for transport:** PostgREST via `@supabase/ssr` / `@supabase/supabase-js@2.117` — no custom API server. Every row in this doc is read from an inspected source (`src/lib/catalogue.ts`, `src/services/*`, `src/types/database.ts`, `src/lib/validation.ts`, `src/lib/pricing.ts`, `src/app/auth/callback/route.ts`, `src/proxy.ts`, live routines §7) — not a heuristic.

## 0. Principles

* **Supabase is the API.** The customer PWA has one Route Handler (`src/app/auth/callback/route.ts`) and one Next 16 `proxy.ts` — everything else is `supabase.from(...).select/insert/update/delete` or `supabase.rpc(...)` through `@supabase/ssr`. There is no service-role client in this repo (`lib/supabase/server.ts` binds to request cookies; `lib/env.ts` rejects `sb_secret_*`).
* **Server is authority.** `lib/pricing.ts:priceFor/cheapestOption/previewTotals` is an explicitly-labelled **preview**; `place_order` recomputes `least(price, coalesce(discount_price,price))` (contract shape) or `least(price, discount_price)` (live `hyibalj...`) inside a transaction plus availability, `quantity_limit/max_order_qty`, `stock`, `min_order_amount`, `delivery_fee/free_threshold`, slot capacity. Client totals are never charged.
* **Idempotent writes.** `POST place_order` and `INSERT cart_items` tolerate `23505 unique_violation` (concurrent double-tap / replay). The client reuses a stable idempotency key per intended order (`requestIdRef` / `p_idempotency_key`) so a lost response can be retried without duplicating the order.
* **RLS is the boundary.** `proxy.ts` does session refresh + security headers only; it does not authorise. `lib/auth.ts:getUser()` (not `getSession()`) is used server-side. Live `hyibalj...` has **RLS OFF on all 12 tables, 0 policies** — P0, see `docs/SUPABASE_AUDIT.md` §3 — this contract describes the target once hardened.
* **Money is integer paise.** `lib/money.ts:toPaise/fromPaise/addMoney/multiplyMoney/applyPercentDiscount/minMoney` + `formatMoney en-IN`; DB is `numeric(12,2)`.

## 1. Transports

### 1.1 PostgREST (Supabase client)

| Table / View | Read | Write | Client entry point | Live divergence |
|---|---|---|---|---|
| `categories` | `select * where is_active order sort_order` | admin only (`is_admin()`) | `lib/catalogue.ts:getCategories()` | live has `icon/slug` not `glyph/accent/image_url` |
| `products` / `product_catalog` | `select PRODUCT_SELECT where availability|is_active, ilike name,description, eq category_id, offersOnly in-memory, limit` `order availability desc, name` | admin only | `lib/catalogue.ts:getCatalogue({categoryId,search,offersOnly,limit})`, `getProductById(id)`, `getRelatedProducts(product,8)` | `PRODUCT_SELECT` selects `glyph/accent/base_price/unit_label/availability/quantity_limit/product_weights/offers` — none exist live; live is `price/discount_price/stock/max_order_qty/unit` |
| `product_weights` | `product_weights(*)` nested | admin only | `PRODUCT_SELECT` nested + `toCatalogueProduct` (grams-sorted) | **missing live** |
| `offers` | `offers(*)` nested | admin only | `PRODUCT_SELECT` nested + `pickActiveOffer` (`is_active && starts_at/ends_at` → `created_at desc limit 1`) | **missing live** — live inline `discount_price` |
| `delivery_slots` | `select * where is_active and starts_at > now() and starts_at < horizon order starts_at` (horizon 7d) vs `getSlotLoad(): select delivery_slot_id,status where status<>'CANCELLED' and delivery_slot_id not null` | admin only | `lib/catalogue.ts:getDeliverySlots(horizonDays)`, `getSlotLoad()` → grey out full | live is `name + start_time/end_time time + max_orders` — no `starts_at/capacity` |
| `addresses` | `select * order is_default desc, created_at` | `insert {label,full_name,phone,house_number,street,landmark,city,state,postal_code,is_default}` → clear-then-set default via `update is_default false where is_default true` | `lib/addresses.ts:listAddressesForUser()`, `services/address-service:list/create/update/delete/setDefault` | live minimal `label∈{Home,Work,Other}, line1/line2/city/pincode 6-digit` vs target multi-column + 5..10 pincode |
| `cart_items` | `select product_id,weight_id,quantity order created_at` | read-then-write: `select id where product_id,weight_id` → `update quantity` or `insert` else `delete where product_id,weight_id`; `replaceRemoteCart` = `delete where user_id=auth.uid()` + `insert lines` | `services/cart-service:fetchRemoteCart/persistLine/deleteLine/clearRemoteCart/replaceRemoteCart` + `stores/cart-store` + `components/cart/cart-provider` | **missing live** — guest Zustand persists, signed-in errors |
| `orders` + `order_items` + `order_status_history` | `select * order created_at desc limit 50`, `select ORDER_DETAIL_SELECT where id=orderId` (`*, order_items(...), order_status_history(*)`) | no customer `INSERT` policy — via `place_order` RPC only | `services/order-service:listOrders(50)/getOrder(id)` | live `orders` is `order_number/delivery_date/slot_id/address_snapshot/subtotal/delivery_fee/total/idempotency_key/status PENDING…`, `order_items` is `unit/line_total generated` without weight/rejected, `order_status_history` **missing** |
| `complaints` + `complaint_messages` + `complaint_attachments` | complaints read self; messages ordered by `created_at` | `insert {subject,description,order_id}` (user_id=enforced), messages `author_role CUSTOMER\|ADMIN`, attachments → storage | `services/support-service`, `app/support/[id]/page.tsx` | `complaint_messages` **missing** (live has `admin_reply inline`), live `complaints.type enum` + `status OPEN/IN_PROGRESS…` vs target `subject` + `OPEN/IN_REVIEW…` |
| `notifications` vs `notification_outbox` | `select * order created_at desc limit 50` | no customer `INSERT` | `lib/notifications.ts:listNotifications/countUnread`, `components/account/notification-actions` | app reads `notifications`; live populates `notification_outbox(audience, type,title,body,data)` via `notify_order/complaint_event` |
| `profiles` | `select * where id=auth.uid()` → `maybeSingle()` → `getProfileOrDefault` displayName fallback | `insert on first authenticated visit` via `ProfileSync`; updates via `profile-form` | `lib/auth.ts:getSessionUser/getProfile/getProfileOrDefault/requireUser` | target `username/first_name/last_name/email` vs live `full_name/phone/role` via `handle_new_user` |
| `store_settings` | `select * where id=1` | admin only | `getSlotLoad` capacity via `store_settings.free_delivery_threshold` + `delivery_fee/min_order_amount/is_open` | matches live |
| `events` | insert `session_hash 8..64, event page_view|search|add_to_cart|place_order, path ≤300, meta jsonb` | no customer select | not yet wired in PWA (future analytics) | live table exists and matches check |

### 1.2 RPCs

#### `place_order` — **only** customer write path for orders

**App contract (what the code calls):**
```ts
supabase.rpc("place_order", {
  p_request_id: string,      // stable per intended order, 8..128 chars (requestIdRef)
  p_address_id: string,      // uuid → addresses.id (ownership checked)
  p_delivery_slot_id: string // uuid → delivery_slots.id
}) → string (order_id)
// throws CatalogueError/OrderError mapped from 42501/22023 and
// "cart item unavailable | cart is empty | delivery slot full/unavailable |
//  address unavailable | authentication required | invalid request id"
```

**Live `hyibalj...` (what the DB exposes):**
```sql
place_order(
  p_address_id uuid,
  p_delivery_date date,         -- must be current_date or +1
  p_idempotency_key uuid,       -- required, unique
  p_items jsonb,                -- [{product_id uuid, qty int 1..50}] 1..50 elements
  p_slot_id uuid                -- FOR UPDATE, is_active, count(status<>'CANCELLED') < max_orders
) returns jsonb
-- jsonb is {order_id,order_number,subtotal,delivery_fee,total} on success
-- or {error:'UNAUTHENTICATED'|'VALIDATION'|'STORE_CLOSED'|'INVALID_DATE'|
--        'INVALID_ADDRESS'|'INVALID_SLOT'|'SLOT_FULL'|'PRODUCT_UNAVAILABLE'|
--        'QTY_EXCEEDED'|'OUT_OF_STOCK'|'BELOW_MIN_ORDER'|'DUPLICATE_OR_RETRY'}
-- also stores address_snapshot{label,line1,line2,city,pincode}, decrements stock, idempotent via on conflict do nothing
```

**Contract mismatch (P0):** client sends `p_request_id/p_delivery_slot_id` with no `p_items/p_delivery_date/p_idempotency_key` → every checkout 400s before business logic. Fix: `API_CONTRACT` §4 pipeline.

**App-side error mapping (target):**
| Client copy | Trigger |
|---|---|
| `One of your items just went out of stock…` | `cart item unavailable` |
| `Your basket is empty…` | `cart is empty` |
| `That delivery slot just filled up…` | `delivery slot full` / `SLOT_FULL` |
| `That slot is no longer available…` | `delivery slot unavailable` / `INVALID_SLOT` |
| `We could not verify that delivery address…` | `address unavailable` / `INVALID_ADDRESS` |
| `Your session expired…` | `authentication required` / `UNAUTHENTICATED` |
| `Something went wrong submitting…` | `invalid request id` / `VALIDATION` |
| `We could not place your order…` | any other |

#### `private.is_admin() / auth.uid()` — authorization helpers

App never calls directly; RLS policies do. Live `handle_new_user` promotes `admin@freshmart.local → admin`. `docs/FUTURE_ADMIN_APP_CONTRACT.md` defines `private.admin_users + is_admin()` once `private` schema exists — live has no `private` schema yet.

#### `gen_order_number()` — `SELECT 'ORD-'||to_char(now(),'YYMMDD')||'-'||upper(substr(md5(random()::text||clock_timestamp()::text),1,5))`

### 1.3 Route Handler & Proxy

| Surface | Method | Auth | Purpose |
|---|---|---|---|
| `src/app/auth/callback/route.ts` | GET | Supabase auth exchange | OAuth / email confirm redirect |
| `src/proxy.ts` (`proxy()`) | every non-asset `GET` | binds `request.cookies ↔ createServerClient(...).auth.getUser()` | session refresh (rotate cookies) + `X-Content-Type-Options nosniff / X-Frame-Options DENY / Referrer-Policy strict-origin-when-cross-origin / Permissions-Policy (camera,microphone,geolocation…) / COOP same-origin / HSTS in prod` — **not auth** |

## 2. Catalogue reads — `PRODUCT_SELECT`

**Target select (single nested fetch, no N+1):**
```sql
select
  id, category_id, name, description, glyph, accent, image_url,
  base_price, unit_label, availability, quantity_limit, created_at, updated_at,
  category:categories (id, name, glyph, accent),
  product_weights (id, product_id, label, grams, price, is_available, created_at, updated_at),
  offers (id, product_id, title, price, discount_percent, starts_at, ends_at, is_active, created_at, updated_at)
from products
order availability desc, name asc
-- filters: eq category_id, ilike name,description via normaliseSearchTerm (strips %_,(),\), offersOnly in-memory via pickActiveOffer, limit
```

Mapper: `catalogue-shared.ts:toCatalogueProduct(row, now) = { ...product, category, weights: weights.sort(grams), activeOffer: pickActiveOffer(offers, now) }` + `lib/catalogue.ts:getCategories() .eq(is_active,true).order(sort_order)`, `getLiveOffers(): getCatalogue({offersOnly:true}) | saving desc`, `isUuid` guard, `normaliseSearchTerm` trim→collapse→strip→slice 80, `getRelatedProducts(category_id, neq id, limit 8)`, horizon `now → now+7d` for slots.

**Live break:** `PRODUCT_SELECT` hits nonexistent columns/tables → `CatalogueError` on every catalogue surface (`/`, `/categories`, `/products/[id]`, `/offers`, `/search`).

## 3. Client services (typed wrappers)

* **Auth** `services/auth-service.ts` — `signUp({firstName,lastName,email,username,password})` `CHECK ^[a-z0-9_]{3,24}$` via `usernameSchema`, `signIn {email,password}`, `signOut`, `Google` gated by `lib/auth-providers.ts` live probe, `ensureProfile` on first visit, `clearPersonalData` `mode=request` only (no service-role delete), `forgot/resetPassword`.
* **Cart** `services/cart-service.ts` — documented in §1.1; app invariant: `cartKey(productId,weightId)` === partial-unique identity; tolerates `23505`.
* **Order** `services/order-service.ts` — `placeOrder({requestId,addressId,deliverySlotId}):Promise<string>` + `newRequestId(): 'ord_'+uuid`, `listOrders(50)`, `getOrder(id) → OrderWithItems {order_items,order_status_history}`, `createComplaint({subject,description,orderId})`, `reorder` reprices via `catalogue-client:getProductsByIds(ids)`.
* **Address** `services/address-service.ts` — `list/create/update/delete/setDefault` + `clearDefaultFlags` respecting `one_default_per_user WHERE is_default`.
* **Support** `services/support-service.ts` — `createComplaint` scoped `user_id=auth.uid()` + order ownership check in policy.

## 4. Validation (`src/lib/validation.ts` — mirrors DB CHECKs)

* `profileUpdateSchema: firstName/lastName ≤60`, `phone 10..15`
* `signInSchema`, `usernameSchema ^[a-z0-9_]{3,24}$`, `signUpSchema {firstName trimmed 60, lastName optional, email, username, password 8..72, acceptedTerms literal true}`, `forgotPasswordSchema`, `resetPasswordSchema (8..72 + match)`
* `addressSchema {label 1..40, full_name 1..120, phone 10..15, house_number optional ≤80, street 1..80, landmark optional ≤80, city 1..80, state 1..80, postal_code ^[0-9]{5,10}$ (live 6-digit), latitude/longitude optional}`
* `complaintSchema {subject 1..160, description 10..10000, orderId uuid optional}`

## 5. Pricing (`src/lib/pricing.ts` + `lib/money.ts`)

`pickActiveOffer` = same predicate `place_order` uses (`is_active && (starts_at IS NULL||≤now) && (ends_at IS NULL||>now) order created_at desc limit 1`). `priceFor(product, weight)` = `least(coalesce(weight.price, base_price), offer.price, base_price*(1-discount_percent/100))` via `minMoney/applyPercentDiscount` using `Math.round`; `cheapestOption` scans available weights; `SHOP_SETTINGS_PREVIEW {deliveryCharge:20, freeDeliveryThreshold:500}`, `previewDeliveryCharge`, `previewTotals`/`TotalsPreview {subtotal/listSubtotal/savings/deliveryCharge/total/freeThreshold/amountToFreeDelivery}`.

## 6. Checkout pipeline (target)

`CheckoutClient({initialAddresses, slots, slotLoad})`: local `selectedAddressId` (default `is_default || first`) + `selectedSlotId` + stable `requestIdRef = newRequestId()` for the session; `readyToPlace = address && slot && !isEmpty && !hydrating`; `placeOrder({requestId:requestIdRef.current, addressId, deliverySlotId}) → clear() → requestIdRef.current=newRequestId() → router.push('/order/'+orderId+'?placed=1')`; on error map `OrderError` vs generic; guards `Choose address/slot` alerts.

## 7. Delivery-slot & notification helpers

`getDeliverySlots(horizonDays=7): .eq(is_active,true).gt(starts_at,now).lt(starts_at,horizon).order(starts_at)` + `getSlotLoad(): count per delivery_slot_id where status<>'CANCELLED'` → `SlotPicker` greys full; `format.ts: ORDER_STATUS_META`, `formatSlotWindow`, `formatMoney`.

## 8. Auth helpers (`src/lib/auth.ts`)

`getSessionUser(): supabase.auth.getUser() → {id,email,phone} | null`, `getProfile(): from profiles where id=user.id maybeSingle()`, `getProfileOrDefault() → {user,profile,displayName,firstName}` (no trigger guarantee), `requireUser(nextPath): redirect /login?next=`, all `server-only`.

## 9. Errors & UX

* `CatalogueError` from `lib/catalogue.ts` — never shows DB text to customer; route `error.tsx`/`global-error.tsx` render friendly.
* `OrderError(sqlState?)` from `order-service` — `mapPlaceOrderError` on `42501/22023` substrings; live needs extend to `UNAUTHENTICATED/INVALID_*` JSONB.
* `CartServiceError` — toast `We could not … your basket` + rollback `replaceAll(snapshot)`.

## 10. Security & transport rules

* Only `NEXT_PUBLIC_SUPABASE_URL` + `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` (fallback anon) may appear in env (validated; `lib/env.ts:looksLikeServiceRoleKey` decodes JWT `role=service_role` and throws).
* `next.config.ts:images.remotePatterns` allowlist tied to `NEXT_PUBLIC_SUPABASE_URL` storage host + dev `images.unsplash.com` only in `NODE_ENV=development`; `minimumCacheTTL 30d`; `optimizePackageImports [lucide-react,motion]` (three excluded, code-split in hero); headers for `sw.js no-cache` / `manifest.webmanifest`.
* `public/sw.js` **never caches Supabase/auth**, network-first nav 2.5s→cache→`/offline`, SWR static.

## 11. Reconciliation to live `hyibalj...`

**When to lock:** Before enabling checkout (`min_order_amount 100` live, `delivery_fee 20 / free 499`, `max_order_qty 10`, slot capacity 25×3 slots time-based).

**Path:** Branch A/B per `docs/DATABASE_ARCHITECTURE.md` §7 — either create `product_weights/offers/cart_items/order_status_history/complaint_messages/notifications (+ columns/buckets)` + `place_order` wrapper, or adapt `PRODUCT_SELECT/pricing/cart-service/order-service/lib/catalogue.ts` to live `price/discount_price/stock` + `p_items/p_delivery_date/p_idempotency_key/p_slot_id`. Either way add `ENABLE RLS` + policies (catalogue `is_active` public, owned `auth.uid()=user_id`, revoke DEFINER EXECUTE, pin `search_path`), create `complaint-images` bucket, bridge `delivery_slots name+time → timestamptz` for `today|tomorrow` in `Asia/Kolkata`.

No destructive ops — forward `supabase/migrations/*_*.sql` only. Success is `GET /,/categories,/search,/products/[id],/offers` populated + `place_order` e2e (guest+authenticated, slot-full guard, idempotent replay) + `/orders/[id]` timeline + `npm run build 22/22`.

