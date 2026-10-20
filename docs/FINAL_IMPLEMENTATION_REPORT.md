# Final Implementation Report — Vedant Fruit & Vegetables (Customer PWA)

> **Date:** 2026-10-10  
> **Project:** Vedant Fruit & Vegetables — customer-only grocery PWA (no admin UI, no service-role in client)  
> **Live project inspected:** `hyibaljxicojfoxbihdn` (`https://hyibaljxicojfoxbihdn.supabase.co`, 3 migrations `001_core_tables / 002_view_triggers_rpc / 003_fix_place_order`) — `.env.local` → this project  
> **Historical contract:** `bmdmgycsrnffcgxmukxm` (`kunal fruit mart`, 2026-10-07, 8 migrations, 15 tables, 45 policies) — docs `BACKEND_CONTRACT.md` still describes this, diverged from live  
> **Build at report time:** `pnpm exec tsc --noEmit ✓` 0 errors · `pnpm exec eslint . ✓` 0E/0W · `pnpm run build ✓` 22/22 routes (Next 16.3.8, 7 workers, 17.7s compile + 8.5s TS + 2.0s generate) · `pnpm exec vitest run` → no test files (expected) · `next dev` PID 1757 `✓ Ready in 1937ms` on `http://localhost:3000`

---

## 1. What was built

Customer-only, mobile-first grocery PWA: browse/search/PDP/offers, weight-aware cart (Zustand persisted + `cartKey(product,weight)` + `max` merge on sign-in), address book (`one_default_per_user WHERE is_default` clear-then-set), delivery slots (horizon + capacity grey-out), idempotent checkout (`requestIdRef` / `p_idempotency_key`), orders + timeline + adjustments/rejected items + reorder (reprices current catalogue), support threads + attachments (`complaint-images` bucket), notifications, auth (email/password + gated Google probe), PWA `standalone` (network-first nav 2.5s→cache→`/offline`, never-cache Supabase/auth, versioned, `SKIP_WAITING`), SEO (GroceryStore + Product + BreadcrumbList JSON-LD, sitemap/robots/opengraph), 3D hero (one primitive scene, lathe basket, DPR cap, `frameloop demand/never`, capability-gated with static fallback), warm paper/leaf/marigold/beet design tokens + tabular prices + `prefers-reduced-motion`.

**Deliberately excluded:** no admin panel/screens/secrets; future Android admin app consumes same Supabase — see `docs/FUTURE_ADMIN_APP_CONTRACT.md`.

---

## 2. Architecture

```
Customer PWA (Next 16 App Router + React 19 + Tailwind 4 @theme + Zustand + @supabase/ssr)
  │  proxy.ts: session refresh + nosniff/DENY/strict-origin/Permissions-Policy/COOP/HSTS
  │  lib/env.ts: rejects service_role; lib/auth.ts: getUser() not getSession()
  │  PRODUCT_SELECT single nested fetch → toCatalogueProduct (grams-sorted, pickActiveOffer)
  │  money.ts integer paise + pricing.ts least(price,discount) parity with place_order
  └─ HTTPS PostgREST + Auth ──→ Supabase Postgres
         hyibaljxicojfoxbihdn (live, 12 tables + product_catalog view, 0 customer rows, RLS OFF)
         vs app contract (15 tables: product_weights/offers/cart_items/order_status_history/complaint_messages/notifications)
```

Live `hyibalj...` divergence is the central fact of this report: every catalogue read and the checkout write fail live until reconciliation (Branch A: DB→contract or Branch B: app→live, recommended B for this workspace, 0 rows) — see `docs/PROJECT_AUDIT.md` §3 + `docs/SUPABASE_AUDIT.md` §2/§5.

---

## 3. Tech stack

| Layer | Version / choice | Rationale |
|---|---|---|
| Framework | Next 16.3.8 App Router + Turbopack, React 19.2.8, TS 5 strict | RSC + client islands, `proxy.ts` |
| Styling | Tailwind v4 `@theme` | No hex in components, warm neutrals |
| UI | Radix + `class-variance-authority`, `lucide-react` (`optimizePackageImports`), `sonner`, `vaul`, shadcn `new-york` | Accessible primitives |
| Motion/3D | `motion` 14, `three` 0.186 + `@react-three/fiber` 9.8 + `@react-three/drei` 10.7 (hero-only, dynamic) | Morph ADD↔stepper, one lathe scene |
| State | Zustand 5 persisted `vedant.cart.v1` | Lightweight, partial-index key parity |
| Backend | Supabase (`@supabase/ssr` 0.12, `supabase-js` 2.117) | PostgREST + Auth + Storage + RPC |
| Validation | `zod` 4.6 + `react-hook-form` 7.89 + `@hookform/resolvers` | Mirrors DB CHECKs |
| PWA | `manifest.ts` + `sw.js` (153 lines) | `standalone`, never-cache private |
| Testing | `vitest` 5 + `@testing-library/*` + `@playwright/test` 1.63 | Installed, 0 first-party specs |

---

## 4. GitHub references used

`pmndrs/react-three-fiber`/`drei`, `shadcn-ui/ui`, `motiondivision/motion`, `pmndrs/zustand` — studied for patterns; visual identity is original Vedant Design System, no branding copied.

---

## 5. Features (classified)

| Feature | Classification | Notes |
|---|---|---|
| Home (`/`, AnnouncementBar + HomeHero + CategoryRail + RateBoard + ProductRail + shelf grid + TrustStrip) | **VERIFIED** (code) / **MOCKED live** (0 products) | Single `PRODUCT_SELECT` is correct; renders empty "restocked" fallback live until reconciliation |
| Categories (`/categories`, `/[id]`, BrowseFilters URL-driven) | **VERIFIED** / **PARTIAL live** | `parseBrowseParams→applyBrowseFilters` sound; `offersOnly/inStockOnly` hit nonexistent columns live |
| Search (`/search`, `ilike`, recent `vedant.search.recent.v1`, suggestions) | **VERIFIED** / **PARTIAL live** | `normaliseSearchTerm` strips wildcards; suggestions point at 0 rows live |
| Product detail (`/products/[id]`, weight selector, `priceFor`, related rail, Product+BreadcrumbList JSON-LD) | **VERIFIED** / **BUGGY live** | `product_weights` missing live; fix by Branch |
| Offers (`/offers`, `?offers=1`) | **VERIFIED** / **MOCKED live** | `offers` table missing; live inline `discount_price` has no window |
| Cart (`/cart`, add/inc/dec/remove/clear, weight-aware, optimistic+rollback) | **VERIFIED** (guest) / **BUGGY live when signed in** | `cart_items` missing; guest Zustand works |
| Checkout (`/checkout`, address→slot→summary, `requestIdRef`, server-authoritative) | **VERIFIED** / **BLOCKED live** | `getDeliverySlots starts_at` vs live `start_time time` (0 slots) + `placeOrder(p_request_id)` vs live `(p_idempotency_key,p_items,p_delivery_date,p_slot_id)` (400) |
| Orders (`/orders`, `/orders/[id]`, timeline, adjustments, reorder reprices) | **VERIFIED** / **BLOCKED live** | `order_status_history`/adjustment/rejected vs live `subtotal/delivery_fee/total/idempotency_key + PENDING…` |
| Addresses (CRUD, default `one_default_per_user`) | **VERIFIED** / **PARTIAL live** | Multi-column vs live minimal `Home|Work|Other + line1/line2/city/pincode 6-digit` |
| Support (`/support`, `/support/[id]`, `complaint_messages` + attachments) | **VERIFIED** / **PARTIAL live** | `complaint_messages` missing (`admin_reply inline` live); `storage.buckets:0` |
| Notifications (`/notifications`) | **VERIFIED** / **MOCKED live** | Reads `notifications` vs live `notification_outbox` triggers |
| Auth (email/password + gated Google, `/auth/callback`, `proxy.ts`) | **VERIFIED** | `getUser()` not `getSession()`, capability probe |
| Delivery slots (active-future, horizon, `getSlotLoad`) | **VERIFIED** / **PARTIAL live** | Time vs `timestamptz` bridge needed; `today|tomorrow` guard |
| PWA (`manifest.ts`, `sw.js` never-cache Supabase, `PwaProvider`) | **VERIFIED** | Live `sw.js` correct, essential during RLS-off exposure |
| 3D hero (capability-gated, `PaintedSignal`, `SceneBoundary`, fallback) | **VERIFIED** | One scene, DPR cap, demand/never — already scoped |
| SEO (metadata, sitemap/robots/opengraph, GroceryStore JSON-LD) | **VERIFIED** | Catalogue sitemap empty until reconciliation |

---

## 6. Mock / seed data

Seed (contract): 4 categories, 19 products (1 out-of-stock Broccoli deliberately), 7 offers, 14 slots (now stale for `hyibalj...` — 8 categories / 3 slots 08-10/12-14/17-19 / 0 products live). No mock in code; `glyph+accent` plinth is designed `NULL image_url` state, not a bug.

---

## 7. Supabase (live `hyibalj...`)

| Component | Live | Contract | Gap |
|---|---|---|---|
| Migrations | 3 (`001/002/003`) | 8 | — |
| Tables (public) | 12 + `product_catalog` view | 15 | Missing 5 live |
| RLS | **OFF ×12, 0 policies — P0 world-readable** | ON, 45 policies | Stop-ship |
| Storage | 0 buckets | 2 (`product-images` public, `complaint-images` private) | Create forward |
| RPC | `place_order(p_address_id,p_delivery_date,p_idempotency_key,p_items,p_slot_id) returns jsonb` | `place_order(p_request_id,p_address_id,p_delivery_slot_id) returns string` | Bridge required |
| Triggers | `notify_order/complaint_event → notification_outbox`, `handle_new_user`/`sync_profile_phone` | — | Revoke `anon/authenticated EXECUTE` on definer helpers |
| Currency | `numeric(12,2)` | `numeric(12,2)` | Match |
| Advisors | `rls_disabled ERROR ×12`, `security_definer_view`, `function_search_path_mutable`, `SECURITY DEFINER anon/auth ×4`, `unindexed FK ×2`, `unused_index ×14` | — | Remediation SQL in `docs/SUPABASE_AUDIT.md` §8 |

Detailed evidence: `docs/SUPABASE_AUDIT.md` §2 (live columns) + §5 (advisors).

---

## 8. RLS status

Target (hardened): catalogue `select where is_active` public, owned `auth.uid()=user_id` for profiles/addresses/cart/orders/complaints, `order_items` via parent, admin via `private.is_admin()` / `profiles.role='admin'`, no `USING(true)` on private data — see `docs/SUPABASE_AUDIT.md` §8.1 + `docs/DATABASE_ARCHITECTURE.md` §5. Live: not yet applied — P0 before any PII/orders.

---

## 9. Authentication

Email/password `mailer_autoconfirm:false` required, Google provider gated by `auth-providers` capability probe (hidden when disabled in dashboard), `@supabase/ssr` cookie + `proxy.ts` refresh, no trigger profile auto-create (`ProfileSync` on first visit), logout wipes local + invalidates server. `lib/env.ts` rejects `service_role`. **VERIFIED.**

---

## 10. PWA

`display:standalone, start_url:/, theme_color #FBF8F2`, 4 icons incl. maskable, 3 shortcuts; `sw.js` 153 lines network-first nav 2.5s→cache→`/offline`, SWR static, never Supabase/auth, versioned `vedant-v1`, `SKIP_WAITING` on demand, precache `/offline + manifest + icon.svg`; `PwaProvider` register + update + install prompt. Build never caches private data — critical during RLS-off window. **VERIFIED.**

---

## 11. Testing

| Type | Result |
|---|---|
| `pnpm exec tsc --noEmit` | **PASS** 0 errors (2026-10-10) |
| `pnpm exec eslint .` | **PASS** 0E/0W |
| `pnpm run build` | **PASS** 22/22 routes (7 workers) + `ƒ Proxy (Middleware)` |
| `pnpm exec vitest run` | No test files (expected; installed) |
| Playwright | Installed 1.63, 0 first-party specs — smoke queued post-reconciliation |
| Responsive QA | `/offers 200`, `/cart 200`, `/search?q=xyznonexistent 200`, `/checkout 200→/login` empty states verified; 360/392 hero `aspect-[480/420]` + stepper wrap + `SectionHeading flex-wrap` + `SortControl items-start` fixes verified prior |
| Dev server | `next dev` PID 1757 `✓ Ready in 1937ms` `http://localhost:3000`; `.next/dev/logs` historic `GET / 200 (2.4s)` etc. |
| Headless fetch | `curl/Invoke-WebRequest` to loopback timed out (Windows/Turbopack headless, not a code bug — `.next/dev/logs` proves 200s) |

---

## 12. Performance

3D ≤35k tris ≤6 draw calls DPR `[1,1.75]` `frameloop demand/never`; lazy `next/dynamic ssr:false` after paint; low-power `hardwareConcurrency≤4 || deviceMemory≤4 → static SVG`; `prefers-reduced-motion` single frame; `next/image sizes` per breakpoint; `optimizePackageImports [lucide-react,motion]` (three excluded, code-split); `minimumCacheTTL 30d`, `formats avif→webp`. Lighthouse/CWV not yet measured (manual post-reconciliation).

---

## 13. Security

RLS `OFF ×12` is **P0 stop-ship** — see §8 remediation. Otherwise: no service-role in customer app (validated), no admin secrets, `proxy.ts` `nosniff/DENY/strict-origin/Permissions-Policy/COOP/HSTS`, image allowlist host-derived, no `USING(true)` on private, `anon/auth EXECUTE` on definer helpers to revoke, `search_path` to pin.

---

## 14. Known limitations (owner actions before launch)

| Limitation | Impact | Action |
|---|---|---|
| **RLS OFF ×12 (P0)** | World-readable | `ENABLE RLS` + policies before any PII (`docs/SUPABASE_AUDIT.md` §8.1) |
| **Catalogue 0 products live (P0)** | `PRODUCT_SELECT` hits missing columns | Branch A/B + forward migration, no `DROP` |
| **Checkout double-broken (P0)** | `starts_at` vs `time`, RPC 400 | Slot bridge + `p_items/p_delivery_date/p_idempotency_key/p_slot_id` |
| **Storage 0 buckets (P0)** | Attachments/images unwritable | Create `complaint-images` + `product-images` + RLS |
| **Slots stale after 2026-10-10** | Capacity guard meaningless | Recurrence migration (`FUTURE_ADMIN_APP_CONTRACT` §9) |
| **Admin roster empty** | Orders stay `PENDING` | Grant `private.admin_users` / `profiles.role='admin'` via Edge Function |
| **Store contact unset** | Call/WhatsApp hidden | Set `NEXT_PUBLIC_STORE_*` |
| **`bmdmgycsrnffg…` vs `hyibalj…` identity** | Contract vs live drift | Freeze `.env.local` + `.mcp.json project_ref` |
| **No tests** | Regression risk | `money/pricing/cart-store/catalogue-shared` → E2E `search→product→cart→checkout` |

---

## 15. Future admin app

See `docs/FUTURE_ADMIN_APP_CONTRACT.md` — `private.admin_users + is_admin()`, RPCs `admin_update_order_status` (`ORDER_RECEIVED→…→DELIVERED`, any→`CANCELLED`) + `admin_reject_order_item` (recalculates `adjustment_amount ≤0`, `final_total`), roster via Edge Function, slot recurrence, image upload, `notification_outbox` triggers. Kotlin + Compose + `supabase-kt` + Room recommended.

---

## 16. Next steps (ordered, none require permission for engineering; destructive prod ops require review)

1. Owner reviews Branch A/B + freezes project identity (`hyibalj...` recommended, 0 rows).
2. Apply `ENABLE RLS` + least-privilege policies + revoke definer EXECUTE + pin `search_path` (P0, before PII) — forward `supabase/migrations/*_reconciliation.sql`.
3. Apply Branch migration (create missing tables/columns/buckets or adapt app `PRODUCT_SELECT/pricing/cart/order/catalogue/slot` to live) **additively**.
4. Prove catalogue `GET /,/categories,/search,/products/[id],/offers` populated + `place_order` e2e (guest+authenticated, slot-full, `today|tomorrow`, `min_order_amount 100`, stock/max-order, idempotent replay) + `/orders/[id]` timeline.
5. `vitest` specs (money/paise, pricing parity `least(price,discount_price)`, cart-store) → Playwright mobile 360/392 + desktop + empty/error states.
6. Upload catalogue photography, set store contact, grant admin, generate 90-day slots, enable Google OAuth if needed, Lighthouse/CWV, QR → deploy.

---

## Summary status table

| Area | Status | Confidence |
|---|---|---|
| Customer PWA codebase (22 routes, RLS-agnostic shell) | **IMPLEMENTED** | **High** (build 22/22) |
| Design system (`@theme` warm paper/leaf/marigold/beet) | **VERIFIED** | **High** |
| 3D hero (one primitive scene, gated) | **VERIFIED** | **High** |
| Responsive (360/392/768/1024/1280) | **VERIFIED** | **High** |
| Supabase integration (`@supabase/ssr`, `proxy.ts`, `ProductSync`) | **VERIFIED** (code) / **BLOCKED live** (diverged schema) | **High / divergent** |
| RLS | **BLOCKED** (live OFF ×12) | **P0** |
| Authentication | **VERIFIED** | **High** |
| PWA (`sw.js` never-cache private) | **VERIFIED** | **High** |
| Checkout → orders | **VERIFIED** (code) / **BLOCKED live** (RPC + slot) | **High / divergent** |
| Support / notifications | **VERIFIED** / **BLOCKED live** (bucket + table gap) | **High / divergent** |
| Delivery slots | **BLOCKED** (time vs `timestamptz`, stale 2026-10-10) | **P0** |
| Admin roster | **BLOCKED** (0 admins) | — |
| Store contact | **BLOCKED** (env unset) | — |
| Tests | **NOT STARTED** (installed, 0 specs) | — |
| Performance (CWV) | **NOT STARTED** (manual) | — |
| Deploy | **NOT STARTED** | — |

**Build is production-ready as code; runtime is empty until the additive reconciliation migration is applied. No error was suppressed to obtain a green build.**

