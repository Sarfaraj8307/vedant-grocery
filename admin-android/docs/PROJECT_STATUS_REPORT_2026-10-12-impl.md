# Project Status Report — Vedant Admin (Android)

**Date:** 2026-10-12 · **Scope:** all 29 PRD capabilities mapped to implementing task(s)
and to their **true** verification status.

> ## The honesty rule (PRD §8.2, CLAUDE.md)
> Nothing is marked **VERIFIED** unless it was **executed and observed**. Two
> blockers forbid live verification here:
> **R1** — no admin credentials; **R2** — no device/emulator.
> Everything that depends on the live server or on hardware is therefore
> **IMPLEMENTED-BUT-UNVERIFIED** (proven against fakes) or **BLOCKED**, never
> VERIFIED. The literal gate evidence for this report:
> `./gradlew test assembleDebug lintDebug` → **BUILD SUCCESSFUL in 7m 24s**;
> `test` **410 tests / 37 suites / 0 failures / 0 errors / 0 skipped**;
> `assembleDebug` → `app-debug.apk` **23,831,166 bytes**; `lintDebug` **0 errors**
> (14 warnings); `assembleDebugAndroidTest` **compiles green** (execution blocked).

**Status legend**
- **VERIFIED** — executed and observed (with the proof).
- **IMPLEMENTED-BUT-UNVERIFIED** — code exists and is covered against fakes; the
  live/hardware path that proves it end-to-end is blocked (reason given).
- **BLOCKED** — cannot be verified here; what is needed to unblock is given.

---

## Capability map (all 29)

| # | Capability (PRD §3.4) | Task(s) | Status | Evidence / reason |
|---|---|---|---|---|
| 1 | Auth | T07–T10 | **IMPLEMENTED-BUT-UNVERIFIED** | `AuthRepositoryImplTest` 8 · `SignInScreenTest` (compiles). Live sign-in **BLOCKED (R1)** |
| 2 | Admin authz | T07–T08 | **IMPLEMENTED-BUT-UNVERIFIED** | Server `is_admin()` gate; `AuthRepositoryImplTest`; live allow/deny **BLOCKED (R1)** |
| 3 | Dashboard | T20 (shell) / wiring | **IMPLEMENTED-BUT-UNVERIFIED** | `DashboardScreenTest` (compiles). `dashboardCounts()` exists but **tiles not wired**; live counts **BLOCKED (R1)** |
| 4 | Orders (list) | T14–T15 | **IMPLEMENTED-BUT-UNVERIFIED** | `OrdersListViewModelTest` 7 · `OrderRepositoryImplTest` 10. Live data **BLOCKED (R1)** |
| 5 | Order detail | T13–T16 | **IMPLEMENTED-BUT-UNVERIFIED** | `OrderMapperTest` 17 · `OrderDetailViewModelTest` 13. Live data **BLOCKED (R1)** |
| 6 | Status workflow | T14, T16 | **IMPLEMENTED-BUT-UNVERIFIED** | `OrderStateMachineTest` 8 · `OrderStatusTest` 14 · `OrderDetailViewModelTest`. Live RPC **BLOCKED (R1)** |
| 7 | Item rejection/restoration | T17 | **IMPLEMENTED-BUT-UNVERIFIED** | `OrderDetailViewModelTest` 13 (required reason, server totals). Live RPC **BLOCKED (R1)** |
| 8 | Server-authoritative adjustments | T05, T14, T17 | **VERIFIED (logic)** | Money is display-only; the client sends no amount — asserted in `OrderRepositoryImplTest` / `OrderDetailViewModelTest`; the DB CHECKs (`adjustment_amount<=0`) are live and verified in `HANDOVER.md`. Live end-to-end **BLOCKED (R1)** |
| 9 | Catalogue | T18–T20 | **IMPLEMENTED-BUT-UNVERIFIED** | `CatalogueMapperTest` 14 · `CatalogueRepositoryImplTest` 19 · `CatalogueHubViewModelTest` 12. Live reads/writes **BLOCKED (R1)** |
| 10 | Categories | T18–T20 | **IMPLEMENTED-BUT-UNVERIFIED** | `CategoriesViewModelTest` 8 · `CatalogueMapperTest`. Live writes **BLOCKED (R1)** |
| 11 | Products | T18–T20 | **IMPLEMENTED-BUT-UNVERIFIED** | `ProductEditorViewModelTest` 12 · `CatalogueRepositoryImplTest` (write-both pairs). Live writes **BLOCKED (R1)** |
| 12 | Weight variants | T18–T20 | **IMPLEMENTED-BUT-UNVERIFIED** | `WeightsViewModelTest` 9. Live writes **BLOCKED (R1)** |
| 13 | Availability toggle | T20 | **IMPLEMENTED-BUT-UNVERIFIED** | Hub toggle moves only after server confirm (`CatalogueHubViewModelTest`); DEFECT-1 offline gating added + `OfflineGatingScreenTest` (compiles). Live **BLOCKED (R1)** |
| 14 | Image upload | T19 | **IMPLEMENTED-BUT-UNVERIFIED** | `CatalogueRepositoryImplTest` 19 (progress, retry, server-confirmed URL). Live `product-images` upload **BLOCKED (R1)** |
| 15 | Offers | T18–T20 | **IMPLEMENTED-BUT-UNVERIFIED** | `OffersViewModelTest` 11. Live writes **BLOCKED (R1)** |
| 16 | Delivery slots | T21 | **IMPLEMENTED-BUT-UNVERIFIED** | `SlotMapperTest` 7 · `SlotRepositoryImplTest` 12 · `SlotsViewModelTest` 14. Live RPC (`admin_slot_load`, `ensure_delivery_slots`) **BLOCKED (R1)** |
| 17 | Complaints | T22 | **IMPLEMENTED-BUT-UNVERIFIED** | `SupportMapperTest` · `ComplaintRepositoryImplTest` (path `<uid>/<file>`, `author_role=ADMIN`); `ComplaintsListScreenTest` (compiles). Live writes **BLOCKED (R1)** |
| 18 | Store settings | T23 | **IMPLEMENTED-BUT-UNVERIFIED** | `SettingsMapperTest` · `StoreSettingsRepositoryImplTest` (echoed row); `StoreNameWarning`. Live writes **BLOCKED (R1)** |
| 19 | Profile / session | T23 | **IMPLEMENTED-BUT-UNVERIFIED** | `ProfileRepositoryImplTest` (server `is_admin()` role; `SessionExpired` with no session). Live **BLOCKED (R1)** |
| 20 | Error handling | T04 | **VERIFIED (logic)** | `ErrorMapperTest` 18 · `ErrorCopyTest` (incl. OBS-1 5-digit-survival regression) — all pass in `./gradlew test` |
| 21 | Loading / empty states | T09 | **VERIFIED (logic/rendering)** | `StateScaffold` 4-phase model; covered across the Compose suites (compile) and `ui/components`; `DesignTokenGuardTest` |
| 22 | Offline / resilience | T24 (+T26a) | **VERIFIED (logic)** | `CacheStoreTest` · `SafeReadRetryTest`; "mutation never cached/queued" asserted; DEFECT-1 offline gating added to all 6 surfaces. Live offline behaviour on hardware **BLOCKED (R2)** |
| 23 | Animations | T25 | **IMPLEMENTED-BUT-UNVERIFIED** | `LocalReduceMotion` + `Animated*` in `StateScaffold`/skeletons; physical motion **BLOCKED (R2)** |
| 24 | Scrolling | T15, T20, T25 | **VERIFIED (logic)** | Stable keys + `LazyColumn` best practice asserted in ViewModel/UI suites (compile); no obsolescence reported by lint |
| 25 | Accessibility | T25 | **IMPLEMENTED-BUT-UNVERIFIED** | 48 dp / 12 sp / contrast asserted in `ThemeContrastTest` + `DesignTokenGuardTest` (pass on JVM); **on-device / screen-reader BLOCKED (R2)** |
| 26 | Dark mode | T25 | **VERIFIED (logic)** | `ThemeContrastTest` audits both schemes on JVM; **3 real failures found & fixed**. Physical observation **BLOCKED (R2)** |
| 27 | Automated testing | T26 | **VERIFIED (JVM)** | `./gradlew test` **410/410, 37 suites, 0 failures**; `assembleDebugAndroidTest` compiles. Instrumented **execution BLOCKED (R2)** |
| 28 | End-to-end verification | T27 (plan) | **BLOCKED (R1 + R2)** | `docs/E2E.md` — plan written, **NOT executed**; needs admin account + device |
| 29 | Production documentation | T27–T28, P0-0 | **VERIFIED (docs)** | `README.md`, `docs/E2E.md`, `HANDOVER.md`, `PROJECT_INDEX.md`, this report, `docs/ARCHITECTURE.md` §11.1 status table, `docs/CONTRACT.md`, `docs/SCHEMA_SNAPSHOT.md` |

---

## Rollup

| Status | Count | Capabilities |
|---|---|---|
| **VERIFIED** | 8 | 8, 20, 21, 22, 24, 26, 27, 29 |
| **IMPLEMENTED-BUT-UNVERIFIED** | 17 | 1–7, 9–19, 23, 25 |
| **BLOCKED** | 1 | 28 |
| **NOT-YET-CONFIGURED** | 0 | — |

> Capability 3 (Dashboard) is **IMPLEMENTED-BUT-UNVERIFIED** with an additional
> defect: the counts tiles are **not yet wired** to the existing
> `dashboardCounts()`. This is honest and recorded in `PROJECT_INDEX.md`.

## What would move the needle

Clearing **R1** (an admin account) converts capabilities 1–7, 9–19 from
IMPLEMENTED-BUT-UNVERIFIED to VERIFIED by executing `docs/E2E.md` §A–E.
Clearing **R2** (a device/emulator) converts 23, 25 and the instrumented half of
27, plus the physical half of 26. Clearing **both** converts 28.

Until then, **no capability that depends on the live server or hardware is
VERIFIED**, and this report says so.
