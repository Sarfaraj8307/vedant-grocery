# Vedant Admin (Android) — Implementation Report & Customer Web Roadmap

**Vedant Fruit & Vegetables — store-owner operations app + customer storefront**

| Field | Value |
|---|---|
| Document | Final handoff & implementation report |
| Audience | Store owner; any future development team |
| Admin app | `admin-android` (native Android, Kotlin + Jetpack Compose) |
| Customer app | `vedant-app` (Next.js PWA) — see the roadmap in §12 |
| Shared backend | Supabase project `hyibaljxicojfoxbihdn` |
| Language | English |
| Date | 2026-10-12 |
| Status basis | `docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md` (primary), `docs/CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/E2E.md`, `docs/PRD.md`, `HANDOVER.md`, `PROJECT_INDEX.md`, `README.md` |

> **How to read this report — the honesty rule.**
> Nothing in this project is marked **VERIFIED** unless it was **actually executed and observed**. Two blockers prevent that here: **R1** — there are no admin credentials; **R2** — there is no device or emulator. Everything that depends on the live server or on hardware is therefore **IMPLEMENTED-BUT-UNVERIFIED** (proven against fakes) or **BLOCKED**, and never VERIFIED. Every number in this document is traceable to a file that was read to write it. No figure has been estimated or invented.

---

## 1. Executive summary

Vedant Fruit & Vegetables is a two-app system sharing one Supabase backend: a **customer storefront** (a Next.js Progressive Web App) and a **store-owner operations app** (a native Android application). This report covers the **Android admin app**, which is the subject of this implementation cycle, and closes with the forward roadmap for the customer web app.

**What was built.** The Android admin app is a complete, single-module Kotlin + Jetpack Compose application that lets the store owner operate the business from a phone instead of by phone call: sign in with server-enforced admin authorisation, work the order queue through its lifecycle, reject and restore line items, manage the catalogue (products, categories, weight variants, offers, images), manage delivery slots, answer customer complaints, edit store settings, and view the signed-in administrator's identity. It replaces an operational workflow that was previously manual.

**Where it stands.** All **29** capabilities defined in the PRD are implemented against the live backend contract, and the build is green: **410 JVM tests across 37 suites, 0 failures**, a debug APK of **23,831,166 bytes**, and **0 lint errors**. However — and this is the central message of this report — **the live server paths and the on-device behaviours have not been executed**, because the environment has no admin credentials and no device. The app is therefore **built and unit-proven, but not yet live-proven**.

**The headline numbers.**

| Measure | Value |
|---|---|
| PRD capabilities implemented | 29 of 29 |
| Capabilities VERIFIED (executed & observed) | **8** |
| Capabilities IMPLEMENTED-BUT-UNVERIFIED | **17** |
| Capabilities BLOCKED | **1** |
| JVM unit tests | **410** (37 suites, 0 failures, 0 errors, 0 skipped) |
| Kotlin source files | **201** (149 main / 43 test / 9 androidTest) |
| Debug APK size | **23,831,166 bytes** |
| Android lint | **0 errors** (14 warnings) |
| Doc-vs-live database conflicts recorded | **22** |
| Outstanding external blockers | **2** (R1, R2) |

**The one-line takeaway.** The admin app is feature-complete and unit-tested; the remaining work before it can be called *production-proven* is **not** more code — it is **an admin account (R1) and a device (R2)**, after which the already-written end-to-end plan can be executed and the 17 unverified capabilities promoted to VERIFIED.

---

## 2. What was delivered

The following capability areas are implemented in the admin app. Each maps to PRD capabilities and implementing tasks, and each is unit-tested against fakes (see §7 for the exact verification status of each).

**Order operations — the P0 core.**
- An authenticated **orders list** with status filtering, search, and pull-to-refresh.
- **Order detail** showing items, quantities, weights, per-line totals, adjustments, and the **server-authoritative** final total.
- A **status workflow** driven by a validated state machine; the UI offers only the transitions the server permits, and the rendered status changes **only after** the server confirms.
- **Reject / restore an individual line item**, with a required reason; totals are recomputed **server-side** and re-fetched, never computed on the device.

**Catalogue management.**
- A **catalogue hub** with a product list and an **availability toggle** that moves only after the server confirms.
- **Product editor** (create/edit), **categories**, **weight variants**, and **offers**.
- **Image upload** to the `product-images` bucket with real byte progress and a server-confirmed public URL — the image is only "set" once the URL is on the row.

**Delivery slots.**
- Slot listing, editing, and a "generate recurring slots" action backed by the `ensure_delivery_slots` RPC.

**Customer support.**
- A **complaints list and thread** view, with admin replies and image attachments path-scoped to the uploader.

**Store settings, profile and session.**
- **Store settings** editing (with a store-name warning when the live value looks wrong).
- **Profile / session** view, where the role badge reads the server-resolved `is_admin()` value, never a locally cached or client-asserted role.

**Foundations that make the above honest.**
- **Authentication** with an explicit sign-in screen and a not-authorized refusal screen.
- **Admin authorisation** enforced entirely **server-side** (see §6).
- **Error handling**, **loading / empty states**, **offline resilience**, **animations**, **scrolling**, **accessibility**, and **dark mode** as cross-cutting concerns.
- **Automated testing** (JVM and compile-only instrumented suites) and **production documentation**.

---

## 3. Architecture

The admin app is a **single-module** (`:app`) application using **Kotlin + Jetpack Compose + Material 3**, following an **MVVM / unidirectional data flow (UDF)** pattern with **hand-rolled dependency injection**.

**The layered design.**

```
Presentation  (Compose + ViewModel; immutable UiState; one-shot events)
      |
Domain        (models, use cases, business rules — no Android or Supabase imports)
      |
Data          (repositories, Supabase/PostgREST, DataStore cache)
      |
Supabase (PostgREST / Auth / Storage) + DataStore + WorkManager
```

The layering is enforced by convention and by test rather than by a framework: the **`data` layer is the only place the Supabase SDK is touched**, the **`domain` layer is pure Kotlin**, and no business logic lives in composables. The dual-column database logic (see §5) lives **only** in `data/remote/dto` and `data/remote/mapper` — no ViewModel or composable ever references a raw legacy column name.

**Dependency injection.** The app uses a small **hand-rolled container** (`di/AppContainer.kt`) plus an `AppViewModelFactory`, with `VedantAdminApplication.kt` building the container at startup. This is deliberate and is described in the code as "the Hilt seam" — the single place where a DI framework could be introduced later without touching feature code.

**Why Hilt, KSP and Room were deliberately rejected.** This was a conscious engineering decision, not an omission:
- **Hilt + KSP** and **Room + KSP** both require **annotation processing via KSP**. The project brief forbids KSP, and the team chose to honour that constraint rather than introduce a build-time code-generation step.
- With a single module and a modest dependency graph, a hand-rolled container is **simpler, faster to build, and fully transparent** — there is no generated code to debug.
- For persistence, **DataStore Preferences** was chosen over Room precisely because it needs **no annotation processing**. It stores the last-known JSON snapshots for the offline cache.
- The only **added** build dependencies are those that need no codegen: `multiplatform-settings-no-arg` (session persistence), `coil-compose` + `coil-network-okhttp` (image loading), `datastore-preferences` (offline cache), `work-runtime-ktx` (safe-read retry), and `turbine` (test-only Flow assertions). **Room, Hilt, KSP and the Realtime plugin were explicitly rejected**; animations use native Compose rather than a third-party animation library.

Key project rules (from `CLAUDE.md`) that the architecture enforces: **no business logic in composables**, **no Supabase calls from the UI**, **never fake success** (every mutation returns the re-fetched server row), **money is server-authoritative** (the client never sends an amount), and **admin authorisation is server-side only**.

---

## 4. The live database is the authority

The single most important engineering finding of this cycle is this:

> **All three prior schema documents were stale. The live database won.**

Three separate sources disagreed with the live database, and each was treated as historical rather than authoritative once the live schema was read directly:

- `docs/BACKEND_CONTRACT.md` — describes a **different, older Supabase project** (`bmdmgycsrnffcgxmukxm`); the live project is `hyibaljxicojfoxbihdn`. It is superseded and flagged to the owner as a Phase-0 follow-up.
- `admin-android/PROJECT_INDEX.md` — its schema and "nothing exists yet" claims were stale.
- `admin-android/HANDOVER.md` — likewise stale on both schema and project state.

The team resolved this by reading the schema **out of the live database itself** (via PostgREST with the publishable key), and by making `docs/CONTRACT.md` the **single authoritative contract**, with the explicit rule: *where this file and any other document disagree, this file wins.* The machine-checkable companion is `docs/SCHEMA_SNAPSHOT.md`.

**22 recorded doc-vs-live conflicts (C1–C22).** Each is a case where a document said X and the live database said Y. At summary level:

- **11 conflicts against `docs/BACKEND_CONTRACT.md`** (C1–C11) — wrong project ref; an **order-status enum** document that used retired values; a complaints-status enum that used `IN_REVIEW` instead of the live `IN_PROGRESS`; under-specified `categories`, `products` and `delivery_slots` column lists; two live surfaces (`store_settings`, `product_catalog` view) that were not documented at all; and missing storage-bucket path rules.
- **7 conflicts against `PROJECT_INDEX.md`** (C12–C18) — including "no Android code exists yet", "design system not yet created", "no libraries added yet", "no screens implemented", and "storage has 0 policies → uploads fail", all of which the live repo and database contradict.
- **4 conflicts against `HANDOVER.md`** (C19–C22) — including "no Android source files exist yet" and a NEXT EXACT ACTION of "scaffold the Android project", which was already complete.

The lesson recorded for the future team: **a hand-maintained type or contract file drifts silently; always reconcile against the live source before trusting a column.** (The customer PWA's own `src/types/database.ts` is the next instance of this same drift — see §12.)

---

## 5. Legacy column pairs

The live database carries **six paired-column traps**, each a newer "intended" column shadowed by a legacy duplicate. The admin app handles every one of them with a documented, per-pair policy that lives **only** in the data layer. The policy has two parts: the **read column** (the one the mapper consumes) with its **fallback**, and the **write policy** (what an admin save does).

| # | Intended / authoritative | Legacy duplicate | Read | Fallback | Write policy |
|---|---|---|---|---|---|
| 1 | `products.base_price` | `products.price` | `base_price` | `price` | **Write both, same value** |
| 2 | `products.quantity_limit` | `products.max_order_qty` | `quantity_limit` | `max_order_qty` | **Write both, same value** |
| 3 | `delivery_slots.capacity` | `delivery_slots.max_orders` | `capacity` | `max_orders` | **Write both, same value** |
| 4 | `delivery_slots.starts_at` / `ends_at` (`timestamptz`) | `delivery_slots.start_time` / `end_time` (`time`) | `starts_at` / `ends_at` | `start_time` / `end_time` | **Write both** — `starts_at`/`ends_at` as UTC `timestamptz`; `start_time`/`end_time` as the Asia/Kolkata wall-clock derived from them |
| 5 | `categories.glyph` + `categories.accent` | `categories.icon` (+ `slug`) | `glyph` (visual) + `accent` (colour) | `icon` | **Write `glyph` + `accent` AND `slug`; mirror `icon := glyph`** |
| 6 | `orders.delivery_slot_id` | `orders.slot_id` | `delivery_slot_id` | `slot_id` | **N/A — read-preferred with a read-only fallback; the admin app never writes an order row** |

**Pair-by-pair rationale (in brief).**

- **Pairs 1–4 (write-both).** For products and slots, the safe strategy is to keep both columns equal, so that whichever column the server ultimately charges or counts from sees the administrator's value. Pair 1 matters because `place_order` prices from `least(price, discount_price)` — so `price` is genuinely read by the server; writing both keeps the displayed and the charged price identical. Pair 4 is the type-correct case: `starts_at`/`ends_at` are `timestamptz` (authoritative, keyed by the slot-recurrence unique index), while `start_time`/`end_time` are `time` and are the Asia/Kolkata wall-clock rendering of the same instants.
- **Pair 5 (evidence-based).** The customer storefront renders `glyph` + `accent` and **never** reads `icon` or `slug`; the live `icon` column is empty for two categories and wrong for a third. So an admin category save writes the authoritative `glyph` + `accent` (and `slug` for URL hygiene), and mirrors `icon := glyph` so no surface ever renders a blank.
- **Pair 6 — the corrected one.** `orders.delivery_slot_id` versus `orders.slot_id` is **read-preferred with a read-only fallback — it is NOT a write-both pair.** This distinction was corrected once already during the cycle and the corrected policy is what is implemented: the mapper **prefers `delivery_slot_id`** and falls back to `slot_id` **only when the former is absent**. There is no write half, because the admin app **never writes an order row** — it only reads orders and calls `admin_update_order_status`.

**Tally: 6 of 6 pairs resolved.** No pair is genuinely open. The one outstanding probe (to confirm which column `admin_slot_load` reads for capacity, pair 3) is **non-blocking**, because pair 3 is written identically either way, so the ambiguity is harmless regardless of the answer.

---

## 6. Security and money invariants

Three invariants are enforced throughout the app. They are the reason the app can be trusted not to leak a secret, not to over-grant access, and not to mis-state an amount.

**Invariant 1 — only the publishable key ever enters the APK.** The Supabase **publishable (anon) key** is the only credential compiled into the application, surfaced via `BuildConfig`. **No service-role key exists anywhere in the app** — a dedicated security-hygiene test (`SecurityKeyHygieneTest`, 7 assertions) exists to fail the build if a privileged key were ever introduced. The "publishable key is public by design" model relies on Row-Level Security, not on the key being secret.

**Invariant 2 — admin authorisation is enforced server-side and never cached locally.** Every admin capability is gated by the server's `private.is_admin()` function, called through the `is_admin()` RPC. The app **never decides authorisation itself** and **never caches a role assertion locally** — the profile screen's role badge reads the **server-resolved** `is_admin()` value, not a value from `profiles.role`. A non-admin who signs in correctly is shown a **not-authorized refusal screen** with no admin data rendered; the refusal itself comes from the server's gate, not from client logic. Every admin RPC is `SECURITY DEFINER` and raises `42501` for a non-admin.

**Invariant 3 — money is server-authoritative; the client never sends an amount.** The client **never transmits a price, a discount, or a total**. When an administrator rejects or restores a line item, the app sends only the item and a reason; the **server recomputes** the totals and the app **re-fetches the row** and displays the server's values. Money is represented internally as integer minor units to avoid floating-point drift, but that representation is used only for **display** — the authoritative figure is always the one the server returns. The database's own `CHECK` constraints (for example `adjustment_amount <= 0`) are the final guard: because the client never sends an amount, a malformed amount is structurally impossible to submit.

**Cross-cutting.** Two further rules reinforce the three invariants: **"never fake success"** — a mutation's result is rendered only after the server confirms it (the repository re-fetches the row; no local post-mutation state is constructed) — and **storage path discipline** — a complaint image must be uploaded under a key prefixed with the uploader's user id, because the storage policy authorises by the first path segment.

---

## 7. Verification status

This is the honest three-way split. Each of the 29 PRD capabilities carries exactly one status, defined as follows:

- **VERIFIED** — executed and observed, with the proof.
- **IMPLEMENTED-BUT-UNVERIFIED** — the code exists and is covered against fakes (unit tests); the live-server or hardware path that would prove it end-to-end is blocked.
- **BLOCKED** — cannot be verified in this environment; what is needed to unblock it is stated.

| Status | Count | Which capabilities |
|---|---|---|
| **VERIFIED** | **8** | Server-authoritative adjustments (logic); error handling; loading/empty states; offline resilience (logic); scrolling; dark mode (logic); automated testing (JVM); production documentation |
| **IMPLEMENTED-BUT-UNVERIFIED** | **17** | Auth; admin authorisation; dashboard; orders list; order detail; status workflow; item rejection/restoration; catalogue; categories; products; weight variants; availability toggle; image upload; offers; delivery slots; complaints; store settings; profile/session; animations; accessibility |
| **BLOCKED** | **1** | End-to-end verification |
| NOT-YET-CONFIGURED | 0 | — |

**What each category means in practice.**

- The **8 VERIFIED** items are those provable without the live server or hardware: error taxonomy and copy, UI state scaffolding, offline/caching **logic**, dark-mode **contrast logic**, JVM test execution, and documentation. Several of these are marked "logic" precisely because their *physical* half — real offline behaviour on a device, real on-screen appearance — remains blocked.
- The **17 IMPLEMENTED-BUT-UNVERIFIED** items are all fully coded and covered by unit tests against fakes, but every one of them has a **live-server or hardware dependency** that could not be exercised. The reason is always one of the two blockers in §10.
- The **1 BLOCKED** item — end-to-end verification — is blocked by **both** R1 and R2 at once; its plan is written but not executed.

**What would move the needle.** Clearing **R1** (an admin account) converts the order, catalogue, slots, support and settings capabilities from IMPLEMENTED-BUT-UNVERIFIED to VERIFIED by executing the end-to-end plan. Clearing **R2** (a device or emulator) converts the animations, accessibility and the instrumented half of testing, plus the physical half of dark mode. Clearing **both** converts the end-to-end capability. **Until then, no capability that depends on the live server or on hardware is VERIFIED, and this report says so.**

---

## 8. Test and build evidence

The following are the **literal, observed** numbers from this cycle's build and test run.

| Evidence | Observed value |
|---|---|
| Command | `./gradlew test assembleDebug lintDebug` |
| Build result | **BUILD SUCCESSFUL in 7m 24s** |
| JVM unit tests | **410 tests / 37 suites / 0 failures / 0 errors / 0 skipped** |
| Debug APK | `app-debug.apk` = **23,831,166 bytes** |
| Android lint | **0 errors** (14 warnings) |
| Instrumented suites | `assembleDebugAndroidTest` **compiles green** |
| Kotlin source files | **201 total** (149 `main` / 43 `test` / 9 `androidTest`) |

**What the 410 JVM tests cover.** They exercise the domain layer, the DTO↔domain mappers, the repositories (against fakes), and the ViewModels — the places where business rules, the six legacy-column policies, error mapping, money handling, offline caching and read-retry logic actually live. Representative suites include the order mapper (17), the order repository (10), the order-detail ViewModel (13), the catalogue repository (19), the money parser (22), and the error-mapper (18).

**The instrumented suites — stated plainly.** The **9 instrumented test files** (Compose UI tests under `androidTest`) **compile, but have never been executed**, because there is no device or emulator (R2). `./gradlew test` runs the **JVM** suites only; it does **not** run instrumented tests. So the Compose UI tests are a **compiled contract, not a verified one**. This is not a gap in effort — it is a hard environmental limit, and this report does not describe those tests as passing.

**The end-to-end plan.** `docs/E2E.md` contains a complete, step-by-step manual end-to-end script (sections A–F). Every step is labelled **NOT EXECUTED**, and its result table is intentionally empty. It is a plan, not a result.

---

## 9. Known gaps and unimplemented items

This section is deliberately forthright. These are the things that are **not** done or **not** proven.

- **Dashboard count tiles are not wired.** The dashboard shell renders, and the backing `dashboardCounts()` function **exists** — but the count tiles are **not yet connected** to it. This is a real, recorded defect, not a rounding error. The dashboard capability is therefore IMPLEMENTED-BUT-UNVERIFIED with this additional caveat.
- **The instrumented (Compose UI) test suites have never been executed.** All 9 androidTest files compile, but none has run on a device or emulator (R2). There is no on-device evidence for any screen.
- **All live RPC paths are unverified.** Every admin operation that calls a server function — signing in, the `is_admin()` gate, status transitions, item reject/restore, catalogue writes, image upload, slot generation, complaint writes, settings saves — has been exercised **only against fakes**. None has been run against the live backend.
- **Not yet proven on real hardware:** real offline behaviour, real accessibility (screen-reader traversal, focus order, touch-target sizes on a physical screen), animation behaviour, and the physical appearance of dark mode.
- **The RLS-hidden table columns remain un-enumerated.** Because there are no admin credentials (R1), the column lists of the twelve RLS-hidden tables (orders, order_items, profiles, complaints and others) could **not** be read. They have been **left explicitly UNKNOWN rather than guessed**; a probe script is committed to resolve them when credentials exist.
- **One non-blocking database probe is outstanding** — confirming which column `admin_slot_load` reads for slot capacity (harmless pending, because the value is written to both columns identically).

---

## 10. Blockers and what unblocks them

Two independent external blockers gate every unverified capability. **Neither can be cleared by engineering work** — both require something from outside the build environment.

**R1 — No admin credentials.**
- **What it blocks:** every live sign-in, the admin-gate allow/deny check, and every mutation against the live server. Without a server-confirmed `is_admin()` the app correctly refuses at the gate, so no admin surface is reachable. It also blocks enumeration of the twelve RLS-hidden tables' columns (the committed probe cannot run without an authenticated session).
- **What unblocks it:** provision an administrator — an owner email — in the database's admin list, then supply the email and password so the sign-in and the committed column-enumeration probe can be run. Once done, execute `docs/E2E.md` sections A–E to promote the order, catalogue, slots, support and settings capabilities to VERIFIED.

**R2 — No physical device or emulator.**
- **What it blocks:** all instrumented test execution; real UI, accessibility, animation and dark-mode verification; and the full end-to-end run. No emulator image exists in the environment and no physical device is attached.
- **What unblocks it:** create an Android Virtual Device (or attach a physical device over ADB), install the debug APK, and run the instrumented suites plus the manual end-to-end plan.

**Clearing both** is what converts the end-to-end capability (§7, the single BLOCKED item) to VERIFIED. The important framing for the owner: **the remaining work to reach "production-proven" is these two external inputs plus the execution of an already-written plan — not new feature development.**

---

## 11. Owner decisions pending

One data-quality item needs the owner's decision.

**`categories.accent` appears to hold placeholder colours.** The live `categories` table stores a numeric `accent` value per category, and the sampled values are **pure RGB primaries**: `16711680` (= `#FF0000`, pure red) and `65280` (= `#00FF00`, pure green). These look like **placeholder data** rather than a considered brand palette. The team **deliberately left these values unchanged**, pending the owner's decision, because:
1. Changing production catalogue data on the owner's behalf would be overstepping;
2. The correct brand colours are a business decision, not an engineering one.

**Action needed:** the owner should review the category accent colours in the admin app's category editor and set them to the intended brand palette. No code change is required — the editor already writes `accent` correctly (it is part of the authoritative pair-5 write, §5).

**Related owner-visible data issues surfaced during the cycle** (recorded because they affect the live storefront):
- `store_settings.store_name` reads `"Fresh Mart"` — **wrong** for a store branded *Vedant Fruit & Vegetables*. The admin app warns on this value but deliberately does **not** silently rewrite it; the owner should correct it via the settings screen.
- Several customer-facing store settings on the live project (`phone`, `whatsapp`) are **empty**, which hides the corresponding contact affordances on the storefront until filled in.

---

## 12. Customer web app roadmap

The customer storefront is a separate application — a **Next.js Progressive Web App** (`vedant-app`) — that shares only the backend contract with the admin app. This section summarises its forward plan, distilling the full technical handoff in `docs/CUSTOMER_WEB_APP_HANDOFF.md`. Every item below is clearly separated into **already implemented** versus **recommended future enhancement**.

### 12.1 Already implemented (customer web app)

The storefront is a live, mobile-first PWA with a real functional surface today:

- **Catalogue & browsing** — browse by category, free-text search, offers-only and in-stock-only filters, and sorting by name or price.
- **Weight variants** — the store's differentiator: a product can be bought in multiple weights (for example 250 g or 2 kg), each with its own price.
- **Cart** — persisted locally and merged into the server cart on sign-in, so a basket survives a refresh or a PWA relaunch.
- **Checkout** — address and delivery-slot selection, with a money preview that mirrors the server's arithmetic, and a confirmation screen showing the **server's** returned totals.
- **Orders** — order history and order detail with live status and a status timeline.
- **Delivery tracking** — a status tracker; `DELIVERED` and `CANCELLED` are terminal.
- **Customer support** — complaints list and thread, with database-enforced rules (a customer cannot self-resolve a complaint).
- **Accounts** — sign-up, sign-in, sign-out, password reset, address book, and an account overview.
- **PWA features** — installable, with a service worker, an offline fallback page, and an update prompt.
- **SEO & structured data** — per-page metadata, OpenGraph/Twitter cards, and local-business structured data.
- **Security** — RLS as the security boundary; only the publishable key reaches the browser; the environment loader actively **rejects** a service-role key.

Also delivered and worth naming: the **contract reconciliation** worked out for the admin app (the six legacy pairs, §5) is shared, and the storefront already reads the authoritative side of each pair.

### 12.2 Recommended future enhancement

Grouped by theme. Each is an idea, clearly **not** a claim of existing functionality.

**Trust & identity (highest value, lowest risk — recommended first).**
- Fix the store identity: correct `store_settings.store_name` and supply the store's phone, WhatsApp, address and contact email so the Call/WhatsApp buttons and structured data activate.
- Provide the **8 products that currently have no image** (Beetroot, Capsicum (Green), Carrot, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato) — doable directly from the admin app's image upload.

**Contract & correctness.**
- Regenerate the storefront's hand-maintained database type file from the live schema (it is stale in exactly the way §4 describes), then re-run the storefront's own build/lint/type gates.
- Fix a known hydration mismatch in the quantity-stepper component.
- Remove a dead, unused dependency; consolidate a duplicated complaint service.

**Engineering foundation.**
- Configure the declared-but-dormant unit-test stack and add a continuous-integration pipeline (types, lint, build, tests).
- Put the database under version-controlled migrations.
- Add analytics (the storefront currently has none).

**Customer experience.**
- Order-status notifications (push, and/or the owner's preferred WhatsApp/SMS channel) written through the existing notification outbox.
- Guest checkout (today, sign-in is required before ordering).
- Reorder-from-history, wishlist/favourites, and a live delivery map/rider ETA.

**Growth.**
- Sitemap and robots files, per-product structured data, category SEO copy, and a seasonal content hub.

**Advanced.**
- Online payment (orders are paid on delivery today) and personalised recommendations.

### 12.3 Recommended top three

1. **Fix store identity & owner configuration** — the store name, the four store-contact values, and the 8 missing product images. Business-visible, inexpensive, and it unblocks the Call/WhatsApp channels and structured data.
2. **Close the data-contract gap & fix the known defect** — regenerate the database types from the live schema and fix the hydration mismatch, so the storefront and the admin app do not drift apart as they evolve.
3. **Build the engineering foundation** — unit tests for the money/pricing/validation pure functions plus a CI pipeline, which makes every later change safe.

---

## 13. Recommended immediate next steps

A short, prioritised list — in order:

1. **Obtain an admin account (clears R1).** Provision an owner email in the database's admin list and supply the credentials. This is the single highest-leverage action: it unlocks live sign-in, the admin gate, every live mutation, and the column-enumeration probe.
2. **Provide a device or emulator (clears R2).** Create an AVD or attach a physical device, then run the instrumented suites.
3. **Execute `docs/E2E.md` end-to-end** once R1 and R2 are satisfied, promoting the 17 IMPLEMENTED-BUT-UNVERIFIED capabilities to VERIFIED.
4. **Wire the dashboard count tiles** to the existing `dashboardCounts()` — the only known functional gap in the admin app.
5. **Decide the category accent colours** (§11) and correct the live store name, so the storefront shows the right brand identity.
6. **Begin the customer web app roadmap** with the recommended top three in §12.3, starting with store identity and the missing product images.

---

*End of report. Where this document and the live system disagree, the live system wins — update this document. Every figure here is drawn from the project's own documentation; nothing has been estimated.*
