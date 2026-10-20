# Project Index — Vedant Fruit & Vegetables (Customer PWA)

> Last updated: 2026-10-11 · source: file-tree + build + verified Supabase schema (`docs/BACKEND_CONTRACT.md`) + fresh re-verification (`docs/VERIFICATION_REPORT_2026-10-11.md`)

## 1. Purpose

Customer-only, mobile-first grocery storefront and installable PWA. No admin UI, no admin secrets — the same Supabase project will later be consumed by a separate Android admin app. See `FUTURE_ADMIN_APP.md` for that contract.

## 2. Tech stack

- **Framework:** Next.js 16.3.8 (App Router, Turbopack), React 19.2.8, TypeScript 5 (strict)
- **Styling:** Tailwind CSS v4 (`@tailwindcss/postcss`), design tokens in `src/app/globals.css` (`@theme`), no hard-coded hexes in UI
- **UI:** shadcn-style primitives + Radix (`dialog`, `dropdown-menu`, `select`, `radio-group`, `checkbox`, `tabs`, `accordion`, `popover`, …), `class-variance-authority`, `lucide-react`, `sonner`
- **Motion:** `motion` (framer-motion successor) — morphs, springs, scroll reveals
- **3D:** Three.js 0.186 + `@react-three/fiber` 9.8 + `@react-three/drei` 10.7 — single hero scene, authored primitives (no GLB), lazy/dynamic, gated by device capability
- **State:** Zustand 5 (local cart), React state elsewhere
- **Backend:** Supabase (Postgres 17.11, Auth, Storage, RPC) — `ap-southeast-1`, project `hyibaljxicojfoxbihdn` (anon key `sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH`); old project `bmdmgycsrnffcgxmukxm` was replaced on 2026-10-10
- **Auth helpers:** `@supabase/ssr` · validation: `zod` + `react-hook-form`
- **PWA:** `src/app/manifest.ts`, `public/sw.js`, icons in `public/icons/`
- **Testing:** Vitest 5, `@testing-library/*`, Playwright (configured, no specs yet)

## 3. Important directories

```
src/
  app/            Next routes + metadata (all §10 pages + 404/error/offline)
    api/…         (auth callback route)
  components/
    ui/           button, input, badge, sheet, skeleton, misc (SectionHeading/EmptyState)
    layout/       SiteHeader, BottomNav
    hero/         HomeHero + HeroVisual (dynamic 3D wrapper) + HeroComposition (baseline SVG)
    three/        ProduceScene (lathe basket + primitive produce)
    brand/        Logo
    products/     ProductCard (server), ProductRailCard, ProductGrid/Rail, PriceTag, QtyStepper, ProductPurchase, ProductImage
    cart/         CartProvider, StickyCartBar, cart rows / order summary
    checkout/     AddressBook, SlotPicker, CheckoutClient (idempotent place_order)
    catalogue/    BrowseFilters (URL-driven)
    account/      profile/address/notification/account-actions
    auth/         auth-forms / auth-shell
    orders/       OrderStatusBadge, ReorderButton
    search/       SearchField, RecentSearches
    store/        CallStoreButton / WhatsAppButton
    support/      complaint form + message threads
    pwa/          PwaProvider (SW register, SKIP_WAITING, install prompt)
    home/         AnnouncementBar, CategoryRail, RateBoard, TrustStrip
  config/         catalogue, site (SEO), store (phone/WhatsApp/address/hours)
  hooks/          use-cart, use-device-capability (+ useDocumentVisible)
  lib/
    supabase/     client.ts (browser), server.ts (RSC/Route Handler)
    pricing.ts    pickActiveOffer / priceFor / cartTotals (parity with place_order)
    money.ts      integer-paise helpers + formatMoney
    format.ts     grams/dates/slots/ORDER_STATUS_META
    validation.ts zod schemas (mirrors DB CHECKs)
    catalogue.ts  server-only data access (PRODUCT_SELECT + getCatalogue/…/getSlotLoad)
    catalogue-shared.ts  shared row→domain mapper
    auth.ts, addresses.ts, notifications.ts, browse.ts, cart-line.ts, env.ts, auth-providers.ts
    utils.ts      cn()
  services/       cart-service (read-then-write), auth-service, order-service, address-service, support-service, catalogue-client
  stores/         cart-store (persisted localStorage)
  types/          database.ts (Supabase generated shapes)
public/
  icons/          pwa-192/512, maskable-512, apple-touch-icon, icon.svg
  sw.js           network-first nav / stale-while-revalidate static / never-cache Supabase
  images/         (placeholder; real catalogue photography goes via Supabase storage later)
scripts/generate-icons.mjs  ·  .logs/  ·  docs/
```

## 4. Implemented features (route inventory: 22 routes + middleware)

| Area | Detail | Tech note |
|---|---|---|
| **Home** (`/`) | announcement, hero (baseline SVG + progressive 3D), category rail, rate board (offers), popular rail, shelf grid | single `PRODUCT_SELECT`, live offers sorted by saving |
| **Catalogue** (`/categories`, `/categories/[id]`) | aisle filter, `On offer`/`In stock` refinement, sort (A–Z / price asc/desc), all URL-driven (shareable, server-rendered) | `parseBrowseParams` → `getCatalogue` → `applyBrowseFilters` |
| **Search** (`/search`) | normalized `ilike` over name+description, recent searches, suggestions | `normaliseSearchTerm` strips wildcards |
| **Product** (`/products/[id]`) | weight selector (backend variants, never hard-coded), price breakdown, offers, related rail | buyer-selected variant priced by `place_order` |
| **Offers** (`/offers`, `/search` filter) | live-offer grid, per-product saving | `getLiveOffers()` biggest saving first |
| **Cart** (`/cart`) | add/inc/dec/remove/clear, weight-aware lines, optimistic + rollback | `cart_items` partial unique indexes → read-then-write |
| **Checkout** (`/checkout`) | address → slot → summary → server validation | `place_order(p_request_id, p_address_id, p_delivery_slot_id)` — idempotent on `request_idRef` |
| **Addresses** (`/account/addresses` + checkout AddressBook) | CRUD, default, Indian postal/phone validation | partial unique index `one_default_per_user` (clear-then-set) |
| **Delivery slots** | active-future filter, horizon, capacity grey-out | `getDeliverySlots()` matches `place_order` predicate |
| **Orders** (`/orders`, `/orders/[id]`) | history, status timeline, adjustments + rejected items, reorder (re-prices current variant) | `orders`/`order_items`/`order_status_history` owned-read |
| **Account** (`/account`, `/account/reset-password`, `/account/addresses`) | profile, password change, danger zone | client wipes local data; server delete is owner/admin flow |
| **Auth** (`/login`, `/signup`, `/forgot-password`, `/auth/callback`) | email/password, Google (hidden unless capability probe says enabled), email verification | `getAuthProviderCapabilities()` live probe; `getSessionUser()` SSR |
| **Support** (`/support`, `/support/[id]`) | complaint threads + attachments (`complaint-images` bucket) | `complaints`/`complaint_messages`/`complaint_attachments` owned-read |
| **Contact** (`/contact`, `/about`, `/privacy`, `/terms`) | store config (phone/WhatsApp/address/hours) centralised under `src/config/store.ts` | null-safe fallbacks when owner hasn't supplied values |
| **Navigation** | mobile bottom nav + sticky cart bar; desktop site header (search + categories + offers + cart) | thumb-zone, RWD deliberately (not stretched) |
| **PWA** | manifest (`standalone`), icons, service worker strategy, install/update handling | SW never caches Supabase/auth |
| **SEO** | title templates, OG/Twitter, sitemap/robots, GroceryStore JSON-LD, Product Breadcrumbs | `src/config/site.ts` |
| **3D** | one hero scene, primitives only, DPR cap, frameloop `demand`/`never`, reduced-motion + low-power fallback | Documents `DESIGN_SYSTEM.md` §10 |

## 5. Incomplete / partial (re-verified 2026-10-11)

- **Real defects (see `VERIFICATION_REPORT_2026-10-11.md` §3)** — Bug 1 P0: `order_items` column is `qty` but app selects `quantity` (breaks `/orders/[id]`); Bug 2 P1: 8 of 24 product images are dead URLs; Bug 3 P1: 0 storage buckets live despite docs; Bug 4 P2: notifications read `notifications` but triggers write `notification_outbox`.
- **Tests** — `vitest` + Playwright are installed; no specs committed yet.
- **Delivery-slot recurrence** — 18 seed slots cover 2026-10-11 → 2026-10-16; recurrence migration proposed in `FUTURE_ADMIN_APP.md`/`BACKEND_CONTRACT.md` but not applied. Checkout blocks on 2026-10-17.
- **`graphify`** — `graphify@1.0.0` dependency is an abandoned package with invalid JS, never imported; remove.
- **No git repo, no local migrations** — `supabase/` dir absent; all DB changes applied live via MCP. Capture as migration `001_...` soon.
- **Owner config** — store phone/WhatsApp/address unset; admin roster empty; Google OAuth disabled by design.

## 6. Database status

Verified 2026-10-11 live (`hyibaljxicojfoxbihdn`): **18 `public` tables, RLS enabled on all (19 least-privilege policies)** — hardened + reconciled to the app contract on 2026-10-10; catalog seeded (3 categories, 24 products, 22 product_weights, 10 time-windowed offers, 18 delivery_slots, `store_settings` 1); RPCs `place_order_app(text,uuid,uuid)` + `place_order(text,uuid,uuid)` wrapper matching the app's call; **0 storage buckets live** (Bug 3 — `product-images` + `complaint-images` must be (re)created; docs' claims here are stale). `product_catalog` is a `security_invoker` view with pinned `search_path`. Money is `numeric(12,2)`. See `BACKEND_CONTRACT.md` + `VERIFICATION_REPORT_2026-10-11.md`.

## 7. Authentication status

Email confirm is **required** (`mailer_autoconfirm: false`), signup **enabled**, Google provider **disabled** (button gated). Session is Supabase cookies via `@supabase/ssr`; proxy refresh + security headers. No OTP/magic-link indirection.

## 8. RLS status

All customer tables: `OWNED` predicates (`auth.uid()`), catalogue: `*_read_public` or `is_active`-gated public select, admin writes gated on `private.is_admin()`. No `USING (true)` on private data. Orders insert path is **RPC only** (`place_order`). Verified in contract §4.

## 9. Testing status

Re-verified 2026-10-11: `pnpm exec tsc --noEmit` ✓ 0 errors · `pnpm exec eslint .` ✓ 0 errors (17 warnings, all vendored in `.agents/skills/**`, none in `src/`) · `pnpm run build` ✓ 22/22 routes · dev-server + Playwright storefront smoke ✓ (home/PDP/search/cart/offers render live data, checkout auth-guards to `/login?next=%2Fcheckout`). No automated test specs yet (`vitest run` → "no test files"). See `docs/FINAL_IMPLEMENTATION_REPORT.md` for full historical run.

## 10. Deployment status

Not deployed. Icons are checked in; assets are local; no CI/CD. Host to be confirmed.

## 11. Known issues (owner/dev action items — see `ACTION_PLAN_2026-10-11.md`)

- **Bug 1 P0 — `order_items.qty` vs `quantity`** — `/orders/[id]` 400s on any real order; reorder reads `undefined`.
- **Bug 2 P1 — 8 dead product image URLs** — Beetroot, Carrot, Capsicum, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato; `ProductImage` has no `onError` fallback.
- **Bug 3 P1 — 0 storage buckets** — `product-images`/`complaint-images` must be (re)created; complaint attachments + catalogue photography broken.
- **Bug 4 P2 — notifications read wrong table** — read `notifications`, triggers write `notification_outbox`.
- **Slots stale after 2026-10-16** — checkout blocked; apply recurrence migration.
- **`private.admin_users` empty** — orders stay `ORDER_RECEIVED` (nobody can confirm).
- **Store phone/WhatsApp/address unset** (`src/config/store.ts` nulls) — Call/WhatsApp buttons hide.
- **No service-role delete** in customer app — account deletion is a request flow (`ACCOUNT_DELETION_MODE='request'`).
- **Project name mismatch** — Supabase dashboard name `kunal fruit mart` (project `hyibaljxicojfoxbihdn`).
- **Google OAuth disabled** — intentionally hidden.

## 12. Next recommended actions

See `docs/ACTION_PLAN_2026-10-11.md` for the full sequenced plan. Short version:
1. **Phase 0 (owner, ~15 min):** grant first admin, set store phone/WhatsApp/address, create the two storage buckets.
2. **Phase 1 (dev, ~0.5–1 day):** fix Bug 1 `qty` (P0), Bug 2 image fallback + dead URLs (P1), Bug 3 buckets (P1), Bug 4 notifications read path (P2).
3. **Phase 2 (dev, ~1–2 days):** remove `graphify`, add pricing/money/cart-store unit tests + a Playwright smoke, apply slot-recurrence migration, `git init` + first commit, capture local migrations.
4. **Phase 3 (launch, ~1–2 days):** deploy + CI + QR, minimal `events` analytics instrumentation, a11y audit, Lighthouse/CWV.

## 11. Update 2026-10-11 (session)

- **Bugs fixed:** order item column `qty` (not `quantity`); `ProductImage` glyph fallback on load error; storage buckets `product-images` + `complaint-images` created; notifications read `notification_outbox` (`audience='customer'`).
- **Denominations:** Lemon (Nimbu) rows in `product_weights`: 250g ₹25, 500g ₹50, 1kg ₹100 (`products.base_price` = 100). Storefront reads these; the admin editor is not built yet.
- **Motion:** `src/components/ui/reveal.tsx` (scroll reveal, reduced-motion aware); keyframes `reveal`, `drift`, `sheen` in `globals.css`; home hero uses staggered reveal.
- **Docs for handoff:** `docs/SESSION_REPORT_2026-10-11.md`, `docs/ANDROID_BUILD_PROMPT.md`.
- **Verified:** `pnpm run build` exit 0; `tsc --noEmit` clean.
- **Not verified in a browser yet.**
