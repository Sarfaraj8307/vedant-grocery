# Android App Build Prompt — Vedant Fruit & Vegetables

> Paste this whole file into Kilo Code (or any coding agent) as the task. It is self-contained.

## Role

You are a senior Android engineer. Build a native Android customer app (Kotlin, Jetpack Compose, Material 3) for **Vedant Fruit & Vegetables**, a local grocery that sells weighed produce for same-day delivery in India. The web app already exists in this repository (Next.js + Supabase). The Android app must use the **same Supabase project and the same database contract**; it must not invent a second backend.

## Source of truth

- Database: Supabase project `hyibaljxicojfoxbihdn`. Read tables as documented in `docs/API_CONTRACT.md`, `docs/DATABASE_ARCHITECTURE.md`, `docs/BACKEND_CONTRACT.md`.
- Web app reference: `src/` in this repo. Mirror its behaviour, pricing rules (`src/lib/pricing.ts`, `src/lib/money.ts`) and copy.
- Design reference: `docs/DESIGN_SYSTEM.md` and `src/app/globals.css` (colour and type tokens). Use these exact brand colours:
  - Leaf green (primary): `#2A6B46` (600), `#1F5537` (700), `#DFEEE2` (100)
  - Harvest amber (accent): `#DE8E18`, `#FDEBDB`
  - Paper background: `#FDFCF7`; ink `#1A2E1A`; muted ink `#5C6B52`; line `#E8DDD0`
  - Berry (offers only, never errors): `#A93A63`
- Never ship the Supabase **service_role** key. Only the publishable key (`sb_publishable_…`) goes in the app.

## Product rules (must match the web app)

1. **Weights are denominations.** Each product has rows in `product_weights` (`label`, `grams`, `price`, `is_available`). Show them as a chip row (for example 250 g, 500 g, 1 kg). Price is per denomination, not per kg. Example: Lemon (Nimbu) is ₹25 / ₹50 / ₹100 for 250 g / 500 g / 1 kg.
2. **Prices in paise.** Store and compute money as integers in paise, format as INR. Never use floats for totals.
3. **Offers.** `offers` is time-windowed. Use `least(price, discount_price)` for the shown price, as the web does.
4. **Delivery.** Free delivery over the threshold in `store_settings`; otherwise the charge applies. Read both from the database, do not hard-code.
5. **Orders.** Place orders only through the `place_order` RPC with a client-generated `request_id` (idempotency). Never insert into `orders` directly.
6. **Order item quantity column is `qty`** (not `quantity`). Cart uses `cart_items.quantity`.
7. **Notifications.** Read `notification_outbox` where `audience = 'customer'` and `user_id = auth.uid()`. Mark read by updating `read_at`.
8. **Images.** `products.image_url` may be null or dead. If null or the load fails, show the product's `glyph` emoji on a tinted `accent` background. Never show a broken image.
9. **Auth.** Email/password via Supabase Auth. Google sign-in is disabled; do not add it.
10. **Account deletion** is a request flow only (no service role in the client).

## Screens (in order of priority)

1. **Splash / brand intro** — fast, under 1 s, leaf green.
2. **Home** — hero card ("Today's produce, weighed the way you want it"), category rail, today's offers, "Ordered most this week" rail, full shelf grid.
3. **Categories and product list** — filters, search entry.
4. **Product detail** — image (photo or glyph), denomination chips, price, quantity stepper, "Add to basket".
5. **Search** — debounced, recent searches.
6. **Basket** — line items with denomination, quantity edit, free-delivery progress bar, totals.
7. **Checkout** — address picker, delivery slot picker, place order via RPC, idempotent retry.
8. **Orders** — list and detail with status timeline (use `order_status_history`), reorder button.
9. **Notifications** — inbox from `notification_outbox`, mark all read.
10. **Account** — profile, addresses, sign-in/sign-up, sign-out.

## Experience requirements

- **Speed:** cold start to home under 2 s on a mid-range device; lists virtualised (`LazyColumn`/`LazyVerticalGrid`); images cached (Coil) with placeholders.
- **Motion:** shared-element transition from product card to detail; staggered reveal on home sections; spring-based quantity stepper; respect the system "reduce motion" setting.
- **Frames and depth:** rounded 18–24 dp cards, soft green-tinted shadows, glass-like sticky basket bar.
- **Accessibility:** 48 dp touch targets, TalkBack labels on every control, minimum 4.5:1 text contrast.
- **Offline:** show the last cached catalogue and basket; clearly mark offline state.

## Technical constraints

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt, Coroutines + Flow.
- Supabase: `io.github.jan-tennert.supabase` (postgrest, auth, storage) or REST via Ktor. Keep the client in one module.
- Room for the local cache of catalogue and basket.
- Min SDK 26, target latest stable.
- Unit tests for price and money logic (paise rounding, `least(price, discount_price)`, free-delivery threshold) and for denomination pricing.
- Compose UI tests for the basket and checkout happy path.

## Deliverables

1. Gradle project in `android/` at the repository root.
2. `android/README.md` with setup steps and the env keys needed (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`).
3. Passing `./gradlew test` and `./gradlew assembleDebug`.
4. A short list of anything you could not verify against the live database, stated plainly.

## Do not

- Do not change the Supabase schema. Report any mismatch instead.
- Do not add a service_role key, analytics SDKs, or ads.
- Do not add features beyond the screens above.
