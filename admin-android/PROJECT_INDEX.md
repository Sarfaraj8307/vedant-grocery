# Vedant Admin Android — Project Index

> **Persistent memory of this project.** A future session must be able to open
> this file and know exactly where the previous one stopped.
> Read alongside `HANDOVER.md` (which carries the live, in-flight detail).
>
> **Last updated:** 2026-10-12 (session 7 — Phase 6 close-out: DEFECT-1 offline gating, OBS-1 regex fix, OBS-2/3 doc fixes, T27/T28 docs gate — green)

---

## Project Status

| Field | Value |
|---|---|
| Current phase | **PHASE 6 (T26–T28)** — **complete & green**; the Android client is feature-complete for all 29 PRD capabilities. Remaining work is verification only, gated on R1/R2. |
| Current milestone | Backend complete + verified; PWA reconciled and green; **Android Phases 1–6 all implemented** (orders, catalogue, slots, complaints, settings/profile, offline, a11y, docs) |
| Overall completion | **~97%** — everything implementable here is implemented and JVM-verified. The remaining ~3% is live E2E + physical QA, which cannot run here (**R1** no admin creds, **R2** no device) |
| Build status | **Android: `assembleDebug` green (debug APK 23,831,166 B ≈ 22.7 MB).** Customer PWA: tsc 0 errors, eslint 0 errors, next build 22/22 |
| Test status | Backend: 4 suites passing. PWA: 3 gates green. Android: **410/410 unit tests pass (37 suites)**; lint 0 errors; instrumented suites compile (execution needs a device) |
| Last verified date | 2026-10-12 |

### Why the Android app was not started until now

The audit found the **customer PWA's checkout was completely broken in
production** (`42883`, every order failed) and that every sub-1kg pack was
**overcharged**. Building an admin app on top of a broken shared contract would
have baked the wrong prices and the wrong status names into a second codebase.
Backend first, then Android. That work is now **complete and verified**, and the
Android scaffold is in place — 7 Kotlin files, `./gradlew assembleDebug` green.

## Architecture

**Foundation in place, feature layer next.** Current state: single-module `:app`,
Kotlin + Jetpack Compose + Material 3, MVVM/UDF, hand-rolled DI (no KSP). Clean
Architecture layering is established: Presentation → Domain → Data → Supabase,
with the SDK quarantined in `data/` (only `SupabaseProvider`, `SessionStore`,
`AdminRpc` import `io.github.jan.*`).

Current packages (`app/src/main/java/com/vedant/admin/`):

```
com.vedant.admin
├── MainActivity.kt          single activity host → VedantNavHost
├── VedantAdminApplication.kt installs log sink + builds AppContainer
├── core/
│   ├── money/               Money (integer paise), MoneySerializer
│   ├── result/              Outcome + outcomeOf
│   ├── error/               AppError, ErrorMapper, ErrorCopy
│   ├── time/                AppClock (Asia/Kolkata)
│   └── logging/             AppLog (redacting) + AndroidLogSink
├── data/
│   ├── remote/              SupabaseProvider, AdminRpc, CatalogueRemoteSource,
│   │                        SlotRemoteSource, ComplaintRemoteSource,
│   │                        SettingsRemoteSource, StorageDataSource, dto/, mapper/
│   ├── local/               CacheStore (DataStore JSON snapshots), SafeReadRetry
│   ├── session/             SessionStore + SupabaseSessionStore
│   └── repository/          AuthRepositoryImpl, OrderRepositoryImpl,
│                            ProductRepositoryImpl, CategoryRepositoryImpl,
│                            WeightRepositoryImpl, OfferRepositoryImpl,
│                            SlotRepositoryImpl, ComplaintRepositoryImpl,
│                            StoreSettingsRepositoryImpl, ProfileRepositoryImpl
├── di/                      AppContainer, AppViewModelFactory
├── domain/
│   ├── model/               OrderStatus, SessionState, AdminIdentity, Profile,
│   │                        Order*, Product*, Category*, Offer*, DeliverySlot,
│   │                        Complaint*, StoreSettings, OperatorProfile
│   ├── usecase/             OrderTransitions
│   └── repository/          Auth, Order, Product, Category, Weight, Offer, Slot,
│                            Complaint, StoreSettings, Profile
├── feature/
│   ├── auth/                Splash, SignIn, NotAuthorized (screen + VM + state)
│   ├── dashboard/           DashboardScreen (shell: orders / catalogue / slots / support)
│   ├── orders/              list/ + detail/ (+ RejectItemSheet)
│   ├── catalogue/           ProductThumb, hub/, editor/, categories/,
│   │                        weights/, offers/
│   ├── slots/               Slots (screen + VM + state)
│   ├── support/             SupportScreen (alias) + list/ + thread/
│   └── settings/            store/ + profile/
└── ui/
    ├── components/          StateScaffold, StatusBadge, VedantTopBar,
    │                        VedantButtons, SectionCard, Skeletons, ActionState,
    │                        DateField, Connectivity (LocalOnlineStatus)
    ├── navigation/          Routes, VedantNavHost, SessionViewModel
    └── theme/               design system: Color, Type, Spacing, Theme, Motion
```

Planned direction (per `docs/FUTURE_ADMIN_APP.md` §7 and `CLAUDE.md`):

- Kotlin + Jetpack Compose + Material 3, MVVM/UDF, Clean Architecture
  (Presentation → Domain → Data → Supabase; the SDK is quarantined in `data/`) ✅
- Supabase Kotlin SDK + Ktor ✅, DataStore, WorkManager, Coil, Compose Navigation ✅,
  Coroutines + Serialization ✅
- **Deliberately deferred:** Hilt and Room (both need KSP — the brief forbids
  annotation processing now); `di/` is the seam for a later Hilt swap
- Single module (`:app`); split into `core/` + `feature-*/` only when a module
  earns its keep
- Target directory: **`admin-android/`** (chosen: keeps Android out of the
  Next.js tree, matches the repo's existing `docs/` naming)

## Backend

### Supabase

| Field | Value |
|---|---|
| Project ref | `hyibaljxicojfoxbihdn` |
| URL | `https://hyibaljxicojfoxbihdn.supabase.co` |
| Publishable key | `sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH` (public — safe in client) |
| Service-role key | **never in any client.** Not present in PWA or APK |
| Postgres | 17.11 |

### Tables used

`profiles, categories, products, product_weights, offers, delivery_slots,
addresses, cart_items, orders, order_items, order_status_history, complaints,
complaint_messages, complaint_attachments, notifications, notification_outbox,
store_settings, events` + view `product_catalog`

### RPCs used (existing)

| RPC | Signature | Notes |
|---|---|---|
| `place_order` | `(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid) → uuid` | the app-facing wrapper the PWA calls |
| `check_username_available` | `(p_username text) → boolean` | signup |

### RPCs created (this project)

| RPC | Purpose |
|---|---|
| `public.is_admin()` / `private.is_admin()` | the single admin gate |
| `public.admin_list_admins()` | roster read |
| `public.admin_grant_admin(p_email, p_note)` | roster write |
| `public.admin_revoke_admin(p_user_id)` | roster write; refuses to remove the last admin |
| `public.admin_update_order_status(p_order_id, p_status, p_reason)` | state machine + history + notify |
| `public.admin_reject_order_item(p_item_id, p_reason)` | item rejection, recomputes totals |
| `public.admin_restore_order_item(p_item_id)` | undo a rejection |
| `public.admin_slot_load(p_from, p_days)` | slot capacity/utilisation |
| `public.record_order_status_history()` | trigger — owns `order_status_history` |
| `private.order_transition_allowed(from, to)` | transition legality |

### RLS requirements

- Admin identity = membership of `private.admin_users`. **Nothing else.**
- Every admin RPC raises `42501` for a non-admin. Verified.
- `role` on `profiles` is immutable for non-admins (escalation hole closed).
- `storage.objects` has **7 policies** (added in migration
  `storage_policies_slot_recurrence_admin_writes`) — uploads/reads are authorised.
- Admin write/read policies for orders/catalogue/support/settings **exist** (14
  added in the same migration).

### Storage buckets

| Bucket | Visibility | Limit | Mime | Policies |
|---|---|---|---|---|
| `product-images` | public read | 5 MB | jpeg/png/webp | public read + admin write |
| `complaint-images` | private | 5 MB | jpeg/png/webp | owner-or-admin; path **must** be `<auth.uid()>/<filename>` |

### Authentication model

Supabase Auth (email + password), the same project as the customer PWA. An
admin signs in as an ordinary authenticated user; admin capability comes from
`private.admin_users` and is checked server-side on every privileged call. The
app must render an explicit "not authorised" state for an authenticated
non-admin and must never expose admin data to them.

## Features

```
[x] Backend — checkout fixed (was 100% broken)
[x] Backend — weight-variant pricing fixed (was overcharging)
[x] Backend — admin roster + is_admin() + privilege-escalation hole closed
[x] Backend — order state machine + item rejection/restore RPCs
[x] Backend — lifecycle verified (12/12 assertions)
[x] Backend — storage policies (7 added; there were ZERO)
[x] Backend — recurring delivery-slot generation (cron + backfill 18 -> 42)
[x] Backend — admin RLS write policies for catalogue/support/settings
[x] PWA — reconciled to live order_status enum
[x] PWA — complaint status IN_PROGRESS
[x] PWA — tsc / eslint / build all green
[x] Android — project scaffold (7 Kotlin files; assembleDebug green)
[x] Android — design system (ui/theme: Color/Type/Spacing/Theme)
[x] Android — Supabase client + DI container (T03–T06)
[x] Android — core primitives (Money/Outcome/AppError/ErrorMapper/ErrorCopy/AppClock/AppLog)
[x] Android — login / session restoration / unauthorised state (T07–T10)
[x] Android — shared UI components (StateScaffold/StatusBadge/TopBar/Buttons)
[x] Android — orders list + filters
[x] Android — order detail + status timeline
[x] Android — order status transitions
[x] Android — item rejection / bill adjustment
[x] Android — catalogue hub (paging, search, facets, availability toggle)
[x] Android — product editor (basics / pricing & weights / media / visibility)
[x] Android — product images (upload with progress + retry, server-confirmed URL)
[x] Android — categories (list, add/edit, delete)
[x] Android — weight variants (inline edit)
[x] Android — offers (date-range, active/scheduled/expired)
[x] Android — delivery slots (day schedule + utilisation bars)
[x] Android — complaints (list + messaging-style thread + attachments)
[x] Android — store settings + operator profile/session
[x] Android — offline cache + banner + WorkManager safe-read retry
[x] Android — animations / accessibility / dark-mode QA (JVM-asserted)
[x] Android — offline gating on all 9 mutation surfaces (DEFECT-1)
[x] Android — ErrorCopy allowlist fix + regression tests (OBS-1)
[x] Android — README + E2E plan (BLOCKED) + status tables (T27/T28)
[ ] Dashboard counts (dashboardCounts() exists — tiles not wired)
[ ] Analytics
[ ] Push notifications (FCM)
```

## Screens

**Implemented (Phase 1):** S1 Splash / session restore · S2 Sign in ·
S3 Not authorized · S4 Dashboard (shell with a "Manage orders" entry point).

**Implemented (Phase 2):** S5 Orders list · S6 Order detail (items, totals,
delivery, timeline, status workflow) · item reject/restore sheet · Support
(Phase-3 placeholder, the complaint-link destination). The navigation graph and
the auth/admin gate live in `ui/navigation/VedantNavHost.kt`.

**Implemented (Phase 3):** S7 Catalogue hub (scannable product list, search,
category + availability facets, inline availability toggle) · S8 Product editor
(grouped sections, sticky save bar, image upload with progress/retry) · S9 Weight
variants (inline edit) · S10 Categories (list, add/edit, delete) · S11 Offers
(date-range presentation, active/scheduled/expired/disabled) · S12 Delivery slots
(day schedule with utilisation bars, full slot visually distinct, `ensureSlots`).

Planned minimum set (see `docs/ARCHITECTURE.md` §8, screens S1–S17):

Auth: Splash · Login · Forgot Password · Unauthorized · Session restoration
Main: Dashboard · Orders · Order Detail · Catalogue · Product List ·
Product Editor · Category Editor · Offers · Delivery Slots · Complaints ·
Complaint Detail · Store Settings · Profile · Notification Center
Optional: Analytics · Activity/Audit

## Database Contract

### Order status enum (LIVE — the app must use these exact names)

```
PENDING | CONFIRMED | PROCESSING | READY_FOR_DELIVERY | OUT_FOR_DELIVERY | DELIVERED | CANCELLED
```

Customer-facing labels (owner-approved):

| DB value | Label |
|---|---|
| `PENDING` | Order received |
| `CONFIRMED` | Confirmed by store |
| `PROCESSING` | Being packed |
| `READY_FOR_DELIVERY` | Ready to dispatch |
| `OUT_FOR_DELIVERY` | Out for delivery |
| `DELIVERED` | Delivered |
| `CANCELLED` | Cancelled |

### State machine

```
PENDING -> CONFIRMED -> PROCESSING -> READY_FOR_DELIVERY -> OUT_FOR_DELIVERY -> DELIVERED
   |           |            |                |                    |
   +-----------+------------+----------------+--------------------+
              any non-terminal -> CANCELLED   (reason REQUIRED)
```

`DELIVERED` and `CANCELLED` are terminal — no transitions out, no item changes.

### Other enums

- `complaint_status`: `OPEN | IN_PROGRESS | RESOLVED | CLOSED`
- `complaint_type`: `WRONG_ITEM | DAMAGED | MISSING_ITEM | LATE_DELIVERY | QUALITY | OTHER`
- `outbox_audience`: `customer | admin`
- `user_role`: `customer | admin`

### Money rules (authoritative)

- Stored as `numeric(12,2)`
- `adjustment_amount <= 0` (enforced by CHECK)
- `final_total = original_total + adjustment_amount`
- `original_total` = the pre-adjustment bill; frozen on first adjustment
- Only `admin_reject_order_item` / `admin_restore_order_item` may change these,
  and neither accepts an amount from the caller

### Order columns that matter

Dual slot columns exist — **write both, read either**:
`orders.slot_id` (legacy) and `orders.delivery_slot_id` (app contract).
`orders.total`, `subtotal`, `delivery_fee`, `delivery_charge`, `original_total`,
`final_total`, `adjustment_amount`, `adjustment_reason`, `adjusted_by`,
`adjusted_at`, `address_snapshot`, `pincode` (generated).

`order_items` uses **`qty`** (not `quantity`), plus `weight_label`, `rejected`,
`rejection_reason`, and a generated `line_total`.

## Design System

**Created** in `app/src/main/java/com/vedant/admin/ui/theme/`:

| File | Provides |
|---|---|
| `Color.kt` | Vedant palette (mirrors the PWA `@theme` tokens) |
| `Type.kt` | typography scale (12 sp floor) |
| `Spacing.kt` | 4 dp base scale + `Radius` + `Sizing` (`touchTarget = 48.dp`, `progressStroke`, `emptyIcon`, `border`) |
| `Theme.kt` | `VedantAdminTheme` + `VedantTheme.spacing/radius/sizing` |

**Shared components** (`ui/components/`, Phase 1):

| Component | Provides |
|---|---|
| `StateScaffold` | the four explicit content phases (Loading/Data/Empty/Error) + persistent `OfflineBanner`, animated |
| `StatusBadge` | status by **word + icon shape + colour** (WCAG 1.4.1); `OrderStatus.badgeVisual()` |
| `VedantTopBar` | app bar built from primitives (no experimental API); back action has a content description |
| `VedantButtons` | `PrimaryButton`/`SecondaryButton`/`DestructiveButton` — 48 dp floor, in-flight spinner, double-submit blocked |

Still to add as screens land: elevation, iconography, component states
(default/pressed/focused/disabled/loading/error/empty) beyond what `VedantButtons`
and `StateScaffold` cover.

Palette anchor: the PWA's existing brand tokens (`src/app/globals.css`,
`@theme`) — leaf green primary, marigold accent. The Android theme reads as the
same brand without copying the web layout.

## Libraries

**Added** in `app/build.gradle.kts`: Compose BOM 2024.10.01 (ui, graphics,
tooling-preview, material3, material-icons-extended), core-ktx, activity-compose,
lifecycle (runtime-ktx / runtime-compose / viewmodel-compose), navigation-compose,
supabase-kt BOM 3.0.3 (postgrest / auth / storage), ktor-client-okhttp + core
3.0.1, kotlinx-serialization-json 1.7.3, kotlinx-datetime 0.6.1,
`multiplatform-settings-no-arg` 1.1.1 (T03 — backs `SettingsSessionManager`),
**Coil 3 `coil-compose` + `coil-network-okhttp` 3.0.4 (T20 — image loading)**,
junit, coroutines-test, Turbine 1.1.0 (T03), espresso, compose ui-test.

**Not yet added** (planned): `datastore-preferences` + `work-runtime-ktx` (T24).
**Hilt/KSP/Room deliberately excluded.**

## Skills Installed

**None yet.** The brief asks to research and install Android/Compose and UI/UX
Claude Code skills into `admin-android/.claude/skills/`. Candidate repos to
evaluate at implementation time: `rinkuniks/android-claude-skill`,
`ceorkm/mobile-app-ui-design`, `ryktara/design-engineering-skill` — evaluate
stars, recency, licence, and Compose compatibility rather than assuming.

## Tests

| Suite | Status |
|---|---|
| Checkout end-to-end (customer through RLS) | PASS — 10 assertions |
| Admin identity & authorization (incl. 3 escalation attempts) | PASS |
| Order lifecycle (transitions, reject/restore, money) | PASS — 12/12 assertions |
| Storage policies / slots / cron | PASS |
| PWA tsc + eslint + build | PASS — 0 errors, 22/22 routes |
| Android unit tests | PASS — **410/410** in 37 suites (JVM) |
| Android lint | PASS — 0 errors, 14 warnings (dependency-version + launcher-icon only) |
| Android Compose UI tests | **compiled** (9 files, incl. `OfflineGatingScreenTest`) — execution **[BLOCKED]** (no device, R2) |

### Known failures

None. The Android build is green. Instrumented (Compose) tests compile but cannot
execute here — no emulator/device (R2).

## Known Bugs

### Fixed this session (all verified)

1. Checkout `42883` -> fixed & verified
2. Weight-variant overcharging -> fixed & verified
3. `profiles` self-promotion to admin -> fixed & verified
4. `handle_new_user` hardcoded admin email -> fixed & verified
5. `order_status_history` never written (empty customer timeline) -> fixed
6. `order_status` enum mismatch (PWA side) -> fixed & verified
7. Complaint status `IN_REVIEW` -> `IN_PROGRESS` -> fixed & verified
8. Zero `storage.objects` policies -> 7 policies added & verified
9. Slots expiring 2026-10-16 -> cron + backfill, horizon now 2026-10-24
10. Adjustment sign error in my own reject/restore RPC -> fixed & verified

### Open — known, not yet fixed

11. **8 products with null `image_url`**: Beetroot, Capsicum (Green), Carrot,
    Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato.
12. **`graphify@1.0.0` in `package.json`** is a broken/abandoned package, not
    imported anywhere. Should be removed.
13. **Store contact unset** — `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` unset,
    so Call/WhatsApp buttons stay hidden. Owner configuration, not a bug.
14. **`complaint-images` path convention is now `<uid>/<file>`** — the PWA's
    attachment upload must match or its RLS insert check fails. Not yet verified
    against the PWA upload code.
15. **`profiles.email` is null** for the seeded admin (`auth.users.email` holds
    it). Cosmetic.

### Also noted

- The old report's "Bug 1" (`order_items.quantity`) was **already fixed** — do
  not redo it.
- The old report's "Bug 3" (0 storage buckets) was **stale** — buckets existed
  all along; it was the *policies* that were missing.
- `docs/PROJECT_REPORT_2026-10-11.md` and
  `docs/VERIFICATION_REPORT_2026-10-11.md` contain several stale claims. The
  live DB and this file win.

## Current Blockers

**Two, both outside engineering:**
- **R1 — no admin credentials.** Blocks every live mutation / RPC / upload and the
  live admin gate. To unblock: provision an owner email in `private.admin_users`
  and provide email + password.
- **R2 — no device/emulator.** `cmdline-tools` and an AVD are missing; no device
  on `adb`. Blocks instrumented-test execution and physical a11y/dark-mode/motion
  QA. To unblock: create an AVD or attach a device.

No code blocker. The client is feature-complete for the 29 capabilities.

## Last Completed Task

**Phase 6 (T26–T28): close-out + docs gate — green.** Closed **DEFECT-1** (offline
gating on all six remaining mutation surfaces, additive `enabled = … && online` +
`OfflineHint`, via the existing `LocalOnlineStatus`; new
`OfflineGatingScreenTest`), **OBS-1** (`ErrorCopy.sanitize` allowlist + regression
tests asserting ₹50000/`12345` survive), **OBS-2/OBS-3** (ARCHITECTURE §10 pair and
§2 naming), and the **T27/T28** documentation gate (README, `docs/E2E.md` marked
BLOCKED, `docs/ARCHITECTURE.md` §11.1 status table,
`docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md`). `assembleDebug` green (APK
23,831,166 B) · `test` **410/410** (37 suites) · `lint` 0 errors ·
`assembleDebugAndroidTest` compiles.

## NEXT TASK

**Verification, not features.** With R1 + R2 available, execute `docs/E2E.md`
end-to-end and replace every "NOT EXECUTED" with an observed result — that is what
converts the status report from IMPLEMENTED-BUT-UNVERIFIED to VERIFIED.

If the owner wants a visible client improvement without R1/R2, the single open
client gap is **wire the dashboard counts tiles** to the existing
`OrderRepository.dashboardCounts()`. Everything else in the PRD is implemented.
Do **not** add analytics/push/FCM — those are outside the 29 capabilities and need
a new PRD.

## NEXT SESSION INSTRUCTIONS

1. Read `HANDOVER.md`, then this file, then `docs/CONTRACT.md`. All are current as
   of 2026-10-12.
2. An Android project exists in `admin-android/`, builds green, and now has the
   full orders workflow wired (Phase 2 done). Verify reality against the live DB
   and the build, not against older docs (`docs/BACKEND_CONTRACT.md` describes a
   different, older project).
3. **Issue exactly one tool call per response when calling Supabase MCP** — mixing
   an MCP call with any other tool call makes the MCP call fail with a ZodError.
4. Do **NEXT TASK**. Do not rebuild what is already applied — check
   `DATABASE CHANGES` in `HANDOVER.md` first.
5. Do not re-introduce `ORDER_RECEIVED`/`OWNER_ACCEPTED`/`PREPARING`/`READY`; the
   owner decided to adopt the live enum names.
6. Update both `PROJECT_INDEX.md` and `HANDOVER.md` at the end of the session.

## Files Changed

| File | Change |
|---|---|
| `admin-android/HANDOVER.md` | created |
| `admin-android/PROJECT_INDEX.md` | created (this file) |
| `src/types/database.ts` | `OrderStatus` -> live enum names; `ComplaintStatus` -> `IN_PROGRESS` |
| `src/lib/format.ts` | `ORDER_STATUS_META` + `ORDER_STATUS_FLOW` keys; complaint key |
| `src/components/orders/order-status-badge.tsx` | `STATUS_STYLES` keys; fallback -> `PENDING` |
| `src/app/orders/[id]/page.tsx` | `"ORDER_RECEIVED"` -> `"PENDING"` |

Five Supabase migrations were applied remotely.

**Session 2 (Android scaffold + Phase 0):** created the `admin-android/` Gradle
project and the 7 Kotlin files listed under *FILES CREATED* in `HANDOVER.md`
(scaffold + design system + `OrderStatus` + its test); created
`admin-android/docs/CONTRACT.md`, `docs/SCHEMA_SNAPSHOT.md` and
`docs/probe_authed_columns.sh`. **No `src/` (PWA) file was modified.**

**Session 3 (Android Phase 1 — T03–T11):** added ~30 Kotlin files (core
primitives, Supabase client + session, DI, `AuthRepository`, navigation + gate,
UI components, Splash/SignIn/NotAuthorized screens) plus 4 new unit-test suites;
modified `app/build.gradle.kts`, `app/proguard-rules.pro`,
`app/src/main/AndroidManifest.xml`, and extended `ui/theme/Spacing.kt` and
`MainActivity.kt`. Full list under *FILES CREATED* in `HANDOVER.md`. **No `src/`
(PWA) file was modified.** Gates: `assembleDebug` green · `test` 74/74 · `lint`
0 errors.

**Session 4 (Android Phase 2 — T12–T18):** added the order domain model + state
machine, DTOs + mapper, `OrderRemoteSource` + `OrderRepositoryImpl`, the list and
detail screens (+ their ViewModels/UiStates), the reject sheet, `SupportScreen`
placeholder, and 2 new test suites (+ test infra); modified `AdminRpc`,
`SupabaseProvider`, `ErrorMapper`, `AppContainer`, `ViewModelFactory`,
`Routes`, `VedantNavHost`, `DashboardScreen`, `Skeletons`. Full list under
*FILES CREATED* in `HANDOVER.md`. **No `src/` (PWA) file was modified.** Gates:
`assembleDebug` green · `test` 129/129 · `lint` 0 errors.

**Session 5 (Android Phase 3 — T18–T21):** added the catalogue/slot domain models
+ DTOs + mappers, the four catalogue repositories + the slot repository, the
`product-images` storage data source, the six Phase-3 screens (+ ViewModels/
UiStates), `ActionState`, `DateField`, `ProductThumb`, `ImagePicker`,
`ImageProcessor`, and 10 new test suites/files; modified `app/build.gradle.kts`
(+Coil 3), `AndroidManifest.xml` + `res/xml/file_paths.xml` (FileProvider),
`AdminRpc`, `Routes`, `VedantNavHost`, `AppContainer`, `ViewModelFactory`,
`DashboardScreen`, and the `OrderDetail*` files (promoted `ActionState`). Full
list under *FILES CREATED* in `HANDOVER.md`. **No `src/` (PWA) file was
modified.** Gates: `assembleDebug` green (APK 22.8 MB) · `test` 247/247 · `lint`
0 errors.

**Session 6 (Android Phase 4+5 — T22–T25):** added complaints (domain/DTOs/mapper/
repo/screens), store settings + operator profile (domain/DTOs/mapper/repos/
screens), the offline cache + safe-read retry + `ConnectivityObserver` +
`LocalOnlineStatus`, `LocalReduceMotion`, and the a11y/contrast tests
(`ThemeContrastTest`, `DesignTokenGuardTest`); modified `app/build.gradle.kts`
(+DataStore, +WorkManager), `StorageDataSource`, `StateScaffold`, `StatusBadge`,
`Skeletons`, `ui/theme`, several `*Screen.kt` (mutations disabled offline),
`MainActivity`, `AppContainer`, `ViewModelFactory`, navigation. Gates:
`assembleDebug` green (APK 22.7 MB) · `test` 349/349 · `lint` 0 errors.

**Session 7 (Android Phase 6 close-out — T26–T28):** closed DEFECT-1 (offline
gating on the 6 remaining surfaces: hub switch, product-editor save, categories/
offers/weights/slots create/save) and OBS-1 (`ErrorCopy.sanitize` allowlist +
regression tests); corrected `docs/ARCHITECTURE.md` §10/§2 (OBS-2/3); added
`README.md`, `docs/E2E.md` (BLOCKED), the §11.1 T01–T28 status table,
`docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md`, and `OfflineGatingScreenTest`
(+ `FakeOfferRepository`/`FakeSlotRepository` fakes). Gates: `assembleDebug`
green (APK 23,831,166 B) · `test` 410/410 (37 suites) · `lint` 0 errors ·
`assembleDebugAndroidTest` compiles.

## Commands

```bash
# verify the PWA still builds after any PWA edit
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables"
pnpm exec tsc --noEmit
pnpm exec eslint .
pnpm run build

# Android (project exists; gradle wrapper + JDK 17; no adb/AVD yet)
export JAVA_HOME="<jdk17>"          # currently unset
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Verification

| Gate | Result | Date |
|---|---|---|
| Checkout end-to-end (impersonated RLS, rolled back) | PASS | 2026-10-11 |
| Admin authorization + escalation attacks | PASS | 2026-10-11 |
| Order lifecycle probe | PASS — 12/12 | 2026-10-11 |
| Storage policies / slot recurrence / cron | PASS | 2026-10-11 |
| Supabase migration application | 5 applied | 2026-10-11 |
| `tsc --noEmit` (PWA) | PASS — 0 errors | 2026-10-11 |
| `eslint` (PWA) | PASS — 0 errors | 2026-10-11 |
| `next build` (PWA) | PASS — 22/22 routes | 2026-10-11 |
| Gradle build (Android) | PASS — `assembleDebug` (debug APK 23,831,166 B) | 2026-10-12 |
| Android unit tests | PASS — **410/410** (37 suites) | 2026-10-12 |
| Android lint | PASS — 0 errors, 14 warnings | 2026-10-12 |
| Android order RPCs (live) | **[BLOCKED]** — no admin credentials (R1); fakes only | 2026-10-12 |
| Android catalogue/slot writes (live) | **[BLOCKED]** — no admin credentials (R1); fakes only | 2026-10-12 |
| Android `product-images` upload (live) | **[BLOCKED]** — no admin credentials (R1); fake remote source only | 2026-10-12 |
| Android instrumentation tests | compiled (9 files) — **execution [BLOCKED]** (no device, R2) | 2026-10-12 |
| Emulator/device test | no AVD, no adb on PATH | — |