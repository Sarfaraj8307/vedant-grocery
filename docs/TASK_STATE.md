# Task State

> Single source of truth. Never mark DONE without implementation + verification.

## DONE — implemented and verified

- **Config & PWA foundation** — `.env.example`, `.env.local` (publishable key only, `lib/env.ts` rejects `service_role`), `next.config.ts` (image allowlist `*.supabase.co` + dev `images.unsplash.com`, `minimumCacheTTL` 30d, SW/manifest headers, `optimizePackageImports [lucide-react,motion]`), `components.json`, PWA `manifest.ts` + `public/sw.js` (network-first nav 2.5s→cache→`/offline`; never caches Supabase/auth) + `public/icons/` + `scripts/generate-icons.mjs` — `tsc --noEmit ✓`, `eslint ✓`, `pnpm build 22/22 ✓` (2026-10-10, 7 workers).
- **Libraries** — `lib/utils`/`money`/`pricing`/`format`/`env`/`validation`/`catalogue(-shared)`/`auth`/`addresses`/`notifications`/`auth-providers`/`browse`/`cart-line` + `supabase/{client,server}` + `src/proxy.ts` (session refresh + `nosniff/DENY/strict-origin-when-cross-origin/Permissions-Policy/COOP/HSTS`) — typecheck PASS.
- **UI primitives** — `ui/button` (6 variants, 4 sizes, loading-width-keep), `input`, `badge`, `skeleton`, `misc` (`SectionHeading`/`EmptyState`), `sheet`, `menu` (Radix), `error-state`. Lint 0E/0W.
- **Storefront** — `ProductImage` (glyph plinth ↔ `next/image`), `PriceTag`/`SavingLabel`, `QtyStepper` (morph ADD↔stepper, reduced-motion fallback, `min-w-[92px]`), `ProductCard` (server), `ProductRailCard`, `ProductGrid`/`ProductRail`, `ProductPurchase`. Image abstraction proven (NULL → glyph).
- **3D** — `use-device-capability` (cores/memory/pointer/saveData/WebGL/IntersectionObserver + `useDocumentVisible`), `ProduceScene` (lathe basket + torus rim + 6 primitive fruits, `ContactShadows`, `PaintedSignal`), `HeroComposition` (baseline SVG `background` vs `subject hideSubject` fade), `HeroVisual` (mounted-latch + `scenePainted` latch + `SceneBoundary onFailure`) — canvas 318×278, subject opacity 0.
- **Routes (22, build-clean 2026-10-10)** — `/`, `/categories`, `/categories/[id]`, `/search`, `/products/[id]`, `/offers`, `/cart`, `/checkout`, `/orders`, `/orders/[id]`, `/account`, `/account/addresses`, `/account/reset-password`, `/auth/callback`, `/login`, `/signup`, `/forgot-password`, `/notifications`, `/support`, `/support/[id]`, `/contact`, `/about`, `/privacy`, `/terms`, `/offline`, `not-found.tsx`/`error.tsx`/`global-error.tsx`/`loading.tsx`/`manifest`/`robots`/`sitemap`/`opengraph-image`. Proxy `ƒ Proxy (Middleware)`.
- **Phase-5/6/7/8 wiring** — cart store (persisted `localStorage vedant.cart.v1` + `cartKey(product,weight)`) + `CartProvider` (optimistic + rollback, `max` merge on sign-in, read-then-write tolerates `23505`) + `useCart` + `cart-service`, `auth-service` (signup/signin/Google gate via `auth-providers` probe, `ensureProfile`, `clearPersonalData mode=request`), `order-service` (idempotent `placeOrder(newRequestId())`, `reorder` reprices current catalogue), `address-service` (default clear-then-set for `one_default_per_user WHERE is_default`), `checkout-client` (stable `requestIdRef`, server-authoritative validation), `slot-picker`, `address-book`, `complaint-form`, `notification-actions`. `pricing.ts` integer-paise parity (`least(price, discount)`, `pickActiveOffer is_active && starts_at/ends_at → created_at desc limit 1`).
- **Responsiveness fixes** — hero `aspect-[480/420]` + `w-full` grid item (0×0 desktop fixed); `HeroComposition` subject vs background split + `PaintedSignal` first-frame handoff + latched mount (stops WebGL churn); `ProductCard`/`ProductRailCard` price row `flex items-end justify-between; PriceTag flex-wrap; stepper shrink-0` (11px ADD clip at 360px fixed); `SectionHeading flex-wrap` (orphan fixed); `SortControl items-start leading-8` (floated caption fixed); `QtyStepper min-w-[92px]`. Verified at 360/392.
- **Full audit 2026-10-10** — `docs/PROJECT_AUDIT.md` (35kb, 112 files classified IMPLEMENTED/WORKING/BUGGY/INCOMPLETE/MOCKED per inspected artifact, live `hyibaljxicojfoxbihdn` diverged from app contract: `product_weights/offers/cart_items/order_status_history/complaint_messages/notifications` missing, `PRODUCT_SELECT` columns absent, `place_order` param mismatch).
- **Supabase live audit 2026-10-10** — `docs/SUPABASE_AUDIT.md` (27kb, `list_tables verbose` 12 tables + `product_catalog SECURITY DEFINER` view, `list_migrations 001/002/003`, `information_schema.columns/routines/triggers`, `pg_policies 0`, `storage.buckets 0`, advisors: `rls_disabled_in_public ERROR ×12 (P0 world-readable)`, `security_definer_view`, `function_search_path_mutable`, `anon/authenticated SECURITY DEFINER` ×4, `unindexed FK` `complaints.order_id` + `notification_outbox.user_id`, `unused_index ×14`; remediation SQL `ENABLE RLS` + least-privilege policies + revoke + pin `search_path` included).
- **Gap analyses 2026-10-10** — `docs/PRODUCT_GAP_ANALYSIS.md` (feature map 1–21, failure chain: `PRODUCT_SELECT` empty → `getDeliverySlots starts_at` 0 slots → `placeOrder p_request_id` 400, preservation list, P0/P1/P2 gaps) + `docs/MARKET_GAP_ANALYSIS.md` (quick-commerce patterns Blinkit/Zepto/Instamart/BigBasket/JioMart, what not to copy).
- **Research & contracts 2026-10-10** — `docs/TECHNOLOGY_RESEARCH.md` (Tailwind v4 `@theme`, Radix, `motion` vs Lenis/GSAP, Zustand, Zod/RHF, `hyibalj...` reconciliation Branch A vs B, PWA/SEO, `vitest/playwright` 0 specs, `ui-ux-pro-max-cli 2.15.0` + `ctx7 0.6.0` evaluated, `supabase` MCP used), `docs/UI_UX_PRO_MAX_USAGE.md` (skill as design intelligence, `npx init` declined by user, audited globals/tokens/nav/product/cart/checkout/search/orders/forms/motion/mobile, forbidden patterns), `docs/DATABASE_ARCHITECTURE.md` (target entity map, keys/indexes/checks, transactions, RLS target, storage, Branch A/B), `docs/API_CONTRACT.md` (PostgREST + RPC `place_order` live vs app contract, `PRODUCT_SELECT`, validation, pricing, checkout pipeline), `docs/FUTURE_ADMIN_APP_CONTRACT.md` (Android admin contract: `private.admin_users+is_admin()`, RPCs `admin_update_order_status`/`admin_reject_order_item`, storage, analytics, setup checklist).
- **Quality gates 2026-10-10** — `pnpm exec tsc --noEmit` ✓ 0 errors, `pnpm exec eslint .` ✓ 0E/0W, `pnpm run build` ✓ 22/22 static pages (7 workers, compiled 17.7s + 8.5s TS, 2.0s generate), `pnpm exec vitest run` → no test files (expected). Dev server `next dev` PID 1757 `✓ Ready in 1937ms` on `http://localhost:3000`; `.next/dev/logs` historic: `GET / 200 (2.4s)`, `/offers 200`, `/categories 200`, `/search?q=Banana 200`, `/products/[id] 200`, `/cart 200`, `/checkout 200→/login?next=%2Fcheckout` (auth guard), `/orders 200→/login`, `/account 200→/login`. Headless `curl/Invoke-WebRequest` timed out (Windows firewall/Turbopack), but in-app fetch verified 200s.
- **Mobile QA sweep** — `/offers` 200 "Today's rate board", `/cart` 200 "empty", `/search?q=xyznonexistent12345` 200 "Nothing matched", `/checkout` 200→login. All empty states render correctly (catalogue empty is correct fallback for live diverged DB).
- **Live-DB hardening 2026-10-10** — Applied `harden_rls_and_security` on `hyibaljxicojfoxbihdn`: `ENABLE ROW LEVEL SECURITY` on all 12 tables (now 18), created 19 least-privilege policies (catalogue public `is_active`, owned `auth.uid()=user_id`, `order_items` via parent, `events` insert-only), recreated `product_catalog` as `security_invoker=true`, pinned `search_path` on `set_updated_at`/`gen_order_number`, revoked `anon/authenticated EXECUTE` on `handle_new_user`/`notify_*`/`sync_profile_phone`, added `complaints_order_id_idx` + `notification_outbox_user_idx`. Verified `rowsecurity=true ×18`, `pg_policies 19`.
- **Live-DB reconciliation 2026-10-10** — Applied `reconcile_schema_to_app_contract` (additive, no DROP): added `categories.glyph/accent/image_url`, `products.glyph/accent/base_price/unit_label/availability/quantity_limit`, `delivery_slots.starts_at/ends_at/capacity`, `profiles.username/first_name/last_name/email`, `addresses.full_name/phone/house_number/street/landmark/state/postal_code/lat/lng`, `orders.request_id/address_id/delivery_slot_id/original_total/delivery_charge/adjustment_*/final_total`, `order_items.weight_label/rejected/rejection_reason`, plus new tables `product_weights` (partial unique on grams), `offers` (time-windowed), `cart_items` (partial uniques on weight_id), `order_status_history`, `complaint_messages`, `notifications`; recreated `product_catalog` invoker view; created `place_order_app(text,uuid,uuid)` + `place_order(text,uuid,uuid)` wrapper (bridges `p_request_id` → `p_idempotency_key/p_items/p_delivery_date/p_slot_id`, maps errors to `42501/22023` substrings, clears `cart_items` on success); created `product-images` (public) + `complaint-images` (private) buckets.
- **Live catalogue seed 2026-10-10** — Seeded `hyibalj`: 8 categories (glyph/accent fixed), 16 products (Tomato, Onion, Potato, Cauliflower, Green Chilli, Coriander, Banana, Apple, Milk, Curd, Atta, Basmati, Biscuit, Mango Juice, Sunflower Oil, Dishwash), 10 product_weights, 7 offers (live window now→+7d), 18 delivery_slots (Morning/Afternoon/Evening for next 7 days in `Asia/Kolkata`, deduped). Verified anon `supabase-js` reads: `categories 8`, `products 16`, nested `PRODUCT_SELECT` with weights/offers OK, `delivery_slots` future 3/day, `store_settings` 1, `orders` RLS filtered 0 for anon.
- **Env fix 2026-10-10** — `.env.local` was pointing at `bmdmgycsrnffcgxmukxm` (old project with `is_active` missing), corrected to `hyibaljxicojfoxbihdn` + `sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH`. Rebuilt: `pnpm run build ✓ 22/22` (8.1s compile + 14.9s TS + 4.6s generate). Verified `supabase-js` anon reads against `hyibalj` all OK.
- **Auto-approve enabled 2026-10-10** — `.claude/settings.local.json` expanded to `Bash(*), Read(*), Write(*), Edit(*), Glob(*), Grep(*), mcp__supabase__*, WebFetch(*), WebSearch(*)` per user request to keep building without permission prompts.
- **Final verification & fresh audit 2026-10-11** — re-ran all gates fresh with zero trust in prior docs: `pnpm exec tsc --noEmit` ✓ 0 errors, `pnpm exec eslint .` ✓ 0 errors (17 warnings all vendored in `.agents/skills/**`), `pnpm run build` ✓ 22/22. Live-probed `hyibaljxicojfoxbihdn`: schema reconciled (every app-contract table/column exists), RLS on ×18 tables + 19 policies (anon INSERT blocked 401; anon sees 0 rows of private tables), seeded (3 categories, 24 products — all F&V/herbs, 22 weights, 10 time-windowed offers, 18 slots), `place_order` RPC matches app signature, `product_catalog` invoker view. Runtime-verified with `next dev` + Playwright: home/PDP/search/offers/cart render live data, guest cart E2E (add → toast → ₹30/₹20/₹50 + free-delivery bar), checkout auth-guards to `/login?next=%2Fcheckout`. Found **4 real bugs** (see below). Deliverables written: `docs/VERIFICATION_REPORT_2026-10-11.md`, `docs/ACTION_PLAN_2026-10-11.md`; `docs/PROJECT_INDEX.md` refreshed. Graphify investigated: abandoned package with invalid JS, never imported → to be removed.

## IN PROGRESS

- **None.** All planned verification work is done. Remaining items are tracked as **BLOCKED** (owner) and **NOT STARTED** (dev) below; sequencing in `docs/ACTION_PLAN_2026-10-11.md`.

## VERIFIED DEFECTS — found 2026-10-11, not yet fixed (see `docs/VERIFICATION_REPORT_2026-10-11.md` §3)

- **Bug 1 P0 — `order_items.qty` vs `quantity`** — `order-service.ts:142` `ORDER_DETAIL_SELECT` + `database.ts:156` + `reorder-button.tsx:85` use `quantity`; live column is `qty` (verified 42703). `/orders/[id]` 400s on any real order; reorder reads undefined. Fix app-side (RPC writes `qty`).
- **Bug 2 P1 — 8 of 24 product images are dead URLs** — Beetroot, Carrot, Capsicum, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato 404 from Unsplash. `ProductImage` (`product-image.tsx:68-76`) has no `onError` fallback.
- **Bug 3 P1 — 0 storage buckets live** — `storage/v1/bucket` empty though `CHANGELOG.md`/`TASK_STATE` claim `product-images` + `complaint-images` created. Complaints + catalogue photography broken.
- **Bug 4 P2 — notifications read path** — `notifications.ts:21` + `notification-actions.tsx:35` read `notifications`; live triggers populate `notification_outbox`. `/notifications` will stay empty forever.

## BLOCKED — cannot verify without owner action

- **`private.admin_users` / `profiles.role` admin roster empty** — orders stay `ORDER_RECEIVED` (nobody can confirm/cancel). Grant via `INSERT INTO private.admin_users` or `UPDATE profiles SET role='admin'` via Edge Function.
- **Store contact** (`NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS`) unset — Call/WhatsApp render empty; `/contact` notes the gap.
- **Storage buckets missing** — create `product-images` (public) + `complaint-images` (private) to unblock Bug 3.
- **Google OAuth disabled** — button intentionally hidden (capability probe `lib/auth-providers.ts`).
- **Account deletion** — no service-role in customer app by design; request flow only (`clearPersonalData mode=request`).
- **Catalogue imagery** — 8 products have dead `image_url` (Bug 2); others may use glyph fallback until photography exists.

## NOT STARTED (owner/development — not blocking full-stack readiness)

- **Bug fixes (see VERIFIED DEFECTS above)** — Phase 1 of `docs/ACTION_PLAN_2026-10-11.md`: `qty` rename (P0) → image fallback + URLs (P1) → buckets (P1) → notifications (P2).
- **Unit/E2E tests** — `vitest@5`/`@playwright/test@1.63` installed, 0 first-party specs. Priority: `pricing.ts:priceFor` parity vs `least(price,discount_price)`, `money.ts` paise rounding, `cart-store` reducers, `catalogue-shared` mapper; then smoke `search→product→cart→checkout` (auth-guarded) E2E.
- **Slot recurrence** — 18 slots cover 2026-10-11 → 2026-10-16; checkout blocks on 2026-10-17. Apply recurrence migration (`FUTURE_ADMIN_APP.md`/`BACKEND_CONTRACT.md`).
- **Remove `graphify`** — `graphify@1.0.0` is an abandoned package with syntactically invalid JS (`SyntaxError: Unexpected string`), never imported in `src/`. `pnpm remove graphify`.
- **Git + migrations** — no git repo yet; `supabase/` dir absent (all DB changes applied live via MCP, nothing checked in). `git init` + first commit; capture schema as migration `001_...`.
- **Performance pass** — Lighthouse/CWV + bundle inspection (manual; post-bug-fix).
- **Deploy** — target host + domain + CI + QR asset TBD (Phase 3 of action plan).


## T06 — Customer web gaps (2026-10-16, software-engineer)

Deliberate **non-rewrite**: the storefront already exists and is good. T06 adds only the three genuine gaps. **No database change was made** (the DB work ended at T05).

### A. Buy again (home)
- `src/lib/buy-again.ts` — pure `pickRecentPurchases(orders, limit)` (newest-first, de-dupe per product, skip rejected / deleted) + shared types.
- `src/lib/buy-again.server.ts` — `getBuyAgainItems(limit)`: reads the customer's own `orders → order_items` (RLS-scoped, no explicit user filter), resolves each against **today's** catalogue, drops items unavailable today, matches the remembered pack with `matchWeightByLabel`. Never throws (returns empty on failure) so the home page cannot 500.
- `src/components/home/buy-again.tsx` — client island: per-item `QtyStepper` + **Add all** (adds each line's last qty through `add(…, {silent:true})`, so it merges + clamps to `quantity_limit` exactly like a manual add). Renders `null` with no history.
- Reuses the existing reorder machinery: `reorder-button` and Buy again now share `matchWeightByLabel` (new, in `lib/cart-line.ts`) instead of duplicating the label rule.

### B. Delivery location + Pune service area + serviceability
- `src/config/service-area.ts` — Pune/Maharashtra (env-overridable `NEXT_PUBLIC_SERVICE_AREA_*`), pincode prefixes default `["411"]`, city aliases.
- `src/lib/serviceability.ts` — pure `checkServiceability({city,state,postalCode})` → serviceable / MISSING_CITY / OUT_OF_AREA.
- `src/app/checkout/actions.ts` — **Server Actions** `verifyAddressServiceability` + `submitOrder`: both re-read the address from the DB with the customer's session, so serviceability is **server-authoritative** (a tampered client cannot fake a Pune pincode). `submitOrder` gates the `place_order` RPC behind the check.
- `src/components/checkout/checkout-client.tsx` — checks on selection, shows the verdict, disables **Place order** until the server says serviceable.
- `src/components/home/delivery-location.tsx` — home bar with three honest states; `src/components/layout/site-header.tsx` — new mobile delivery row (part of the sticky header); `--header-h` mobile 56px → 93px.
- `src/lib/addresses.ts` — `getDeliveryLocation()`; the layout now uses it.

### C. Address creation — verdict: **not reproducible; already correct**
- Static: `createAddress` sends the legacy NOT NULL columns (`line1`/`line2`/`pincode`) **and** the new ones; `sync_address_pincode()` backfills them; `user_id DEFAULT auth.uid()`; RLS `addresses_owner_all` allows own-row insert.
- Empirical: all 19 `addresses` columns confirmed live via PostgREST column probes; the exact `createAddress` payload POSTed as `anon` returns **42501 (RLS)** — it passes every column / NOT NULL / CHECK check and is refused only for being unauthenticated (a schema/constraint bug would return PGRST204 / 23502 / 23514).
- The brief's "creation bug" was the pre-rewrite legacy-column failure, already remediated in `services/address-service.ts`.
- Delivered instead: `AddressForm` pre-fills **City = Pune, State = Maharashtra** for new addresses (both editable), with a service-area hint under City.

### Verification
- `tsc --noEmit` clean; `eslint .` **0 errors** (17 pre-existing warnings in `.agents/skills/**`); `pnpm build` 22/22.
- `vitest` (new `vitest.config.mts`, `pnpm test`): **18 tests pass** — `serviceability` (12) + `buy-again` (6).
- Playwright `tests/home-gaps.spec.ts`: **5 passed / 1 skipped** across mobile + desktop; 360px has no horizontal overflow; screenshots read (header row, delivery card at 360 + desktop).
- **Not verified end-to-end (blocked):** signed-in states (populated Buy again rail, serviceability verdicts, address pre-fill in the sheet) need an email-confirmed test account — creating one is an auth-data change, left for approval.
- Lint debt unblocked (not caused by T06): `eslint.config.mjs` now ignores `**/build/**` (Gradle output) + `.tmp/**`; `tests/fixtures.ts` has a scoped `react-hooks` disable for Playwright's `use` fixture.

## T13 — close the direct-INSERT surface on `orders` / `order_items` (2026-10-16, software-engineer)

QA surfaced a defect the three Advisor exports never flagged: an `authenticated` customer could **direct-INSERT a fabricated order through PostgREST**, bypassing `place_order` and setting an arbitrary `total` (probe used `total = 0.01`). This violates master brief §10 ("the client cannot manipulate price/total").

**Finding (reproduced, rollback-scoped).** Two permissive `INSERT` policies:
- `public.orders :: orders_insert_own` — `with_check (select auth.uid()) = user_id`
- `public.order_items :: order_items_insert` — `with_check EXISTS(order owned by caller)`

`src/services/order-service.ts:14` already *claimed* no such policy existed for customers — the DB had drifted from the contract.

**Fix.** `supabase/migrations/20261016_006_close_order_insert.sql` — drops both policies. Order placement keeps exactly one write path (`place_order_app → place_order`, `SECURITY DEFINER` owned by `postgres`; tables are `postgres`-owned with `relforcerowsecurity = false`, so the DEFINER body bypasses RLS on its own INSERTs).

**Proof (empirical, every batch ends in `ROLLBACK`).**

| Condition | Direct `INSERT orders` | `place_order_app` RPC |
|---|---|---|
| policies **PRESENT** | **SUCCEEDED** (`total=0.01`) — reproduced | n/a |
| policies **DROPPED** (in-txn) | **BLOCKED** `42501` | **SUCCEEDED** (order_id returned) |

**Verification after applying (persistent).** `pg_policies` → both `0`; authenticated direct `INSERT orders` → `42501`; authenticated direct `INSERT order_items` **on the caller's own order** → `42501`; `place_order_app` legit checkout → **SUCCEEDED**; post-rollback probe rows `0 / 0`. Migration idempotent (applied twice, both HTTP 201).

**Blast radius (grep-verified):** every `orders` / `order_items` reference in `src/` (web) and `admin-android/` (Kotlin) is a `SELECT`; no client writes to either table. `order_status_history` already had no customer `INSERT` policy.

**Migration ledger.** `supabase_migrations.schema_migrations` was missing all six manual `20261016_00{1..6}` migrations; recorded idempotently (`version = 20261016_00X`, `name` = descriptive, `statements` = file body, `created_by = 'platform-remediation (management-api)'`). Details in `docs/SUPABASE_FINDING_REGISTER.md` §T13.

## T14 — DB serviceability + close the `order_items` UPDATE/DELETE surface (2026-10-17, software-engineer)

Two client-bypassable ceilings on the order path, both QA-found and both fixed in `supabase/migrations/20261016_007_serviceability_and_close_item_mutation.sql`.

**Finding 1 — serviceability was app-layer only.** The "do we deliver here?" rule existed only in `src/lib/serviceability.ts` + the checkout Server Action, so a hand-crafted `place_order_app` RPC call placed an order for an out-of-area address.

**Finding 2 — customers could rewrite their own order's lines.** `order_items_update` / `order_items_delete` both allowed `EXISTS (own order)`, so a customer could `UPDATE order_items SET unit_price = 0.01` (2 rows) or `DELETE` (2 rows). `line_total` is GENERATED; no trigger recomputes `orders` totals from `order_items`, so the bill could not be lowered — but history was falsifiable, violating brief §17.

**Fix.**
1. New `public.service_areas (id, city, state, pincode_prefixes text[], is_active, created_at, updated_at)`, seeded **Pune / Maharashtra / `{411}`**, RLS + a `to authenticated` SELECT policy, unique index, `set_updated_at` trigger. Answers brief §35 — a new city is one INSERT.
2. New `private.address_is_serviceable(city, pincode)` — a faithful port of the client rule; EXECUTE revoked from client roles.
3. `place_order_app` now raises `22023 'delivery area not served'` before any order work. The 3-arg `place_order` wrapper inherits it; the 5-arg engine is unreachable by `authenticated` (`has_function_privilege` = false).
4. Dropped `order_items_update` and `order_items_delete`; `admin_reject_order_item` / `admin_restore_order_item` keep working (SECURITY DEFINER, `postgres`-owned, `relforcerowsecurity = false`).

**Proof (empirical, every batch ends in `ROLLBACK`).** *Before:* out-of-area `place_order_app` **PLACED**; customer `UPDATE order_items` **2 rows**; customer `DELETE` **2 rows**. *After (DDL applied in-txn, then rolled back):* out-of-area → **22023 `delivery area not served`**; in-area → **PLACED**; customer `UPDATE` → **0 rows** (`unit_price` still `40.00`); customer `DELETE` → **0 rows** (2 items left); `admin_reject_order_item` → `rejected=true`; `admin_restore_order_item` → `rejected=false`.

**Verification after applying (persistent).** The same six probes re-run live, all in `ROLLBACK`, gave identical results. `order_items` policies = `order_items_select` only; `place_order_app`'s live definition contains the guard and the helper call; the helper is not executable by `authenticated`; the trigger and unique index exist; a signed-in customer reads `Pune, Maharashtra`, `anon` reads 0 rows. No test rows persisted.

**Blast radius.** Full `pg_policies` before/after differ by exactly two removals (`order_items.order_items_delete`, `order_items.order_items_update`) and one addition (`service_areas.service_areas_select`). No existing ACL changed — the new table's grants come from the pre-existing default ACL on `public`. An RLS-blocked `UPDATE`/`DELETE` affects 0 rows (default-deny) rather than raising `42501`, so the proof asserts on row count plus a value read-back.

**Migration ledger.** `20261016_007` recorded in `supabase_migrations.schema_migrations` (idempotent, `on conflict (version) do nothing`), `created_by = 'platform-remediation (management-api)'`. Verified by grep that no later migration (008–012) defines `place_order_app`, so nothing was clobbered.

Details in `docs/SUPABASE_FINDING_REGISTER.md` §T14.
