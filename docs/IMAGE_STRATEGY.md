# Image & Asset Strategy

The catalogue currently ships **no photography**: `products.image_url` and
`categories.image_url` are `NULL` for all 23 rows, and `product-images` is an
empty public bucket. This document is how the app stays beautiful anyway, and
exactly how real photos drop in later with **no refactor**.

---

## 1. The abstraction

Product imagery never appears as a bare `<img src={product.image_url}>`. It goes
through one component:

```
src/components/products/ProductImage.tsx   ← the only image surface
src/lib/assets.ts                          ← URL resolution + sizing + blur
```

`ProductImage` accepts a `ProductImageSource`:

```ts
type ProductImageSource = {
  imageUrl: string | null;   // from products.image_url / categories.image_url
  glyph: string;             // database emoji, e.g. "🍎"
  accent: number;            // database decimal RGB, e.g. 16639968
  name: string;              // for alt text
};
```

It renders **one of two states**, and the decision is inside the component:

1. **Photography state** — `imageUrl` is set → `next/image` with responsive
   `sizes`, `fill`, a tinted plinth behind it for letterboxing, and the same
   blur placeholder used by the glyph state.
2. **Glyph state** (current production state) — `imageUrl` is `NULL` → a
   **designed plinth**: the `accent` RGB becomes a soft radial tint on
   `--surface-muted`, the catalogue `glyph` is set at ~44% of the plinth
   width, and a subtle elliptical `--shadow-produce` grounds it. This is not a
   broken-image box; it is a deliberate, consistent visual identity that looks
   intentional beside real photography.

Because the branch lives in one component, populating `products.image_url` for
a single product makes exactly that product switch to photography, live, with no
deploy of new client code.

### Placement contract

```ts
// src/lib/assets.ts
resolveProductImage(product)   // → CachedPublicUrlSource | GlyphSource
resolveCategoryImage(category) // → same
productImageAlt(product, weightLabel?) // → "Shimla Apple, 1 kg"
blurDataUrl(accent)            // → deterministic warm tint data-URI
```

Remote URLs are only accepted from a **known origin allowlist**
(`*.supabase.co` storage paths for the project). Anything else falls back to the
glyph state, so a bad/malicious URL in the database can never cause the app to
proxy arbitrary remote content into `next/image`.

---

## 2. Directory layout (for the owner's real assets)

```
public/
  images/
    brand/       logo.svg, logo-mark.svg, og-default.jpg, apple-touch-icon.png
    hero/        hero-produce.webp (static 3D fallback composition)
    categories/  fruits.webp, vegetables.webp, leafy-greens.webp, herbs-exotics.webp
    products/    shimla-apple.webp, tomato.webp, ...   (kebab-case, matches slug)
    icons/       pwa-192.png, pwa-512.png, maskable-512.png
  models/        (intentionally unused — 3D is authored from primitives, §DESIGN_SYSTEM 10)
```

The **preferred production path** is Supabase Storage:
upload to the public `product-images` bucket under
`products/<product-id>/<variant>.webp`, then set `products.image_url` to the
public object URL. That keeps asset management inside the same system the future
admin Android app will use, and means the owner never edits code to change a
photo.

---

## 3. Sizing & formats

| Slot | Intrinsic | Rendered sizes (`sizes`) | Notes |
|---|---|---|---|
| Product card | 512×512 | `(max-width:640px) 45vw, (max-width:1024px) 30vw, 240px` | 1:1, `object-cover` |
| Product detail | 1024×1024 | `(max-width:768px) 92vw, 520px` | 1:1 |
| Cart thumb | 128×128 | `64px` | 1:1 |
| Category tile | 512×512 | `(max-width:640px) 34vw, 180px` | 1:1 |
| Hero (static fallback) | 1600×1000 | `(max-width:1024px) 100vw, 720px` | 8:5 |
| OG image | 1200×630 | — | `display: none`, metadata only |

Rules:
* `next/image` handles AVIF/WebP negotiation; source uploads should be
  **WebP or AVIF, ≤ 160KB** for product images.
* Product imagery is 1:1 so the grid never shifts between glyph and photo
  states.
* `priority` is set **only** on the first 4 above-the-fold product images.
  Everything else is lazy.
* Every image has explicit width/height (or `fill` + an aspect-ratio wrapper) —
  a missing aspect ratio is a CLS bug and is treated as one.

---

## 4. Placeholder / blur

`blurDataUrl(accent)` deterministically derives a 8×8 warm tint from the
product's own `accent` colour, returned as a base64 data-URI. So:
* the blur is always brand-consistent,
* no per-image base64 payload is stored,
* the transition from placeholder → photo is a colour-matched fade, never a
  grey-box → photo pop.

---

## 5. What must NOT happen

* No hot-linking to remote/unreliable image hosts.
* No random low-quality stock photography dropped into the repo.
* No `<img>` bypassing `ProductImage` (except the brand logo, which is inline
  SVG).
* No emoji used as UI chrome — the catalogue glyph is *only* ever rendered
  through the plinth, at a deliberate size, as a designed state.
* No image that is critical to comprehension without a text alternative.
