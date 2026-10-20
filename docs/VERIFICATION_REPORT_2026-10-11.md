# Verification Report — Vedant Fruit & Vegetables

> **Date:** 2026-10-11 · **Method:** fresh inspection, zero trust in prior docs
> **Build gates re-run today:** `pnpm exec tsc --noEmit` ✅ 0 errors · `pnpm exec eslint .` ✅ 0 errors (17 warnings — all inside vendored `.agents/skills/**` code, none in `src/`) · `pnpm run build` ✅ 22/22 routes
> **Live DB probed today:** `hyibaljxicojfoxbihdn` via anon REST (`sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH`)
> **Runtime verified today:** `next dev` + Playwright at 390×844

---

## 1. Headline verdict

**The previous agent's claims are ~90% stale.** Most audit docs (`PROJECT_AUDIT.md`, `SUPABASE_AUDIT.md`, `PRODUCT_GAP_ANALYSIS.md`, `API_CONTRACT.md` §11, `DATABASE_ARCHITECTURE.md` §7, `FINAL_IMPLEMENTATION_REPORT.md` §7) still describe a **broken, empty, RLS-off database**. That is no longer true. The last batch (`CHANGELOG.md` 2026-10-10 entries) really did apply hardening + reconciliation + seed, and today's live probes confirm it.

What the project actually is today:

| Layer | Status (verified today) |
|---|---|
| App code (109 files, 22 routes) | ✅ Complete, typecheck-clean, lint-clean, build-clean |
| Live schema vs app contract | ✅ **Reconciled** — every app-contract table and column exists |
| RLS | ✅ **Enabled** — anon `INSERT` into `products` blocked (HTTP 401), anon sees 0 rows of orders/profiles/addresses/cart/complaints/notifications |
| Catalogue data | ✅ Seeded — 3 categories, 24 products, 22 weights, 10 offers, 18 slots (all 24 products are fruits/vegetables/herbs — matches the F&V-only scope) |
| `place_order` RPC | ✅ Exists with the exact 3-param signature the app calls; unauthenticated call returns the expected `42501 authentication required` |
| Storefront runtime | ✅ Home/PDP/search/categories/offers/cart render real products, live prices, live offers; checkout auth-guards to `/login?next=%2Fcheckout` |
| **Remaining defects** | ⚠️ **4 real bugs + ~10 unfinished items** — see §3 |

So: **nothing "failed to build."** The codebase builds and runs. What's left is a short list of genuine bugs, zero tests, no git history, no deployment, and no owner configuration.

---

## 2. What is implemented and verified working (today)

- **Storefront:** `/` (hero + category rail + rate board + rails + shelf grid), `/categories`, `/categories/[id]`, `/products/[id]` (weight selector, offers, related rail, Product+Breadcrumb JSON-LD), `/offers`, `/search` (live `ilike`, recent searches, refine chips) — all render real seeded data.
- **Cart:** guest Zustand cart verified end-to-end in browser — add from card → toast → `/cart` shows lines, quantity steppers, subtotal ₹30, delivery ₹20, total ₹50, free-delivery progress bar.
- **Checkout guard:** `/checkout` correctly redirects signed-out users to `/login?next=%2Fcheckout`; proxy rate-limits `/login` `/signup` `/auth/callback` `/checkout`.
- **Auth:** email/password + email-confirmation required; Google correctly hidden (live probe: `google=false`, `mailer_autoconfirm=false`, `disable_signup=false`).
- **Security headers:** `proxy.ts` sets nosniff / DENY / strict-origin / Permissions-Policy / COOP / HSTS(prod) and refreshes sessions; `lib/env.ts` rejects service-role keys.
- **PWA:** manifest + `sw.js` (never caches Supabase/auth) + icons.
- **SEO:** GroceryStore/Product/BreadcrumbList JSON-LD, sitemap/robots/opengraph.
- **3D hero:** one capability-gated primitive scene with static fallback.
- **DB hardening:** RLS on 18 tables, least-privilege policies, `security_invoker` view, pinned `search_path`, revoked definer EXECUTE, FK indexes — all confirmed live today.

## 3. Real defects found today (the actual bug list)

### Bug 1 — P0 for orders: `order_items.quantity` does not exist (live column is `qty`)

- `src/services/order-service.ts:142` (`ORDER_DETAIL_SELECT`) selects `quantity`.
- `src/types/database.ts:156` types `OrderItemRow.quantity`.
- `src/components/orders/reorder-button.tsx:85` reads `item.quantity`.
- Live DB: `order_items.qty` ✅ exists, `order_items.quantity` ❌ **42703 column does not exist** (verified by direct PostgREST probe).
- **Impact:** `/orders/[id]` order-detail query 400s for every real order; reorder adds `undefined` quantities. Latent today only because 0 orders exist. Any first real customer order breaks this page.
- **Fix:** rename to `qty` in the select + type + reorder-button (and check the order-detail page's display code), or add a DB view. Prefer app-side rename — the RPC writes `qty`.

### Bug 2 — P1: 8 of 24 product images are dead URLs (404 from Unsplash)

Verified by HEAD request on every `products.image_url`: **16 OK, 8 dead** (Beetroot, Carrot, Capsicum, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato). `ProductImage` (`src/components/products/product-image.tsx:68-76`) has **no `onError` fallback** — a dead URL renders a broken-image box instead of falling back to the designed glyph plinth. This is exactly the failure mode `IMAGE_STRATEGY.md` §5 forbids.
- **Fix:** (a) replace the 8 URLs, and (b) add an error state to `ProductImage` that swaps to the glyph plinth, so a bad URL can never show a broken image again.

### Bug 3 — P1: storage has **0 buckets** (docs claim otherwise)

`storage/v1/bucket` returns an empty list; `product-images` public object probe → HTTP 400. `CHANGELOG.md` and `TASK_STATE.md` claim `product-images` + `complaint-images` buckets were created — **they were not** (or were deleted). Consequence: complaint attachments (`complaint_attachments.storage_path`) cannot be written, and the intended Supabase-hosted catalogue photography pipeline has no home.

### Bug 4 — P2: notifications read path may never receive rows

`src/lib/notifications.ts:21` and `notification-actions.tsx:35` read table `notifications`. Live triggers (`notify_order_event`/`notify_complaint_event`) populate **`notification_outbox`**. Both tables exist, but nothing verified writes to `notifications`. `/notifications` will show an honest empty state forever even after real orders. Decide: point the read path at `notification_outbox` (filter `audience='customer' and user_id=auth.uid()`), or create a view/backfill trigger.

## 4. Unfinished / not started (no code exists)

| # | Item | Evidence |
|---|---|---|
| 1 | **Tests** — 0 specs, 0 config files | `vitest`/`@playwright/test` installed; `glob **/*.{test,spec}.*` → no files; no `vitest.config.*`/`playwright.config.*` |
| 2 | **Git repository** | `git status` → "not a git repository". 109 files, no history, no remote, no branch safety |
| 3 | **Local migrations** | `supabase/` directory does not exist; all DB changes were applied live via MCP with no SQL checked in. No drift detection possible |
| 4 | **Slot recurrence** | 18 slots cover only 2026-10-11 → 2026-10-16. Checkout blocks on 2026-10-17 unless slots are regenerated |
| 5 | **Store contact** | `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` unset → Call/WhatsApp buttons hidden (`needsOwnerConfiguration: true`) |
| 6 | **Admin roster** | `profiles` = 0 rows; no admin. Orders can never leave `ORDER_RECEIVED` |
| 7 | **Google OAuth** | Disabled in dashboard; button hidden by capability probe (correct behaviour, owner decision) |
| 8 | **Account deletion** | Request-mode only by design (no service-role in customer app) |
| 9 | **Analytics** | `events` table exists + RLS insert policy claimed; no client code writes events |
| 10 | **Deployment** | Not deployed; no CI; no domain; no QR |
| 11 | **Lighthouse/CWV** | Never measured |
| 12 | **A11y audit** (axe + keyboard sweep) | Not run |
| 13 | **`graphify` package** | `graphify@1.0.0` in `package.json` is an abandoned random-graph-generator with **syntactically invalid JS** (fails to parse: `SyntaxError: Unexpected string`). Not imported anywhere in `src/`. Should be removed |

## 5. On "Graphify and the index file"

- **Graphify:** ran `node -e "require('graphify')"` → `SyntaxError: Unexpected string`. The package is a 36-line joke/broken module (`document.createElementByID`, `Grapify.CreateLines`, `document.push`). It has no CLI, no bin, no relation to this project, and zero imports in `src/`. It is dead weight in `dependencies` — remove it.
- **Index file:** the project's index is `docs/PROJECT_INDEX.md`, last updated **2026-10-07** — it still describes the *old* Supabase project (`bmdmgycsrnffcgxmukxm`, "15 tables, 45 policies") and says "Catalogue 0 products live". Stale. It has been refreshed in the same batch as this report to match verified reality.

## 6. Doc drift warning

Six docs (`PROJECT_AUDIT`, `SUPABASE_AUDIT`, `PRODUCT_GAP_ANALYSIS`, `API_CONTRACT` §11, `DATABASE_ARCHITECTURE` §7, `FINAL_IMPLEMENTATION_REPORT` §7) still assert "RLS OFF, 0 products, checkout double-broken, missing 6 tables". That was true **before** the 2026-10-10 reconciliation batch and is false today. Only `CHANGELOG.md`, `TASK_STATE.md` (DONE section) and this report match reality. Do not plan work from the old audit docs.

## 7. What must not regress

- `money.ts` integer-paise math and `pricing.ts` `pickActiveOffer`/`priceFor` parity with `place_order`.
- `sw.js` never-cache-Supabase rule.
- `lib/env.ts` service-role rejection.
- Cart `cartKey(product,weight)` + read-then-write `23505` tolerance.
- Single nested `PRODUCT_SELECT` (no N+1).
- Warm `@theme` tokens; no hex in components.
- 3D scoped to hero only.
- Proxy-as-session-refresh-not-auth boundary.
