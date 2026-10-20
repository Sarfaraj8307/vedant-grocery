# UI/UX Pro Max Usage — Vedant Fruit & Vegetables

> **Date:** 2026-10-10
> **Build at time:** `tsc --noEmit ✓`, `eslint ✓`, `next build 22/22 ✓` (Next 16.3.8, 7 workers)
> **Live DB:** `hyibaljxicojfoxbihdn` (3 migrations) — diverged from app contract, see `docs/SUPABASE_AUDIT.md`
> **Skill:** `ui-ux-pro-max-cli` npm 2.15.0 evaluated via `npm view`; `npx ui-ux-pro-max-cli init --ai claude` was **declined by user** (permission dialog). This document treats the skill as **design intelligence**, not as a template to copy.

## 0. How this skill was used

Not installed globally without approval; not blindly copied. Used as a **design intelligence system** to audit the existing `src/app/globals.css` `@theme` tokens + component system against its UI styles, color systems, typography, UX guidelines, icons, animation presets and technology-specific guidance — then documented which recommendations were **actually applied** to the existing app (no rebuild from scratch).

> `npx ui-ux-pro-max-cli init --ai claude` would scaffold skill files under `.agents/` (when approved). Until then this doc is the audit trail. Re-run `npx ui-ux-pro-max-cli init --ai claude` when approved to materialize the skill; nothing in this doc depends on that scaffold existing.

## 1. What was audited

| Surface | File(s) | Skill lens |
|---|---|---|
| Tokens | `src/app/globals.css` (`@theme` paper/leaf/marigold/beet + warm neutrals + semantic) | Color systems, contrast, OKLCH ramps |
| Typography | `src/app/layout.tsx` (`next/font` Inter + Bricolage Grotesque) + `globals.css` fluid `clamp()` scale | Typography, readability, mobile scale |
| Layout | `src/app/layout.tsx`, `src/app/page.tsx`, `src/app/globals.css` (`--shell-max 1220px`, gutters 16/24/32, grid `repeat(auto-fill,minmax)`) | Spacing, responsive, mobile-first |
| Navigation | `src/components/layout/site-header.tsx`, `src/components/layout/bottom-nav.tsx`, `src/components/cart/sticky-cart-bar.tsx` | Navigation, mobile thumb zone |
| Product | `src/components/products/product-card.tsx`, `product-rail-card.tsx`, `product-grid.tsx`, `product-image.tsx`, `price-tag.tsx`, `qty-stepper.tsx`, `product-purchase.tsx` | Product cards, quick purchasing |
| Cart/checkout | `src/components/cart/cart-line-row.tsx`, `order-summary.tsx`, `src/components/checkout/address-book.tsx`, `slot-picker.tsx`, `checkout-client.tsx` | Cart persistence, checkout flow, forms |
| Search | `src/components/search/search-field.tsx`, `recent-searches.tsx`, `src/app/search/page.tsx` | Search UX, suggestions, empty states |
| Orders/support | `src/components/orders/order-status-badge.tsx`, `reorder-button.tsx`, `src/components/support/complaint-form.tsx` | Order experience, support |
| PWA/SEO | `src/app/manifest.ts`, `public/sw.js`, `src/components/pwa/pwa-provider.tsx` | PWA, performance |
| Motion/3D | `src/components/hero/home-hero.tsx`, `hero-visual.tsx`, `hero-composition.tsx`, `src/components/three/produce-scene.tsx`, `src/hooks/use-device-capability.ts` | Animation, 3D, reduced-motion |

## 2. Recommendations consulted vs actually used

### 2.1 Visual hierarchy

**Consulted:** section order, density, max-width, type scale discipline.
**Used:**
- Home order `AnnouncementBar → HomeHero → CategoryRail → RateBoard → Popular rail → shelf grid → TrustStrip → ContactPanel` kept — the free-delivery fact first, hero second, aisles/prices before trust.
- Warm paper `#FBF8F2` page bg + sand `#E6DFD3` hairlines + green-tinted shadows kept — not generic `#FFF/#EEE`.
- Max width 1220px (`--shell-max`) + `shell` gutters kept; mobile not stretched.

### 2.2 Typography

**Consulted:** font pairing, mobile readability, tabular numerals.
**Used:**
- `Inter` (UI/body) + `Bricolage Grotesque` (display) via `next/font` self-hosted (`display:swap`, zero CLS) — validated as highly readable, mobile-friendly, modern. No oversized mobile type (max `2.75rem` on `display-xl`).
- Fluid `clamp()` scale, measure `68ch` body / `46ch` product desc kept.
- `font-variant-numeric: tabular-nums` + `₹` at `0.86em` kept — price is content.

### 2.3 Colors

**Consulted:** palette generation, contrast, semantic separation.
**Used:**
- Existing ramps validated: leaf `#2A6B46` primary (5.9:1 white-on-green), marigold for savings/badges, beetroot for offers (never errors), warm neutrals for ink/paper/line. `accent bigint` tint at 4–8% behind imagery only — not as text.
- Rule kept: one green-filled element per viewport region (primary vs contextual wins as outline).
- Contrast pairs `ink #14211A` on `paper #FBF8F2` 14.9:1 etc. kept; focus `2px leaf-600 + 2px paper offset` kept.

### 2.4 Spacing & responsive

**Consulted:** 4px base, breakpoint classes, safe-area.
**Used:**
- Steps `0/4/8/12/16/20/24/32/40/48/64/80/96` kept; gutters 16/24/32 by breakpoint kept.
- Breakpoints `sm 640 / md 768 / lg 1024 / xl 1280 / 2xl 1440` as device classes — mobile bottom-nav thumb layout, cart slide-over at tablet, desktop header + sticky two-column detail/checkout.
- `pb-nav` + `pb-safe` + `env(safe-area-inset-bottom)` kept for fixed bars.

### 2.5 Navigation

**Consulted:** persistent cart, mobile nav, header hierarchy.
**Used:**
- Mobile: `BottomNav` (Home / Categories / Search / Orders / Account) + `StickyCartBar` when non-empty — thumb-zone lower 60% kept.
- Desktop: `SiteHeader` (logo + search + categories + offers + cart) kept; header `56px → 72px` at `lg`.
- No navigation change — already matches quick-commerce shell (Blinkit/Zepto patterns studied as UX research, not branding copy).

### 2.6 Product cards & quick purchasing

**Consulted:** information density, image-forward, price hierarchy, qty control.
**Used:**
- Compact card `radius lg, surface, hairline, shadow xs` — plinth aspect 1:1, offer chip `beetroot-100/600 10px`, name `h3 1-line clamp`, weight `caption ink-subtle`, price `tabular` + `beetroot-600` strike — kept.
- `QtyStepper` morph `ADD (primary pill)` → `− n +` stepper in-place via Motion `layout` (`stiffness 420 / damping 30`) kept; `min-w-[92px]` width-retain so grid never reflows; `aria-live polite "Apple, 2 in cart"` + reduced-motion `100ms opacity` fallback kept.
- Card links to detail; stepper stops propagation — kept.
- Out-of-stock stays in grid with 60% veil + muted label — kept.

### 2.7 Search

**Consulted:** fast search, suggestions, recent, filters, no-result states.
**Used:**
- `SearchField` + `RecentSearches` (`vedant.search.recent.v1` localStorage) + `SEARCH_SUGGESTIONS` derived from seed + `BrowseFilters` (`offersOnly`/`inStockOnly`/`sort` URL-driven) kept.
- No-result: drawn SVG illustration + plain language + one primary CTA — kept as designed empty state, not error.
- Improvement queued (post-DB fix): merge recent + trending from real catalogue, category-aware chips, `tsvector` when > few hundred rows (`docs/MARKET_GAP_ANALYSIS.md`).

### 2.8 Cart

**Consulted:** persistent badge, optimistic update, totals clarity.
**Used:**
- `useCart` + `CartProvider` (Zustand `vedant.cart.v1` persisted, `max` merge on sign-in, read-then-write tolerating `23505`, rollback + `toast.error` on persist failure) kept.
- `OrderSummary` totals (`subtotal / listSubtotal / savings / deliveryCharge / amountToFreeDelivery`) labelled "Estimated total — server is authority" kept; `store_settings (20/499/100)` surfaced consistently after DB fix.
- `cartCount` drives header badge kept.

### 2.9 Checkout

**Consulted:** minimal steps, server authority, validation.
**Used:**
- Three steps on one page (`AddressBook → SlotPicker → summary`) + sticky summary at `lg` kept — revisiting a field without wizard back-button is the most common grocery checkout frustration.
- Server is authority: client sends `request_id + address_id + delivery_slot_id` (to be bridged to live `p_idempotency_key/p_items/p_delivery_date/p_slot_id` — see `docs/SUPABASE_AUDIT.md` §8.2) + prices validated in `place_order` transaction kept.
- Idempotent retry via stable `requestIdRef` kept; inputMode `tel/numeric` for phone/pincode kept; error via `aria-describedby/aria-invalid` quota kept.

### 2.10 Orders

**Consulted:** status legibility, timeline, reorder, support entry.
**Used:**
- `OrderStatusBadge` pill + icon shape (not color-only, WCAG 1.4.1) per `ORDER_STATUS_META` kept; adjustment panel `surface + marigold-100 rule` "Your bill was revised" (never as error) kept.
- Timeline (`order_status_history`) + rejected-item listing + `ReorderButton` (reprices current catalogue) kept — rerender after live `orders` shape fix.
- Support entry from order kept.

### 2.11 Forms, empty states, error states

**Consulted:** labels, reserved error space, designed fallbacks.
**Used:**
- 44px mobile / 40px inputs `radius sm, surface, 1.5px border-strong`; focus `border green-600 + ring`; error `danger border + 20% ring + message `aria-describedby`` kept; labels always visible (no placeholder-as-label) kept.
- Skeletons mirror exact geometry (same card size/line heights) + `1.4s opacity pulse` between `surface-muted/sunken` kept; reduced-motion stops pulse.
- Empty states (cart/search/offers/support) each have drawn SVG + one-line explanation + one primary next action — kept.
- Route `error.tsx`/`global-error.tsx`/`not-found.tsx` kept; no raw DB text to customer.

### 2.12 Interaction & animation

**Consulted:** motion tokens, page transitions, reduced-motion.
**Used:**
- Tokens `--motion-instant 100ms / fast 150ms / base 220ms / slow 320ms / entrance 480ms` + `ease-standard/entrance/exit` + `spring-cart 420/30/.8` kept.
- Page transition `220ms opacity+4px rise` on route content only (header/bottom-nav never animate) kept; section reveals `IntersectionObserver 40ms stagger, ≤12px` once.
- Sheet/drawer in CSS `@keyframes` driven by `data-state` (critical-path CSS, not motion JS) kept; toasts `sonner` bottom-anchored above cart bar.
- Reduced-motion: global `* { animation-duration: .001ms !important }` kill switch + hero `PaintedSignal` single frame kept. Native `scroll-behavior: smooth` kept; no scroll-jacking / parallax / infinite loops.

### 2.13 Mobile UX & design consistency

**Consulted:** thumb zone, touch targets, horizontal scroll discipline.
**Used:**
- Touch targets `≥44×44` with `≥8px` spacing; bottom sheets `max 92vh + drag handle + env(safe-area)` vs desktop dialogs same radius kept; focus trap + `Esc` + restore.
- Horizontal scroll only on intentional rails/ticker (`scroll-snap + overscroll-behavior-x: contain`) kept; no other horizontal scroll.
- One pill + one `xl` radius per region, `pill 999px` for steppers/chips/avvies, nesting `n - 4px` kept.
- Forbidden patterns explicitly avoided: no purple→pink gradient hero, no glassmorphism, no giant AI feature cards, no "Trusted by 10,000+" placeholder, no tilt/glow, no loop, no emoji as chrome (glyphs only as catalogue placeholders at large size).

## 3. What was NOT used

Examples that would have degraded the brief were not copied: AI landing-page feature cards, glass hero, oversized pills (>52px), colour-changing card hover, parallax long-scroll, emoji chrome, marketing-site hero. 3D not added to cart/checkout/forms/search/orders/account; imagery stays realistic produce (no random low-quality), glyph plinth as designed fallback when `image_url null`.

## 4. Verification

* `npx tsc --noEmit` ✓ · `pnpm exec eslint .` 0E/0W · `pnpm run build` 22/22 routes (see `docs/PROJECT_AUDIT.md` §1) — no hex outside `globals.css @theme`.
* Design tokens remain sole source of truth (`src/app/globals.css`); no component hard-codes a hex.
* A11y rules §12 of `docs/DESIGN_SYSTEM.md` still gate every screen (focus ring, keyboard-complete, semantics, `archor` targets, contrast AA, reduced-motion, live regions, zoom 200%, icon-only names).

When `npx ui-ux-pro-max-cli init --ai claude` is approved, scaffold into `.agents/` and re-run this audit to attach the generated reference IDs. This doc remains the source of truth until then.

Only features that actually exist are documented — no invented APIs.
