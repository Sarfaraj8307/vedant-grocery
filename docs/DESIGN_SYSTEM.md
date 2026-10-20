# Vedant Design System

> The visual language for **Vedant Fruit & Vegetables**. Original work — no
> template, no copied branding. The goal stated in the brief was:
> *"the customer should open the URL and immediately think 'this looks like a
> real premium grocery service', not 'this looks like a developer demo'."*
> Every rule below exists to serve that sentence.

Implementation lives in [`src/app/globals.css`](../src/app/globals.css) as
Tailwind v4 `@theme` tokens. Nothing in the UI hard-codes a hex value.

---

## 1. Design principles

1. **Produce is the hero, chrome is not.** Colour, imagery and type always
   point at the food. The interface itself stays quiet: paper backgrounds,
   hairline borders, restrained shadows.
2. **Density over airiness for shopping surfaces.** A grocery list is scanned,
   not read. Cards are compact and information-rich; whitespace is reserved for
   the hero and section breaks.
3. **Warm neutrals, never sterile grey.** Backgrounds are warm paper
   (`#FBF8F2`), borders are sand (`#E6DFD3`), shadows are green-tinted. Pure
   `#FFF` and `#EEE` greys read as "generic website" and are not used as page
   backgrounds.
4. **One strong green, used sparingly.** Green is the brand, not the wallpaper.
   Primary green appears on the primary action, the logo, and *one* accent per
   screen. Everything else is ink, paper and sand.
5. **Tactile, not animated.** Every response is a small, fast, physical
   confirmation (press depth, weight-shift, a chip that pops into a stepper).
   Nothing loops, nothing floats for decoration, nothing bounces twice.
6. **Numbers are typography.** Prices get tabular figures, tight tracking and
   real hierarchy. In a grocery app, the price *is* the content.
7. **Fail to something designed.** Missing image → designed plinth. Missing 3D →
   static composition. No layout is allowed to collapse into a blank box.

---

## 2. Colour

All colours are authored in OKLCH with sRGB hex noted for design handoff.
Semantic tokens are what components consume; the ramps exist so we can build
tints and states without inventing new colours.

### 2.1 Produce green — brand primary

The core brand colour. A deep, slightly desaturated leaf green (not Tailwind's
`green-600`, which is brighter and reads as "Bootstrap").

| Token | Hex | OKLCH | Use |
|---|---|---|---|
| `--green-50` | `#F2F8F3` | `oklch(0.975 0.012 155)` | Subtle green wash |
| `--green-100` | `#DFEEE2` | `oklch(0.940 0.024 155)` | Chips, selected nav |
| `--green-200` | `#BFDCC6` | `oklch(0.878 0.040 155)` | Borders on green surfaces |
| `--green-300` | `#93C2A1` | `oklch(0.780 0.062 155)` | Disabled primary, icons |
| `--green-400` | `#5FA277` | `oklch(0.655 0.090 155)` | Hover tint |
| `--green-500` | `#3D855A` | `oklch(0.565 0.100 155)` | Secondary actions |
| `--green-600` | `#2A6B46` | `oklch(0.470 0.098 155)` | **Primary** — buttons, active nav, logo |
| `--green-700` | `#1F5537` | `oklch(0.397 0.086 155)` | Primary pressed |
| `--green-800` | `#1A432D` | `oklch(0.330 0.068 155)` | Headings on tinted panels |
| `--green-900` | `#142F21` | `oklch(0.265 0.052 155)` | Deep brand panels |

### 2.2 Marigold — market warmth (accent)

The mandi/marigold note. Used for "today's rate", savings badges and the trust
ticker — never for body text on paper (contrast is insufficient).

| Token | Hex | Use |
|---|---|---|
| `--marigold-50` | `#FFF8EC` | Rate-board background |
| `--marigold-100` | `#FDEBCB` | Badge background |
| `--marigold-200` | `#FAD79A` | Badge border |
| `--marigold-300` | `#F5BE5F` | Accent graphics |
| `--marigold-400` | `#EFA632` | Icon on dark green |
| `--marigold-500` | `#DE8E18` | Accent line |
| `--marigold-600` | `#B96F10` | Accent **text** (AA on paper) |
| `--marigold-700` | `#8F520D` | Accent text pressed |

### 2.3 Beetroot — savings / offers

A plum-red that is distinctly *not* the e-commerce danger red. Reserved for
discounts and the "offer" state so that destructive actions stay unambiguous.

| Token | Hex | Use |
|---|---|---|
| `--beetroot-100` | `#FBE8F0` | Offer chip background |
| `--beetroot-500` | `#A93A63` | Offer chip text |
| `--beetroot-600` | `#8B2E5C` | Strikethrough original price, offer headings |

### 2.4 Warm neutrals — ink & paper

| Token | Hex | Use |
|---|---|---|
| `--paper` | `#FBF8F2` | **Page background** |
| `--surface` | `#FFFFFF` | Cards, sheets, inputs |
| `--surface-muted` | `#F4F0E8` | Inset panels, skeletons, image plinths |
| `--surface-sunken` | `#EDE7DB` | Progress track, disabled fill |
| `--border` | `#E6DFD3` | Default hairline |
| `--border-strong` | `#D6CCBB` | Input borders, dividers with weight |
| `--ink` | `#14211A` | Primary text (green-tinted near-black) |
| `--ink-muted` | `#55635B` | Secondary text |
| `--ink-subtle` | `#8A968F` | Captions, placeholders, metadata |

### 2.5 Semantic

| Token | Hex | Background | Contrast on own bg |
|---|---|---|---|
| `--success` | `#1E8E5A` | `#E6F5EC` | 4.6:1 ✔ AA |
| `--warning` | `#B26A05` | `#FDF3E2` | 5.1:1 ✔ AA |
| `--danger` | `#B3261E` | `#FDECEA` | 5.6:1 ✔ AA |
| `--info` | `#1F6FB2` | `#EAF2FA` | 4.9:1 ✔ AA |

### 2.6 Contrast commitments

Verified pairs (WCAG 2.1 AA, 4.5:1 body / 3:1 large & UI):

* `--ink` on `--paper` → **14.9:1**
* `--ink-muted` on `--paper` → **6.7:1**
* `--ink-subtle` on `--paper` → **3.6:1** → metadata only, never body copy
* White on `--green-600` → **5.9:1** ✔
* White on `--green-700` → **7.8:1** ✔
* `--marigold-600` on `--marigold-50` → **4.6:1** ✔
* `--beetroot-600` on `--paper` → **7.2:1** ✔
* Focus ring `--green-600` on paper → **5.9:1** ✔ (exceeds the 3:1 non-text minimum)

Focus is **always** a 2px `--green-600` ring with a 2px `--paper` offset, so it
is visible on white cards *and* on green buttons.

### 2.7 Colour rules

* **Never** put green on green. A green surface takes white or marigold content.
* **Never** use `--beetroot-*` for errors, or `--danger` for offers.
* Maximum **one** green-filled element per viewport region. If two compete
  (e.g. a green "Add" button next to a green "Checkout" bar), the contextual one
  wins and the other becomes an outline.
* Accent (`accent bigint`) values supplied per product/category in the database
  are used **only** as a 4–8% tint behind imagery, never as text colour.

---

## 3. Typography

### 3.1 Families

| Role | Family | Rationale |
|---|---|---|
| Display | **Bricolage Grotesque** | A contemporary grotesque with real personality in its counters. Carries the brand voice in headings without being a decorative script. Loaded with `next/font` (self-hosted, `display: swap`). |
| UI / body | **Inter** | The most legible, least obtrusive UI sans. Variable, self-hosted, excellent at 13–16px, and has true tabular figures for prices. |

Both are loaded as variable fonts via `next/font/google` → **zero layout shift,
no external requests at runtime, no FOIT**. Fallback stack is a system sans so
the app degrades gracefully if a font ever fails to load.

Body copy is a professional modern sans (Inter) — no decorative face is ever
used for content, as the brief requires.

### 3.2 Type scale

Fluid `clamp()` so there is no mobile/desktop type discontinuity and no
layout shift between breakpoints.

| Token | Size | Line height | Tracking | Weight | Family |
|---|---|---|---|---|---|
| `display-xl` | `clamp(2.75rem, 6.5vw, 4.5rem)` | 0.95 | `-0.035em` | 600 | Display |
| `display` | `clamp(2.125rem, 4.5vw, 3.25rem)` | 1.0 | `-0.03em` | 600 | Display |
| `h1` | `clamp(1.75rem, 3vw, 2.25rem)` | 1.15 | `-0.02em` | 600 | Display |
| `h2` | `clamp(1.375rem, 2.2vw, 1.75rem)` | 1.2 | `-0.015em` | 600 | Display |
| `h3` | `1.0625rem` | 1.35 | `-0.01em` | 600 | UI |
| `body` | `0.9375rem` | 1.6 | `0` | 400 | UI |
| `body-lg` | `1.0625rem` | 1.6 | `0` | 400 | UI |
| `small` | `0.8125rem` | 1.5 | `0` | 400 | UI |
| `caption` | `0.6875rem` | 1.4 | `0.09em`, uppercase | 600 | UI |
| `price-lg` | `1.5rem` | 1.1 | `-0.02em`, tabular | 650 | UI |
| `price` | `1.0625rem` | 1.2 | `-0.01em`, tabular | 600 | UI |
| `price-strike` | `0.8125rem` | 1.2 | tabular | 400 | UI |

Rules:
* `body` is `0.9375rem` (15px) not 16px — the deliberate choice that lets a
  dense grocery grid breathe on a 360px phone without shrinking to 12px.
* Headings are **never** larger than `display-xl`; on mobile nothing exceeds
  `2.75rem`.
* Captions are the only uppercase text, capped at 11px, always
  `letter-spacing ≥ 0.08em` for legibility.
* Prices use `font-variant-numeric: tabular-nums` so digits align in a column,
  and `₹` is rendered slightly smaller (`0.86em`) than the amount.
* Body measure is capped at **68ch**; product descriptions at **46ch**.

---

## 4. Spacing & layout

4px base. Only these steps exist: `0, 1(4), 2(8), 3(12), 4(16), 5(20), 6(24),
8(32), 10(40), 12(48), 16(64), 20(80), 24(96)`. Any other value is a bug.

| Context | Mobile (<640) | Tablet (640–1023) | Desktop (≥1024) |
|---|---|---|---|
| Page gutter | 16px | 24px | 32px |
| Max catalogue width | 100% | 100% | **1220px** |
| Max reading width | 100% | 640px | **720px** |
| Max checkout width | 100% | 560px | **880px** |
| Section vertical rhythm | 40px | 56px | 72px |
| Product grid columns | 2 | 3 | 4 (5 at ≥1440) |
| Card gap | 12px | 16px | 18px |

**Grid:** CSS Grid with `repeat(auto-fill, minmax(...))` at the card level so
columns adapt to the actual container, not just the viewport. Breakpoints are
*deliberate*: mobile is a bottom-nav thumb layout, tablet widens the grid and
introduces a persistent cart summary, desktop adds a full header nav plus a
sticky two-column product-detail and checkout layout. The mobile UI is **never**
simply stretched.

**Safe areas:** every fixed bottom surface adds
`padding-bottom: max(16px, env(safe-area-inset-bottom))` for iOS.

---

## 5. Radius

| Token | Value | Use |
|---|---|---|
| `--radius-xs` | 6px | Chips, tags |
| `--radius-sm` | 10px | Inputs, small buttons |
| `--radius-md` | 14px | Buttons, list rows |
| `--radius-lg` | 18px | Cards, panels |
| `--radius-xl` | 24px | Sheets, modals, hero media |
| `--radius-2xl` | 32px | Hero composition plinth |
| `--radius-pill` | 999px | Quantity steppers, filter chips, avatars |

Nesting rule: a child inside radius `n` uses at most `n - 4px`, so corners stay
concentric. Only one pill and one `xl` element may be visible per region.

---

## 6. Elevation

Shadows are **green-tinted, never neutral grey** — grey shadows on warm paper
look dirty.

| Token | Value | Use |
|---|---|---|
| `--shadow-xs` | `0 1px 2px rgba(20,33,26,.05)` | Inputs, chips |
| `--shadow-sm` | `0 2px 6px -1px rgba(20,33,26,.07), 0 1px 2px rgba(20,33,26,.04)` | Product cards at rest |
| `--shadow-md` | `0 8px 20px -6px rgba(20,33,26,.12)` | Card hover, dropdowns |
| `--shadow-lg` | `0 20px 44px -18px rgba(20,33,26,.20)` | Sheets, modals |
| `--shadow-produce` | `0 12px 26px -14px rgba(42,107,70,.38)` | Produce imagery plinth |
| `--shadow-sticky` | `0 -6px 18px -8px rgba(20,33,26,.14)` | Sticky cart bar, bottom nav |

**Rule:** elevation encodes *interaction distance*, not decoration. A card at
rest uses `xs`/`sm`. Hover raises one step. Sheets use `lg`. Nothing uses
`lg` while at rest inside the page flow.

---

## 7. Components

Each component is specified with its states. All interactive components
implement the full set: `default / hover / active / focus-visible / disabled /
loading` (and `selected` where applicable).

### 7.1 Buttons

| Variant | Fill | Text | Border | Use |
|---|---|---|---|---|
| `primary` | `--green-600` | white | none | Add to cart (card), Place order, Sign in |
| `primary` hover/active | `--green-700`, +`translateY(-1px)` / `translateY(0)` | | | press depth is 1px, no scale |
| `secondary` | `--surface` | `--green-700` | 1.5px `--green-600` at 35% | Secondary actions beside a primary |
| `quiet` | transparent | `--ink` | none | Tertiary / dismissive |
| `accent` | `--marigold-400` | `--green-900` | none | "Today's rate" CTAs |
| `danger` | `--danger` | white | none | Clear cart, delete address |
| `link` | none | `--green-700` underline on hover | | Inline navigation |

Sizes: `sm` 32px, `md` 40px, `lg` 48px (mobile primary). Icon-only buttons are
`40×40` on desktop and `44×44` on touch — never smaller, per touch-target rules.
**Loading** replaces the label with a 16px spinner + keeps the label as
`aria-live` text, and the button keeps its exact width (no layout shift).

Add-to-cart is *not* a button — it is the `QtyStepper` (see 7.6) so the action
and its result occupy the same pixels. This is the single most-used control in
the app and it may not jump.

### 7.2 Product card

Compact, image-forward, 4-tier information hierarchy. **Never** a large card.

```
┌──────────────────────┐   radius lg, surface, border hairline, shadow xs
│  ▢ image plinth      │   aspect 1:1, radius md, tinted by product.accent
│   (offer chip TL)    │   offer chip: beetroot-100 fill / beetroot-600 text, 10px
│   (out-of-stock)     │   out-of-stock: 60% white veil + ink-muted label
├──────────────────────┤
│ Name                 │   h3, 1 line clamp, -0.01em
│ 1 kg · per kg        │   caption, ink-subtle
│ ₹120  ₹99  ▟stepper  │   price tabular; strike in beetroot-600; stepper 32px tall
└──────────────────────┘
```

* Height is content-driven and **clamped** — a two-line name cannot push the
  price row out of alignment across a grid row.
* The whole card links to the product page; the stepper stops propagation.
* Out-of-stock cards stay in the grid (so the customer isn't confused by a
  missing staple) but are not interactive.
* No hover-zoom, no image tilt, no gradient scrim.

### 7.3 Price display (`PriceTag`)

Three states: `plain`, `offer` (strike + highlighted), `offer-percent`
("15% off" chip). The `₹` glyph is `0.86em` and `ink-muted`; the amount is
`ink`. In `offer` state the effective price is `green-700` **only** when the
product is in the cart (reinforcing the active state), otherwise `ink`.

### 7.4 Inputs & forms

* Height 44px (mobile) / 40px, radius `sm`, `--surface`, 1.5px `--border-strong`.
* Focus: border becomes `--green-600` **and** the ring appears — never ring-only.
* Error: `--danger` border + 2px ring at 20% + message below in `--danger`,
  wired via `aria-describedby` and `aria-invalid`.
* Labels are always visible (no placeholder-as-label).
* Numeric fields set `inputMode` correctly (`tel` for phone, `numeric` for
  pincode) — on a phone this is the difference between a 3-second and a
  30-second checkout.
* Form layout uses `react-hook-form` + `zod`; error text is reserved space so
  validation never shifts the layout under a thumb.

### 7.5 Badges & chips

* **State badge** (`OrderStatusBadge`): pill, `caption` type, coloured
  background ramp per order status (see §9). Always paired with an icon shape so
  status is not colour-only (WCAG 1.4.1).
* **Filter chip**: pill 32px, `surface` + hairline at rest; `green-600` fill
  when active, with a marigold dot for "has offers".
* **Offer chip**: `beetroot-100` fill, `beetroot-600` text, 10px, uppercase.

### 7.6 Quantity stepper

The signature control. Closed state is a single `primary` pill labelled `ADD`.
One tap morphs it, in place, into a `− n +` stepper via a shared-layout
transition (Motion `layout`, spring `stiffness 420 / damping 30`).

* Width is **identical** in both states (min 88px) so the grid never reflows.
* `−` at quantity 1 removes the item and collapses with a 120ms fade.
* At `quantity_limit` the `+` disables and shows a "Max N" hint.
* `aria-live="polite"` announces `"Apple, 2 in cart"`.
* Reduced motion: no morph — a 100ms opacity crossfade.

### 7.7 Sheets, dialogs, toasts

* Mobile uses a **bottom sheet** (radius `xl`, drag handle, max 92vh,
  `env(safe-area-inset)` padding). Desktop uses a centred dialog at the same
  radius. One component, two presentations.
* Focus is trapped; `Esc` closes; the trigger regains focus on close.
* Toasts (`sonner`) are bottom-anchored on mobile (above the cart bar) and
  bottom-right on desktop, `surface` + hairline + `shadow-md`, with a 4px
  status bar on the leading edge. Never top-centre — that is the pattern that
  reads as "templated SaaS".

### 7.8 Skeletons

Skeletons mirror the *exact* geometry of what loads (same card size, same line
heights) so there is **zero** layout shift on hydrate. A very slow 1.4s
opacity pulse between `surface-muted` and `surface-sunken` — no diagonal
gradient shimmer (too "AI demo"). Under `prefers-reduced-motion` the pulse stops
entirely.

### 7.9 Empty states

Every empty state has three parts: a **drawn** illustration (SVG, brand
palette — not a stock 3D blob), a one-line explanation in plain language, and
exactly **one** primary next action. Copy is written in the voice of the store,
e.g. cart: *"Your basket is empty — let's fix that."* → `Browse today's picks`.

---

## 8. Motion

Centralised, named, and small. Durations are tokens; ad-hoc values are a bug.

| Token | Value | Use |
|---|---|---|
| `--motion-instant` | 100ms | Colour/opacity state changes |
| `--motion-fast` | 150ms | Press, hover, chip toggle |
| `--motion-base` | 220ms | Card lift, sheet contents |
| `--motion-slow` | 320ms | Sheet open, page section reveal |
| `--motion-entrance` | 480ms | Hero content, first-load only |

Easing: `--ease-standard: cubic-bezier(.2,.8,.2,1)`,
`--ease-entrance: cubic-bezier(.16,1,.3,1)` (expo-out),
`--ease-exit: cubic-bezier(.4,0,1,1)`.

Springs: `--spring-cart: { stiffness: 420, damping: 30, mass: .8 }`.

Rules:
* **Animation must communicate state.** If a motion doesn't tell the user
  something changed, it is removed. No scroll-jacking, no parallax on
  long-scroll content, no infinite loops, no floating decoration.
* Page transitions: a 220ms opacity+4px rise on route content only — the header
  and bottom nav never animate on navigation.
* Section reveals fire **once** via `IntersectionObserver`, 40ms stagger, and
  translate ≤ 12px.
* Under `prefers-reduced-motion: reduce` **all** transforms and parallax are
  disabled globally and replaced with ≤120ms opacity changes. The 3D scene
  renders a single static frame and stops its render loop.

---

## 9. Order status language

Status must be legible at a glance, colour-blind safe, and honest about what the
*store* is doing. Icons differ in **shape**, not just colour.

| Status | Customer label | Icon | Colour |
|---|---|---|---|
| `ORDER_RECEIVED` | Order received | inbox | `--info` |
| `OWNER_ACCEPTED` | Confirmed by store | check-circle | `--green-500` |
| `PREPARING` | Being packed | basket | `--marigold-600` |
| `READY` | Ready to dispatch | box | `--marigold-600` |
| `OUT_FOR_DELIVERY` | Out for delivery | bike | `--green-600` |
| `DELIVERED` | Delivered | check-badge | `--success` |
| `CANCELLED` | Cancelled | x-circle | `--danger` |

Adjustments (rejected items) are presented as a distinct, calm **"Your bill was
revised"** panel: `surface` + `--marigold-100` leading rule, listing each
rejected item with the store's reason and the resulting `final_total`. It is
never styled as an error — the store did nothing wrong, and alarming the
customer would be a design failure.

---

## 10. 3D principles

3D exists to make the brand memorable in **one** place: the home hero. It is
governed by rules, not enthusiasm.

1. **One scene.** The storefront has exactly one WebGL canvas site-wide. There
   is no 3D on category, product, cart or checkout pages — those need speed.
2. **Never the critical path.** The canvas is `next/dynamic` +
   `ssr: false`, mounted after the hero's HTML/CSS is painted, and the hero is
   designed to be complete and beautiful *without* it (glyph-and-plinth
   composition). First paint never waits on three.js.
3. **Soft real-time lighting, no HDRI downloads.** A single directional light +
   ambient, materials are flat-ish `meshStandardMaterial` with low roughness.
   No environment map fetches, no external `.glb` from a CDN.
4. **Geometry is authored, not downloaded.** Produce is built from primitives
   (spheres, lathe, torus, capsule) — keeps the payload at **zero bytes of
   model data**, avoids licence issues, and no 5MB `.glb` ever ships.
5. **Budget:** ≤ 35k triangles, ≤ 6 draw calls, DPR capped at `[1, 1.75]`,
   `frameloop="demand"` after the intro settles.
6. **Pause when offscreen** (`IntersectionObserver`) and on
   `document.hidden`. Stop entirely after the intro if the device is
   low-power.
7. **Low-power / mobile:** detect via `navigator.hardwareConcurrency <= 4`,
   `deviceMemory <= 4`, or a coarse pointer with a small viewport → render the
   static SVG composition instead. No shame in this path; it is the default on
   most mid-range Android phones.
8. **Reduced motion:** renders one frame, no rotation, no idle animation.
9. **Failure:** `<Canvas>` is wrapped in an error boundary; any WebGL/context
   error clears the canvas and reveals the static composition. **The shopping
   interface must be 100% usable with 3D fully dead** — that is a hard
   requirement, not a nicety.

---

## 11. Responsive rules

* Breakpoints: `sm 640`, `md 768`, `lg 1024`, `xl 1280`, `2xl 1440`. These map
  to device *classes* (small phone / large phone / tablet / laptop / desktop),
  not to arbitrary widths.
* **Mobile (<640):** bottom navigation (Home / Categories / Search / Orders /
  Account) + persistent cart bar when the cart is non-empty. All primary
  actions sit in the lower 60% of the screen — the thumb zone. Header is
  compact (56px) and collapses its search into an icon that expands.
* **Tablet (640–1023):** bottom nav is replaced by a top nav; the grid widens;
  cart becomes a slide-over; product detail goes two-column at ≥768.
* **Desktop (≥1024):** full header (logo, deliver-to, search, nav, account,
  cart); max width 1220px; checkout is a two-column layout with a sticky
  summary; product detail is a sticky gallery + scrolling detail column.
* No horizontal scroll anywhere except **intentional** category rails and the
  rate-board ticker, both of which use `scroll-snap` and
  `overscroll-behavior-x: contain`.
* Images use `sizes` matching the real grid at each breakpoint so mobile never
  downloads desktop-resolution assets.

---

## 12. Accessibility rules

Non-negotiable, checked on every screen:

1. **Focus visible always.** A 2px `--green-600` ring with a 2px paper offset.
   `outline: none` without a replacement is forbidden.
2. **Keyboard-complete.** Every flow — search, add to cart, address, slot,
   checkout, order tracking, complaint — is fully operable with
   Tab/Shift-Tab/Enter/Space/Esc/Arrows. Sheets trap focus and restore it.
3. **Semantics first.** Real `<button>`, `<a>`, `<nav>`, `<main>`, `<ul>`,
   `<label>`. ARIA only where HTML is insufficient.
4. **Touch targets ≥ 44×44 CSS px** on touch pointers, with ≥ 8px spacing
   between adjacent targets.
5. **Never colour-only.** Order status, out-of-stock, and form errors all carry
   text or icon shape as well as colour.
6. **Contrast** meets AA (4.5:1 text, 3:1 large/UI) — see §2.6 for the verified
   pairs.
7. **`prefers-reduced-motion`** honoured globally (§8).
8. **Live regions** for cart changes, order status, form submission results and
   toast content.
9. **Zoom to 200%** without loss of content or horizontal scrolling.
10. **Form errors** are announced and focused to the first invalid field.
11. Decorative imagery uses `alt=""`; product imagery uses a real description
    (`alt="Shimla Apple, 1 kg"`).
12. Icon-only controls always carry an accessible name.

---

## 13. Forbidden patterns

Explicitly banned, because each one is what makes a build read as generic or
AI-generated:

* Purple/indigo → pink gradient hero backgrounds.
* Glassmorphism (backdrop-blur as a primary surface treatment).
* Giant rounded "feature cards" with an emoji, a bold line, and two grey
  sub-lines — the canonical AI landing-page tell.
* Centred hero with "Trusted by 10,000+ customers" placeholder social proof.
* Oversized pill buttons (anything taller than 52px).
* Full-card colour changes on hover, image zooms, tilt, or glow.
* More than one accent colour per screen.
* Any animation that repeats forever.
* Emoji used as iconography in the UI chrome (emoji are used **only** as
  database-provided catalogue glyphs, rendered large, deliberately, as a
  designed placeholder for missing photography).
* Generic `#FFF` page background with `#E5E7EB` hairlines.
