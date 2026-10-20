# CLAUDE.md — Vedant Admin (Android)

Guidance for Claude Code sessions working in `admin-android/`.
Read `PROJECT_INDEX.md` and `HANDOVER.md` **first** — they carry the live state.

---

## What this app is

The **store-owner operations app** for Vedant Fruit & Vegetables. It is a
separate Android application that talks to the **same Supabase project** as the
customer PWA (`hyibaljxicojfoxbihdn`).

It is **not** an extension of the customer PWA. Never add admin screens, admin
routes, owner PINs, or privileged logic to the Next.js app, and never couple
Compose code to the web app. The only shared boundary is the backend contract.

## Hard rules

1. **No privileged secrets, ever.** The APK carries only the *publishable*
   Supabase key. The service-role key must never appear in `local.properties`,
   `build.gradle.kts`, `BuildConfig`, resources, or code.
2. **Admin authorization is server-side only.** The single gate is
   `private.is_admin()` in Postgres. UI visibility is not authorization. Never
   trust a locally-cached role, a hidden button, or a client-side check.
3. **Never fake success.** An order/status/money mutation is only "done" once the
   server confirms it. Do not optimistically show a status change or a new total
   that the backend has not acknowledged. For financial and order-state
   mutations, server confirmation is mandatory.
4. **The database owns the wires.** `OrderStatus` values (and every other enum)
   are the Postgres enum names. Do not rename them in the client; change the
   labels instead.
5. **Money is server-authoritative.** `adjustment_amount <= 0` and
   `final_total = original_total + adjustment_amount` are enforced by the
   database. The client never computes an amount it then sends.

## Architecture

```
Presentation (Compose + ViewModel, immutable UiState, one-shot events)
        |
Domain (models, use cases, business rules, no Android/Supabase imports)
        |
Data (repositories, Supabase/PostgREST, Room cache, DataStore)
        |
Supabase (PostgREST / Auth / Storage)  +  Room  +  DataStore  +  WorkManager
```

- Unidirectional data flow. `ViewModel` exposes `StateFlow<UiState>`; the screen
  emits `Event`s up.
- **No business logic inside composables.** A composable formats and renders; it
  does not decide.
- **No Supabase calls from UI.** Only repositories touch the SDK.
- Immutable UI state. Prefer a single `data class XUiState` per screen with
  `loading / data / error / empty` modelled explicitly.
- Current DI is a small hand-rolled container in `di/` with a ViewModel factory —
  deliberately no KSP, so the build needs no annotation processing. If Hilt is
  added later, `di/` is the seam to replace.

### Modules

Single-module (`:app`) for now. Split into `core/` + `feature-*/` only when a
module earns its keep; premature modularisation costs build time for nothing.

Current packages:

```
com.vedant.admin
├── MainActivity.kt          single activity host
├── domain/model/            pure Kotlin models (OrderStatus, ...)
├── ui/theme/                design system: Color, Type, Spacing, Theme
└── (data/, di/, feature-*/ to come)
```

## Design system

Defined in `ui/theme/`. **Use the tokens; never hard-code a hex or a dp.**

- `Color.kt` — the Vedant palette. These are the *same values* as the customer
  PWA's `@theme` tokens in `src/app/globals.css`. If a genuinely new role is
  needed, add it to the PWA token set first.
- `Spacing.kt` — 4 dp base scale plus `Radius` and `Sizing`
  (`Sizing.touchTarget = 48.dp` — the accessibility floor).
- `Type.kt` — flatter than a marketing scale; **12 sp is the floor** for anything
  a person must read.
- `Theme.kt` — `VedantAdminTheme`, `VedantTheme.spacing/radius/sizing`.

Notes:
- **No dynamic colour.** A status colour must mean exactly one thing everywhere;
  letting the wallpaper repaint "ready" or "cancelled" is a usability regression.
- Status is communicated by **word + icon shape + colour**, never colour alone
  (WCAG 1.4.1).
- Motion: prefer `AnimatedVisibility` / `animate*AsState` / `AnimatedContent`.
  Motion must never delay an operational action, and should respect
  reduced-motion settings.

## Backend contract (essentials)

- `order_status`: `PENDING | CONFIRMED | PROCESSING | READY_FOR_DELIVERY |
  OUT_FOR_DELIVERY | DELIVERED | CANCELLED`. `DELIVERED`/`CANCELLED` are terminal.
- Admin RPCs: `admin_update_order_status`, `admin_reject_order_item`,
  `admin_restore_order_item`, `admin_slot_load`, `ensure_delivery_slots`,
  `admin_list_admins`, `admin_grant_admin`, `admin_revoke_admin`.
- `orders` has **two** slot columns — `slot_id` (legacy) and `delivery_slot_id`.
  Write both, read either.
- `order_items` uses **`qty`**, not `quantity`.
- `complaint_status`: `OPEN | IN_PROGRESS | RESOLVED | CLOSED`.
- `complaint-images` object paths **must** be `<auth.uid()>/<filename>` — the
  storage policy reads the first path segment to authorise the owner.

Full detail: `PROJECT_INDEX.md` section "Database Contract".

## Testing rules

- **Unit-test the domain, not the framework.** Order state machine, money
  arithmetic, validation, mappers, and repository logic with a fake data source.
- Compose UI tests for: login, dashboard, order list, order detail, accepting an
  order, rejecting an item, catalogue, product editing, complaints.
- Gates to run before calling anything done:
  `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.
- **Never report a test as passing unless it was actually run.** Distinguish
  IMPLEMENTED / TESTED / VERIFIED / BLOCKED / NOT YET CONFIGURED.

## Local environment notes (this machine)

- `JAVA_HOME` is unset; the JDK is pinned in `gradle.properties`
  (`org.gradle.java.home`), JDK 17 Temurin.
- **Use forward slashes in `.properties` files.** A backslash is an escape
  character there and silently mangles Windows paths — this already cost one
  debugging cycle.
- AGP 8.7.3 / Kotlin 2.0.20 / Gradle 8.12 are pinned to match the local Gradle
  cache, keeping builds fast and offline-friendly.
- `cmdline-tools` and any AVD are missing, so `sdkmanager` and emulator runs need
  setup first; `platform-tools/adb.exe` exists but is not on `PATH`.
- Supabase credentials live in `local.properties` (git-ignored), surfaced as
  `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.

## Working with the Supabase MCP

- **Issue exactly one tool call per response when calling a Supabase MCP tool.**
  Mixing an `mcp__supabase__*` call with any other tool call in the same response
  makes the MCP call fail with `ZodError: expected string, received undefined`.
- `execute_sql` returns **only the last result set** — aggregate into a single
  `select jsonb_build_object(...)` rather than issuing several selects.
- Never run destructive DDL or data changes against production without explicit
  confirmation.

## Session hygiene

At the end of every significant session, update **both** `PROJECT_INDEX.md` and
`HANDOVER.md`. `HANDOVER.md` must always end with an explicit **NEXT EXACT
ACTION** and a **DO NOT REPEAT** list so the next session does not rediscover the
same traps.