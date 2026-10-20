# Vedant Admin (Android)

The **store-owner operations app** for Vedant Fruit & Vegetables. A separate
Android application that talks to the **same Supabase project** as the customer
PWA (`hyibaljxicojfoxbihdn`). It is **not** an extension of the web app — the only
shared boundary is the backend contract.

For the operators it replaces the "phone the store" workflow with a native app:
accept/advance orders, reject and restore line items, manage the catalogue
(products, categories, weight variants, offers), manage delivery slots, answer
customer complaints, edit store settings, and see the signed-in admin's identity.

---

## Prerequisites

| Tool | Version |
|---|---|
| JDK | **17** (Temurin) — `JAVA_HOME` is unset on the dev box; the JDK is pinned in `gradle.properties` via `org.gradle.java.home` |
| Android SDK | **API 35** (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`) |
| Gradle | 8.12 (wrapper committed — always use `./gradlew`) |
| AGP / Kotlin | 8.7.3 / 2.0.20 (pinned to match the local Gradle cache) |

`local.properties` (git-ignored) supplies `SUPABASE_URL` and `SUPABASE_ANON_KEY`,
surfaced to code as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.
**Only the publishable key may ever appear in the APK.**

---

## Build

```bash
cd admin-android
./gradlew assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device/emulator with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

---

## Tests

```bash
./gradlew test        # JVM unit tests (domain, mappers, repos, ViewModels)
./gradlew lintDebug   # Android lint
```

Instrumented (Compose UI) tests live in `app/src/androidTest/`. They need a
device or emulator and currently **cannot execute here** (no AVD) — the ceiling is
that they *compile*:

```bash
./gradlew assembleDebugAndroidTest
```

> `./gradlew test` runs the **JVM** suites only. Instrumented tests are not run by
> `test`; see `docs/E2E.md` for the manual end-to-end plan (BLOCKED on
> credentials + device).

---

## Package layout

Single-module (`:app`), Kotlin + Jetpack Compose + Material 3, MVVM/UDF, hand-rolled
DI (**no Hilt, no KSP, no Room**). Clean-Architecture layering:

```
Presentation (Compose + ViewModel, immutable UiState, one-shot events)
        |
Domain (models, use cases, business rules — no Android/Supabase imports)
        |
Data (repositories, Supabase/PostgREST, DataStore cache)
        |
Supabase (PostgREST / Auth / Storage) + DataStore + WorkManager
```

```
app/src/main/java/com/vedant/admin/
├── MainActivity.kt            single activity host (provides LocalOnlineStatus)
├── VedantAdminApplication.kt  builds AppContainer
├── core/                      money · result (Outcome) · error · time · logging · network
├── domain/                    model/ · usecase/ · repository/ (interfaces only)
├── data/                      remote/ (SDK quarantined here) · local/ · session/ · repository/
├── di/                        AppContainer + AppViewModelFactory (the Hilt seam)
├── ui/                        theme/ · components/ · navigation/
└── feature/                   auth · dashboard · orders · catalogue · slots · support · settings
```

Key rules (see `CLAUDE.md`): **no business logic in composables**, **no Supabase
calls from UI**, **never fake success** (every mutation returns the re-fetched
server row), **money is server-authoritative** (the client never sends an amount),
**admin authorization is server-side only** (`private.is_admin()`).

---

## Documentation map

| File | What it holds |
|---|---|
| `CLAUDE.md` | hard project rules for an AI session |
| `HANDOVER.md` | live, in-flight state; ends with NEXT EXACT ACTION + DO NOT REPEAT |
| `PROJECT_INDEX.md` | persistent project memory / index |
| `docs/ARCHITECTURE.md` | system design, package layout, task list, status table |
| `docs/CONTRACT.md` | authoritative live backend contract (Phase 0) |
| `docs/E2E.md` | the end-to-end test plan (currently **BLOCKED**) |
| `docs/PRD.md` | product requirements + the 29 capabilities |
| `docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md` | capability → task → verification map |
