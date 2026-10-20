# Action Plan — Next Steps (2026-10-11)

> **Basis:** `docs/VERIFICATION_REPORT_2026-10-11.md` — fresh inspection today, zero trust in prior docs.
> **Verdict first:** nothing failed to build. App builds and runs (tsc ✅, eslint ✅, build 22/22 ✅), live DB is reconciled + seeded (24 products, 3 categories, 22 weights, 10 offers, RLS on). What remains is a short bug list + unfinished owner/dev items.
> **Critical doc-drift caveat:** six old docs (`PROJECT_AUDIT`, `SUPABASE_AUDIT`, `PRODUCT_GAP_ANALYSIS`, `API_CONTRACT` §11, `DATABASE_ARCHITECTURE` §7, `FINAL_IMPLEMENTATION_REPORT` §7) describe a broken/empty RLS-off DB — false since 2026-10-10. Do not plan from them.

---

## Phase 0 — Preconditions that unblock everything else (owner, ~15 min)

Do these first; three of the four bugs and most launch items depend on them.

| # | Action | Why | Owner gesture |
|---|---|---|---|
| 0.1 | **Grant first admin** — `INSERT INTO private.admin_users` or `UPDATE profiles SET role='admin'` | Orders can never leave `ORDER_RECEIVED`; nobody can confirm/cancel | SQL in Supabase SQL Editor |
| 0.2 | **Set store identity** — `NEXT_PUBLIC_STORE_PHONE`, `NEXT_PUBLIC_STORE_WHATSAPP`, `NEXT_PUBLIC_STORE_ADDRESS` in `.env.local` | Call/WhatsApp buttons + `/contact` currently render empty | `.env.local` + `pnpm dev` restart |
| 0.3 | **Create the two storage buckets** — `product-images` (public) and `complaint-images` (private) | Both are claimed in docs but **0 buckets exist live** → complaint attachments and catalogue photography have no home (Bug 3) | SQL/Storage dashboard |

---

## Phase 1 — Fix the 4 verified bugs (dev, ~0.5–1 day)

Sequenced by severity. Each fix gate: `pnpm exec tsc --noEmit` + `pnpm exec eslint .` + targeted smoke.

### Bug 1 · P0 — `order_items.quantity` does not exist (live column is `qty`)
**Files:** `src/services/order-service.ts:142` (`ORDER_DETAIL_SELECT`), `src/types/database.ts:156`, `src/components/orders/reorder-button.tsx:85`, plus any order-detail display reading it.
**Impact:** `/orders/[id]` 400s on every real order; reorder adds `undefined` quantities. Latent only because 0 orders exist — the first real order breaks.
**Fix (app-side, do NOT change the RPC which writes `qty`):**
1. Rename `quantity` → `qty` in `ORDER_DETAIL_SELECT`.
2. Update `OrderItemRow.quantity` → `OrderItemRow.qty` in `database.ts`.
3. Update `reorder-button.tsx` and the order-detail page display to read `qty`.
4. Verify with a real order: place one as a signed-in test customer, open `/orders/[id]`, confirm timeline + line items render.

### Bug 2 · P1 — 8 of 24 product images are dead Unsplash URLs
**Files:** seeded `products.image_url` rows; `src/components/products/product-image.tsx:68-76` (no `onError`).
**Dead:** Beetroot, Carrot, Capsicum, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato.
**Fix:**
1. Add `onError` fallback in `ProductImage` that swaps to the existing glyph plinth (this is what `IMAGE_STRATEGY.md` §5 requires — a bad URL must never show a broken-image box).
2. Replace the 8 dead URLs with live photography (from the cataloguer or Supabase storage once 0.3 exists).

### Bug 3 · P1 — storage has 0 buckets
**Impact:** complaint attachments + catalogue photography pipeline broken.
**Fix:** phase 0.3 (create buckets), then add owner-only RLS on `complaint-images` objects and verify `storage/v1/bucket` lists both.

### Bug 4 · P2 — notifications read path may never receive rows
**Files:** `src/lib/notifications.ts:21`, `src/components/notifications/notification-actions.tsx:35` read table `notifications`; live triggers write `notification_outbox`.
**Fix — pick one:**
- **A (recommended):** point the read path at `notification_outbox`, filter `audience='customer' AND user_id=auth.uid()`.
- **B:** create a view or backfill trigger that moves `notification_outbox` → `notifications`.
Verify by placing an order as a signed-in customer and confirming `/notifications` shows the event.

---

## Phase 2 — Repay the unfinished items (dev, ~1–2 days)

| # | Item | Concrete step |
|---|---|---|
| 2.1 | **Remove `graphify`** | `graphify@1.0.0` is an abandoned package with **syntactically invalid JS**, never imported in `src/`. `pnpm remove graphify` (it is dead weight in dependencies). |
| 2.2 | **Unit tests** | Vitest + Playwright are installed, 0 specs. Priority order from TASK_STATE: `pricing.ts:priceFor` parity vs `least(price,discount_price)`, `money.ts` paise rounding, `cart-store` reducers, `catalogue-shared` mapper; then one Playwright smoke `search → product → cart → checkout(auth-guarded)`. |
| 2.3 | **Slot recurrence** | 18 slots cover only 2026-10-11 → 2026-10-16. Checkout blocks on 2026-10-17. Apply the recurrence migration proposed in `BACKEND_CONTRACT.md`/`FUTURE_ADMIN_APP.md` (generate next N days of Morning/Afternoon/Evening slots in `Asia/Kolkata`). |
| 2.4 | **Git repository** | `git init`, add current tree, first commit, choose host (`docs/VERIFICATION_REPORT` §4–10). 109 files with zero history is unsafe. |
| 2.5 | **Local migrations** | `supabase/` doesn't exist; all DB changes were applied live via MCP with no SQL checked in. Capture current schema as migration `001_...` and adopt migration-based drift detection. |

---

## Phase 3 — Launch readiness (owner + dev, ~1–2 days)

| # | Item | Details |
|---|---|---|
| 3.1 | **Deploy** | Target host + domain, CI (GitHub Actions: `tsc` → `eslint` → `build` → deploy), QR asset → `/`. |
| 3.2 | **Analytics** | `events` table + RLS insert policy exist but no client code writes events. Add minimal client instrumentation (page view, add-to-cart, order placed) writing to `events`. |
| 3.3 | **A11y audit** | axe + keyboard sweep on product card / slot picker / address form before claiming AA (market P2 #13). |
| 3.4 | **Lighthouse / CWV** | Never measured. Run on mid-range Android after Bugs 1–4 are fixed (market P2 #14). |
| 3.5 | **Google OAuth** | Disabled by design; enable in dashboard only if owner decides it replaces the email-first flow. Keep the capability probe hiding the button until then. |

---

## Explicitly NOT in scope (decisions already made)

- **Account deletion** — request-mode by design (no service-role key in a customer app; `lib/env.ts` rejects it).
- **Coupons / wallet / discount codes** — `store_settings` and `place_order` do not model them; surface real `discount_price` honestly.
- **10-minute SLA / dark-store model** — Vedant weighs to order; promise same-day slots, not minutes.
- **3D beyond the hero** — keep scoped to one capability-gated scene.

---

## Definition of done for "market-ready" (from MARKET_GAP_ANALYSIS §3)

A first-time buyer on a 360px Android can: **search → product → choose weight → add → badge + toast → edit qty in cart → pick address → pick tomorrow Morning slot → see min-order/free-delivery estimate → place → see `ORD-...` + timeline in `/orders/[id]` → reorder.** Plus: photography real (no dead URLs), RLS proven (anon cannot read others' rows), PWA installs from QR and never serves stale private data.