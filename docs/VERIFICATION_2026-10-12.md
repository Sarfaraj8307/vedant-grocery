# Verification Report (Re-run) — Vedant Fruit & Vegetables

> **Date:** 2026-10-12 · **Author:** 严过关 (Yan), QA Engineer
> **Method:** real-browser E2E via Playwright 1.63 + Chromium, zero trust in prior docs
> **Target:** Next.js 16.3.8 dev server on `http://localhost:3100` (Playwright/harness-owned)
> **Result:** **71 passed · 2 failed · 1 skipped** — the 2 failures are the same defect (see §5)
> **Verdict:** ⚠️ **PASS WITH DEFECTS** — every route and every core flow works; one real (cosmetic) defect confirmed on the home page.

---

## 1. What was run

| Artifact | Path |
|---|---|
| Playwright config | `playwright.config.ts` |
| Test specs | `tests/routes.spec.ts`, `tests/flows.spec.ts`, `tests/hydration.spec.ts` |
| Shared helpers / fixtures | `tests/helpers.ts`, `tests/fixtures.ts`, `tests/catalogue.ts` |
| Machine-readable results | `.logs/verify/results.json` |
| Console log of the run | `.logs/verify/run-full.log` |
| Screenshots (78 PNG) | `.logs/verify/route-*.png`, `.logs/verify/flow-*.png`, `.logs/verify/hydration-*.png` |

Run command (single command, harness owns the server):

```bash
node node_modules/next/dist/bin/next dev --port 3100   # started by the QA harness
node node_modules/@playwright/test/cli.js test         # 74 tests, 2 projects, workers:1
```

Two viewports: **mobile 390×844** (primary) and **desktop 1280×800** (spot-check).

### Environment notes (important for reproducibility)

1. **The externally-started dev server does not survive between agent turns.** An earlier run failed with a cascade of `ECONNREFUSED`/`ETIMEDOUT` because the server vanished mid-run. The suite now runs against a server started by the harness on **port 3100** and kept alive for the whole run.
2. **Playwright's own `webServer` block was tried and rejected.** It kept the server alive, but its teardown **hangs on Windows** — tests all pass and the port is released, yet the runner never exits, so the JSON reporter's `onEnd` never fires and `results.json` is never written (reproduced with a single test). The harness-owned server avoids this.
3. **Remote images are masked in the browser during the run.** Catalogue photography is served through the Next.js image optimizer (`/_next/image?url=https://images.unsplash.com/…`); this sandbox's egress to Unsplash is slow/unreliable, and the resulting flood of failed upstream image requests destabilised the dev server (it died after the 24-image `/categories` page). The fixture (`tests/fixtures.ts`) fulfils every optimizer request with a 1×1 PNG so the suite tests **app behaviour**, not the sandbox's image egress. Image URLs were verified separately (§8).
4. A sandbox bulk-delete guard blocks Playwright's `rm -rf` of a populated output dir, so `outputDir` is unique per run.

---

## 2. Headline verdict

**The application works.** All 24 application routes render their expected content, all 8 core user flows pass, assets (manifest/robots/sitemap) are correct, and guarded routes correctly bounce guests to sign-in. The only defect found is the **home-page React hydration mismatch** (§5), which is **cosmetic** (console error + a one-frame flash for returning customers) — it does **not** break interactivity.

The previously-reported bugs were **not reproducible** in this run:
- **Bug 2 (dead Unsplash URLs) — CLEARED.** All 15 unique image URLs return HTTP 200; 8 products simply have `image_url = NULL` and use the designed glyph fallback (§8).
- **Bug 1 (`order_items.qty`) — NOT EXERCISABLE.** `/orders/[id]` is auth-gated and there are no orders/sessions, so the detail query could not be reached in this run. Status unchanged (still a latent risk); see §7.

---

## 3. Route coverage (27 routes)

Raw status = status of the initial document request (redirects **not** followed). "Console errors" = `console.error` + uncaught exceptions, excluding Next.js dev advisories. All 27 routes are **PASS** in both projects.

| # | Route | Raw status | Final URL | Expected content | Console errors |
|---|---|---|---|---|---|
| 1 | `/` | 200 | `/` | ✅ hero, category rail, rate board, shelf grid | none |
| 2 | `/about` | 200 | `/about` | ✅ story + how-it-works | none |
| 3 | `/contact` | 200 | `/contact` | ✅ contact panel | none |
| 4 | `/offers` | 200 | `/offers` | ✅ "on offer right now", offer grid | none |
| 5 | `/categories` | 200 | `/categories` | ✅ filters + 24 products | none |
| 6 | `/categories/[id]` | 200 | `/categories/<fruits-uuid>` | ✅ category name + grid | none |
| 7 | `/search` | 200 | `/search` | ✅ popular searches | none |
| 8 | `/products/[id]` | 200 | `/products/<uuid>` | ✅ price, pack selector, related rail | none |
| 9 | `/cart` | 200 | `/cart` | ✅ "Your basket" (empty state) | none |
| 10 | `/checkout` | 200 | `/login?next=%2Fcheckout` | ✅ guard → sign-in | none |
| 11 | `/orders` | 200 | `/login?next=%2Forders` | ✅ guard → sign-in | none |
| 12 | `/orders/[id]` | 200 | `/login?next=%2Forders` | ✅ guard → sign-in | none |
| 13 | `/account` | 200 | `/login?next=%2Faccount` | ✅ guard → sign-in | none |
| 14 | `/account/addresses` | 200 | `/login?next=%2Faccount%2Faddresses` | ✅ guard → sign-in | none |
| 15 | `/account/reset-password` | 200 | `/forgot-password` | ✅ guard → forgot-password | none |
| 16 | `/notifications` | 200 | `/login?next=%2Fnotifications` | ✅ guard → sign-in | none |
| 17 | `/support` | 200 | `/login?next=%2Fsupport` | ✅ guard → sign-in | none |
| 18 | `/support/[id]` | 200 | `/login?next=%2Fsupport` | ✅ guard → sign-in | none |
| 19 | `/login` | 200 | `/login` | ✅ "Welcome back" form | none |
| 20 | `/signup` | 200 | `/signup` | ✅ create-account form | none |
| 21 | `/forgot-password` | 200 | `/forgot-password` | ✅ reset form | none |
| 22 | `/privacy` | 200 | `/privacy` | ✅ policy | none |
| 23 | `/terms` | 200 | `/terms` | ✅ terms | none |
| 24 | `/offline` | 200 | `/offline` | ✅ "You're offline" | none |
| 25 | `/manifest.webmanifest` | 200 | — | ✅ `application/manifest+json` | none |
| 26 | `/robots.txt` | 200 | — | ✅ `text/plain` | none |
| 27 | `/sitemap.xml` | 200 | — | ✅ `application/xml` | none |

**No error boundary and no 404 page rendered on any valid route.** Screenshots: `.logs/verify/route-<name>-<mobile|desktop>.png` (54 files).

> ⚠️ **Documented behaviour — guards are client-side redirects.** Guarded routes return **HTTP 200**, not 307/308. The 200 body carries
> `<meta id="__next-page-redirect" http-equiv="refresh" content="1;url=/login?next=%2Fcheckout">` plus a `NEXT_REDIRECT;…;307;` RSC digest, and the browser performs the redirect (~1 s meta-refresh). The lead's observation that `/checkout` returns 200 is **correct**; the guard is real and preserves `next`. Verified for `/checkout`, `/orders`, `/orders/[id]`, `/account`, `/account/addresses`, `/notifications`, `/support`, `/support/[id]`.

---

## 4. Core user flows (a–h)

All flows **PASS** on both mobile (390×844) and desktop (1280×800).

| Flow | Result | Evidence / notes |
|---|---|---|
| **a.** Home → category → product (variant selector) → add to cart | ✅ PASS | Category tile → `/categories/<uuid>` → first product → `/products/<uuid>`; pack radiogroup present with ≥1 option, selection toggles `aria-checked`; ADD pill morphs into the `− n +` stepper. `.logs/verify/flow-a-*.png` |
| **b.** Cart: line, inc/dec, remove, totals, free-delivery bar | ✅ PASS | Seeded line (₹30, qty 1): subtotal **₹30**, delivery **₹20**, total **₹50**; inc → ₹60/₹80; dec → ₹30; progressbar `aria-valuenow` present; remove → "Your basket is empty". `.logs/verify/flow-b-cart-*.png` |
| **c.** Cart persistence across reload | ✅ PASS | Line survives a full `page.reload()`; `localStorage['vedant.cart.v1']` retains the product id. |
| **d.** Search match + no-match | ✅ PASS | `?q=Banana` → results list + product link; `?q=zzzznosuchproduce` → "Nothing matched …" empty state. `.logs/verify/flow-d-search-{hit,empty}-*.png` |
| **e.** Offers page renders live offers | ✅ PASS | "Today's rate board", "N items are on offer right now", ≥1 product card. `.logs/verify/flow-e-offers-*.png` |
| **f.** Checkout as guest | ✅ PASS | `raw=200 clientRedirect=true final=/login?next=%2Fcheckout`. See §3 note. `.logs/verify/flow-f-checkout-guest-*.png` |
| **g.** Login/signup validation | ✅ PASS | Empty submit shows "Enter a valid email address", "Enter your password"; signup also "This is required" + "Please accept the terms to continue". No crash. `.logs/verify/flow-g-*.png` |
| **h.** 404 handling | ✅ PASS | Unknown route `/this-route-does-not-exist-12345` → **HTTP 404** + not-found page. Unknown product id → not-found page renders, but **HTTP 200** (soft 404 — see §6). `.logs/verify/flow-h-*.png` |

---

## 5. Defect — React hydration mismatch on the home page (CONFIRMED) — **P1**

**Status: CONFIRMED** (previously a suspect). Reproduced deterministically on both viewports.

- **Where:** `src/components/products/qty-stepper.tsx:125` (`<motion.div key="stepper">`), rendered via `RateBoard` in `src/components/home/home-sections.tsx:165` (stepper instance at `home-sections.tsx:211`), on `HomePage` (`src/app/page.tsx:77`).
- **Trigger:** a returning customer whose persisted basket already contains a product shown on the RateBoard (i.e. any repeat visitor who added an offer item). Reproduced by seeding `localStorage['vedant.cart.v1']` and loading `/`.
- **Exact error (uncaught, `pageerror`):**

```
Hydration failed because the server rendered HTML didn't match the client. As a result this
tree will be regenerated on the client. …
  …
    <QtyStepper product={{id:"97d06e…"}} weight={{id:"0e1eec…"}} size="sm">
      <div className="inline-fle…">
        <div
+         className="relative inline-flex min-w-[92px] select-none items-center justify-center…"
-         className="relative inline-flex min-w-[92px] select-none items-center justify-center…"
        >
          …
            <motion.div initial={{opacity:0,…}} animate={{opacity:1,scale:1}} …>
+             <div className="flex h-full w-full items-center justify-between" style={{opacity:1,transform:"none"}} …>
-             <button type="button" className="inline-flex h-full w-full items-center justify-center…"
-               aria-label="Add Carrot to basket" style={{opacity:"1",transform:"none"}}>
    at div (<anonymous>)
    at QtyStepper (src/components/products/qty-stepper.tsx:125:13)
    at <anonymous> (src\components\home\home-sections.tsx:211:19)
    at RateBoard (src\components\home\home-sections.tsx:165:18)
    at HomePage (src\app\page.tsx:77:11)
```

- **Root cause:** `useCartStore` (`src/stores/cart-store.ts:91`) uses Zustand `persist` with a **synchronous** `localStorage` backend. On the client's **first** render the store is already rehydrated (quantity > 0), so `useCartQuantity` returns > 0 and the client emits the stepper `<div>`; the server (no `localStorage`) emitted the `ADD` `<button>`. The `hydrating` flag in `cart-provider.tsx` only gates **server-cart** sync, not `localStorage` rehydration, so it does not gate this.
- **Impact:** React regenerates the affected subtree on the client — a visible one-frame flash for returning customers, plus a red console error on every visit for those users. **Interactivity is NOT broken** (see below).
- **Evidence:** `.logs/verify/hydration-home-mobile.png`, `.logs/verify/hydration-home-desktop.png`; failing test `tests/hydration.spec.ts` ("returning customer … hydrates cleanly") — 2 failures in the run.
- **Suggested fix (for the engineer, not applied):** gate the persisted cart behind a mounted/hydrated flag (e.g. render quantity 0 until `useEffect` has run, or set `skipHydration: true` and rehydrate in an effect) so the client's first render matches the server.

### Interactivity check — the mismatch is COSMETIC, not fatal

The companion test **"RateBoard stepper stays interactive after hydration"** **PASSES** on desktop: after the mismatch, the stepper shows the seeded quantity and a `+` tap increments it correctly. → **Do not treat this as breaking the UI**; severity is **P1** (console error + flash for every returning customer), not P0. *(The mobile variant of this check is skipped because the RateBoard stepper is `hidden` below the `sm` breakpoint — 1 skipped test.)*

---

## 6. Documented behaviours (not defects, but worth recording)

| # | Behaviour | Severity | Detail |
|---|---|---|---|
| B1 | Guarded routes return **HTTP 200 + client-side redirect** | P2 (SEO/observability) | `/checkout`, `/orders`, `/account`, `/notifications`, `/support` etc. return 200 with a `<meta http-equiv="refresh">` to `/login?next=…` (see §3). The guard works for users; the 200 status is unusual for a redirect and may confuse monitoring/SEO crawlers. |
| B2 | Unknown **product id** is a **soft 404** (HTTP 200) | P2 (SEO) | `/products/<unknown-uuid>` renders the not-found UI but returns **200**, not 404. (The unknown *route* correctly returns 404.) Likely a streaming artefact of `notFound()`; confirm whether it also occurs in a production build. |

---

## 7. Coverage limitations (honest scope)

- **`/orders/[id]` and `/support/[id]` detail renders were not exercised** — both are auth-gated and this run had no signed-in session. The prior P0 (`order_items.qty` vs `quantity`) therefore remains **unverified**, not fixed. Recommend a follow-up with a seeded test user + one order.
- **`/account/*`, `/notifications`, `/support`** were verified only to the extent of the guest guard (redirect target + `next` preservation), not their signed-in content.
- **Cart/checkout were verified as a guest** (localStorage cart, `place_order` not invoked — requires auth).
- **Images were masked in-browser** (see §1.3); image health was checked separately (§8).

---

## 8. Image verification (independent)

Probed the live `products.image_url` values directly (bypassing the browser):

- **24 active products.** 16 have a URL; **8 have `image_url = NULL`** — Beetroot, Carrot, Capsicum (Green), Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato (these render the designed glyph fallback, not a broken image).
- **All 15 unique URLs return HTTP 200** (0 unreachable). → The previously-reported "8 dead Unsplash URLs" (**Bug 2**) is **CLEARED**; the URLs are live. (The earlier `net::ERR_CONNECTION_TIMED_OUT` storm came from the dev-server image optimizer, not from dead URLs.)

---

## 9. Defect summary

| ID | Severity | Area | Summary | Evidence |
|---|---|---|---|---|
| D1 | **P1** | Home page | React hydration mismatch — `QtyStepper` (server `ADD` button vs client stepper) when the persisted cart already contains a RateBoard item. Console error on every visit for returning customers; cosmetic (interactivity intact). | §5 · `tests/hydration.spec.ts` (2 failures) · `.logs/verify/hydration-home-*.png` |
| D2 | P2 | Guards | Guarded routes return HTTP 200 + meta-refresh client redirect instead of 307. | §3 note · `results.json` `guard=` annotations |
| D3 | P2 | Product route | Unknown product id → soft 404 (HTTP 200). | §6 · `results.json` `soft-404=` annotation |
| — | — | Orders | Prior P0 `order_items.qty` — **not exercisable** this run (auth-gated, no data). | §7 |

---

## 10. Verdict

**PASS WITH DEFECTS.**

- Routes: **27/27 pass** (mobile + desktop).
- Flows: **8/8 pass** (mobile + desktop).
- Tests: **71 passed · 2 failed · 1 skipped**; both failures are the same **P1** hydration defect (D1), which is confirmed but cosmetic.
- No P0 defects found. Two **P2** behavioural notes (D2, D3) and one prior P0 that could not be reached.

The storefront is functionally sound for a guest/returning customer: browse → product → cart → search → offers → auth-guarded checkout all work end-to-end.
