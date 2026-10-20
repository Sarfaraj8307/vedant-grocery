# Session Report — 2026-10-11

## 1. Bugs fixed

| # | Bug | Fix | Verified |
|---|-----|-----|----------|
| 1 | `order_items.quantity` vs live column `qty` | Renamed in `order-service.ts`, `database.ts`, `orders/page.tsx`, `orders/[id]/page.tsx`, `reorder-button.tsx` | tsc, build |
| 2 | Dead product image URLs, no fallback | `ProductImage` falls back to glyph on load error (`"use client"`); 8 dead `image_url` values set to null in DB | tsc, build |
| 3 | No storage buckets | Created `product-images` (public) and `complaint-images` (private) | SQL check |
| 4 | Notifications read the wrong table | `lib/notifications.ts` and `notification-actions.tsx` now use `notification_outbox` filtered to `audience='customer'` | tsc, build |

## 2. Features

- **Weight denominations (admin-driven data):** Lemon (Nimbu) now has 250 g ₹25, 500 g ₹50, 1 kg ₹100 in `product_weights`. The storefront reads these rows, so no code change was needed for display. `products.base_price` set to ₹100.
- **Admin UI for denominations:** NOT built yet. Rows are created by SQL for now. The admin app must add a denomination editor (label, grams, price, availability) per product.

## 3. UI / UX

- Global motion tokens added in `src/app/globals.css`: `reveal`, `drift`, `sheen` keyframes with `--animate-*` utilities. Existing reduced-motion rule still applies.
- Home hero (`src/components/hero/home-hero.tsx`): layered warm gradient background, staggered text reveal, floating Lemon denomination card, copy now lists 250 g / 500 g / 1 kg.
- Still to do: category rail, product card, and section-level scroll reveal across home and product pages.

## 4. Verification

- `pnpm exec tsc --noEmit` — pass.
- `pnpm run build` — pass (exit 0).
- `pnpm exec eslint .` — no errors.
- Browser/visual QA of the hero and motion: NOT done. Check on a 360 px Android viewport before shipping.

## 5. Open items

1. Admin denomination editor (web admin / future admin app).
2. Recurrence for delivery slots: 18 slots still end 2026-10-16; checkout will block from 2026-10-17.
3. Remove unused `graphify` dependency (`pnpm remove graphify`).
4. Owner actions: grant first admin in `private.admin_users`; set `NEXT_PUBLIC_STORE_PHONE`, `NEXT_PUBLIC_STORE_WHATSAPP`, `NEXT_PUBLIC_STORE_ADDRESS`.
5. Unit tests for `pricing.ts` and `money.ts` (none exist).
6. Git repository not initialised.
7. Android app: build with `docs/ANDROID_BUILD_PROMPT.md` in Kilo Code, or continue here.

## 6. Files of interest

- `docs/ANDROID_BUILD_PROMPT.md` — paste into Kilo Code.
- `docs/TASK_STATE.md` and `docs/ACTION_PLAN_2026-10-11.md` — earlier plans; the bug list here supersedes them.
