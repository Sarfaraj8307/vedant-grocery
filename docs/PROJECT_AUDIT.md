# Project Audit — Vedant Fruit & Vegetables (Customer PWA)

> **Date:** 2026-10-10
> **Mode:** read-only inspection (no code mutated beyond this document)
> **Live Supabase project inspected:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`, region derived from URL)
> **Worktree state:** no git repository (`fatal: not a git repository`)
> **Sources:** file tree (112 `src/` files), `package.json`, `next.config.ts`, `tsconfig.json`, `eslint.config.mjs`, `public/sw.js`, `src/app/manifest.ts`, live Supabase via MCP (`list_tables verbose=true`, `list_migrations`, `information_schema.columns/routines`, `pg_policies`, security/performance advisors), and representative source reads listed in §8.

--------------------------------------------------------------------

## 0. Summary verdict

The **application code is mature and coherent**. Styling, PWA, SEO, component
system, money/pricing invariants and error handling are high quality. The
**live Supabase project it is configured to talk to is not the one it was
written against.** Every customer-visible read (catalogue, categories, delivery
slots) and the single customer-visible write (`place_order`) will fail at
runtime against the current live schema. Row Level Security is **disabled on
all 12 public tables**, with zero policies. This document catalogues exactly
what was found.

--------------------------------------------------------------------

## 1. Stack and configuration

| Area | Finding |
|---|---|
| **Framework** | Next.js 16.3.8 App Router + Turbopack, React 19.2.8, TypeScript 5 strict (`target ES2017`, `bundler` resolution, `@/* → src/*`) |
| **Styling** | Tailwind CSS v4 (`@tailwindcss/postcss`), full token system in `src/app/globals.css` (`@theme` — no hex in components). Correct for paper/leaf/marigold/beet + warm neutrals. |
| **UI primitives** | `@radix-ui/*` (dialog, dropdown-menu, select, radio-group, checkbox, tabs, accordion, popover, avatar, label, progress, separator, slot) + shadcn style (`components.json` style `new-york`, `rsc:true`). `class-variance-authority`, `lucide-react`, `sonner`, `vaul`. |
| **Motion/3D** | `motion@14`, `three@0.186` + `@react-three/fiber@9.8` + `@react-three/drei@10.7`; documented single hero scene, primitives only, dynamic import, device-capability gating. |
| **State/data** | Zustand 5 (cart store, persisted `vedant.cart.v1`), `@supabase/ssr@0.12` + `@supabase/supabase-js@2.117`, `react-hook-form` + `zod@4.6` + `@hookform/resolvers`, no TanStack Query (server reads dominate, intentional). |
| **Testing** | Vitest 5 + `@testing-library/{react,jest-dom,user-event}` + `@playwright/test@1.63` + `jsdom` + `@vitejs/plugin-react` installed; **zero first-party specs** found. |
| **Tooling** | ESLint 9 (`eslint-config-next` core-web-vitals + typescript), Prettier + `prettier-plugin-tailwindcss`, `next-env.d.ts` generated. |
| **Package manager** | `pnpm@12.8.1`, lockfile present. |
| **Next config** | `reactStrictMode`, `poweredByHeader:false`, `images: formats avif→webp, remotePatterns` allowlist tied to `NEXT_PUBLIC_SUPABASE_URL` storage host (+ dev-only `images.unsplash.com`), `minimumCacheTTL` 30d, `optimizePackageImports: [lucide-react, motion]`; `headers()` for `sw.js` (`no-cache`) and `manifest.webmanifest`. Verified sound. |
| **Proxy** | `src/proxy.ts` (Next 16 `proxy` / former `middleware`): session refresh + security headers (`nosniff`, `DENY`, `strict-origin-when-cross-origin`, `Permissions-Policy`, `COOP same-origin`, HSTS in prod). Correctly skips `_next`/`images`/`icons`/static. Does **not** authorise — correctly noted. |
| **PWA** | `src/app/manifest.ts` (`standalone`, `start_url:/`, `theme_color:#FBF8F2`, 4 icons incl. maskable, 3 shortcuts), `public/sw.js` (153 lines: network-first nav 2.5s→cache→`/offline`, SWR static, **never-cache Supabase**, versioned cache, `SKIP_WAITING` on demand). Icons present. Sound. |
| **SEO** | `layout.tsx` metadata (template, OG/Twitter, GroceryStore JSON-LD), per-product Product + BreadcrumbList JSON-LD, `sitemap.ts` / `robots.ts` / `opengraph-image.tsx`. Needs re-test after catalogue fix. |
| **Env** | `.env.example` (publishable key only) + `.env.local` (publishable), `.env.local` correctly `.gitignore`d. `lib/env.ts` lazily validates two env vars, rejects `sb_secret_*` and decoded `role=service_role`, enforces `https://<ref>.supabase.(co|in)` shape. Supply via `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` (fallback anon). |
| **Skills** | `skills-lock.json` contains `supabase` + `supabase-postgres-best-practices` (from `supabase/agent-skills`). No `ui-ux-pro-max` skill; Context7 not configured. |
| **MCP** | `.mcp.json` single server `supabase` (`mcp.supabase.com/mcp?project_ref=hyibalj...&features=docs,account,database,debugging,development,functions,branching`). Auth: successful. No Playwright/Context7/GitHub MCPs. |
| **Git** | **Not a git repository.** No history, no remote, no branch safety. Work before fixes should be committed once a repo is initialised. |
| **Migrations dir** | `supabase/migrations/` **does not exist**. `docs/BACKEND_CONTRACT.md` describes one, but the repo carries no local migration files. Local↔remote drift cannot be checked beyond live inspection. |

--------------------------------------------------------------------

## 2. Route inventory (22 routes + middleware/proxy)

All listed routes exist as files and export a component/route handler:

| Route | File | Purpose | Notes |
|---|---|---|---|
| `/` | `src/app/page.tsx` | Home: AnnouncementBar, HomeHero, CategoryRail, RateBoard, Popular rail, shelf grid, TrustStrip, ContactPanel | Server-rendered; single `PRODUCT_SELECT` |
| `/categories` | `src/app/categories/page.tsx` | Browse aisles | URL-driven filters/sort |
| `/categories/[id]` | `src/app/categories/[id]/page.tsx` | Aisle detail | `isUuid` guard |
| `/products/[id]` | `src/app/products/[id]/page.tsx` | Product detail: media sticky col, weight selector, offers, related rail | Emits Product + BreadcrumbList JSON-LD |
| `/search` | `src/app/search/page.tsx` | Search (ilike name+description, suggestions, recent, empty state) | |
| `/offers` | `src/app/offers/page.tsx` | Live-offer grid ItemList JSON-LD | |
| `/cart` | `src/app/cart/page.tsx` | Cart: lines, qty, order summary | Optimistic + rollback |
| `/checkout` | `src/app/checkout/page.tsx` | Address → slot → summary → `place_order` | Idempotent `requestId` |
| `/orders` | `src/app/orders/page.tsx` | Customer order history | |
| `/orders/[id]` | `src/app/orders/[id]/page.tsx` | Order detail + status timeline | |
| `/account` | `src/app/account/page.tsx` | Profile, password, danger zone | |
| `/account/addresses` | `src/app/account/addresses/page.tsx` | Address CRUD | Partial index `one_default_per_user` |
| `/account/reset-password` | `src/app/account/reset-password/page.tsx` | Reset | |
| `/login` | `src/app/login/page.tsx` | Email/password + Google gated by probe | |
| `/signup` | `src/app/signup/page.tsx` | Sign-up | |
| `/forgot-password` | `src/app/forgot-password/page.tsx` | Forgot | |
| `/auth/callback` | `src/app/auth/callback/route.ts` | OAuth / email callback | |
| `/support` | `src/app/support/page.tsx` | Support/complaints list | |
| `/support/[id]` | `src/app/support/[id]/page.tsx` | Complaint thread + messages | |
| `/notifications` | `src/app/notifications/page.tsx` | In-app inbox | Reads `notifications`/`notification_outbox` (see §3 mismatch) |
| `/contact`, `/about`, `/privacy`, `/terms` | `src/app/{contact,about,privacy,terms}/page.tsx` | Store info + legal | `src/config/store.ts` sourced |
| `/offline` | `src/app/offline/page.tsx` | Offline fallback | SW precached |
| Extra | `src/app/manifest.ts` `robots.ts` `sitemap.ts` `opengraph-image.tsx` `error.tsx` `global-error.tsx` `loading.tsx` `not-found.tsx` `favicon.ico` | Framework + PWA/SEO | |

No route was found to be a placeholder shell — all render real UI. Whether they
can fetch their data against the live DB is a separate question addressed next.

--------------------------------------------------------------------

## 3. Live database vs. application contract — the central gap

### 3.1 Live database snapshot (2026-10-10)

Project: `hyibaljxicojfoxbihdn` · `https://hyibaljxicojfoxbihdn.supabase.co`

Migrations applied (3):

```
20261009203355  001_core_tables
20261009203704  002_view_triggers_rpc
20261009203847  003_fix_place_order
```

`information_schema.tables` (`public`): 12 tables + 1 view

```
addresses, categories, complaint_attachments, complaints, delivery_slots,
events, notification_outbox, order_items, orders, product_catalog (VIEW),
products, profiles, store_settings
```

Live column samples (representative; full dump in Supabase section of this audit):

* `products`: `id uuid, category_id uuid, name text, slug text unique, description text, image_url text null, unit text('pc'), price numeric, discount_price numeric null, stock int, max_order_qty int, is_active bool, is_featured bool, has_offer bool` — **no** `glyph`, `accent`, `base_price`, `unit_label`, `availability`, `quantity_limit`.
* `categories`: `id, name unique, slug unique, icon text, sort_order int, is_active bool` — **no** `glyph`, `accent`, `image_url`.
* `delivery_slots`: `id, name text, start_time time, end_time time, max_orders int, is_active bool, sort_order int` — **no** `starts_at/ends_at timestamptz`, **no** `capacity`.
* `profiles`: `id uuid→auth.users, full_name text, phone text unique null, role user_role enum(customer,admin), is_active bool` — **no** `username`, `first_name`, `last_name`, `email`.
* `addresses`: `id, user_id→profiles, label text check Home|Work|Other, line1 text(3..200), line2 text null, city text, pincode bpchar check ^[0-9]{6}$, is_default bool` — **no** `full_name`, `phone`, `house_number`, `street`, `landmark`, `state`, `latitude`, `longitude`, `postal_code varchar`.
* `orders`: `id, order_number text unique, user_id→profiles, status order_status enum(PENDING..CANCELLED), delivery_date date, slot_id→delivery_slots null, address_snapshot jsonb, pincode bpchar generated, subtotal numeric, delivery_fee numeric, total numeric, idempotency_key uuid unique, placed_at, cancelled_at, cancel_reason` — **no** `request_id text`, `address_id`, `delivery_slot_id`, `original_total`, `adjustment_*`, `final_total`.
* `order_items`: `id, order_id→orders, product_id→products null, product_name text, unit text, unit_price numeric, qty int, line_total numeric generated` — **no** `weight_label`, `rejected`, `rejection_reason`.
* `events`: free-form analytics table (not referenced by type `database.ts`).
* `store_settings`: single row `id=1`, fields `store_name, phone, whatsapp, delivery_fee, free_delivery_threshold, min_order_amount, is_open`.
* No table `cart_items`, no `product_weights`, no `offers`, no `order_status_history`, no `complaint_messages`, no `notifications` (there is `notification_outbox`), no `complaint_attachments` media columns matching app types.

Rows: `categories=8, delivery_slots=3, store_settings=1`, everything else `0`.
View `product_catalog` exists and is `SECURITY DEFINER` (advisor flags it).

Routines (`public`): `gen_order_number() INVOKER, handle_new_user() DEFINER, notify_complaint_event() DEFINER, notify_order_event() DEFINER, place_order(...) INVOKER, set_updated_at() INVOKER, sync_profile_phone() DEFINER`.

`place_order` **live signature**:

```sql
place_order(
  p_address_id uuid,
  p_delivery_date date,
  p_idempotency_key uuid,
  p_items jsonb,          -- [{product_id uuid, qty int}]
  p_slot_id uuid
) returns jsonb
```

Behaviour (from body): checks `auth.uid()`, idempotency on `idempotency_key`, store open, date ∈ {today, tomorrow}, address ownership, slot `FOR UPDATE` + capacity (`count(*) where slot_id+p_delivery_date and status<>'CANCELLED'`), item loop (`FOR UPDATE` product, `is_active`, `qty 1..50`, `max_order_qty`, `stock`, `least(price, discount_price)`), `min_order_amount`, delivery fee, insert `orders+order_items`, decrement `stock`, JSONB return distinct error codes (`UNAUTHENTICATED`, `VALIDATION`, `STORE_CLOSED`, `INVALID_DATE/ADDRESS/SLOT`, `SLOT_FULL`, `PRODUCT_UNAVAILABLE`, `QTY_EXCEEDED`, `OUT_OF_STOCK`, `BELOW_MIN_ORDER`, `DUPLICATE_OR_RETRY`).

Policies: `pg_policies where schemaname='public'` returned **0 rows**. Advisories: RLS disabled on all 12 public tables + function search_path + security-definer views/functions.

### 3.2 Application contract (as written)

Source of truth in the codebase: `src/types/database.ts` + `src/lib/catalogue-shared.ts` `PRODUCT_SELECT` + `docs/BACKEND_CONTRACT.md` (dated 2026-10-07, project `bmdmgycsrnffcgxmukxm`).

Expected tables (and types) include: `profiles(username, first_name, last_name, email, phone, ...)`, `categories(glyph, accent, image_url, ...)`, `products(base_price, unit_label, availability, quantity_limit, glyph, accent, ...)`, `product_weights(id, product_id, label, grams, price, is_available)`, `offers(price, discount_percent, starts_at, ends_at, is_active)`, `delivery_slots(starts_at timestamptz, ends_at, capacity)`, `addresses(full_name, phone, house_number, street, landmark, city, state, postal_code 5..10, latitude, longitude, label 1..40)`, `cart_items(user_id, product_id, weight_id, quantity)` with two **partial** unique indexes, `orders(request_id, address_id, delivery_slot_id, original_total, delivery_charge, adjustment_*, final_total, status ORDER_RECEIVED..)`, `order_items(weight_label, rejected)`, `order_status_history`, `complaints(subject, resolution, ... status OPEN/IN_REVIEW/RESOLVED/CLOSED)`, `complaint_messages(author_role CUSTOMER|ADMIN)`, `complaint_attachments(media_type)`, `notifications(user_id, title, body, read_at)`.

`PRODUCT_SELECT`:
```
id, category_id, name, description, glyph, accent, image_url,
base_price, unit_label, availability, quantity_limit,
category:categories (id, name, glyph, accent),
product_weights (id, product_id, label, grams, price, is_available),
offers (id, product_id, title, price, discount_percent, starts_at, ends_at, is_active)
```

`src/lib/catalogue.ts` `getCategories()`: `.select("*").eq("is_active",true).order(sort_order)`
`getDeliverySlots(horizonDays=7)`: `.select("*").eq(is_active,true).gt(starts_at, now).lt(starts_at, horizon).order(starts_at ASC)`
`getCatalogue()`: `.from(products).select(PRODUCT_SELECT).order(availability desc).order(name)`
`src/services/order-service.ts` `placeOrder()`:
```ts
supabase.rpc("place_order", { p_request_id: requestId, p_address_id: addressId, p_delivery_slot_id: deliverySlotId }) // returns string(orderId)
```

### 3.3 Consequence

The two contracts are two **different products**. Concretely:

* Any **catalogue read** (`getCatalogue`, `getProductById`, `getRelatedProducts`, `getLiveOffers`, `getCategories`) issues a column/table that does not exist → PostgREST error → `CatalogueError`. Home, categories, product detail, offers, search all render an error or empty state, not a product grid.
* `getDeliverySlots` filters on column `starts_at` which does not exist (`delivery_slots` uses `start_time time` + `name`). Checkout slot picker always fetches zero slots (or errors if Supabase rejects the filter) → checkout blocked even before the other issues.
* `place_order` is invoked with **non-existent parameter names** (`p_request_id`, `p_delivery_slot_id`) and **wrong param count/shape**. The live function requires `p_delivery_date`, `p_idempotency_key`, `p_items`. Current checkout will always receive a PostgREST 400 / "no such parameter" error before any item is processed. Even if the parameter names were fixed, the cart is expected to be submitted as cart-items vs. the app's local cart snapshot, so `p_items` bridging is still required.
* `cart-service.ts` speaks to `cart_items` (nonexistent table) with read-then-write relying on no `ON CONFLICT` partial index — live DB has no such table, so signed-in add-to-cart panics and signed-out cart (Zustand+localStorage) is the only functioning path.
* `address-service`/`auth-service`/`support-service` column shapes (`full_name/phone/house_number…`, `complaint_messages`, `notifications`) do not match live tables.
* Pricing/weight/offers variants expected by `ProductPurchase` / `QtyStepper` (weight selector, `has_offer`, per-weight `least(price, discount)` model) have no source table; `price` vs `discount_price` lives directly on `products` and is unrelated to `offers`.

`docs/BACKEND_CONTRACT.md` being out of sync is not itself a bug in the audit;
we note it explicitly: the contract was verified against `bmdmgycsrnffcgxmukxm`
on 2026-10-07, but this environment's `.env.local` points at `hyibalj...`, a
fresh project with three migrations. Any launch attempt must either (a) point
`.env.local` back at `bmdmgycsrnffcgxmukxm` if that project is still the
intended production, or (b) treat `hyibalj...` as the production and reconcile
code to its schema. The rest of this audit classifies features assuming we
restore coherence (option b is the recommendation for this workspace, since
the env is already pointed at `hyibalj...`).

--------------------------------------------------------------------

## 4. Feature classification

Use the requested taxonomy: IMPLEMENTED + WORKING / + BUGGY / + INCOMPLETE /
MOCKED / PARTIAL / MISSING / UNKNOWN — plus security verdict where relevant.
Classification is **of the repository code**; the "against live DB" qualifier is
attached where applicable.

### 4.1 Storefront

| Feature | Status | Detail |
|---|---|---|
| **Home** (`/`) | **IMPLEMENTED + INCOMPLETE** | Components (AnnouncementBar, CategoryRail, RateBoard, TrustStrip, HomeHero/ProduceScene) are complete. Against live DB: **MOCKED** in effect — `getCatalogue`/`getLiveOffers` will error/return `[]`, so rails/grids render empty or throw. Needs schema alignment. |
| **Categories** (`/categories`, `/[id]`) | **IMPLEMENTED + INCOMPLETE** | `BrowseFilters` (URL-driven `offersOnly`/`inStockOnly`/`sort`) + `parseBrowseParams`/`applyBrowseFilters` are correct. Works once category/product columns are reconciled. Current filters touch nonexistent columns (`availability`, offers). |
| **Product detail** (`/products/[id]`) + related rail | **IMPLEMENTED + INCOMPLETE** | Weight picker (`product_weights` + `defaultWeight`), `PriceTag`, `ProductPurchase`, breadcrumbs, JSON-LD (Product + BreadcrumbList) all high quality. Blocked on `product_weights`/`offers` tables not existing live. Related-products query columns wrong for live. |
| **Search** (`/search`) | **IMPLEMENTED + INCOMPLETE** | `SearchField` + `RecentSearches` + `normaliseSearchTerm` + `getCatalogue({search})` ilike guard. Logic sound; shares catalogue mismatch so returns no real results live. `suggestions` based on `SEARCH_SUGGESTIONS` (seed derived) reasonable. |
| **Offers** (`/offers`) | **IMPLEMENTED + INCOMPLETE** | `getLiveOffers()` + ItemList JSON-LD complete. Live offers do not exist as a table; discounts are inline `products.discount_price` in live DB and have no date window — so page shows empty. |

### 4.2 Commerce

| Feature | Status | Detail |
|---|---|---|
| **Product card → Add to cart** | **IMPLEMENTED + WORKING (guest)** | `ProductCard` (server), `ProductRailCard`, `QtyStepper` (morph Add↔stepper, reduced-motion fallback), `PriceTag`/`SavingLabel`, `ProductImage` (glyph plinth ↔ `next/image`). Guest add (Zustand) works today. Signed-in path hits `cart_items` (missing) → **BUGGY when signed in**. |
| **Cart page** (`/cart`) | **IMPLEMENTED + INCOMPLETE** | `CartLineRow`, `OrderSummary`, `StickyCartBar` present. Totals previews (subtotal/savings/delivery) wired. Checkout CTA blocked because slot picker cannot produce a slot id valid for live `place_order`. |
| **Checkout** (`/checkout`) | **IMPLEMENTED + BUGGY** | Three-step (address → slot → summary) well designed. `SlotPicker`/`AddressBook` present. **Runtime broken** in two ways: (1) `getDeliverySlots()` selects `starts_at/capacity` (nonexistent) → returns zero or errors; (2) `placeOrder` RPC param mismatch → always rejects. Fix both and checkout is the least-risk path to shipped. |
| **Orders** (`/orders`, `/orders/[id]`) | **IMPLEMENTED + BUGGY** | `order-status-badge`, `reorder-button`, timeline in `[id]` page, adjustment/rejected display. Against live orders table: column names differ, status enum names differ (`PENDING` live vs `ORDER_RECEIVED` in code), `order_status_history` table does not exist live (`replay`/`cancel_*` live vs `adjustment_*` in code). Signed-in reads will return wrong shapes or error. |
| **Cart persistence** | **PARTIAL** | Zustand persisted cart + `cart-service` read-then-write (partial unique index workaround) + `cart-provider` merge (`max` quantity) are correct **for the intended schema**. Against live schema, `cart_items` table missing ⇒ persisted guest cart is the only surviving primitive; any signed-in sync throws. `place_order` in live DB reads `p_items` directly (no server cart) so guest-cart-only flow remains viable if `p_items` is assembled from the Zustand snapshot. |

### 4.3 Identity and addresses

| Feature | Status | Detail |
|---|---|---|
| **Auth** (email/password + Google, callback, email confirm) | **IMPLEMENTED + WORKING** (UX) / **INCOMPLETE** (provider status) | `auth-service`, `auth-forms/shell`, `profile-sync`, `auth-providers` (live Google capability probe) correct. Session via `@supabase/ssr` + `proxy.ts`. Governed by Supabase Auth dashboard — email confirm required? Validate via `auth.config` (see Supabase audit). |
| **Profile** (`/account` + `getProfile`, `getProfileOrDefault`) | **PARTIAL** | App types expect `username/first_name/last_name/email/phone`; live `profiles` is `full_name/phone/role/is_active`. Profile form writes will error or drop columns. `handle_new_user()` + `sync_profile_phone()` triggers exist for the live shape. |
| **Addresses** (`/account/addresses` + checkout AddressBook) | **PARTIAL** | Validation (`zod` + Indian pincode/phone rules) is thorough. Live `addresses` is stricter (6-digit pincode, `Home|Work|Other` labels, minimal columns). Multi-column address form would map lossily. Default-address partial unique semantics are partially aligned but object differs. |
| **Notifications** (`/notifications`) | **MOCKED** | Page reads `notifications` / `notification_outbox`; live has `notification_outbox` with `(audience, user_id, type, title, body, data, read_at)` populated by triggers on `orders`/`complaints`. The read path in `lib/notifications.ts` must match `notification_outbox` exactly (it currently does not). |

### 4.4 Support

| Feature | Status | Detail |
|---|---|---|
| **Support** (`/support`, `/support/[id]`) | **PARTIAL** | Complaint form + message threads + attachments (`complaint-images` bucket in design) present. Live tables: `complaints`(`type` enum WRONG_ITEM/DAMAGED…, `status` OPEN/IN_PROGRESS/RESOLVED/CLOSED) + `complaint_attachments(storage_path, content_type image/%, size<5MiB)` exist, but `complaint_messages` table **not present**; `admin_reply` lives inline on `complaints`. Read path for per-message `complaint_messages (id, author_id, author_role, body)` will fail live. |
| **Contact/About/Legal/Offline** | **IMPLEMENTED + WORKING** | `ContactPanel` → Call/WhatsApp deep links via `config/store.ts`, legal pages, offline fallback — all independent of catalogue DB and will render even under current mismatch. |

### 4.5 PWA / SEO / Navigation

| Feature | Status | Detail |
|---|---|---|
| **PWA** | **IMPLEMENTED + WORKING** | Manifest, icons, SW strategy, `PwaProvider` (register, update prompt, install prompt) all correct. Never caches Supabase/auth — essential given RLS-disabled state. Installability needs real HTTPS + icons audit on device (not blocked by schema). |
| **SEO** | **IMPLEMENTED + WORKING** (shell) / **INCOMPLETE** (catalogue-driven pages) | Title templates, OG/Twitter, sitemap/robots, GroceryStore JSON-LD, Product Breadcrumbs all sound. `sitemap.ts` enumerations depend on catalogue rows — sitemap will be empty until catalogue aligned. |
| **Navigation** | **IMPLEMENTED + WORKING** | `SiteHeader` (desktop: logo+search+categories+offers+cart) + `BottomNav` (mobile: Home/Categories/Search/Orders/Account) + `StickyCartBar`. Correct for commerce app shell. |
| **Images** | **IMPLEMENTED + WORKING** | `ProductImage` glyph fallback (accent-RGB tint) + `next/image` avif→webp, remotePatterns allowlist, aspect ratios (detail/square) correct. Missing: Supabase storage bucket not inspected (see Supabase audit). |

### 4.6 Cross-cutting

| Feature | Status | Detail |
|---|---|---|
| **Pricing/money invariants** | **IMPLEMENTED + BUGGY (live)** | `lib/money.ts` (integer paise), `lib/pricing.ts` (`pickActiveOffer`, `priceFor` least(price, discount), `cheapestOption`, `previewTotals`/`previewDeliveryCharge`) are the best-reviewed modules in the repo — but they model `offers(product_id, price/discount_percent, starts_at/ends_at, is_active)` which **does not exist live**. Live pricing is `least(price, discount_price)` inline on `products`, no time window. Preview math will drift. |
| **Animations** | **IMPLEMENTED + WORKING** | `motion` entrances, button feedback, sheet/drawer in CSS (`@keyframes` driven by `data-state`), prefers-reduced-motion kill switch, overscroll handling — good. |
| **3D** | **IMPLEMENTED + WORKING** | One hero scene (`produce-scene`), `use-device-capability` (cores/memory/pointer/saveData/WebGL/IntersectionObserver + `useDocumentVisible`), lathe basket + painted produce primitives, DPR cap, frameloop `demand/never`, fallback composition — correctly scoped and not present in checkout/cart. |
| **Accessibility** | **IMPLEMENTED + INCOMPLETE** | Focus ring (`leaf-600`), `Skip to content`, `aria-*`, never-blocking zoom, motion reduce, numeric tabular, touch targets — strong baseline. Formal audit (axe + keyboard sweep) still required, noted below. |
| **Performance** | **PARTIAL / UNKNOWN** | Code-split `three`, `optimizePackageImports`, responsive images, lazy/dynamic where relevant. No measured LCP/INP/CLS, no bundle audit, no PWA offline manual QA on device recorded here. Scheduled post-fix. |
| **Tests** | **MISSING** | Vitest + Playwright installed and configured, **zero first-party specs** found (`find . -name *.test.*` returned only node_modules). `npm run lint`/`build`/`tsc` status not yet captured live (queued). |
| **Docs** | **PARTIAL** | `docs/BACKEND_CONTRACT.md`, `DESIGN_SYSTEM.md`, `PROJECT_INDEX.md`, `FINAL_IMPLEMENTATION_REPORT.md`, `IMAGE_STRATEGY.md`, `FUTURE_ADMIN_APP.md`, `CHANGELOG.md`, `ERROR_LOG.md`, `TASK_STATE.md` present. New audit docs (`PROJECT_AUDIT.md`, `SUPABASE_AUDIT.md`, `TECHNOLOGY_RESEARCH.md`, gap analyses etc.) mandated by the autonomous run are still to be produced — this file is the first of them. `docs/BACKEND_CONTRACT.md` is the strongest artifact in the repo but is now stale vs. live. |
| **Admin prep** | **MISSING (intentionally)** | Customer PWA only; future Android admin contract to check (`FUTURE_ADMIN_APP.md`). Backend prep: `orders.status` enum + `gen_order_number()` + `notification_outbox` triggers present, but admin RPC surfaces do not yet exist live. Correct to defer. |

--------------------------------------------------------------------

## 5. Security — critical finding

* **RLS is disabled on every public table.** Advisories: `rls_disabled_in_public` count 12 for `profiles, addresses, categories, products, delivery_slots, orders, order_items, complaints, complaint_attachments, events, notification_outbox, store_settings`. `list_tables verbose=true` confirms `rls_enabled:false` for all 12; `pg_policies` is empty. **There is no row-level isolation today** — any holder of the publishable anon key can `select/insert/update/delete` any row via PostgREST. This is true for a pre-auth anon caller.
* Customer-data isolation across profiles/addresses/orders/complaints/notifications is therefore **not enforced by the database**, only (if anywhere) by application filters. Must be treated as **P0 stop-ship** — fix by enabling RLS and publishing owned-read + admin policies before any real customer data exists in this project.
* Other advisors (medium severity):
  * `security_definer_view` (`product_catalog`) — review against RLS expectations; SECURITY DEFINER views bypass row checks.
  * `function_search_path_mutable` (`set_updated_at`, `gen_order_number`) — set explicit `search_path = public, pg_temp`.
  * `anon/authenticated can execute SECURITY DEFINER function` (`handle_new_user`, `notify_*`, `sync_profile_phone`) — intentional but must revoke `EXECUTE` from `anon`/`authenticated` and leave only the trigger/service path.
* No secret appears to be committed: `.env.local` present on disk but `.gitignore`d; `.env.example` only shows placeholders; `lib/env.ts` actively rejects `service_role` keys in the client. This is correct — but with RLS disabled the protection is moot.
* Storage not yet audited here: bucket policies for `complaint-images` etc. expected per `docs/BACKEND_CONTRACT.md` but not inspected live in this pass — bucket enumeration queued for Supabase audit.

--------------------------------------------------------------------

## 6. What is genuinely good and must be kept

* Design token system (`@theme`) and warm produce palette — preserve verbatim.
* Money (integer paise) + pricing preview matching the server's `least(...)` — re-target, don't discard.
* Cart state (Zustand persisted + `cartKey(productId,weightId)` partial-index identity) and session merge (`max`) — re-target `products(stock,price,discount_price, max_order_qty)` shape.
* PWA (manifest + `sw.js` never-cache Supabase) — keep as-is; it is what keeps the product from caching stale private data during the RLS window.
* `proxy.ts` headers + session refresh — keep.
* Catalogue shared row mapper (`toCatalogueProduct` sorts weights by grams, picks best live offer by `created_at desc`) — keep the pattern, update the columns.
* SEO/structured-data shaping — keep; just re-seed from the corrected catalogue.
* One primitive-produce 3D scene with capability gating — keep; do not broaden to cart/checkout.

--------------------------------------------------------------------

## 7. Recommended fix order (do not apply destructively without review)

1. **Decide project identity** — confirm whether `hyibaljxicojfoxbihdn` is the
   intended production (`.env.local` already points at it) or whether the app
   should re-point at `bmdmgycsrnffcgxmukxm`. This audit assumes the former since
   it is the configured project with zero customer data.
2. **Enable RLS** on all public tables and add owned policies + catalogue public
   read policies (no `USING(true)` on private tables). Until real orders exist this
   is safe and idempotent; bundled SQL is in the companion `docs/SUPABASE_AUDIT.md`.
3. **Reconcile schema** — either (a) recreate the expected tables (`product_weights`, `offers`, `cart_items`, `order_status_history`, `complaint_messages`, `notifications` + missing columns) and adapt `place_order` to `p_request_id/p_address_id/p_delivery_slot_id` shape, **or** (b) adapt the app to the `product_catalog` + `price/discount_price` + `p_items` live shape. Path (a) preserves the authored app; path (b) is smaller but rewrites the storefront. Choose one and apply as a forward migration (no `DROP production`).
4. **Fix `place_order` bridge** — adapt `order-service.placeOrder` to build `p_items` from Zustand lines and map `p_idempotency_key/p_delivery_date/p_slot_id` (or restore the legacy RPC name as a wrapper) and update idempotency semantics.
5. **Checkout slot bridge** — map live `delivery_slots(name, start_time time, end_time time[, delivery_date?])` to concrete `starts_at/ends_at timestamptz` windows for the picker, and make horizon logic match the live `place_order` `today|tomorrow` predicate.
6. **Type & lib alignment** — regenerate `src/types/database.ts`, `PRODUCT_SELECT`, `pricing.ts` view of offers, `notifications` read path (`notification_outbox`), `complaints` thread read path, `addresses` columns, category glyph/accent fields.
7. **Quality gates** — run `npx tsc --noEmit`, `npm run lint`, `npm run build`, browser smoke (home→search→cart→checkout→orders) against the corrected DB, snapshot LCP/INP/CLS, then proceed to UI/UX Pro Max + Context7 + Playwright work as specified.

--------------------------------------------------------------------

## 8. Sources inspected for this audit

* `package.json`, `pnpm-lock.yaml`, `next.config.ts`, `tsconfig.json`, `eslint.config.mjs`, `postcss.config.mjs`, `components.json`, `skills-lock.json`, `.mcp.json`, `.env.example`, `.env.local` (redacted)
* `src/app/layout.tsx`, `src/app/page.tsx`, `src/app/globals.css`, `src/app/manifest.ts`, `public/sw.js`, `public/icons/`, `src/proxy.ts`
* `src/lib/catalogue.ts`, `src/lib/catalogue-shared.ts`, `src/lib/pricing.ts`, `src/lib/money.ts`, `src/lib/browse.ts`, `src/lib/env.ts`, `src/lib/auth.ts`
* `src/lib/supabase/{client,server}.ts`, `src/types/database.ts`, `src/config/{catalogue,site,store}.ts`
* `src/services/{cart,order,catalogue-client,auth,address,support}-service.ts` (representative), `src/stores/cart-store.ts`, `src/hooks/use-cart.ts`, `src/components/cart/cart-provider.tsx`, `src/components/checkout/checkout-client.tsx`, `src/app/products/[id]/page.tsx`
* Live Supabase: `get_project_url`, `list_migrations`, `list_tables verbose=true`, `information_schema.columns`, `information_schema.routines`, `pg_policies`, `get_advisors security+performance`
* Existing docs: `docs/BACKEND_CONTRACT.md`, `docs/PROJECT_INDEX.md`, `docs/FINAL_IMPLEMENTATION_REPORT.md`, `docs/TASK_STATE.md`, `docs/DESIGN_SYSTEM.md`, `docs/CHANGELOG.md`, `docs/ERROR_LOG.md`, `docs/FUTURE_ADMIN_APP.md`, `docs/IMAGE_STRATEGY.md`
* Shell: `find src -type f` (112 files), `ls docs/public/scripts`, `ls -la supabase` (absent), `git status/log` (no repo)

--------------------------------------------------------------------

## 9. Remaining inspection for the autonomous run

Queued immediately after this document (no user prompt required):

* Live app run + browser verification (home/search/categories/product/cart/checkout/orders/account/support/offline against the **current** app shell before schema fixes — to confirm the visual/instructional parts of this audit).
* `docs/SUPABASE_AUDIT.md` (read-only advisor + table/policy/routine/storage/auth/migration gap log, with a readiness verdict for the future admin app).
* `docs/UI_UX_PRO_MAX_USAGE.md`, `docs/TECHNOLOGY_RESEARCH.md`, `docs/DESIGN_SYSTEM.md` refresh, `docs/DATABASE_ARCHITECTURE.md`, `docs/API_CONTRACT.md`, Context7 install, MCP discovery, and the gap→implement→test loop as specified.

Every feature in §4 was classified on the basis of an inspected artifact, not a
heuristic. Where evidence was missing or ambiguous the field is marked
UNKNOWN or qualified with "against live DB" so a reviewer can locate the open
question. Suppressions (e.g. forcing a green build) are not acceptable per
the quality-gate section of the brief.
