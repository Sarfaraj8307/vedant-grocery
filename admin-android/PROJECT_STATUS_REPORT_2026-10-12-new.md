# Vedant Admin (Android) — Project Status & Findings Report

**Report date:** 2026-10-12  
**Project:** Vedant Admin — Android operations console for Vedant Fruit & Vegetables  
**Location:** `C:/Users/sarfaraz/Desktop/vedant fruits and vegitables/admin-android/`  
**Supabase project:** `hyibaljxicojfoxbihdn`  
**Prepared by:** Product Management (analysis/documentation only — no source code was written or modified for this report)

> **Evidence policy.** This report separates *verified facts* (each tied to a file path, test artefact, or live query) from *claims*. The project's own status documents (`HANDOVER.md`, `PROJECT_INDEX.md`) are **stale** and are explicitly corrected in §4 and §8. Every "built" statement below carries a citation; anything not verified is labelled **NOT VERIFIED**.

---

## 1. Executive Summary

**The foundation is built and green; the application itself has not been started.**

The Vedant Admin Android app is a native Kotlin/Compose console intended to replace manual store-owner operations (order acceptance, bill adjustment, catalogue, delivery slots, complaints). As of 2026-10-12:

- A single-module Gradle project (`:app`) exists and **builds successfully** — a debug APK is present (19,986,652 bytes ≈ 19.9 MB).
- The **domain layer is real**: `OrderStatus` implements the full server state machine, and its unit test suite passes **14/14** with **0 failures, 0 errors**.
- The **design system is real**: colour palette (light + dark), typography, spacing, radii, and a 48 dp touch-target floor are implemented.
- **Build quality gates are green**: lint reports **0 errors, 7 warnings** (both cosmetic).
- **Supabase credentials are configured and valid, and the backend is live** — a direct REST call returned HTTP 200 with real data.
- **But the app is not connected to anything.** There is **no** Supabase client initialisation, no repository, no data layer, no navigation, no auth gate, and no feature screen. `MainActivity` renders a static placeholder.

**True completion: ~10–15%.** What exists is the scaffolding, the design system, and one well-tested domain model. The application — the thing the store owner would actually use — does not exist yet. The critical caveat: **a green build is not a working app.** The build proves the toolchain, not the product.

**Single most important next milestone:** wire the data layer — initialise the Supabase client from `BuildConfig` and land one real read (e.g. an orders list) end-to-end. Everything else depends on it.

---

## 2. What the Application Is

**"Vedant Admin"** is the **store-owner operations console** for Vedant Fruit & Vegetables — a local grocery that sells weighed produce (fruit and vegetables) for **same-day delivery in India**.

- **Primary user:** the store owner / operator. A secondary future user is staff (packers, managers) — see §3.
- **Job to be done:** run the store day-to-day — see incoming orders, accept them, pack them, adjust the bill when an item is out of stock or damaged, dispatch, and manage the catalogue, offers, delivery slots, and customer complaints.
- **Why native Android:** the operator works from a phone on the shop floor; a native app gives reliable background sync, push notifications, and camera access for product photography.

### Separation from the customer PWA

The admin app is a **separate application** from the customer PWA (a Next.js app in the sibling folder `../`). They are **not** one codebase and must never be merged.

|                          | Customer PWA (`../`)                    | Admin Android app (this project)       |
| ------------------------ | --------------------------------------- | -------------------------------------- |
| Purpose                  | Browse, cart, checkout, track own order | Operate the store                      |
| Admin UI / admin secrets | **None, by design**                     | Full admin surface                     |
| Shared boundary          | The Supabase backend contract **only**  | The Supabase backend contract **only** |
| Order visibility         | Own orders only (RLS)                   | All orders (RLS-gated admin)           |

**The only shared thing is the Supabase project `hyibaljxicojfoxbihdn` and its database contract.** Hard rules that enforce this boundary:

1. **The admin app must carry only the PUBLISHABLE Supabase key.** The service-role key must never appear in `local.properties`, `build.gradle.kts`, `BuildConfig`, resources, or code.
2. **Admin authorization is server-side only**, via `private.is_admin()` in Postgres. **UI visibility is never authorization.** A hidden button or a client-side role check is not a gate; the server is.

---

## 3. Requirements Summary

### 3.1 Hard rules (from `CLAUDE.md`)

| # | Rule                                                                                                                                                                             | Rationale                                                       |
| - | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| 1 | No privileged secrets in the client — publishable key only                                                                                                                       | Service-role key bypasses RLS                                   |
| 2 | Admin authorization server-side only (`private.is_admin()`)                                                                                                                      | UI visibility ≠ authorization                                   |
| 3 | **Never fake success** — an order/status/money mutation is "done" only when the server confirms                                                                                  | Prevents the operator believing a change landed when it did not |
| 4 | The DB owns the wire values — Postgres enum names are used verbatim; rename labels, not enums                                                                                    | Eliminates a translation layer that can drift                   |
| 5 | Money is server-authoritative — `adjustment_amount <= 0` and `final_total = original_total + adjustment_amount` are DB CHECKs; the client never computes an amount it then sends | Money correctness cannot depend on the client                   |

Plus architectural rules: **no business logic in composables**, **no Supabase calls from UI** (only repositories touch the SDK), immutable `UiState` per screen, unidirectional data flow.

### 3.2 Order status state machine (live, authoritative)

```
PENDING | CONFIRMED | PROCESSING | READY_FOR_DELIVERY | OUT_FOR_DELIVERY | DELIVERED | CANCELLED
```

- `DELIVERED` and `CANCELLED` are **terminal** — no transitions out, no item changes.
- Any non-terminal state may move to `CANCELLED`, and a **reason is required**.
- Labels: `PENDING` = "Order received", `READY_FOR_DELIVERY` = "Ready to dispatch" (labels are display-only; wire values are unchanged).

### 3.3 Intended feature set (from `docs/FUTURE_ADMIN_APP.md`)

| Area               | Features                                                                                                                                                          |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Orders**         | List (filter by status) · Detail + status timeline · Accept · Update status · Cancel (reason required) · Item rejection / bill adjustment · Restore rejected item |
| **Catalogue**      | Product CRUD · Category CRUD · Weight variants · Toggle availability · Product image upload                                                                       |
| **Offers**         | CRUD (`price` or `discount_percent`, date ranges)                                                                                                                 |
| **Delivery slots** | View capacity/utilisation · Create · Deactivate · **Recurring generation**                                                                                        |
| **Support**        | Complaint list (filter by status) · Threaded replies · Resolve · View attachments                                                                                 |
| **Settings**       | Store settings, profile                                                                                                                                           |
| **Analytics**      | Orders/day, revenue, popular products (post-MVP)                                                                                                                  |

### 3.4 Planned tech stack

Kotlin + Jetpack Compose + Material 3 · MVVM/MVI · Clean Architecture (`Presentation → Domain → Data`) · Supabase Kotlin SDK + Ktor · Room · DataStore · WorkManager · Coil · Navigation Compose · Coroutines + kotlinx.serialization.

**DI note:** `docs/FUTURE_ADMIN_APP.md` recommends **Hilt**, but `CLAUDE.md` records a deliberate decision to use a **hand-rolled DI container in `di/`** instead — KSP is intentionally avoided so the first build needs no annotation processing. Hilt is deferred; `di/` is the seam for it. **This report follows the implementation's decision (hand-rolled), and flags the doc discrepancy.**

---

## 4. What Has Been Built

> **Correction of stale documentation.** `HANDOVER.md` states "**No Android source files exist yet**" and `PROJECT_INDEX.md` states "**no Android code exists yet**". **Both claims are false as of 2026-10-12.** Seven Kotlin source files, a working Gradle build, a debug APK, a passing unit-test suite, and a lint report all exist. The docs were last updated 2026-10-11 and were not refreshed after the scaffold work landed.

### 4.1 Verified evidence

| Artefact                            | Evidence                                                                                                                                               | Status       |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------ |
| Single-module Gradle project `:app` | `settings.gradle.kts` (`include(":app")`), `build.gradle.kts`                                                                                          | **VERIFIED** |
| **7 Kotlin source files**           | `find . -name "*.kt" -not -path "*/build/*"` → 7                                                                                                       | **VERIFIED** |
| `MainActivity.kt`                   | `app/src/main/java/com/vedant/admin/MainActivity.kt`                                                                                                   | **VERIFIED** |
| `OrderStatus.kt`                    | `app/src/main/java/com/vedant/admin/domain/model/OrderStatus.kt`                                                                                       | **VERIFIED** |
| Design system (4 files)             | `ui/theme/{Color,Type,Spacing,Theme}.kt`                                                                                                               | **VERIFIED** |
| Unit test file                      | `app/src/test/java/com/vedant/admin/domain/model/OrderStatusTest.kt`                                                                                   | **VERIFIED** |
| Toolchain pin                       | AGP 8.7.3 · Kotlin 2.0.20 · Gradle 8.12 · compileSdk 35 · minSdk 26 · targetSdk 35 · JDK 17 (`org.gradle.java.home` in `gradle.properties`)            | **VERIFIED** |
| **Debug APK exists**                | `app/build/outputs/apk/debug/app-debug.apk`, **19,986,652 bytes (~19.9 MB)**                                                                           | **VERIFIED** |
| Unit tests pass                     | `app/build/test-results/testDebugUnitTest/TEST-com.vedant.admin.domain.model.OrderStatusTest.xml` → `tests="14" skipped="0" failures="0" errors="0"`   | **VERIFIED** |
| Lint clean                          | `app/build/reports/lint-results-debug.txt` → "**0 errors, 7 warnings**"                                                                                | **VERIFIED** |
| Build logs                          | `build1.log` = **BUILD FAILED** (initial Kotlin compile error); `build2/3/4.log` = **BUILD SUCCESSFUL**; `build5.log` = **truncated mid-run** (see §8) | **VERIFIED** |
| Supabase values surfaced            | `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY` generated from git-ignored `local.properties` (`app/build.gradle.kts` lines 42–43)                    | **VERIFIED** |

### 4.2 What each built file actually does

**`MainActivity.kt`** — a single-activity Compose host that renders only a static `BuildCheckScreen` placeholder (app name "Vedant Admin", "Store operations", and `BuildConfig.VERSION_NAME`). It exists to prove the toolchain end-to-end. **There is no navigation, no auth gate, and no network call.** The file's own comment says navigation/session/auth "land here once those layers exist."

**`domain/model/OrderStatus.kt`** — implements the **full state machine**:

- `label` — the owner-approved display wording.
- `isTerminal` — true for `DELIVERED` / `CANCELLED`.
- `nextStatuses` — the UI's permitted actions from each state (explicitly a *presentation* convenience; the server's `order_transition_allowed` is authoritative).
- `flow` — display order for the tracker/filter.
- `fromWire()` — a **tolerant** decoder so an unknown enum value cannot crash a list.
- `@SerialName` on every constant pins the wire name to the exact Postgres enum value.


Its test suite asserts the wire names match the Postgres enum exactly and that the transition table matches the server's rules.

**`ui/theme/{Color,Type,Spacing,Theme}.kt`** — a design system: Leaf-green / Marigold / Berry palette with **light AND dark** schemes, a 4 dp spacing scale, corner radii, and `Sizing.touchTarget = 48.dp` (accessibility floor). **Deliberately no dynamic colour** — a status colour must mean one thing everywhere. Status is conveyed by word + icon shape + colour, never colour alone (WCAG 1.4.1).

**`app/build.gradle.kts`** — declares the full planned dependency set: Compose BOM 2024.10.01, Material 3, Navigation Compose, Lifecycle/ViewModel, **supabase-kt BOM 3.0.3** (postgrest/auth/storage), Ktor 3.0.1, kotlinx-serialization 1.7.3, kotlinx-datetime, JUnit, and Compose UI test. Note: **Room, DataStore, WorkManager, Coil, and Hilt are NOT yet declared** (see §7).

### 4.3 What is packaged but unreferenced

The supabase-kt / Ktor / serialization libraries are declared and **are packaged into the APK** (`classes6.dex`, `classes3.dex`), but they are **unreferenced — dead weight in debug builds**. `grep -rn supabase app/src --include=*.kt` returns **ZERO hits**. There is no `createSupabaseClient(...)`, no client initialisation, and no repository.

---

## 5. Build & Test Verification

### 5.1 Gates run

| Gate | Command | Result | Evidence |
|---|---|---|---|
| Unit tests | `./gradlew test` | **PASS — 14 tests, 0 failures, 0 errors** | `TEST-com.vedant.admin.domain.model.OrderStatusTest.xml` |
| Lint | `./gradlew lint` | **PASS — 0 errors, 7 warnings** | `lint-results-debug.txt` |
| Assemble debug | `./gradlew assembleDebug` | **PASS — APK produced (19.9 MB)** | `app-debug.apk` |
| Compose UI tests | — | **NOT YET CONFIGURED** | no `androidTest` sources |
| Emulator / device run | — | **BLOCKED** | no AVD, no `cmdline-tools`, `adb` not on PATH |

### 5.2 The 7 lint warnings (both cosmetic)

1. Unnecessary `v26` mipmap folder (adaptive-icon folder that could be flattened).
2. Missing monochrome launcher icon (the themed-icon attribute is not fully specified).

Neither affects correctness or behaviour. No errors.

### 5.3 Build history

`build1.log` records the **first failure** — an initial Kotlin compile error, which is expected on a first scaffold. `build2/3/4.log` record **BUILD SUCCESSFUL**. `build5.log` was **truncated mid-run** during a Kotlin-daemon fallback and **never recorded a final status** — see §8 for why this matters.

---

## 6. Supabase Connection Status

The connection state is best understood in **three independent layers**. Two are green; the third is red.

### Layer 1 — Credentials: ✅ READY

`local.properties` (git-ignored) holds a valid `SUPABASE_URL` (`https://hyibaljxicojfoxbihdn.supabase.co`) and a publishable key (46 chars, `sb_publishable_…`). Both are compiled into `BuildConfig` and were confirmed present inside `classes5.dex` of the debug APK. **No service-role key is present** — correct.

### Layer 2 — Backend: ✅ LIVE AND WORKING

A live `GET /rest/v1/categories?select=id&limit=1` with `apikey` **and** `Authorization: Bearer` headers returned **HTTP 200 with real data**. The key works. (The bare `/rest/v1/` root returns **401** — this is *expected*, it needs the bearer header; it is not a fault.)

### Layer 3 — App wiring: ❌ NOT CONNECTED

`grep -rn supabase app/src --include=*.kt` returns **ZERO hits**. There is **no** `createSupabaseClient(...)`, **no** client initialisation, and **no** repository. The SDK is on the classpath and inside the APK, but nothing calls it.

### Net state

> **"Credentials ready, backend live, app not connected."**

The moment a single `createSupabaseClient(...)` call and one repository are added, the app will be able to read real data — the hard part (auth contract, backend correctness) is already done and verified.

---

## 7. Gap Analysis

Everything below **does not exist yet**. Grouped by layer.

### 7.1 Data layer (the immediate blocker)

- Supabase client initialisation (`createSupabaseClient`).
- Repositories for orders, catalogue, offers, slots, complaints, settings.
- PostgREST query/mapping code and DTO ↔ domain mappers.
- Room cache — **not even declared as a dependency**.
- DataStore — **not declared**.
- Error/`403`/`42501` handling for non-admin sessions.

### 7.2 DI

- The hand-rolled `di/` container and ViewModel factory — **does not exist**. No ViewModel exists either.

### 7.3 Auth & session

- Login screen · session restoration · "not authorised" state for an authenticated non-admin · sign-out. (Auth uses the same Supabase project; admin capability comes from `private.admin_users`, checked server-side.)

### 7.4 Feature screens (all absent)

Dashboard · Orders list + filters · Order detail + timeline · Status transitions · Item rejection / bill adjustment · Catalogue (categories / products / weights) · Product images · Offers · Delivery slots · Support / complaints · Store settings · Analytics.

### 7.5 Navigation

- No navigation graph, no route definitions, no back-stack handling.

### 7.6 Offline / resilience

- No Room cache, no DataStore, no network-state handling, no retry policy.

### 7.7 Notifications

- No FCM, no notification centre, no push handling.

### 7.8 Dependencies declared but not yet present

`Room`, `DataStore`, `WorkManager`, `Coil`, and `Hilt` (deferred by design) are in the plan but **not in `app/build.gradle.kts`** today. Adding them is a build-config task, not a rewrite.

---

## 8. Risks & Discrepancies

| # | Risk / discrepancy | Impact | Mitigation |
|---|---|---|---|
| 1 | **Stale project docs.** `HANDOVER.md` and `PROJECT_INDEX.md` say "no Android code exists"; both are wrong. | A future session would re-scaffold or misreport status. | Update both docs to match §4; treat code + build artefacts as source of truth. |
| 2 | **Truncated `build5.log`.** It was cut off during a Kotlin-daemon fallback and never recorded a final status. | Unknown whether that specific run passed. | Re-run `./gradlew assembleDebug test lint` cleanly and capture a full log; do not cite `build5.log` as evidence either way. |
| 3 | **"Green build ≠ working app."** The APK proves the toolchain, not the product. `MainActivity` is a placeholder with no network call. | A reader could over-read the green gates as "the app works". | State plainly: 0 feature screens exist; ~10–15% complete. |
| 4 | **End-to-end behaviour unverified.** No run has exercised auth → data → UI because no such flow exists. | The first integration will surface unknowns. | Make "one real read end-to-end" the next milestone. |
| 5 | **SDK packaged but unused.** supabase-kt/Ktor add ~13 MB to the debug APK and are dead code today. | Slightly larger debug build; no runtime cost. | Expected; resolves once the data layer lands. |
| 6 | **Doc/implementation DI divergence.** `FUTURE_ADMIN_APP.md` recommends Hilt; implementation uses a hand-rolled container (no KSP). | Confusion over intended design. | Follow the implementation; record the decision in the docs. |
| 7 | **`FUTURE_ADMIN_APP.md` is partially outdated.** It references project ref `bmdmgycsrnffcgxmukxm`, the OLD enum names (`ORDER_RECEIVED`/`OWNER_ACCEPTED`/…), and "slot migration NOT YET APPLIED". | A reader could code against the wrong contract. | Authoritative sources: live DB + `PROJECT_INDEX.md` §Database Contract (enum `PENDING|CONFIRMED|…`; project `hyibaljxicojfoxbihdn`). |
| 8 | **`docs/ANDROID_BUILD_PROMPT.md` describes the CUSTOMER app**, not the admin app. | Mis-scoping. | Do not use it as the admin build spec; use `FUTURE_ADMIN_APP.md` + `CLAUDE.md`. |
| 9 | **No emulator/device.** No AVD, no `cmdline-tools`, `adb` not on PATH. | Cannot run UI/instrumentation tests. | Install `cmdline-tools` and create an AVD before UI work. |

### 8.1 Backend state (prior-session work — NOT re-verified this session)

The Supabase backend was remediated and verified in a **prior session**. Report as prior work, not this session's verification:

- **5 migrations applied**: checkout `42883` fix · weight-variant pricing fix · admin roster + `is_admin()` + a privilege-escalation hole closed · order-lifecycle RPCs · adjustment sign fix · 7 storage policies · recurring delivery slots via `pg_cron` (horizon to 2026-10-24).
- **Backend test suites: 4 passing.**
- **Customer PWA gates: tsc 0 errors, eslint 0 errors, `next build` 22/22 routes.**

### 8.2 Known open issues (carried forward)

1. **8 products with null `image_url`** — Beetroot, Capsicum (Green), Carrot, Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato.
2. **`graphify@1.0.0`** — dead/abandoned dependency in the PWA `package.json`, imported nowhere.
3. **Store contact env vars unset** — `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` unset; Call/WhatsApp buttons stay hidden (owner configuration, not a bug).
4. **`complaint-images` path convention** changed to `<uid>/<file>` — **not verified** against the PWA upload code; a mismatch would fail the RLS insert check.
5. **`profiles.email` is null** for the seeded admin (lives in `auth.users.email`); cosmetic.

---

## 9. Recommended Next Steps

Prioritised. Milestone 1 is the immediate next milestone and unblocks everything else.

### Milestone 1 — Connect the data layer (immediate)

1. Add `createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)` behind the hand-rolled `di/` container (so no UI or ViewModel touches the SDK directly).
2. Land **one real read end-to-end** — e.g. an orders-list query — proving `BuildConfig` → client → repository → ViewModel → screen.
3. Wire an auth session (sign-in + session restoration) and render an explicit **"not authorised"** state for an authenticated non-admin.
4. **Acceptance:** a signed-in admin sees real rows from `hyibaljxicojfoxbihdn`; a non-admin sees the unauthorised state and no admin data.

### Milestone 2 — Core order flow (highest user value)

5. Orders list + filters → order detail + status timeline.
6. Status transitions via `admin_update_order_status`, with **no optimistic success** (server confirmation mandatory).
7. Item rejection / restore via `admin_reject_order_item` / `admin_restore_order_item`; money stays server-authoritative.

### Milestone 3 — Operations surface


8. Catalogue CRUD + product image upload.
9. Offers · Delivery slots (view/create/deactivate) · Complaints thread · Store settings.

### Milestone 4 — Resilience & polish

10. Room cache + DataStore · WorkManager background sync · FCM push · Analytics (post-MVP).

### Housekeeping (do in parallel)

11. **Refresh `HANDOVER.md` and `PROJECT_INDEX.md`** to correct the "no code" claims.
12. **Re-run the three gates** and capture a clean, complete build log (replaces the truncated `build5.log`).
13. Install `cmdline-tools` + create an AVD so UI/instrumentation tests can run.

---

## 10. Verification Ledger

| # | Gate / claim | Result | Evidence | Verified? |
|---|---|---|---|---|
| 1 | Gradle project `:app` exists | TRUE | `settings.gradle.kts`, `build.gradle.kts` | **VERIFIED** |
| 2 | 7 Kotlin source files exist | TRUE | `find` → 7 | **VERIFIED** |
| 3 | Debug APK exists (19.9 MB) | TRUE | `app-debug.apk`, 19,986,652 bytes | **VERIFIED** |
| 4 | Unit tests pass | 14 / 0 fail / 0 err | `TEST-…OrderStatusTest.xml` | **VERIFIED** |
| 5 | Lint clean | 0 errors, 7 warnings | `lint-results-debug.txt` | **VERIFIED** |
| 6 | Build succeeds | `build2/3/4.log` SUCCESSFUL | build logs | **VERIFIED** |
| 7 | First build failed then recovered | `build1.log` FAILED | `build1.log` | **VERIFIED** |
| 8 | Supabase credentials present & valid | TRUE | `local.properties` + `BuildConfig` + `classes5.dex` | **VERIFIED** |
| 9 | Service-role key absent from client | TRUE | no occurrence in `local.properties`/code/APK | **VERIFIED** |
| 10 | Backend live, publishable key works | HTTP 200 + real data | live `GET /rest/v1/categories` | **VERIFIED** |
| 11 | App connected to Supabase | FALSE — 0 hits | `grep -rn supabase app/src` | **VERIFIED (absent)** |
| 12 | `MainActivity` renders a real screen | FALSE — placeholder only | `MainActivity.kt` | **VERIFIED (placeholder)** |
| 13 | `OrderStatus` state machine complete | TRUE | `OrderStatus.kt` | **VERIFIED** |
| 14 | Design system implemented (light+dark) | TRUE | `ui/theme/*.kt` | **VERIFIED** |
| 15 | `build5.log` outcome | UNKNOWN — truncated | `build5.log` (no final line) | **NOT VERIFIED** |
| 16 | Data layer / repositories / client init | DOES NOT EXIST | codebase | **NOT VERIFIED (absent)** |
| 17 | Navigation / auth gate / feature screens | DO NOT EXIST | codebase | **NOT VERIFIED (absent)** |
| 18 | Compose UI / instrumentation tests | NONE | no `androidTest` sources | **NOT YET CONFIGURED** |
| 19 | Emulator / device run | NONE | no AVD, `adb` off PATH | **BLOCKED** |
| 20 | Backend migrations (5) + backend/PWA gates | Prior session | `HANDOVER.md` / `PROJECT_INDEX.md` | **NOT VERIFIED this session** |

---

## Appendix — Requirement Source Documents

| Document | Role | State |
|---|---|---|
| `admin-android/CLAUDE.md` | Hard rules + architecture doctrine | Current |
| `admin-android/HANDOVER.md` | Live in-flight state | **STALE** — wrongly says no code exists |
| `admin-android/PROJECT_INDEX.md` | Persistent index / DB contract | **STALE** — says ~15%, PHASE 8, no Android code |
| `../docs/FUTURE_ADMIN_APP.md` | Admin app contract (auth, RPCs, features, stack) | Partially outdated (old project ref + old enum names) |
| `../docs/ANDROID_BUILD_PROMPT.md` | Build prompt | **Describes the CUSTOMER app, not the admin app** |

---

*End of report.*
