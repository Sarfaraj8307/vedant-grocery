# Customer Web Application — Technical Handoff & Enhancement Roadmap

**Vedant Fruit & Vegetables — customer storefront (PWA)**

| Field | Value |
|---|---|
| Document | Technical handoff + enhancement roadmap (master prompt §39) |
| Audience | Store owner + any future development team |
| App | Customer storefront, `vedant-app` (Next.js PWA) |
| Repo root | `C:/Users/sarfaraz/Desktop/vedant fruits and vegitables/` |
| Backend | Supabase project `hyibaljxicojfoxbihdn` (shared with the Android admin app) |
| Language | English |
| Date | 2026-10-12 |
| Author | 许清楚 (Xu), Product Manager |
| Evidence basis | Read-only inspection of the live codebase on 2026-10-12 |

> **How to read this document — the honesty rule.**
> Every section separates **Already implemented** (grounded in a file I actually inspected — the path is cited) from **Recommended future enhancement** (an idea, not a claim). Nothing planned is described as if it exists. Where something could not be determined from the code, it is marked **Unknown** with what would resolve it.
>
> **Contract authority.** `src/types/database.ts` is hand-maintained and **stale**. The live schema authority is `admin-android/docs/CONTRACT.md`, which was read directly out of the live database. Where the two disagree, the live database wins.

---

## 1. Product Overview

**What it is.** A mobile-first **Progressive Web App** that lets customers of Vedant Fruit & Vegetables browse fresh produce, choose a **weight** (e.g. 500 g, 1 kg), add to a basket, and place a **same-day delivery** order — paid on delivery. It is the customer-facing half of a two-app system; the store owner operates the other half from a native Android admin app. The two share only the Supabase backend contract.

**Positioning.** Local grocery, *weighed to order*: the differentiator is the weight-variant model (buy 250 g or 2 kg of the same product), not a fixed-pack supermarket SKU.

### Already implemented
- Storefront identity, catalogue, weight selection, cart, checkout, order history, delivery slots, support/complaints, account & addresses, legal pages — see §17 for the full route list.
- Installable PWA with a service worker (`public/sw.js`) and manifest.
- Local-business structured data (`GroceryStore` schema) emitted from the root layout (`src/app/layout.tsx:214`).

### Recommended future enhancement
- Online payment (currently none — the order is placed and paid on delivery).
- Multi-store / multi-outlet support (single store today).
- Personalised recommendations and reorder-one-tap from history.

---

## 2. Current Architecture

**Shape.** A Next.js **App Router** application. Server Components do data reads with the customer's own session; Client Components own interaction and submission. There is **no separate API layer** — the browser and server both talk to Supabase (PostgREST/Auth/Storage) directly under RLS.

```
Browser (React 19 / App Router)
   │  Server Components  ──►  @supabase/ssr server client (cookie session)  ──┐
   │  Client Components  ──►  @supabase/ssr browser client (anon key)         │
   │                                                                          ▼
   └── Zustand cart (localStorage) ───────────────────────────────────►  Supabase
                                                                  PostgREST · Auth · Storage
                                                                  + Postgres RLS + RPCs
```

**Layering that actually exists (inspected):**
- **Routing / pages:** `src/app/**` — 24 `page.tsx`, 1 `layout.tsx`, 1 `route.ts`.
- **Server data access:** `src/lib/catalogue.ts`, `src/lib/addresses.ts`, `src/lib/auth.ts`, `src/lib/notifications.ts` — all import `server-only`.
- **Shared, environment-agnostic logic:** `src/lib/{money,pricing,cart-line,catalogue-shared,browse,format,validation,env}.ts` — pure functions, no I/O.
- **Client-side service layer:** `src/services/{auth,order,cart,address,support,catalogue-client}.ts` — `"use client"`, the only place the browser SDK is called for writes.
- **Client state:** `src/stores/cart-store.ts` (Zustand + persist).
- **Presentation:** `src/components/**` — 44 `.tsx` files grouped by domain (cart, checkout, catalogue, layout, orders, support, ui, …).
- **Config:** `src/config/{store,site,catalogue}.ts`.
- **Types:** `src/types/database.ts` (hand-maintained, stale).

### Recommended future enhancement
- A thin server-side action layer for writes if/when audit logging or rate-limiting needs to move server-side.
- Route-group based code organisation (`(shop)`, `(account)`) as the app grows.
- Extract the shared catalogue projection into a published package if a third client appears.

---

## 3. Technology Stack

### Already implemented (from `package.json`, verified)

| Area | Choice | Version |
|---|---|---|
| Framework | Next.js (App Router) | 16.3.8 |
| UI runtime | React / React DOM | 19.2.8 |
| Language | TypeScript | ^5 |
| Styling | Tailwind CSS v4 (`@tailwindcss/postcss`) | ^4 |
| Backend SDK | `@supabase/supabase-js` / `@supabase/ssr` | 2.117.2 / 0.12.7 |
| Client state | Zustand (+ persist) | ^5.0.15 |
| Forms | react-hook-form + `@hookform/resolvers` | ^7.89 / ^5.9 |
| Validation | Zod | ^4.6.5 |
| Primitives | Radix UI (accordion, dialog, select, tabs, …) | various ^1–^2 |
| Motion | `motion` | ^14.0 |
| Toasts | `sonner` | ^2.0 |
| Drawer | `vaul` | ^1.1 |
| Icons | `lucide-react` | ^1.52 |
| Utilities | `clsx`, `tailwind-merge`, `class-variance-authority` | — |
| Unit/component tests | Vitest + Testing Library + jsdom | declared, **not configured** |
| E2E tests | Playwright | ^1.63 |
| Lint/format | ESLint 9 (`eslint-config-next`) + Prettier | — |
| Package manager | pnpm | 12.8.1 |

**Fonts:** self-hosted via `next/font/google` — Inter (body) and Bricolage Grotesque (display), no runtime Google request (`src/app/layout.tsx:31`).

### Known issues / debt in the stack
- **`graphify@1.0.0`** is in `dependencies` but **imported nowhere** (`grep -rn graphify src/` → 0 hits). Broken/abandoned — **remove it**.
- **Vitest is declared but has no config** (no `vitest.config.*`) and there is **no `test` npm script**; `tests/` is a Playwright suite. The unit-test capability is currently dormant.

### Recommended future enhancement
- Adopt `supabase gen types` to generate `src/types/database.ts` and delete the hand-maintained file.
- Add a `test` script + `vitest.config.ts` and a CI workflow (see §27, §28).

---

## 4. Supabase Integration

### Already implemented
- **Two SSR clients, both carrying only the customer's session:**
  - Browser: `src/lib/supabase/client.ts` — memoised `createBrowserClient`, anon/publishable key.
  - Server: `src/lib/supabase/server.ts` — `createServerClient` bound to request cookies; used by Server Components, Route Handlers.
- **No service-role client anywhere.** `src/lib/env.ts` actively rejects a service-role key (`looksLikeServiceRoleKey`) and refuses to boot with one — a hard guard, not a convention.
- **RPC usage (inspected):**
  - `place_order(p_request_id, p_address_id, p_delivery_slot_id)` — the **only** way an order is created (`src/services/order-service.ts:105`).
  - `check_username_available(p_username)` — pre-signup username probe (`src/services/auth-service.ts:72`).
- **Tables/views read by the app:** `categories`, `products`, `product_weights`, `offers`, `delivery_slots`, `orders`, `order_items`, `order_status_history`, `addresses`, `cart_items`, `profiles`, `complaints`, `complaint_messages`, and `notification_outbox` (read as customer notifications, `src/lib/notifications.ts:18`).
- **PostgREST nested selects** avoid N+1: `PRODUCT_SELECT` pulls a product with its category, weights and offers in one request (`src/lib/catalogue-shared.ts:21`).

### Notes / gotchas (verified in code)
- `cart_items` uses **two partial unique indexes**; PostgREST `upsert(onConflict)` cannot express them, so the service does an explicit read-then-write (`src/services/cart-service.ts`).
- `addresses` still has **legacy NOT NULL columns** (`line1`, `pincode`); the client writes them alongside the newer `postal_code` (`src/services/address-service.ts:52`).
- `orders` has **two slot columns** (`slot_id` legacy, `delivery_slot_id`) — the PWA reads `delivery_slot_id`.
- **6 legacy/duplicate column pairs** exist across `products`, `delivery_slots`, `categories`, `orders` — full policy in `admin-android/docs/CONTRACT.md` §7.

### Recommended future enhancement
- Move authoritative shop settings (`private.shop_settings`) behind a read the checkout consumes, replacing the hard-coded preview constants in `src/lib/pricing.ts` (`SHOP_SETTINGS_PREVIEW` = ₹20 delivery, free at ₹500).

---

## 5. Authentication

### Already implemented
- **Email + password** sign-up / sign-in / sign-out, and **password reset** via `src/services/auth-service.ts`.
- **Google OAuth** code path exists (`signInWithGoogle`), but the button renders only when the provider is genuinely enabled — a live capability probe (`src/lib/auth-providers.ts`, `GET /auth/v1/settings`). **Google is disabled on the live project** (verified `google: false`), so the button is hidden.
- **Email confirmation is required** on the live project (`mailer_autoconfirm: false`).
- **Session stored in cookies** via `@supabase/ssr`, readable by Server Components; server gating uses `getUser()` (token validated against the auth server), not `getSession()` (`src/lib/auth.ts:31`).
- **Profile row is created explicitly** — there is **no DB trigger** on `auth.users`, so `ensureProfile()` writes `public.profiles` after signup or on first authenticated visit (`src/services/auth-service.ts:276`). `profiles.username` is `NOT NULL UNIQUE`, hence the signup form collects a username.
- **Friendly error mapping** for provider errors (invalid credentials, unconfirmed email, rate limit, network) — raw provider text is never shown (`friendlyAuthError`).
- **Account deletion is a recorded request, not a self-serve delete** (`ACCOUNT_DELETION_MODE = "request"`): deleting an `auth.users` row needs a service-role call, which this project forbids in the browser. Customers can self-clear their own data (`clearPersonalData`).

### Recommended future enhancement
- Enable Google sign-in in the Supabase dashboard (no code change needed — the probe reveals the button automatically).
- Self-serve account deletion via a server-side function with a scoped admin credential (kept out of the browser).

---

## 6. Database Relationship Overview

### Already implemented (live contract — `admin-android/docs/CONTRACT.md`)

```
categories ──1:N── products ──1:N── product_weights
                        └──1:N── offers
products ──N:M── orders  (via order_items)
orders ──1:N── order_status_history
orders ──N:1── addresses (ON DELETE SET NULL — history preserved)
orders ──N:1── delivery_slots
orders ──1:N── complaints ──1:N── complaint_messages
                              └──1:N── complaint_attachments
profiles ──1:1── auth.users
cart_items ──N:1── products / product_weights
notification_outbox (audience = customer | admin)
```

**Anon-visible tables (storefront reads):** `categories`, `products`, `product_weights`, `offers`, `delivery_slots`, `store_settings`, and the **view** `product_catalog` (a flattened product+category+price projection with a server-computed `effective_price`).

**RLS-hidden from anon (12 tables):** `orders`, `order_items`, `order_status_history`, `profiles`, `addresses`, `cart_items`, `complaints`, `complaint_messages`, `complaint_attachments`, `notifications`, `notification_outbox`, `events`. These correctly return **zero rows** to the publishable key — RLS working, not a defect.

**Enums (live):** `order_status` (`PENDING · CONFIRMED · PROCESSING · READY_FOR_DELIVERY · OUT_FOR_DELIVERY · DELIVERED · CANCELLED`), `complaint_status` (`OPEN · IN_PROGRESS · RESOLVED · CLOSED`), `complaint_type`, `user_role` (`customer · admin`), `outbox_audience`, `author_role` (`CUSTOMER · ADMIN`).

**Delivery slots** are generated/maintained by a daily `pg_cron` job (`vedant-delivery-slots`) keeping a **14-day horizon**; the storefront reads a 7-day window (`src/lib/catalogue.ts:205`).

### Recommended future enhancement
- A `products_search` SQL view with a `tsvector` + keyset pagination once the catalogue exceeds a few hundred products (noted in `src/lib/browse.ts`).

---

## 7. Customer Journey

### Already implemented
Home → browse categories / search / offers → product detail (choose weight) → add to cart → cart → **sign in / sign up** (required) → checkout (address + delivery slot) → **`place_order`** → order confirmation → order history & detail with live status → optional support/complaint.

Key facts:
- Checkout is a **Server Component gate** (`requireUser("/checkout")`) wrapping a client selection/submission flow (`src/app/checkout/page.tsx`).
- Order creation is **idempotent** by `request_id`: retrying the same intended order returns the existing order id rather than duplicating it (`src/services/order-service.ts`, `newRequestId()`).
- The cart is **persisted to `localStorage`** and **merged into the server cart on sign-in** (`CartSync`), so a signed-out basket survives refresh and PWA relaunch.

### Recommended future enhancement
- Guest checkout (currently sign-in is mandatory before placing an order).
- Saved-payment / UPI intent (today: cash/UPI on delivery).

---

## 8. Product Catalogue

### Already implemented
- Browse by category, free-text **search** (name + description, `ilike`, wildcards stripped), **offers-only** and **in-stock-only** filters, and sort by name / price asc / price desc (`src/lib/browse.ts`).
- **Weight variants** per product, sorted by grams; a product with no variants is sold at its base price (`src/lib/cart-line.ts:43`).
- **Offers** resolved with the same rule `place_order` uses (`is_active` + time window, latest wins), applied via nested `least()` so a fixed-price and a percentage offer can each lower the price (`src/lib/pricing.ts:83`).
- **Product cards fall back to a glyph + accent "plinth"** when `image_url` is null — and **8 products currently have no image**: Beetroot, Capsicum (Green), Carrot, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato.
- Deterministic ordering (`availability` desc, then name) to avoid hydration mismatch and layout shift (`src/lib/catalogue.ts:64`).

### Recommended future enhancement
- Owner-supplied images for the 8 products above (do this from the Android admin app's image upload).
- Faceted filtering, "recently viewed", and per-category landing SEO copy.

---

## 9. Cart

### Already implemented
- Zustand store persisted to `localStorage` under `vedant.cart.v1` (`src/stores/cart-store.ts`).
- Cart line identity = **(product, weight)** — the same key the DB's partial unique indexes use, so local/server reconciliation is trivial.
- Each line carries a **display + price snapshot** (name, glyph, accent, image, unit/weight label, unit price, list price, quantity limit) so the cart renders instantly without refetching.
- Quantity clamped to `products.quantity_limit`; `refreshSnapshots()` re-prices and re-clamps when the catalogue changes underneath the customer.
- **Server persistence** for signed-in customers (`src/services/cart-service.ts`), with an explicit read-then-write and `23505` treated as success on concurrent insert.
- The **snapshot is a preview only** — `place_order` re-prices everything server-side.

### Recommended future enhancement
- Abandoned-cart nudges (see §26).
- "Save for later".

---

## 10. Checkout

### Already implemented
- Server-fetched address book and delivery slots with the customer's session; the client owns only selection + submission (`src/app/checkout/page.tsx`).
- Slot picker **greys out full slots** using an aggregate load read (`getSlotLoad`) before the customer tries to check out.
- **Money preview** mirrors `place_order` arithmetic in **integer paise** to avoid float drift (`src/lib/money.ts`, `src/lib/pricing.ts`), and the confirmation screen shows the **server's** returned totals.
- Error mapping turns `place_order` failures into actionable copy (`42501` auth/ownership, `22023` validation) — e.g. "One of your items just went out of stock…", "That delivery slot just filled up…" (`src/services/order-service.ts:37`).

### Known caveat
- `SHOP_SETTINGS_PREVIEW` (₹20 delivery, free at ₹500) is a **hard-coded mirror** of `private.shop_settings`; if the owner changes delivery rules server-side, the preview can drift from the charge until it is updated (`src/lib/pricing.ts:164`).

### Recommended future enhancement
- Read delivery rules from a server source of truth instead of the preview constant.
- Save the last-used address as default on checkout.

---

## 11. Orders

### Already implemented
- Order history (`/orders`) and order detail (`/orders/[id]`) with items, per-line totals, adjustment, final total, and **status history** (`src/services/order-service.ts`).
- RLS scopes every read to the signed-in customer's own rows — no explicit `user_id` filter is used (and adding one would misleadingly suggest the filter is the protection).
- Customer-visible status labels and plain-language descriptions (`ORDER_STATUS_META` in `src/lib/format.ts:138`).

### Recommended future enhancement
- Reorder-from-history, invoice/receipt download (PDF), and item-level complaint from an order line.

---

## 12. Delivery Tracking

### Already implemented
- **Status timeline** per order (`order_status_history`) rendered as a tracker with labels ("Order received" → "Confirmed by store" → "Being packed" → "Ready to dispatch" → "Out for delivery" → "Delivered").
- Delivery **slot window** formatting in the customer's local timezone (`formatSlotWindow` / `formatSlotRange`).
- `DELIVERED` and `CANCELLED` are terminal.

### Recommended future enhancement
- **Live delivery map / rider ETA** — not implemented; there is no rider-tracking data in the schema today.
- Push/status-change notifications (see §26).

---

## 13. Customer Support

### Already implemented
- Support list (`/support`) and thread (`/support/[id]`) backed by `complaints` + `complaint_messages` (`src/services/support-service.ts`).
- **DB-enforced rules (not client):** a customer's complaint must have `status = 'OPEN'`, `resolved_* IS NULL`, and any attached order must belong to them; a `complaint_messages` insert must have `author_role = 'CUSTOMER'` and ownership of the parent thread; **UPDATE on `complaints` is admin-only** (a customer cannot self-resolve).
- Complaint status labels (`OPEN · IN_PROGRESS · RESOLVED · CLOSED`) with customer-friendly wording ("In review" for `IN_PROGRESS`).

### Recommended future enhancement
- Attach photos to a complaint (the `complaint_attachments` table and `complaint-images` bucket exist; verify whether the upload UI is wired before claiming it — see §14).
- SLA/response-time indicators.

---

## 14. Storage / Images

### Already implemented (live bucket rules — `admin-android/docs/CONTRACT.md` §6)

| Bucket | Visibility | Read | Write | Path rule |
|---|---|---|---|---|
| `product-images` | public | anyone | **admin only** (`is_admin()`) | any key; public URL stored in `products.image_url` |
| `complaint-images` | private | owner-or-admin | owner-or-admin | **must be `<auth.uid()>/<filename>`** — the policy reads the first path segment |

- Product images render through `next/image` with the Supabase host allow-listed (`next.config.ts:16`) and a 30-day minimum cache TTL.
- Products with no image fall back to the glyph+accent plinth (see §8).

### Unknown / to verify
- Whether the **customer-side complaint photo upload UI** is wired end-to-end is **not confirmed** from a static read. The service layer for attachments was not found among the inspected files; the table and bucket exist. **Resolve by** searching for a storage upload call against `complaint-images`.

### Recommended future enhancement
- Client-side image compression before upload (5 MB limit, `image/jpeg|png|webp`).
- Owner-uploaded product images surfaced for the 8 missing products.

---

## 15. Security / RLS

### Already implemented
- **RLS is the security boundary**, not the UI. Every table is protected; server-side auth helpers exist only for UX (redirects, personalisation) and say so explicitly (`src/lib/auth.ts:14`).
- **No privileged secret in the client:** `src/lib/env.ts` validates the two public env values and throws if a service-role key is detected; `.env.example` documents the rule.
- **Order writes are impossible from the client** — there is no INSERT policy on `orders`/`order_items`/`order_status_history`; only `place_order` writes them.
- **`place_order` is idempotent** via an advisory lock on `(user_id, request_id)`.
- **`complaint-images` path rule** ties an object to its uploader.
- **Password-reset does not reveal whether an email exists** (enumeration-resistant).
- **Checkout is never indexed or cached** (`robots: { index: false }`, `dynamic = "force-dynamic"`).

### Recommended future enhancement
- A Content-Security-Policy header set (not observed in `next.config.ts` today).
- Server-side rate limiting that survives multi-instance deploys — the current limiter is **in-memory** (`src/lib/rate-limit.ts`), suitable for a single instance only.

---

## 16. Environment Configuration

### Already implemented
- `.env.example` documents the exact variables; `.env.local` (git-ignored) currently holds **three** values: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `NEXT_PUBLIC_SITE_URL`.
- `src/lib/env.ts` performs **lazy, validated** reads and fails with one actionable message; `isSupabaseConfigured()` lets pages degrade instead of crashing.

| Variable | Purpose | State |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Project URL | Set |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Public key (anon) | Set |
| `NEXT_PUBLIC_SITE_URL` | Canonical origin / redirects | Set |
| `NEXT_PUBLIC_STORE_PHONE` | Call button | **Unset** |
| `NEXT_PUBLIC_STORE_WHATSAPP` | WhatsApp button | **Unset** |
| `NEXT_PUBLIC_STORE_ADDRESS` | Address / structured data | **Unset** |
| `NEXT_PUBLIC_STORE_EMAIL` | Contact email | **Unset** |

**Consequence of the unset store-contact vars:** `src/config/store.ts` resolves them to `null`, and the Call / WhatsApp buttons are **hidden** by design (a `null` channel hides the affordance rather than rendering a dead button). `store.needsOwnerConfiguration` is therefore `true`.

### Recommended future enhancement
- Have the owner supply the four store-contact values (or serve them from `store_settings`), which instantly activates Call/WhatsApp and completes the structured-data `telephone`/`address` fields.

---

## 17. Existing Functionality

### Already implemented — full route set (24 pages, verified by directory scan)

| Route | Purpose |
|---|---|
| `/` | Home — hero, category tiles, product rails |
| `/about` | About the store |
| `/account` | Account overview / profile |
| `/account/addresses` | Address book (CRUD, default selection) |
| `/account/reset-password` | Set a new password |
| `/auth/callback` | OAuth / email-confirmation callback (Route Handler) |
| `/cart` | Basket |
| `/categories` | Category index |
| `/categories/[id]` | Category products |
| `/checkout` | Address + slot selection and order placement |
| `/contact` | Contact |
| `/forgot-password` | Request password reset |
| `/login` | Sign in |
| `/notifications` | In-app notifications (real but empty until the admin side writes rows) |
| `/offers` | Live offers |
| `/offline` | Offline fallback page |
| `/orders` | Order history |
| `/orders/[id]` | Order detail + status tracker |
| `/privacy` | Privacy policy |
| `/products/[id]` | Product detail (weight picker) |
| `/search` | Search results |
| `/signup` | Create account |
| `/support` | Support / complaints list |
| `/support/[id]` | Complaint thread |
| `/terms` | Terms |

**Cross-cutting features implemented:** PWA install + update prompt (`src/components/pwa/pwa-provider.tsx`), service worker (`public/sw.js`), skip-to-content link, toast system (`sonner`), local-business JSON-LD, fonts self-hosted, cart merge on sign-in.

### Recommended future enhancement
- Wishlist/favourites, order tracking map, referral programme, seasonal "what's in season" content.

---

## 18. Known Limitations

### Confirmed limitations
1. **Sign-in is required to place an order** — there is no guest checkout.
2. **No online payment** — orders are paid on delivery.
3. **Store contact channels are hidden** because `NEXT_PUBLIC_STORE_*` are unset (§16).
4. **8 products have no image** (§8).
5. **`store_settings.store_name` reads `"Fresh Mart"`** — wrong for a store branded *Vedant Fruit & Vegetables*. **Owner action.** (`admin-android/docs/CONTRACT.md` §3.6.)
6. **Notifications are empty by design** — the `notification_outbox` has no customer-facing INSERT path; the list is honest about this rather than showing placeholders.
7. **`src/types/database.ts` is stale** (missing live columns on `categories`/`products`, and does not model `store_settings` or the `product_catalog` view). It is the PWA's compile source, so it must be regenerated carefully and followed by the PWA gates (§28).
8. **Rate limiting is in-memory** — single-instance only.
9. **No `supabase/` migrations directory in the repo** — the database is managed outside version control; the live schema is captured only in `admin-android/docs/CONTRACT.md` and `SCHEMA_SNAPSHOT.md`.
10. **Account deletion is a request, not self-serve** (§5).

### Recommended future enhancement
- Address each item above as part of the roadmap (§29).

---

## 19. Technical Debt

### Confirmed debt (with evidence)

| # | Item | Evidence | Suggested action |
|---|---|---|---|
| D1 | Stale hand-maintained DB types | `src/types/database.ts` header + `CONTRACT.md` §11 | Regenerate from live schema; delete the hand-maintained file |
| D2 | Broken/abandoned dependency | `graphify@1.0.0`, 0 imports | Remove from `package.json` |
| D3 | Dormant unit-test stack | `vitest` + Testing Library declared, **no config, no `test` script** | Add `vitest.config.ts` + `test` script |
| D4 | Suspected hydration mismatch | `tests/hydration.spec.ts` documents the `qty-stepper.tsx` cart-rehydrate mismatch | Fix by gating client-only cart render or `useSyncExternalStore` |
| D5 | Hard-coded shop-settings preview | `SHOP_SETTINGS_PREVIEW` in `src/lib/pricing.ts` | Source from server |
| D6 | Legacy/duplicate DB columns | 6 pairs (`CONTRACT.md` §7) | Follow the documented write-both / read-new policy; migrate away over time |
| D7 | Legacy `addresses` columns written alongside new ones | `address-service.ts:52` | Drop legacy columns after a migration |
| D8 | In-memory rate limiter | `src/lib/rate-limit.ts` | Move to a shared store |
| D9 | No migrations in VCS | no `supabase/` dir | Adopt Supabase CLI migrations |
| D10 | Duplicate complaint-creation service | `createComplaint` exists in both `services/order-service.ts` and `services/support-service.ts` | Consolidate to one |

### Recommended future enhancement
- A debt-burndown track in each sprint; D1–D4 are the highest-value, lowest-risk fixes.

---

## 20. Recommended Future Enhancements (overview)

Grouped by theme; each is expanded in §21–§26.

| Theme | Enhancements |
|---|---|
| Business-critical | Fix store identity & contact (§16), add the 8 product images, surface real delivery rules |
| Product | Guest checkout, online payment, reorder, wishlist, live tracking |
| Experience | Skeleton/loading polish, better empty/error states, PWA offline improvements |
| Accessibility | Audit + fixes (§23) |
| Growth | SEO content, structured data expansion (§24), analytics (§25), notifications (§26) |
| Engineering | Types generation, tests + CI, migrations in VCS, observability |

---

## 21. Performance Improvements

### Already implemented
- **Single nested PostgREST select** per catalogue page — no N+1 (`src/lib/catalogue-shared.ts`).
- `next/image` with AVIF/WebP, a Supabase host allow-list, and a **30-day minimum cache TTL** (`next.config.ts:16`).
- `optimizePackageImports` for `lucide-react` and `motion` (build-time tree-shaking).
- Self-hosted fonts with `display: swap` → no FOIT, minimal CLS.
- Service worker: stale-while-revalidate for immutable `/_next/static` assets; Supabase responses **never cached** (`public/sw.js`).
- Deterministic ordering to avoid hydration mismatch and layout shift.

### Recommended future enhancement
- Add route-level `loading.tsx` / skeletons for perceived speed.
- Set explicit `Cache-Control` / ISR (`revalidate`) on public catalogue routes.
- A `products_search` view with `tsvector` + keyset pagination for catalogue scale.
- Bundle analysis (`@next/bundle-analyzer`) to trim client JS.

---

## 22. UX Improvements

### Already implemented
- Mobile-first layout with bottom nav, sticky cart bar, and a `pb-nav` safe-area class (`src/app/layout.tsx`).
- Toasts for feedback; a dismissible, remembered install prompt; an "update available" bar that never interrupts checkout.
- Honest empty/blocked states (e.g. notifications say they are empty rather than faking content; contact buttons hide when unset).
- Plain-language error copy throughout the order flow.

### Recommended future enhancement
- Loading skeletons on catalogue/orders; optimistic UI where safe (never for money/status — the preview pattern already guards this).
- Richer "out of stock" recovery (substitute suggestions).
- Saved addresses surfaced on the home screen for one-tap reorder.

---

## 23. Accessibility Improvements

### Already implemented
- **Zoom is never blocked** — `maximumScale: 5`, `userScalable: true` (`src/app/layout.tsx:87`), an explicit a11y decision.
- **Skip-to-content** link present.
- Semantic `role="status"` on the PWA banner; `aria-hidden` on decorative icons.
- Colour + text labels for status (never colour alone).

### Recommended future enhancement
- A formal **WCAG 2.1 AA audit** (axe/Lighthouse), focusing on: contrast of muted text tokens, focus-visible rings on all interactive elements, form error announcement via `aria-live`, and full keyboard traversal of the Radix dialogs/drawers.
- Respect `prefers-reduced-motion` in the `motion` animations.

---

## 24. SEO Improvements

### Already implemented
- Per-page `metadata` with title template, description, canonical, OpenGraph and Twitter cards (`src/app/layout.tsx:46`).
- `robots` allows indexing site-wide; **checkout is excluded** (`index: false`).
- **Local-business structured data** (`GroceryStore`) with conditional `telephone`/`address`/`openingHours` — omitted rather than faked when unset (`src/app/layout.tsx:214`).
- Human-readable, keyword-rich default copy (`src/config/site.ts`).

### Recommended future enhancement
- `sitemap.xml` and `robots.txt` (not observed).
- Per-product `Product`/`Offer` structured data on `/products/[id]`.
- Category landing copy + FAQ schema; a blog/seasonal content hub.
- Complete the structured data by setting the store address (§16).

---

## 25. Analytics Opportunities

### Already implemented
- **None observed.** No analytics SDK in `package.json`; no analytics calls found. An `events` table exists in the schema (RLS-hidden from anon), but the client does not write to it.

### Recommended future enhancement
- Privacy-first product analytics (page views, funnel: home → product → cart → checkout → order).
- Emit to the existing `events` table (server-side) or a hosted tool — decide based on the owner's privacy stance.
- Conversion dashboards: cart-abandonment rate, slot fill rate, offer performance.

---

## 26. Notification Opportunities

### Already implemented
- An in-app notifications page reads `notification_outbox` where `audience = 'customer'` (`src/lib/notifications.ts`). The table has **no customer INSERT policy** — only the backend/admin writes rows, so the list is real but currently empty.
- Order status history is visible in-app on the order detail page.

### Recommended future enhancement
- **Order-status push/email**: have the backend (or the Android admin app) write to `notification_outbox` on each status transition; surface unread counts in the header.
- Web Push via the existing service worker (a push subscription path is not implemented today).
- WhatsApp/SMS status updates (the owner's preferred channel in India).
- Abandoned-cart reminders.

---

## 27. Testing Strategy

### Already implemented
- A **Playwright** suite in `tests/` (`routes.spec.ts`, `flows.spec.ts`, `hydration.spec.ts`, plus `fixtures.ts`, `helpers.ts`, `catalogue.ts`).
  - Route coverage: every route is visited, its status/redirect/rendered content asserted, console errors captured, and a screenshot written to `.logs/verify/`.
  - Core flows exercised at **390×844** (mobile-first) with a desktop spot-check.
  - A **hydration-mismatch probe** documents the suspected `qty-stepper.tsx` defect.
- `playwright.config.ts` boots its **own** dev server on port 3100 (self-contained), serial workers for deterministic timing, JSON reporter to `.logs/verify/results.json`.

### Gaps / not yet configured
- **Vitest has no config and no `test` script** — the declared unit-test stack is dormant.
- **No CI workflow** observed.

### Recommended future enhancement
1. Add `vitest.config.ts` + a `test` script; unit-test `money.ts`, `pricing.ts`, `browse.ts`, `validation.ts`, `cart-line.ts` (pure functions — cheap and high-value).
2. Fix the hydration defect surfaced by `hydration.spec.ts`.
3. Wire Playwright + Vitest into CI (see §28).
4. Add a smoke test that asserts the **server's** returned totals equal the confirmation screen (guards the "never fake success" invariant).

---

## 28. Deployment Considerations

### Already implemented
- Standard Next.js build (`next build`) / start (`next start`); pnpm workspace.
- `next.config.ts` sets cache headers for `/sw.js` (no-store) and `/manifest.webmanifest`, disables the `X-Powered-By` header, and enables `reactStrictMode`.
- Env validation fails fast with an actionable message when Supabase config is missing.

### Not configured / unknown
- **No CI/CD config observed** (no workflow files in the repo root listing).
- **No `supabase/` migrations directory** — schema changes are not version-controlled with the app.
- Hosting target is **not specified** in the repo; Vercel is the natural fit for this stack but is **not asserted** here.

### Recommended future enhancement
- Adopt a CI pipeline running `tsc --noEmit`, `eslint`, `next build`, Vitest, and the Playwright suite as a gate.
- Put the database under Supabase CLI migrations; run them in CI against a staging project.
- Define environments (preview / staging / production) with the correct `NEXT_PUBLIC_SITE_URL` per environment.

---

## 29. Future Roadmap

A pragmatic sequence. **Priority 1–3 are the recommended top three** (see the summary sent to the team lead).

| Phase | Theme | Contents |
|---|---|---|
| **P1 — Trust & identity (do first)** | Make the storefront truthful and complete | Fix `store_settings.store_name` ("Fresh Mart" → Vedant); set `NEXT_PUBLIC_STORE_*` so Call/WhatsApp/address work; add the 8 missing product images |
| **P2 — Contract & correctness** | Remove the risk of silent drift | Regenerate `src/types/database.ts` from the live schema (then re-run the PWA gates); fix the hydration mismatch; remove `graphify`; consolidate duplicate complaint service |
| **P3 — Engineering foundation** | Enable safe iteration | Vitest config + unit tests; CI pipeline (types, lint, build, tests); migrations in VCS; observability/analytics |
| **P4 — Customer experience** | Reduce friction & grow | Order-status notifications (push/WhatsApp) via `notification_outbox`; guest checkout; reorder-from-history; wishlist |
| **P5 — Growth** | Acquire & retain | Sitemap/robots + product structured data; category SEO copy; offers/promotions tooling; referral programme |
| **P6 — Advanced** | New capability | Online payments; live delivery tracking; personalised recommendations |

### Recommended top 3 (highest value / lowest risk)
1. **Fix store identity & owner configuration** — `store_name`, the four `NEXT_PUBLIC_STORE_*` values, and the 8 missing product images. Business-visible, cheap, and unblocks Call/WhatsApp and structured data.
2. **Close the data-contract gap & fix the known defect** — regenerate the DB types from the live schema and fix the `qty-stepper` hydration mismatch. Prevents silent breakage as the Android app and PWA evolve together.
3. **Build the engineering foundation** — Vitest + unit tests for the money/pricing/validation pure functions, plus a CI pipeline. Makes every later change safe.

---

## 30. Developer Onboarding Instructions

### Prerequisites
- Node.js 20+, **pnpm 12.8.1** (`packageManager` is pinned).
- A Supabase project (or access to `hyibaljxicojfoxbihdn`).
- Read `AGENTS.md` first — **this is Next.js 16, which has breaking changes**; consult `node_modules/next/dist/docs/` before writing code.

### First run
```bash
# 1. Install
pnpm install

# 2. Configure environment (copy the template, fill in real values)
cp .env.example .env.local
#    Required: NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY, NEXT_PUBLIC_SITE_URL
#    Optional: NEXT_PUBLIC_STORE_PHONE / WHATSAPP / ADDRESS / EMAIL

# 3. Develop
pnpm dev            # http://localhost:3000

# 4. Gates before calling anything done
pnpm exec tsc --noEmit
pnpm exec eslint .
pnpm run build
pnpm exec playwright test    # self-contained; boots its own server on :3100
```

### Where things live
| You want to… | Go to |
|---|---|
| Add a page | `src/app/<route>/page.tsx` |
| Read public catalogue data (server) | `src/lib/catalogue.ts` |
| Do a customer write | `src/services/*.ts` (`"use client"`) |
| Touch money | `src/lib/money.ts` (integer paise) — never raw floats |
| Touch pricing preview | `src/lib/pricing.ts` — and remember it is a **preview**, not authority |
| Change store identity | `src/config/store.ts` + `.env.local` |
| Change SEO defaults | `src/config/site.ts` + `src/app/layout.tsx` metadata |
| Understand the DB | **`admin-android/docs/CONTRACT.md`** (live authority), not `docs/BACKEND_CONTRACT.md` (stale, wrong project) |

### Non-negotiable rules for this codebase
1. **Only the publishable key** may reach the browser — `src/lib/env.ts` enforces it.
2. **The database owns the wires** — enum names are the Postgres names; labels live in `src/lib/format.ts`.
3. **The client never computes an authoritative amount** — `place_order` re-prices everything; show the server's totals.
4. **RLS is the security boundary** — server auth helpers are UX only.
5. **Never fake success** — a mutation is done only when the server confirms.
6. **Do not edit the Android app's code or the customer app's admin surface** — the shared boundary is the backend contract only.

### Traps to avoid (learned from the repo)
- `docs/BACKEND_CONTRACT.md` describes a **different, older project** — do not trust it.
- `src/types/database.ts` is **stale** — check `CONTRACT.md` before relying on a column.
- `cart_items` partial unique indexes break PostgREST `upsert` — use read-then-write.
- `addresses` still has legacy NOT NULL columns — write `line1`/`pincode` too.
- The service worker is **production-only** (dev would serve stale modules).
- The `graphify` dependency is dead — remove it.

---

*End of handoff document. Where this document and the live system disagree, the live system wins — update this document.*
