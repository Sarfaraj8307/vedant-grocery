# Project Report — Vedant Fruit & Vegetables (Customer PWA)

> **Report date:** 2026-10-11 · **Live project:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`)
> **Re-verified today:** `tsc --noEmit ✓` · `eslint ✓` · `next build 22/22 ✓` · live DB probes (see §8)
> **Higher-level docs:** `docs/VERIFICATION_REPORT_2026-10-11.md` (what is implemented vs broken) · `docs/ACTION_PLAN_2026-10-11.md` (next steps) · `docs/PROJECT_INDEX.md` (index)
> **Purpose of this file:** a single report that explains **what the project is, what functionality it possesses, how its functions are organised (graph index), and the detailed contract for building the **separate Admin Android application** on the same Supabase backend.

---

## 1. What the project is

**Vedant Fruit & Vegetables** is a customer-only, mobile-first **grocery storefront PWA** (Next.js 16 App Router + React 19 + Supabase) for a single F&V shop that sells only **fruits, vegetables and herbs, weighed and packed to order**. It deliberately ships **no admin UI and no service-role key**. Admin functionality will live in a **separate Android application** consuming the same Supabase project.

| Aspect | Value |
|---|---|
| Stack | Next.js 16.3.8 (App Router, Turbopack) · React 19.2.8 · TypeScript 5 (strict) · Tailwind v4 (`@theme` tokens) · Zustand 5 (cart) · `@supabase/ssr` · Three.js (hero only) |
| Backend | Supabase Postgres 17.11 — project `hyibaljxicojfoxbihdn`, anon key `sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH` |
| Routes | 22 app pages + proxy (middleware) + manifest/sitemap/robots/opengraph |
| Build state | `tsc` 0 errors · `eslint` 0 errors (warnings only in vendored `.agents/skills/**`) · `next build` 22/22 ✓ |
| Live DB today | 18 public tables, RLS ON (19 policies), seeded: 3 categories · 24 products · 22 weights · 10 offers · 18 slots · `store_settings` 1; **0 storage buckets** |
| Known defects | 4 verified bugs (see `docs/VERIFICATION_REPORT_2026-10-11.md` §3) — most critically `order_items.quantity` vs live `qty` |

### Delivery model (important for admin design)

- **No online payment.** "No payment is collected here. You pay when the order is delivered or as arranged with the shop." → Admin handles COD settlement offline.
- **Same-day slots, not minutes.** Order window group = Morning/Afternoon/Evening; slots are generated server-side as `starts_at/ends_at` windows with capacity.
- **Weigh-to-order.** Every product carries backend variant weights (`product_weights`) with prices — the buyer picks the pack, the server re-prices at `place_order`.
- **Order adjustments.** An item can be rejected by admin (`admin_reject_order_item` → `adjustment_amount ≤ 0` → new `final_total`).

---

## 2. Functionality the customer app possesses (feature map)

### 2.1 Storefront (public)
| Feature | Implementation | Notes |
|---|---|---|
| Home `/` | `HomeSections` + `HomeHero` (SVG baseline + capability-gated 3D `ProduceScene`), `CategoryRail`, `RateBoard`, `TrustStrip`, product rails + shelf grid | one nested `PRODUCT_SELECT`; live offers sorted by saving |
| Categories `/categories`, `/categories/[id]` | `BrowseFilters` URL-driven chips (category / on-offer / in-stock / sort A-Z, price) | server-rendered, shareable, back-button works |
| Search `/search` | `ilike` over name+description (`normaliseSearchTerm` strips wildcards), recent searches persisted, refine chips | 0 N+1 |
| Product `/products/[id]` | weight selector (backend variants, grams-sorted), price breakdown, offer badge, related rail, JSON-LD Breadcrumb | `buildCartLine`/`priceFor` preview |
| Offers `/offers` | live-offer grid, biggest saving first | `pickActiveOffer` window predicate |
| Contact `/contact`, `/about`, `/privacy`, `/terms` | `src/config/store.ts` centralised identity (phone/WhatsApp/address/hours) — null-safe when owner hasn't supplied | Call/WhatsApp buttons hidden until configured |
| SEO | sitemap/robots/opengraph, GroceryStore/Product/BreadcrumbList JSON-LD | `src/config/site.ts` |
| PWA | manifest (standalone), service worker `public/sw.js` (never caches Supabase/auth), icons, `/offline` | install prompt in `PwaProvider` |

### 2.2 Commerce flows
| Flow | How it works |
|---|---|
| **Cart (guest)** | Zustand store persisted to `localStorage vedant.cart.v1`; add/morph stepper (`QtyStepper`), `cartKey(product,weight)`; quantities clamped to `quantity_limit` |
| **Cart (signed-in)** | `CartProvider` reconciles local ↔ server `cart_items` once per sign-in (max-merge), then every mutation mirrors server-side with optimistic UI + rollback; `cart-service` read-then-write tolerating `23505` |
| **Checkout `/checkout`** | single page: address → slot → summary; auth-guarded to `/login?next=%2Fcheckout`; `requestIdRef` stable per session → `place_order` idempotent retry; success → `/order/[id]?placed=1` |
| **Addresses** | CRUD + default (`clear-then-set` for partial unique index), Indian phone/pincode zod validation, geolocation capture, Google-Maps preview, `formatAddressLine/City`, `addressMapsUrl` |
| **Delivery slots** | `getDeliverySlots` (7-day horizon, `is_active AND starts_at > now()`), `getSlotLoad` capacity, `SlotPicker` groups by day, greys full slots, `formatSlotRange/Window` |
| **Orders `/orders`, `/orders/[id]`** | history + detail with `order_status_history` timeline, `final_total`, rejected items; **« Order again »** re-prices against today's catalogue & substitutes pack sizes |
| **Support `/support`, `/support/[id]`** | `complaint-form` (subject/description/optional order), threaded `complaint_messages` (CUSTOMER/ADMIN), reply box ≤2000 chars; attachments via `complaint_attachments` → `complaint-images` bucket (bucket missing live — Bug 3) |
| **Notifications `/notifications`** | reads `notification_outbox` (`audience='customer'`), mark-all-read (Bug 4: docs/read-path mismatch watches) |

### 2.3 Auth & account
| Feature | Implementation |
|---|---|
| Email/password signup, sign-in, forgot/reset | `auth-service`; signup metadata → `ensureProfile()`; email confirmation required (live `mailer_autoconfirm=false`) |
| Google OAuth | hidden unless `getAuthProviderCapabilities()` live probe reports enabled; currently disabled |
| Username availability | `check_username_available` RPC + `deriveUsername` fallback |
| Profile | read/update first/last/phone; username unique |
| Account deletion | request-mode (`clearPersonalData` wipes addresses+cart; no service-role in app) |
| Session | `@supabase/ssr` cookies; `src/proxy.ts` refreshes session, rate-limits `/login /signup /auth/callback /checkout`, sets security headers (`nosniff DENY Referrer-Policy Permissions-Policy COOP`, HSTS prod) |

### 2.4 Money, pricing, validation
- `lib/money.ts` — integer-paise math: `toPaise/fromPaise/addMoney/multiplyMoney/applyPercentDiscount/minMoney`, `formatMoney/formatAmount/splitMoney` (en-IN lakh-grouping).
- `lib/pricing.ts` — **preview only**: `pickActiveOffer` (window + `created_at desc limit 1`), `listPriceFor` (`coalesce(weight.price, base_price)`), `priceFor` (`least(price, discount)` mirroring `place_order`), `cheapestOption`, `cartKey`, `previewDeliveryCharge/previewTotals/lineTotal`.
- `lib/validation.ts` — zod schemas mirroring DB CHECKs (`addressSchema`, `complaintSchema`, etc.).
- `lib/catalogue.ts` + `catalogue-shared.ts` — single `PRODUCT_SELECT`, `toCatalogueProduct`, `getCatalogue/getProductById/getRelatedProducts/getCategories/getLiveOffers/getDeliverySlots/getSlotLoad`, `normaliseSearchTerm/isUuid`.

### 2.5 Cross-cutting
- `lib/auth.ts` (server) — `getSessionUser/getProfile/getProfileOrDefault/requireUser`.
- `lib/env.ts` — lazy validation; **rejects service-role keys**; `isSupabaseConfigured()` guard.
- `lib/rate-limit.ts` — in-memory bucket limiter + `clientIp`.
- `lib/format.ts` — `formatGrams/weightLabel/perUnit/relativeDay/formatDate/formatTime/formatDateTime/formatSlotWindow/formatSlotRange/timeAgo`, `ORDER_STATUS_META/ORDER_STATUS_FLOW/COMPLAINT_STATUS_META`, `pluralise/initials/maskPhone`.
- `lib/browse.ts` — `parseBrowseParams/applyBrowseFilters/sortCatalogue` (in-memory, in-stock-first).
- `lib/cart-line.ts` — `buildCartLine/defaultWeight/unavailableReason`.
- `lib/supabase/{client,server}.ts`, `lib/addresses.ts`, `lib/notifications.ts`.

### 2.6 Current verified defects (to be fixed before admins/owners rely on reports)
1. **P0 `order_items.qty` vs `quantity`** — app selects nonexistent `quantity` (live = `qty`). `/orders/[id]` 400s on real orders; reorder reads `undefined`.
2. **P1 8/24 dead product images** (Beetroot, Carrot, Capsicum, Cucumber, Grapes (Black), Lemon, Mint, Potato) — verified 404 from Unsplash; `ProductImage` now has `onError` → glyph plinth fallback.
3. **P1 0 storage buckets** — docs claim `product-images` + `complaint-images`; live `storage/v1/bucket` = empty (Bug 3 above).
4. **P2 notifications read path** — app reads `notification_outbox`; historical triggers/outbox naming mismatch documented (Bug 4 above).

---

## 3. Graph index — how the modules connect

```
        ┌────────────────────── HTTP (Next.js App Router / proxy.ts) ──────────────────────┐
        │                                                                                  │
 path    src/app/*/page.tsx (22)            src/proxy.ts (session refresh + security)     │
        │  │  │   │                        lib/rate-limit.ts (login/signup/callback/…)    │
        ▼  ▼  ▼   ▼                                                                      │
  ┌─ Server Components ───────────────────────────────────────────────────────────────────┐
  │  lib/auth.ts ── lib/supabase/server.ts ── lib/env.ts                                  │
  │  lib/catalogue.ts (getCatalogue/getProductById/getRelatedProducts/getCategories/…    │
  │    getLiveOffers/getDeliverySlots/getSlotLoad)                                        │
  │  lib/catalogue-shared.ts PRODUCT_SELECT ── lib/pricing.ts (pickActiveOffer/…)        │
  │  lib/addresses.ts · lib/notifications.ts · lib/browse.ts · lib/cart-line.ts           │
  │  lib/money.ts ── lib/format.ts ── lib/validation.ts (zod)                             │
  └───────────────────────────────────────────────────────────────────────────────────────┘
        │
        ▼  (props / JSON serialisable data)
  ┌─ Client Components (src/components) ─────────────────────────────────────────────────┐
  │  ui/*  (button,input,badge,sheet,menu,skeleton,misc,error-state,reveal)              │
  │  layout/ (SiteHeader,BottomNav) · hero/ (HomeHero,HeroVisual) · three/ (ProduceScene) │
  │  products/ (ProductCard,ProductRailCard,ProductGrid/Rail,PriceTag,QtyStepper,        │
  │            ProductPurchase,ProductImage)                                             │
  │  catalogue/ (BrowseFilters) · search/ (SearchField,RecentSearches)                   │
  │  cart/ (CartProvider,StickyCartBar,OrderSummary,CartLineRow)                         │
  │  checkout/ (CheckoutClient,AddressBook,SlotPicker)                                   │
  │  account/ (ProfileForm,AddressManager,NotificationActions,AccountActions,ProfileSync)│
  │  orders/ (OrderStatusBadge,ReorderButton) · support/ (ComplaintForm)                 │
  │  auth/ (AuthForms,AuthShell) · store/ (ContactButtons) · pwa/ (PwaProvider)          │
  │  brand/logo · legal/legal-page · home/ (AnnouncementBar,CategoryRail,RateBoard,…)    │
  └───────────────────────────────────────────────────────────────────────────────────────┘
        │
        ▼  (useCart hook → Zustand store)
  ┌─ State & services (src/hooks, src/stores, src/services) ──────────────────────────────┐
  │  stores/cart-store.ts (Zustand + persist)  hooks/use-cart.ts,use-device-capability    │
  │  services/cart-service.ts   (fetchRemoteCart,persistLine,deleteLine,clearRemoteCart, │
  │                              replaceRemoteCart)                                       │
  │  services/order-service.ts  (placeOrder,newRequestId,listOrders,getOrder,            │
  │                              createComplaint)                                         │
  │  services/auth-service.ts   (checkUsername,deriveUsername,signUpWithPassword,        │
  │                              signInWithPassword,signInWithGoogle,sendPasswordReset,  │
  │                              updatePassword,signOut,ensureProfile,updateProfile,     │
  │                              clearPersonalData,friendlyAuthError)                     │
  │  services/address-service.ts(listAddresses,createAddress,updateAddress,deleteAddress,│
  │                              setDefaultAddress,formatAddressLine,formatAddressCity,  │
  │                              addressMapsUrl)                                          │
  │  services/support-service.ts(createComplaint,listComplaints,getComplaint,             │
  │                              replyToComplaint)                                        │
  │  services/catalogue-client.ts(getProductsByIds)                                       │
  │  lib/supabase/client.ts ── lib/env.ts                                                │
  └───────────────────────────────────────────────────────────────────────────────────────┘
        │
        ▼  PostgREST / Auth / Storage
  ┌─ Supabase (project hyibaljxicojfoxbihdn) ─────────────────────────────────────────────┐
  │  RPCs:  place_order(p_request_id, p_address_id, p_delivery_slot_id) [verified 401]   │
  │         check_username_available(p_username)                                          │
  │  (missing live): admin_update_order_status · admin_reject_order_item · is_admin       │
  │  Tables: profiles,categories,products,product_weights,offers,delivery_slots,          │
  │          addresses,cart_items,orders,order_items,order_status_history,complaints,     │
  │          complaint_messages,complaint_attachments,notifications,notification_outbox,  │
  │          store_settings,events · view product_catalog                                 │
  │  Storage: product-images (public) · complaint-images (private) — BOTH MISSING live    │
  └───────────────────────────────────────────────────────────────────────────────────────┘
```

### Data-dependency digraph (who calls what)
- `page.tsx` (server) → `lib/catalogue.ts` → `catalogue-shared.ts` + `pricing.ts` + `money.ts` → `supabase/server.ts` → `env.ts`
- Cart/checkout (client) → `hooks/use-cart.ts` → `stores/cart-store.ts` + `components/cart/cart-provider.tsx` → `services/cart-service.ts` → `supabase/client.ts`
- Checkout → `checkout-client.tsx` → `services/order-service.ts` → RPC `place_order`; `services/address-service.ts`; `services/catalogue-client.ts` (reorder)
- Accounts/support → `services/auth-service.ts` / `address-service.ts` / `support-service.ts` → RLS-gated PostgREST
- Shared domain bytes: `money.ts` (integer paise), `pricing.ts` (single price rule), `catalogue-shared.ts` (single row→domain mapper) — **the three modules that must stay in lock-step with the DB** (and with the future admin app).

---

## 4. Function inventory (all exported functions, by module)

> `src/` = 110 files. Below is the full exported-function inventory for lib/services/stores/hooks/proxy/config.

### `src/lib`
| Module | Exports |
|---|---|
| `money.ts` | `toPaise, fromPaise, addMoney, multiplyMoney, applyPercentDiscount, minMoney, formatMoney, formatAmount, splitMoney, CURRENCY` |
| `pricing.ts` | `pickActiveOffer, isOfferLive, listPriceFor, priceFor, cheapestOption, cartKey, previewDeliveryCharge, previewTotals, lineTotal, SHOP_SETTINGS_PREVIEW` |
| `catalogue-shared.ts` | `PRODUCT_SELECT, toCatalogueProduct, ProductNestedRow` |
| `catalogue.ts` | `getCatalogue, getProductById, getRelatedProducts, getCategories, getCategoryById, getLiveOffers, getDeliverySlots, getSlotLoad, isUuid, normaliseSearchTerm, CatalogueError, CatalogueQuery` |
| `catalogue-client.ts` | `getProductsByIds` (see §services) |
| `cart-line.ts` | `buildCartLine, defaultWeight, unavailableReason` |
| `browse.ts` | `parseBrowseParams, applyBrowseFilters, sortCatalogue` |
| `format.ts` | `formatGrams, weightLabel, perUnit, relativeDay, formatDate, formatTime, formatDateTime, formatSlotWindow, formatSlotRange, timeAgo, orderStatusLabel, complaintStatusLabel, pluralise, initials, maskPhone, ORDER_STATUS_META, ORDER_STATUS_FLOW, COMPLAINT_STATUS_META` |
| `validation.ts` | zod schemas: `addressSchema, complaintSchema, AuthSchema…` (mirrors DB CHECKs) |
| `auth.ts` (server) | `getSessionUser, getProfile, getProfileOrDefault, requireUser` |
| `auth-providers.ts` (server) | `getAuthProviderCapabilities` |
| `addresses.ts` (server) | `listAddressesForUser` |
| `notifications.ts` (server) | `listNotifications, countUnread` |
| `env.ts` | `publicEnv, isSupabaseConfigured` (reject service_role) |
| `rate-limit.ts` | `rateLimit, clientIp` |
| `supabase/client.ts` | `getSupabaseBrowserClient` |
| `supabase/server.ts` | `getSupabaseServerClient` |
| `utils.ts` | `cn` |

### `src/services` (all `"use client"`)
| Module | Exports |
|---|---|
| `auth-service.ts` | `friendlyAuthError, checkUsername, deriveUsername, signUpWithPassword, signInWithPassword, signInWithGoogle, sendPasswordReset, updatePassword, signOut, ensureProfile, updateProfile, clearPersonalData, ACCOUNT_DELETION_MODE, AuthResult` |
| `address-service.ts` | `listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress, formatAddressLine, formatAddressCity, addressMapsUrl, AddressResult` |
| `cart-service.ts` | `fetchRemoteCart, persistLine, deleteLine, clearRemoteCart, replaceRemoteCart, CartServiceError, RemoteCartLine` |
| `order-service.ts` | `placeOrder, newRequestId, listOrders, getOrder, createComplaint, OrderError, OrderWithItems` |
| `support-service.ts` | `createComplaint, listComplaints, getComplaint, replyToComplaint, SupportResult` |
| `catalogue-client.ts` | `getProductsByIds` |

### `src/stores`, `src/hooks`
| Module | Exports |
|---|---|
| `stores/cart-store.ts` | `useCartStore` (add/setQuantity/increment/decrement/remove/clear/replaceAll/refreshSnapshots/setSyncing/setSyncError/quantityOf), `selectLines, cartCount, cartSubtotal, cartListSubtotal, CartLine, CartStore` |
| `hooks/use-cart.ts` | `useCart, useCartQuantity` |
| `hooks/use-device-capability.ts` | device capability probe for 3D gating |

### `src/config`, `src/proxy`, `src/types`
| Module | Exports |
|---|---|
| `config/store.ts` | `store, whatsappHref, telHref, hasPhone, hasWhatsApp, whatsappLink` |
| `config/site.ts` | `site, absoluteUrl` |
| `config/catalogue.ts` | catalogue constants (unit labels, suggestions) |
| `proxy.ts` | `proxy` + `config.matcher` (session refresh, rate-limit, security headers) |
| `types/database.ts` | `OrderStatus, ComplaintStatus, ComplaintAuthorRole, MediaType, ProfileRow, CategoryRow, ProductRow, ProductWeightRow, OfferRow, DeliverySlotRow, AddressRow, CartItemRow, OrderRow, OrderItemRow, OrderStatusHistoryRow, ComplaintRow, ComplaintMessageRow, ComplaintAttachmentRow, NotificationRow, CatalogueProduct, PriceBreakdown` |

### `src/components` (feature surface, not every DOM token)
Layout: `SiteHeader, BottomNav` · Home: `AnnouncementBar, CategoryRail, RateBoard, TrustStrip, HomeSections, HomeHero, HeroVisual` · Three: `ProduceScene` · Products: `ProductCard, ProductRailCard, ProductGrid, ProductRail, PriceTag, SavingLabel, QtyStepper, ProductPurchase, ProductImage` (+`accentToHex`) · Catalogue: `BrowseFilters` (`FilterChip, CategoryChips, RefinementChips, SortControl, ActiveFilterSummary, buildBrowseHref`) · Search: `SearchField, RecentSearches` · Cart: `CartProvider (+useCartSession), StickyCartBar, OrderSummary, CartLineRow` · Checkout: `CheckoutClient, AddressBook, SlotPicker` · Account: `ProfileForm, AddressManager, NotificationActions (MarkAllReadButton), AccountActions, ProfileSync` · Orders: `OrderStatusBadge, ReorderButton` · Support: `ComplaintForm, ComplaintReplyBox` · Auth: `AuthForms, AuthShell` · Store: `ContactButtons` · PWA: `PwaProvider` · Brand: `Logo` · Legal: `LegalPage` · UI: `Button, Input/Field/Textarea, Badge, Skeleton, Sheet*, Menu/RadioGroup/Select, Misc (SectionHeading/EmptyState), ErrorState, Reveal`.

---

## 5. Verified live database state (probed today — source of truth for admin build)

| Item | Live status (2026-10-11) |
|---|---|
| Storage buckets | **0** (`storage/v1/bucket` → empty). Missing `product-images`, `complaint-images`. |
| `place_order(p_request_id,p_address_id,p_delivery_slot_id)` | **Exists** — anon call → HTTP 401 (matches report). |
| `check_username_available` | **404** on empty-body probe → verify with `p_username` before relying (see §7.1 note). |
| `admin_update_order_status` | **MISSING live** (PGRST202). |
| `admin_reject_order_item` | **MISSING live** (PGRST202). |
| `gen_order_number` | Exists (utility). |
| Tables | 18 public tables, RLS ON, 19 least-privilege policies (verified §8 of previous report). |

> ⚠️ **Doc-drift warning.** `docs/BACKEND_CONTRACT.md` and `docs/FUTURE_ADMIN_APP*.md` describe the **old** project state (`bmdmgycsrnffcgxmukxm`, 45 policies, buckets created, admin RPCs present). Those were true on the old project but are **false on the live `hyibalj...`**. The admin app must be built against the **verified live** facts above + the additions in §7.

---

## 6. What the future Admin application must be able to do (feature breakdown)

The admin app is a **separate Android application** (see `docs/FUTURE_ADMIN_APP.md`). Shared backend = same Supabase project. It must authenticate as an **admin user** (listed in `private.admin_users` / `profiles.role='admin'`) and perform:

### 6.1 Order lifecycle management
- List all orders, filter by status/slot/date/customer.
- View order detail: line items (with `qty`, unit price, line total, rejected flags), **status timeline** (`order_status_history`), address, delivery slot, totals (`original_total`, `delivery_charge`, `adjustment_amount`, `final_total`).
- Transition status via `admin_update_order_status` (7-state machine below) — insert `order_status_history`.
- Reject an item with reason via `admin_reject_order_item` → recompute `final_total`.
- View per-slot load (`count(*) WHERE status<>'CANCELLED'`).

**Status machine (customer PWA already knows it):**
`ORDER_RECEIVED → OWNER_ACCEPTED → PREPARING → READY → OUT_FOR_DELIVERY → DELIVERED` ; any → `CANCELLED` (reason required).

### 6.2 Catalogue management
- CRUD categories / products / **product_weights** (variant grams + price) / **offers** (time-windowed: `starts_at/ends_at`, `price` or `discount_percent`).
- Toggle `availability`/`is_available` (soft-hide, never delete history).
- Upload photography to `product-images` bucket; set `image_url`.

### 6.3 Delivery slots
- View/create/deactivate slots (`delivery_slots`: `starts_at/ends_at/capacity/is_active`).
- **Recurring generation** (critical — current seed expires 2026-10-16; checkout blocks after). Pattern: generate next N days 09:00–20:00 30-min windows, capacity e.g. 20, `ON CONFLICT DO NOTHING`.

### 6.4 Customer support
- List complaints + threads (`complaint_messages`), reply as `ADMIN`, mark `status` `IN_REVIEW`/`RESOLVED` (+`resolution`, `resolved_by`, `resolved_at`).
- View complaint attachments (requires `complaint-images` bucket + RLS).

### 6.5 Admin roster & account
- Grant/revoke admin (`private.admin_users` or `profiles.role`) — via Edge Function + service-role (never in APK).
- View store_settings; set delivery fee / free threshold / min order / is_open.

### 6.6 Analytics (future)
From `events` (append-only `page_view|search|add_to_cart|place_order`; no PII) + orders: orders/day, revenue (`final_total`), popular products/categories, conversion `place_order/add_to_cart`, repeat customers, per-slot performance.

---

## 7. Backend contract the admin app must be written against (live-first)

### 7.1 Reuse the verified live RPC surface
| RPC | Signature (live today) | App usage |
|---|---|---|
| `place_order` | `(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid) → uuid` (verified 401 on anon) | Customer checkout; admin may auto-replace later as `place_order_v2` |
| `check_username_available` | `(p_username text) → boolean` (app calls with `{p_username}` — *probe above sent empty body, hence 404; app contract documents this RPC*) | signup |

### 7.2 RPCs the admin app requires — **do NOT exist live yet** (create as migrations)
```sql
-- Admin state machine (SECURITY DEFINER, search_path='', gate on private.is_admin())
create or replace function public.admin_update_order_status(
  p_order_id uuid, p_status text, p_reason text default null
) returns uuid  security definer set search_path='' language plpgsql as $$ … -- validate transition, insert order_status_history, update orders.status, notify via outbox
$$;

create or replace function public.admin_reject_order_item(
  p_item_id uuid, p_reason text
) returns void security definer set search_path='' language plpgsql as $$ … -- set rejected=true, raw reason, recompute adjustment (≤0) + final_total, notify customer
$$;

create or replace function private.is_admin() returns boolean
  security definer set search_path = '' stable language sql as $$
    select exists (select 1 from private.admin_users a where a.user_id = (select auth.uid()));
$$;
create table if not exists private.admin_users (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
```
> Match the **live status enum** used by `orders.status`. The PWA renders `ORDER_RECEIVED|OWNER_ACCEPTED|PREPARING|READY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED`; live `hyibalj...` may store an equivalent enum — **verify before writing** the wrapper so the mapping stays honest.

### 7.3 Tables the admin app owns (live, RLS-gated — *all exist*)
Catalogue: `categories, products, product_weights, offers, delivery_slots` (admin write = `is_admin()`; public/anon read only `is_active`).
Orders & support: `orders, order_items, order_status_history, complaints, complaint_messages, complaint_attachments` (admin read-all / write via RPC).
Cross: `notifications` / `notification_outbox` (admin writes rows; customer reads own), `store_settings` (singleton), `events` (append-only), `profiles` (role).

### 7.4 Storage
| Bucket | Visibility | RLS | Handles |
|---|---|---|---|
| `product-images` | public | public read, admin write | `products.image_url` / `categories.image_url` |
| `complaint-images` | private | owner-or-admin | `complaint_attachments.storage_path` |
> **Both must be created** (live = 0 buckets) before the admin app can upload.

### 7.5 Security rules for the admin build
- **Never ship service-role key** in the APK — RLS + `SECURITY DEFINER` RPCs are the boundary.
- Authenticate against the **same Supabase project**; verify `is_admin()` server-side and handle `403` gracefully.
- `private` schema is not reachable from clients → roster management via Edge Function (service-role).
- Pin `search_path` on all definer functions; revoke `EXECUTE` from `anon/authenticated` on `handle_new_user / notify_* / sync_profile_phone`.
- Money stays `numeric(12,2)`; compute adjustments exactly (`adjustment_amount ≤ 0`, `final_total = original_total + adjustment_amount`, `CHECK`s enforced).

### 7.6 Recommended Android stack (from `FUTURE_ADMIN_APP.md` §7)
Kotlin · MVVM + Compose · `supabase-kt` · Room + DataStore (offline-first order list) · Compose Navigation · Hilt · Ktor · Coil · WorkManager (periodic slot refresh/generation). Push via Supabase Edge Functions + FCM (future).

---

## 8. Quick-start / handoff

1. **Read in order:** `VERIFICATION_REPORT_2026-10-11.md` → `PROJECT_INDEX.md` → this file → `ACTION_PLAN_2026-10-11.md` → `FUTURE_ADMIN_APP.md`.
2. **On the backend:** create the 2 storage buckets, add the 3 admin RPCs + `private` roster (SQL above), grant first admin, run recurring-slot generation.
3. **Fix the 4 customer-app bugs** (§2.6) — especially the `qty` rename — before any admin dashboard depends on `/orders/[id]` data.
4. **Build the admin Android app** against §6 + §7 tables and RPCs (they share the same Supabase project).
5. **Never put service-role keys in any client.**

---

*This report was generated from fresh source inspection (110 files) + live DB/RPC probes on 2026-10-11. Where historical docs conflict with the live database, the live database wins and the conflict is flagged inline.*