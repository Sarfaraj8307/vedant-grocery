# Market Gap Analysis — Vedant vs Quick-Commerce

> **Date:** 2026-10-10
> **Context:** `docs/PROJECT_AUDIT.md` + `docs/PRODUCT_GAP_ANALYSIS.md` + live `hyibaljxicojfoxbihdn`
> **Lens:** customer buying produce on a phone in India, 2026 — compare Vedant (customer PWA, single-store) against Blinkit, Zepto, Swiggy Instamart, BigBasket, JioMart on the patterns that actually decide purchase, not branding.

Vedant is **not** trying to be a dark-store aggregator. It is a single shop
with same-day weighing and packing. The comparison is therefore for **UX
patterns**, not for assortment or 10-minute SLA.

--------------------------------------------------------------------

## 0. What Vedant already gets right vs market

| Pattern | Vedant today | Market parity | Note |
|---|---|---|---|
| Design tokens, not hex in components | `@theme` warm paper/leaf/marigold/beet + tabular prices, `prefers-reduced-motion` kill switch | Matches BigBasket/Zepto polish, ahead of most single-store sites | Keep verbatim (`src/app/globals.css`) |
| PWA `standalone` + `never-cache Supabase` SW | `public/sw.js` 153 lines, versioned, `SKIP_WAITING` on demand | Ahead of most grocery PWAs (many cache private data) | Keep — it contains the RLS-off window |
| Single `PRODUCT_SELECT` catalogue fetch | One nested PostgREST select, no N+1 | Matches BigBasket's server batching, simpler | Retarget columns only |
| Money integer-paise + `least(price,discount)` parity | `lib/money.ts` + `lib/pricing.ts` exactly mirrors `place_order` | Ahead — most demos float-math | Retarget to `discount_price` if Branch B |
| Cart `cartKey(product,weight)` + partial indexes | Read-then-write tolerating `23505`, merge `max` on sign-in | Matches Zepto's variant key | Retarget to live `max_order_qty/stock` |
| Address `clear-then-set` default | `addresses_one_default_per_user_idx` partial unique | Matches Blinkit | Keep pattern, remap columns |
| Hero limited to one primitive 3D scene, capability-gated | `produce-scene.tsx` lathe basket, DPR cap, `demand/never` | Correct — Blinkit/Zepto use no 3D in cart/checkout | Keep scoped to hero only |

--------------------------------------------------------------------

## 1. Gaps that matter to a produce buyer (ranked)

### P0 — must close to be sellable

| # | Gap vs market | Why it loses the order | Vedant status | Fix |
|---|---|---|---|---|
| 1 | **Empty shelf on every browse/search/offers** | Buyer opens app, sees "shelves being restocked" — leaves. Blinkit/Zepto never show empty as default. | `PRODUCT_SELECT` selects nonexistent `glyph/product_weights/offers` → live returns 0 rows on `hyibalj...` (8 categories, 0 products). `docs/PRODUCT_GAP_ANALYSIS.md` §2. | Choose Branch A/B and ship a real catalogue. No UI polish matters before this. |
| 2 | **Checkout is blocked (no selectable slots, RPC 400)** | Instamart/Zepto: slot is one tap, then pay. Vedant: `SlotPicker` shows 0 slots (`starts_at` vs live `start_time time`), then `placeOrder(p_request_id)` 400s vs live `(p_idempotency_key,p_items,p_delivery_date,p_slot_id)`. | `IMPLEMENTED + BUGGY` | Slot bridge `name+time → timestamptz` + `p_items` from Zustand + `delivery_date` + `idempotency_key` wrapper. |
| 3 | **No photos of the actual stock** | Produce is bought with eyes. Market apps lead with photography, not glyphs. | `ProductImage` glyph plinth is correct fallback but 0 buckets live (`storage.buckets:0`). No catalogue photography pipeline. | Create bucket + `next/image` AVIF/WebP pipeline (already allowlisted to `*.supabase.co/storage/v1/object/public/**`). Source real photography before marketing. |
| 4 | **Trust is invisible until checkout** | Blinkit: delivery fee / free threshold / min order shown on every product and in cart, not just at pay. | `previewTotals` exists but models wrong offer table; `store_settings (20/499/100, is_open)` is live and correct but not surfaced consistently on PLP/cart. | Surface `store_settings` preview on PLP `PriceTag` + cart `OrderSummary` + product detail, labelled "estimate — server is authority". |

### P1 — must close to be competitive on phone

| # | Gap vs market | Vedant status | What good looks like (steal the pattern, not the brand) |
|---|---|---|---|
| 5 | **Search feels like a filter, not search** | `ilike name,description` + `normaliseSearchTerm` works, but suggestions are static `SEARCH_SUGGESTIONS`, no "recent + trending" merge, no category-aware chips. | Zepto: recent (persisted `vedant.search.recent.v1` already exists), trending from catalogue, category chips inline, "no results → did you mean" from real catalogue terms. Add `tsvector` view when catalogue > few hundred rows (`docs/PRODUCT_GAP_ANALYSIS.md` §1.3). |
| 6 | **Quantity control requires a second tap** | `QtyStepper` morph `ADD → stepper` is built and correct, but with empty catalogue it is never exercised; on small screens the price row previously clipped 11px (fixed) but needs re-proof after DB fix. | Blinkit/Zepto: `ADD` → `1` → `+/-` with undo toast. Vedant already has `toast.success` on `add()` + `min-w-[92px]` width-retain — keep and prove at 360/392. |
| 7 | **Delivery promise is vague** | `Delivery slots Morning/Afternoon/Evening 08-10/12-14/17-19` exist live but are `time` not concrete windows; no "today/tomorrow" + cut-off + pincode check. | Instamart: "Deliver in X-Y min / slot" + pincode gate + cut-off countdown. Vedant: map `start_time/end_time` → concrete `timestamptz` (`Asia/Kolkata`) for `today/tomorrow`, grey out `SLOT_FULL` (already computed in `getSlotLoad`), show `min_order_amount` before CTA. |
| 8 | **Reorder is the retention loop — it is BUGGY live** | `reorder-button` reprices from current catalogue (correct) but live order shape is `subtotal/delivery_fee/total + line_total generated` vs app `original_total/adjustment_*/final_total + rejected`. | BigBasket: one-tap reorder after delivered. Vedant: fix read path then keep repricing — do not store old price. |
| 9 | **Support thread is half-wired** | `complaints` + `complaint_attachments` exist live, `complaint_messages` does not; `complaint-images` bucket 0; `notification_outbox` not `notifications`. | Zepto/Instamart: order → help → thread with photos. Vedant: wire attachments to bucket with owner-only storage RLS, and unify read path to `notification_outbox` or create `notifications` view. |

### P2 — needed for "premium" bar the brief sets

| # | Gap | Detail |
|---|---|---|
| 10 | **Store identity is unset** | `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` empty → `needsOwnerConfiguration` true, Call/WhatsApp deep links hidden. Market apps show store header + phone + hours on every surface. Owner must supply before launch; UI already hides safely. |
| 11 | **No loyalty / repeat proof** | No "ordered most this week" from real purchase stats (currently `activeOffer ? 1 : 0` + name sort). Keep as defensible until `events` + orders volume exists; do not fake stats. |
| 12 | **Offline is fallback, not feature** | `/offline` precached, SW never caches Supabase — correct. No "add to cart offline then sync" yet — intentionally deferred (cart is `localStorage` for guest, `cart_items` for signed-in post-fix). |
| 13 | **Accessibility beyond baseline** | Focus `leaf-600`, skip link, tabular prices, `overscroll-behavior-y:none` done. Needs axe + keyboard sweep + screen-reader pass on product card / slot picker / address form before claiming AA. |
| 14 | **Performance proof** | `optimizePackageImports`, `minimumCacheTTL 30d`, dynamic 3D are in place. No measured LCP/INP/CLS on mid-range Android after DB fix — schedule post-remediation. |

--------------------------------------------------------------------

## 2. What NOT to copy from market

* **10-minute countdown / dark-store SLA** — Vedant is a single shop that weighs to order. Promise same-day slots, not minutes.
* **Infinite assortment** — Vedant's catalogue is small (tens of rows) and curated. Keep in-memory `applyBrowseFilters` (no `tsvector` until hundreds of rows).
* **Wallet / coupon engine** — not in `store_settings` and not in `place_order`. Do not invent discount codes; surface real `discount_price` honestly.
* **3D in cart/checkout** — market apps use none. Vedant correctly scopes 3D to hero only.
* **Marketing-site hero** — the brief is explicit: app, not landing page. `AnnouncementBar + HomeHero + CategoryRail + RateBoard + ProductRail + shelf grid + TrustStrip` is the right order.

--------------------------------------------------------------------

## 3. Acceptance for "market-ready"

* A first-time buyer on a 360px Android can: **search → product → choose weight → add → see badge + toast → edit qty in cart → pick address → pick tomorrow Morning slot → see `min_order/free_delivery` estimate → place → see `ORD-YYMMDD-XXXXX` + timeline in `/orders/[id]` → reorder.** Every step proves against live `hyibalj...` after the additive migration.
* Photography is real, not placeholders; `images.unsplash.com` dev allowlist removed in prod (already gated by `NODE_ENV` in `next.config.ts`).
* RLS proven: anon cannot enumerate another customer's `orders/addresses/complaints`; `categories/products/delivery_slots` public read succeeds.
* PWA installs from QR, opens `standalone`, works offline for shell, never serves stale private data.

Every gap above is traced to an inspected file or live query in `docs/PROJECT_AUDIT.md` / `docs/SUPABASE_AUDIT.md` — no heuristic.
