# Technology Research — Vedant Fruit & Vegetables

> **Date:** 2026-10-10
> **Context:** Next.js 16.3.8 App Router + React 19.2.8 + Tailwind v4 + Supabase (`hyibaljxicojfoxbihdn`) customer PWA. Build at research time: `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓`.
> **Method:** docs + MCP + file-tree inspection + npm view + web research against maintenance, license, React/Next/TS compatibility, bundle, a11y, security.

## 0. Decision

No new dependencies installed in this pass. The stack as authored is correct for a small-catalogue, mobile-first grocery PWA. Every install below is **evaluated, not applied** — each must earn its way in via a forward `supabase/migrations/*.sql` or a UI proof, not because it looks impressive.

Current lockfile: `pnpm@12.8.1`, `next@16.3.8`, `react@19.2.8`, `tailwindcss@4`, `motion@14`, `three@0.186`, `@react-three/fiber@9.8`, `@react-three/drei@10.7`, `zustand@5`, `@supabase/ssr@0.12`, `@supabase/supabase-js@2.117`, `zod@4.6`, `react-hook-form@7.89`, `lucide-react`, `sonner`, `vaul`, Radix primitives, `vitest@5`, `@playwright/test@1.63`.

## 1. UI / Design system

| Area | Current | Candidates evaluated | Verdict |
|---|---|---|---|
| Styling | Tailwind v4 `@theme` tokens (`src/app/globals.css`) | Tailwind v4 is correct — v4 `@theme` removes hard-coded hex, paper/leaf/marigold/beet ramps are real. Keep verbatim. | Keep |
| Primitives | Radix (`dialog`, `dropdown-menu`, `select`, `radio-group`, `checkbox`, `tabs`, `accordion`, `popover`, `avatar`, `label`, `progress`, `separator`, `slot`) + `class-variance-authority` + shadcn `new-york` | Radix is the right call for a11y (focus, keyboard, aria). No headless swap needed. | Keep |
| Icons | `lucide-react` + `optimizePackageImports: [lucide-react]` | `lucide-react` tree-shakes correctly; `optimizePackageImports` proven by build. No icon-font. | Keep |
| Toasts/drawers | `sonner` + `vaul` | `sonner` bottom-center, `vaul` drawer — both maintained, a11y correct. Keep. | Keep |
| Typography | `next/font` self-hosted `Inter` + `Bricolage_Grotesque` | Self-hosted via `next/font` avoids Google runtime, zero CLS. Pairing is correct (Inter body, Bricolage display). Keep. | Keep |

## 2. Motion & scrolling

| Candidate | Maintenance | Bundle | A11y | Verdict for this app |
|---|---|---|---|---|
| `motion` (framer-motion v14 successor) | Active, React 19 compatible | Tree-shakable; already `optimizePackageImports` | Respects `prefers-reduced-motion` when wired; this app already has kill switch | **Keep** — morph `ADD→stepper`, sheet/drawer in CSS `data-state` is correct split (critical-path CSS, decorative motion JS). |
| `Lenis` (smooth scroll) | Active | ~8kb gzip, needs `raf` loop | Breaks `prefers-reduced-motion` if mis-applied; iOS rubber-banding. | **Defer** — `html { scroll-behavior: smooth }` + `data-scroll-behavior="smooth"` is enough for a grocery shell. Lenis is for editorial, not commerce. Native scroll must remain. |
| `GSAP` | Active, commercial license nuance | Larger; imperative | Not React-idiomatic for this shell | **Defer** — `motion` covers button feedback, cart badge, quantity change, modal/drawer. No GSAP timeline needed. |
| React Three Fiber | `three@0.186` + `@react-three/fiber@9.8` + `@react-three/drei` | Heavy; already code-split behind dynamic `HeroVisual` | Gated by `use-device-capability` (cores/memory/pointer/saveData/WebGL) | **Keep scoped to hero only** — one lathe-basket scene, `dpr` cap, `frameloop demand/never`, `HeroComposition` fallback. Never in cart/checkout/forms. |

Decision: `motion` stays; no scroll library added; 3D stays hero-only with fallback.

## 3. State, data fetching, forms, validation

| Area | Current | Evaluation | Verdict |
|---|---|---|---|
| Cart state | `zustand@5` persisted `vedant.cart.v1` (`cartKey(product,weight)`) | Correct — lightweight, persisted for guest, `replaceAll/refreshSnapshots` for catalogue drift. No Redux needed. | Keep |
| Server data | Server Components + `lib/catalogue.ts` single nested `PRODUCT_SELECT` | No TanStack Query — correct for SSR-first catalogue (no client cache needed). `catalogue-client.ts:getProductsByIds(ids)` only for "reorder reprices current catalogue" gesture. | Keep; add TanStack Query only if admin analytics needs polling. |
| Forms | `react-hook-form@7.89` + `zod@4.6` + `@hookform/resolvers` | Zod v4 literal migration already noted; schemas mirror DB `CHECK`s (phone 10-15, postal 5-10 vs live 6-digit, label 1-40 vs live Home|Work|Other). Keep, but remap constraints after DB branch choice. | Keep |
| Env | `lib/env.ts` lazy validation, rejects `sb_secret_*` / `role=service_role`, enforces `https://<ref>.supabase.(co|in)` | Correct — publishable key only, no service-role in customer app. | Keep |

## 4. Backend — Supabase

| Area | Finding | Action |
|---|---|---|
| DB | `hyibalj...` 3 migrations (001/002/003), 12 tables + `product_catalog` view, 0 rows in domain tables, `delivery_slots` is `time` not `timestamptz` | Choose **Branch A (DB→contract)** or **Branch B (app→live)** before any UI polish. `docs/SUPABASE_AUDIT.md` §8 + `docs/DATABASE_ARCHITECTURE.md` record the branch. No `DROP production`; forward `supabase/migrations/*.sql` only. |
| RLS | Disabled on all 12 tables, 0 policies — advisor `ERROR ×12` | **P0:** `ENABLE RLS` + least-privilege policies (catalogue public read `is_active`, owned `auth.uid()=user_id`). Revoke `anon/authenticated EXECUTE` on `SECURITY DEFINER` helpers; pin `search_path`. See `docs/SUPABASE_AUDIT.md` §8.1 SQL. |
| Auth | `@supabase/ssr` + `proxy.ts` session refresh + `handle_new_user`/`sync_profile_phone` triggers | Probe Google provider via `lib/auth-providers.ts` capability check; email confirm stays required. Keep. |
| Storage | `storage.buckets:0` | Create `complaint-images` (private, owner-only RLS). Catalogue images via `*.supabase.co/storage/v1/object/public/**` allowlist (already host-derived in `next.config.ts`). |
| RPC | `place_order(p_address_id,p_delivery_date,p_idempotency_key,p_items,p_slot_id) returns jsonb` vs client `p_request_id/p_delivery_slot_id` | Bridge: client builds `p_items:[{product_id,qty}]` from Zustand + `p_delivery_date=today|+1` + `p_idempotency_key uuid` + slot bridge `start_time time → timestamptz`. |
| Realtime/Triggers | `notify_order_event`/`notify_complaint_event` → `notification_outbox` | Keep; wire read path to `notification_outbox` (not `notifications`) or add view. |
| `product_catalog` view | `SECURITY DEFINER` flagged | Switch to `INVOKER` if RLS must gate it, or document why definer is intentional. |

MCP used: `supabase` (`mcp.supabase.com/mcp?project_ref=hyibalj...`) — `list_tables verbose`, `list_migrations`, `information_schema.*`, `pg_policies`, `get_advisors security/performance`, `storage.buckets`. Auth successful.

## 5. PWA & SEO

| Area | Current | Verdict |
|---|---|---|
| Manifest | `src/app/manifest.ts` `standalone`, `start_url:/`, `theme_color:#FBF8F2`, 4 icons incl. maskable, 3 shortcuts | Keep |
| Service worker | `public/sw.js` 153 lines — network-first nav (2.5s→cache→`/offline`), SWR static, **never-cache Supabase/auth**, versioned `CACHE vedant-v1`, `SKIP_WAITING` on demand | Keep — it contains the RLS-off exposure window |
| SEO | `layout.tsx` GroceryStore JSON-LD, per-product Product+BreadcrumbList, `sitemap.ts`/`robots.ts`/`opengraph-image.tsx` | Keep; reseed catalogue-driven sitemap after DB reconciliation |

## 6. Testing & quality

| Tool | Status | Next |
|---|---|---|
| `vitest@5` + `@testing-library/*` + `jsdom` + `@vitejs/plugin-react` | Installed, 0 first-party specs (`find … -name *.test.*` → only `node_modules`) | Priority specs: `money.ts` paise rounding, `pricing.ts:priceFor` parity (`price/discount_price`), `cart-store` reducers, `catalogue-shared` mapper. Then smoke `search→add→cart→checkout` (auth-guarded). |
| `@playwright/test@1.63` | Configured, no specs | Mobile (360/392) + desktop smoke: `home→search→cart→checkout→orders→account→support→offline`, empty states, error states. Screenshots for visual consistency. |
| Gates | `pnpm exec tsc --noEmit ✓`, `pnpm exec eslint . ✓`, `pnpm run build ✓ 22/22` (verified 2026-10-10) | Keep as CI gate (`pnpm build` is source of truth, not `eslint --fix` alone). |

## 7. 3D / imagery

| Area | Current | Verdict |
|---|---|---|
| 3D | Single `ProduceScene` (lathe basket + 6 primitive fruits, `ContactShadows`, `PaintedSignal`), dynamic import in `HeroVisual` with `scenePainted` latch + `SceneBoundary`, gated by `use-device-capability` | Keep scoped; add fallback rendering already proven. No 3D in checkout/cart/forms/search/orders/account. |
| Images | `next/image` `formats avif→webp`, `remotePatterns` allowlist tied to `NEXT_PUBLIC_SUPABASE_URL` storage host (+ dev `images.unsplash.com`), `ProductImage` glyph plinth on `image_url null` | Keep; source real photography before marketing; dev placeholder removed in prod via `NODE_ENV` check. |

## 8. Skills & MCP discovery

| Surface | Status | Note |
|---|---|---|
| Skills installed | `supabase` + `supabase-postgres-best-practices` from `supabase/agent-skills` (`skills-lock.json`) | Available; best-practices skill applies to RLS/storage/migration work. |
| `ui-ux-pro-max-cli` (`npm view` 2.15.0) | `npx ui-ux-pro-max-cli init --ai claude` was **declined** by user | Treat as **design intelligence** — `docs/UI_UX_PRO_MAX_USAGE.md` documents which recommendations were used without blindly copying examples. Do not install globally without approval; `npx` is fine when approved. |
| `ctx7` (`npm view` 0.6.0) | `npx ctx7 setup --claude` / `plugin marketplace` Context7 not yet installed | Install when current library docs needed (Next/React/Supabase/Tailwind/Motion/R3F/Zustand/TanStack/Zod/RHF/PWA). Until then rely on installed skill docs + `supabase search_docs` MCP. |
| MCP servers | `supabase` (http, `hyibalj...`) enabled; no Playwright/Context7/GitHub MCPs in `.mcp.json` | Add Playwright MCP when browser automation needed; GitHub MCP not required for this workspace (no git remote). |

## 9. What not to install (dependency discipline)

*No* to: `lenis`/`gsap` (native scroll + `motion` sufficient), `tanstack-query` (SSR-first), `@tanstack/table`, `framer-motion` (superseded by `motion`), `three` optimizers in `next.config.ts:optimizePackageImports` (intentionally excluded, code-split instead), duplicate PWA workbox (custom `sw.js` is enough), icon fonts.

Every dependency must have a reason. Prefer few, mature, maintained, fast, accessible, well-documented — bundle size and mobile performance over impressiveness.

## 10. References

* `package.json` + `pnpm-lock.yaml` (resolved tree)
* `docs/PROJECT_AUDIT.md` §1, `docs/SUPABASE_AUDIT.md` §5, `docs/DESIGN_SYSTEM.md`, `docs/BACKEND_CONTRACT.md` (2026-10-07 contract)
* Live Supabase advisors (`security` + `performance`) via MCP
* `ui-ux-pro-max-cli` npm 2.15.0, `ctx7` 0.6.0

Only features that actually exist are documented — no invented APIs.
