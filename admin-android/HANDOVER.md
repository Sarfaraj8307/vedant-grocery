# Vedant Admin Android — HANDOVER

> Read this **first**, then `PROJECT_INDEX.md`. Both are updated at the end of
> every significant work session.
>
> **Last updated:** 2026-10-12 (session 7 — T26 close-out: DEFECT-1 offline gating on all 6 surfaces, OBS-1 regex fix + regression tests, OBS-2/3 doc fixes, T27/T28 docs gate — green)

---

## CURRENT PHASE

**PHASE 6 (T26–T28).** **Complete and green.** The final hardening pass is done:
DEFECT-1 closed (all 9 mutation surfaces now gate on `LocalOnlineStatus`), OBS-1
closed (`ErrorCopy.sanitize` allowlist + regression tests), OBS-2/OBS-3 doc
corrections, and the T27/T28 documentation gate (README, E2E plan, status tables).
All gates pass (`assembleDebug`, `test` **410/410**, `lint` 0 errors,
`assembleDebugAndroidTest` compiles). Phases 1–5 remain green underneath.

> **Live RPCs, writes and uploads are still [BLOCKED]** — there are no admin
> credentials (R1). Every complaint reply/status/attachment, every settings save
> and the profile role are proven against a **fake remote source** / **fake
> repository**; no live `complaints` / `complaint_messages` / `complaint_attachments`
> / `store_settings` write and no `complaint-images` upload has been exercised.
> **Physical UI / accessibility / animation verification is [BLOCKED without a
> device]** — the contrast, token and typography checks below are asserted in unit
> tests, not observed on hardware.

## CURRENT TASK

Phases 0–6 are done (T01–T28; T02 is the one **BLOCKED (R1)** probe). The Android
**client** is feature-complete for the PRD's 29 capabilities. What remains is
**not engineering** — it is the two blockers: **R1** (admin credentials) and **R2**
(a device/emulator), which gate all live and hardware verification. See
`docs/E2E.md` (BLOCKED) and `docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md`.

## LAST COMPLETED

**Session 7 — T26 close-out + T27/T28 docs gate.**

- **DEFECT-1 (offline gating, MEDIUM).** Six of nine mutation surfaces let the
  user press an action while offline. All six now use the existing
  `LocalOnlineStatus` CompositionLocal + `OfflineHint`:
  catalogue-hub availability `Switch`, product-editor save, categories/offers/
  weights/slots "create/save" buttons. Gating is **additive**
  (`enabled = <existing-precondition> && online`) so it never couples to
  loading/saving state. Added `OfflineGatingScreenTest` (androidTest, **compiles**;
  execution BLOCKED R2) covering hub + 4 forms offline (disabled + hint) and
  online (enabled).
- **OBS-1 (over-redaction, LOW).** `ErrorCopy.sanitize` no longer strips *any*
  five-digit number; it strips only the SQLSTATEs actually mapped (42501, 22023,
  23514, 23505, 23503) + `PGRST\d+`. Regression tests assert a ₹50000 / `12345`
  survive while a mapped code is stripped (**+3 tests**).
- **OBS-2 / OBS-3 (docs).** Fixed `docs/ARCHITECTURE.md` §10 (the orders
  slot pair is **read-preferred with a read-only fallback to `slot_id`**, not
  write-both) and §2 (as-built naming: `WeightsScreen` not `WeightVariantsScreen`).
- **T27.** Created `README.md`, `docs/E2E.md` (**BLOCKED** at the top).
- **T28.** Added the T01–T28 status table to `docs/ARCHITECTURE.md` §11.1;
  created `docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md` (all 29 capabilities
  mapped with true verification status).

**`assembleDebug` green (debug APK 23,831,166 B ≈ 22.7 MB, rebuilt); `test`
410/410 green (37 suites); `lint` 0 errors (14 warnings);
`assembleDebugAndroidTest` compiles green.**

Previously (session 6): **Phase 4 (T22–T23) + Phase 5 (T24–T25)** — complaints,
store settings & profile, offline cache/read-retry, a11y/dark-mode QA — green
(`test` 349/349). (session 5): **Phase 3 (T18–T21)** — catalogue, products,
offers, slots, image upload — green (`test` 247/247). (session 4): **Phase 2
(T12–T18)** — orders core, status workflow, reject/restore — green (`test`
129/129). (session 3): **Phase 1 (T03–T11)** — client, DI, auth/admin gate,
shared UI kit — green (`test` 74/74).

### Session-6 detail — Phase 4 (T22–T23) + Phase 5 (T24–T25)

- **T22 complaints** — `Complaint` domain (tolerant `ComplaintStatus` with
  **`IN_PROGRESS`** live and `IN_REVIEW` retired, `AuthorRole`, `ThreadEntry` +
  `buildThread`), the complaint DTOs + `SupportMapper`, `ComplaintRemoteSource`,
  `ComplaintRepositoryImpl` (reply written as `author_role = 'ADMIN'`; attachment
  path **`<auth.uid()>/<complaintId>_<stamp>_<file>`** so the storage RLS owner
  check passes; 5 MB / jpeg|jpg|png|webp), and the two screens: a filterable
  paginated list and a **messaging-style thread** (customer left, store right,
  centred status events, pinned composer reflecting pending state, attachments
  strip). `SupportScreen` is now a thin alias of the list.
- **T23 settings + profile** — `StoreSettings` (+ `StoreSettingsDraft`),
  `OperatorProfile`, the settings/profile DTOs + `SettingsMapper`, the two
  repositories (settings save returns the echoed row; profile role is the
  **server's `is_admin()`**, never a cached column), and the two screens: grouped
  store settings (Store / Opening / Delivery & order limits) with a
  **`StoreNameWarning`** that surfaces the live `store_name = "Fresh Mart"`
  mismatch without rewriting it, and a profile screen whose role badge reads
  "confirmed by the server (is_admin())", plus a destructive sign-out.
- **T24 offline** — `CacheStore` (`DataStore`-backed JSON snapshots via
  kotlinx-serialization; **not Room**) + `SafeReadRetry` (`ReadRetryScheduler` /
  `WorkManagerReadRetryScheduler` / `SafeReadWorker`, safe **reads only**),
  `ConnectivityObserver`, the `LocalOnlineStatus` composition-local and the
  persistent "Offline — showing saved data" banner wired through `StateScaffold`.
  Order/product page-0 reads cache and fall back **only** on `AppError.Network`;
  **a mutation is never written to the cache as a success** (asserted:
  `cache.entries.isEmpty()` after a mutation) and mutations are **disabled, not
  queued**, while offline.
- **T25 a11y + dark-mode QA** — `LocalReduceMotion` respected by the skeletons,
  `StateScaffold` transitions and the offline banner (no new animation library);
  the offline banner is a polite `liveRegion`; the 12 sp type floor and the 48 dp
  touch target are asserted; and a **WCAG AA contrast audit** now runs in
  `ThemeContrastTest`. That audit **found and fixed three real failures**: the
  storefront `Success` (3.67:1) and `Warning` (3.85:1) were unreadable as 12 sp
  badge text on their own tints (darkened to `#157A47` / `#9A5C04` → 4.77 / 4.89),
  and dark-mode `onError` was white on the light destructive fill (1.70:1; now
  `OnDangerDark` `#690005` → 7.72). A `DesignTokenGuardTest` enforces "no
  hard-coded hex/dp/sp outside `ui/theme`".

**`assembleDebug` green (APK 23,831,166 B ≈ 22.7 MB); `test` 349/349 green (33
suites); `lint` 0 errors (14 warnings).**

Previously (session 5): **Phase 3 (T18–T21)** — catalogue, products, offers,
slots, image upload — green (`test` 247/247). (session 4): **Phase 2 (T12–T18)** —
orders core, status workflow, reject/restore — green (`test` 129/129).
(session 3): **Phase 1 (T03–T11)** — client, DI, auth/admin gate, shared UI kit —
green (`test` 74/74).


## FILES CREATED

| File | Purpose |
|---|---|
| `admin-android/HANDOVER.md` | this file |
| `admin-android/PROJECT_INDEX.md` | persistent project memory / index |
| `admin-android/docs/CONTRACT.md` | authoritative live backend contract (Phase 0) |
| `admin-android/docs/SCHEMA_SNAPSHOT.md` | regenerable live column snapshot (Phase 0) |
| `admin-android/docs/probe_authed_columns.sh` | T02 probe for the RLS-hidden columns (BLOCKED) |

**Android source files (session-2 scaffold — 7 Kotlin files, all still present):**

| File | Purpose |
|---|---|
| `app/src/main/java/com/vedant/admin/MainActivity.kt` | single-activity host — now hosts `VedantNavHost` (T08) |
| `app/src/main/java/com/vedant/admin/domain/model/OrderStatus.kt` | live enum + labels + transition hints (untouched) |
| `app/src/main/java/com/vedant/admin/ui/theme/Color.kt` | design-system palette (untouched) |
| `app/src/main/java/com/vedant/admin/ui/theme/Type.kt` | typography scale (untouched) |
| `app/src/main/java/com/vedant/admin/ui/theme/Spacing.kt` | spacing / radius / sizing tokens (extended additively) |
| `app/src/main/java/com/vedant/admin/ui/theme/Theme.kt` | `VedantAdminTheme` + `VedantTheme` (untouched) |
| `app/src/test/java/com/vedant/admin/domain/model/OrderStatusTest.kt` | 14 passing unit tests (untouched) |

**Phase-1 files (T03–T11 — the app now builds and runs the auth flow):**

| File | Purpose |
|---|---|
| `core/money/Money.kt` | exact integer-paise money (`@JvmInline value class`), tolerant `parse` + `format`/`toPlainString` |
| `core/money/MoneySerializer.kt` | kotlinx-serialization codec for `numeric(12,2)` money |
| `core/result/Outcome.kt` | `Success | Failure(AppError)` + `outcomeOf` (rethrows `CancellationException`) |
| `core/error/AppError.kt` | closed error taxonomy (6 variants) |
| `core/error/ErrorMapper.kt` | SQLSTATE/PostgREST → `AppError` (sole code consumer) |
| `core/error/ErrorCopy.kt` | sole producer of user-facing strings; strips raw codes |
| `core/time/AppClock.kt` | Asia/Kolkata clock + PostgREST time helpers |
| `core/logging/AppLog.kt` | redacting logger (JWT / `sb_*` key / Bearer / email) |
| `data/remote/SupabaseProvider.kt` | builds the one client — Auth+Postgrest+Storage, publishable key only |
| `data/remote/AdminRpc.kt` | `is_admin()` RPC wrapper (fails closed) |
| `data/remote/dto/ProfileDto.kt` | wire DTO for `profiles` |
| `data/remote/mapper/ProfileMapper.kt` | DTO → domain |
| `data/session/SessionStore.kt` | persistent session + `StateFlow<SessionState>` |
| `data/repository/AuthRepositoryImpl.kt` | composes session + server admin gate |
| `domain/repository/AuthRepository.kt` | auth + server-authz interface |
| `domain/model/{SessionState,AdminIdentity,Profile}.kt` | domain models |
| `di/AppContainer.kt` | hand-rolled container (no Hilt/KSP) |
| `di/ViewModelFactory.kt` | constructor-injection ViewModel factory |
| `VedantAdminApplication.kt` | installs log sink + builds container (registered in manifest) |
| `ui/navigation/{Routes,VedantNavHost,SessionViewModel}.kt` | graph + declarative auth/admin gate |
| `ui/components/{StateScaffold,StatusBadge,VedantTopBar,VedantButtons}.kt` | shared UI kit |
| `feature/auth/{SplashScreen,SplashViewModel,SplashUiState}.kt` | S1 splash / session restore |
| `feature/auth/{SignInScreen,SignInViewModel,SignInUiState}.kt` | S2 sign-in (double-submit blocked) |
| `feature/auth/{NotAuthorizedScreen,NotAuthorizedViewModel,NotAuthorizedUiState}.kt` | S3 refusal (no admin data, sign-out only) |
| `feature/dashboard/DashboardScreen.kt` | S4 Phase-1 placeholder (carries the shell forward) |
| `app/src/test/.../SecurityKeyHygieneTest.kt` | publishable-key guard (falsifiable) — 7 tests |
| `app/src/test/.../core/error/ErrorMapperTest.kt` | error mapping table — 18 tests |
| `app/src/test/.../core/money/MoneyTest.kt` | money parse/format/serialize — 22 tests |
| `app/src/test/.../data/repository/AuthRepositoryImplTest.kt` | server authz composition — 8 tests |
| `app/src/test/.../ui/navigation/SessionViewModelTest.kt` | gate mapping — 5 tests |

Also modified: `app/build.gradle.kts` (added `multiplatform-settings-no-arg`
1.1.1 + Turbine 1.1.0; **no Hilt/KSP/Room**), `app/proguard-rules.pro` (wire-DTO
keep rules), `app/src/main/AndroidManifest.xml` (`android:name` for the
Application).

**Phase-2 files (T12–T18 — the orders core):**

| File | Purpose |
|---|---|
| `domain/model/Order.kt` | `Order` + `OrderItem` + `OrderStatusEvent`; money is display-only, `finalTotal` rendered verbatim |
| `domain/model/OrderSummary.kt` | list row + `shortId`/`statusLabel`/`customerLabel` |
| `domain/model/OrderFilter.kt` | status facet + search term (server-side) |
| `domain/model/DashboardCounts.kt` | counts by status + total |
| `domain/usecase/OrderTransitions.kt` | client state machine (convenience; server authoritative) |
| `data/remote/dto/OrderDto.kt` | wire DTO — **both** `delivery_slot_id` + `slot_id` |
| `data/remote/dto/OrderItemDto.kt` | wire DTO — `qty` (never `quantity`) |
| `data/remote/dto/OrderStatusHistoryDto.kt` | timeline DTO — status fields as `String` |
| `data/remote/mapper/OrderMapper.kt` | DTO → domain; §10 read/fallback + tolerant money/timestamp parse |
| `data/remote/OrderRemoteSource.kt` | PostgREST reads (+ best-effort `profiles` embed with `PGRST2xx` retry) |
| `domain/repository/OrderRepository.kt` | reads + the 3 mutations (each returns the re-fetched row) |
| `data/repository/OrderRepositoryImpl.kt` | composes remote reads + admin RPCs; re-fetch after every mutation |
| `ui/components/Skeletons.kt` | `SkeletonBlock` / `OrderRowSkeleton` / `OrderListSkeleton` |
| `ui/components/SectionCard.kt` | `SectionCard` + `DetailRow` (receipt building blocks) |
| `feature/orders/list/{OrdersListScreen,OrdersListViewModel,OrdersListUiState}.kt` | S5 — paginated list, filter, debounced search, pull-to-refresh, skeleton, stable keys |
| `feature/orders/detail/{OrderDetailScreen,OrderDetailViewModel,OrderDetailUiState}.kt` | S6 — receipt view, status workflow (server truth, no double-submit) |
| `feature/orders/detail/RejectItemSheet.kt` | T17 — reject with **required** reason + restore |
| `feature/support/SupportScreen.kt` | complaint-link destination (Phase-3 placeholder, like the Phase-1 dashboard) |
| `app/src/test/.../testing/{MainDispatcherRule,OrderTestData}.kt` | test dispatcher rule + fake `OrderRepository` + builders |
| `app/src/test/.../feature/orders/detail/OrderDetailViewModelTest.kt` | T16/T17 acceptance — 13 tests |
| `app/src/test/.../feature/orders/list/OrdersListViewModelTest.kt` | T15 acceptance — 7 tests |

**Modified in Phase 2:** `data/remote/AdminRpc.kt` (+3 mutation wrappers),
`data/remote/SupabaseProvider.kt` (tolerant `KotlinXSerializer`),
`core/error/ErrorMapper.kt` (+`NoSuchElementException` → `AppError.Server`),
`di/AppContainer.kt` (+`orderRepository`), `di/ViewModelFactory.kt` (+2 ViewModels),
`ui/navigation/{Routes,VedantNavHost}.kt` (order + support routes),
`feature/dashboard/DashboardScreen.kt` (+"Manage orders" entry point).

**Phase-3 files (T18–T21 — catalogue, products, offers, slots, images):**

| File | Purpose |
|---|---|
| `domain/model/{Product,Category,ProductWeight,Offer}.kt` | catalogue domain + drafts + `ProductQuery`; no legacy column name above the data layer |
| `domain/model/DeliverySlot.kt` | `DeliverySlot` + `SlotLoad` + `DeliverySlotDraft`; `day`/`startTimeIst`/`endTimeIst` for editor pre-fill |
| `data/remote/dto/{ProductDto,CategoryDto,ProductWeightDto,OfferDto,DeliverySlotDto,SlotLoadDto}.kt` | wire DTOs carrying **both** members of every legacy pair; `SlotLoadDto` declares every plausible alias |
| `data/remote/mapper/{CatalogueMapper,SlotMapper}.kt` | read fallback + `toWriteFields()` writing both columns; `icon := glyph`; `start_time = IST wall-clock of starts_at` |
| `domain/repository/{ProductRepository,CategoryRepository,WeightRepository,OfferRepository}.kt` | contracts; every write returns the **server-confirmed row**; `MAX_IMAGE_BYTES` 5 MB, allowed mime jpeg/jpg/png/webp |
| `domain/repository/SlotRepository.kt` | `listSlots`/`slotLoad`/`ensureSlots`/`saveSlot`/`deleteSlot` |
| `data/remote/CatalogueRemoteSource.kt` | PostgREST reads/writes; every insert/update uses `{ select() }` so the stored row is echoed |
| `data/remote/StorageDataSource.kt` | `product-images` upload with real byte progress (`onUpload`); returns `publicUrl(response.path)` |
| `data/remote/SlotRemoteSource.kt` | bounded on `starts_at` (`gte`/`lt`); `select()` on writes |
| `data/repository/{ProductRepositoryImpl,CategoryRepositoryImpl,WeightRepositoryImpl,OfferRepositoryImpl}.kt` | compose remote + storage; validate image before sending; `notFound()` → `NoSuchElementException` → `AppError.Server` |
| `data/repository/SlotRepositoryImpl.kt` | half-open window via `MILLIS_PER_DAY`; validates `endsAt > startsAt` and `capacity > 0` |
| `ui/components/ActionState.kt` | `Idle \| InFlight \| Failed` — promoted out of `OrderDetailUiState` to break a feature→feature dependency |
| `ui/components/DateField.kt` | Material 3 date picker; converts the picker's UTC midnight to an IST start-of-day instant |
| `feature/catalogue/ProductThumb.kt` | Coil 3 `AsyncImage` with a glyph fallback in a fixed-size box |
| `feature/catalogue/hub/{CatalogueHubScreen,CatalogueHubViewModel,CatalogueHubUiState}.kt` | paging, debounced search, category + availability facets (server-side), inline availability `Switch` that only moves after the server confirms |
| `feature/catalogue/editor/{ProductEditorScreen,ProductEditorViewModel,ProductEditorUiState,ImageProcessor,ImagePicker}.kt` | sections Basics / Pricing & weights / Photo / Visibility + sticky `SaveBar`; `UploadState = Idle \| InFlight(progress) \| Failed`; baseline-driven `isDirty`; down-sample to 1600 px + JPEG 85 |
| `feature/catalogue/categories/{CategoriesScreen,CategoriesViewModel,CategoriesUiState}.kt` | list + add/edit sheet + delete confirm |
| `feature/catalogue/weights/{WeightsScreen,WeightsViewModel,WeightsUiState}.kt` | inline row→form editing + trailing "Add variant" |
| `feature/catalogue/offers/{OffersScreen,OffersViewModel,OffersUiState}.kt` | date-range presentation; Active/Scheduled/Expired/Disabled badge per `statusLabel(now)` |
| `feature/slots/{SlotsScreen,SlotsViewModel,SlotsUiState}.kt` | day-schedule grouping, utilisation bars in error colour when full, `Full` pill, `ensureSlots` action |
| `app/src/test/.../testing/CatalogueTestData.kt` | builders + five fake repositories (with an availability gate) for the Phase-3 ViewModel tests |
| `app/src/test/.../data/remote/mapper/{CatalogueMapperTest,SlotMapperTest}.kt` | T18 mapper acceptance — 14 + 7 tests |
| `app/src/test/.../data/repository/{CatalogueRepositoryImplTest,SlotRepositoryImplTest}.kt` | T19/T21 write contract — 19 + 12 tests |
| `app/src/test/.../feature/catalogue/hub/CatalogueHubViewModelTest.kt` | T20 hub acceptance — 12 tests |
| `app/src/test/.../feature/catalogue/editor/ProductEditorViewModelTest.kt` | T20 editor + image acceptance — 12 tests |
| `app/src/test/.../feature/catalogue/categories/CategoriesViewModelTest.kt` | T20 categories acceptance — 8 tests |
| `app/src/test/.../feature/catalogue/weights/WeightsViewModelTest.kt` | T20 weights acceptance — 9 tests |
| `app/src/test/.../feature/catalogue/offers/OffersViewModelTest.kt` | T20 offers acceptance — 11 tests |
| `app/src/test/.../feature/slots/SlotsViewModelTest.kt` | T21 slots acceptance — 14 tests |

**Modified in Phase 3:** `app/build.gradle.kts` (+Coil 3 `coil-compose` /
`coil-network-okhttp` 3.0.4), `app/src/main/AndroidManifest.xml` (+`FileProvider`),
`res/xml/file_paths.xml` (NEW — `<cache-path name="captures" path="captures/" />`),
`data/remote/AdminRpc.kt` (+`ensureDeliverySlots`, +`adminSlotLoad`),
`ui/navigation/{Routes,VedantNavHost}.kt` (+7 destinations),
`di/AppContainer.kt` (+8 members), `di/ViewModelFactory.kt` (+6 ViewModels),
`feature/dashboard/DashboardScreen.kt` (+"Catalogue" / "Delivery slots" entries),
`feature/orders/detail/{OrderDetailUiState,OrderDetailViewModel,OrderDetailScreen}.kt`
(import the promoted `ActionState`), and the `FakeAdminRpc`s in
`OrderRepositoryImplTest` / `AuthRepositoryImplTest`.

**Phase-4 files (T22 — complaints / support):**

| File | Purpose |
|---|---|
| `domain/model/Complaint.kt` | `ComplaintStatus` (**`IN_PROGRESS`** live, `IN_REVIEW` retired, tolerant `fromWire`), `ComplaintType`, `AuthorRole`, `Complaint`, `ComplaintMessage`, `ComplaintAttachment`, `ComplaintFilter`, `ThreadEntry` (`Message`/`StatusEvent`) + pure `buildThread` |
| `domain/repository/ComplaintRepository.kt` | contract; `MAX_ATTACHMENT_BYTES` 5 MB, allowed mime jpeg/jpg/png/webp |
| `data/remote/dto/{ComplaintDto,ComplaintMessageDto,ComplaintAttachmentDto}.kt` | wire DTOs, enums kept as `String`, all-plausible aliases, every field nullable except `id` |
| `data/remote/mapper/SupportMapper.kt` | `toDomain()` ×3; `complaintReplyFields` sets **`author_role = ADMIN`** |
| `data/remote/ComplaintRemoteSource.kt` | PostgREST reads; `status` filter server-side; `{ select() }` on writes |
| `data/repository/ComplaintRepositoryImpl.kt` | composes remote + storage + auth; **`attachmentPath = "<uid>/<complaintId>_<stamp>_<file>"`**; refuses a blank session / oversized / non-image before sending |
| `feature/support/list/{ComplaintsListScreen,ComplaintsListViewModel,ComplaintsListUiState}.kt` | status chips + debounced search + paging + pull-to-refresh |
| `feature/support/thread/{ComplaintThreadScreen,ComplaintThreadViewModel,ComplaintThreadUiState}.kt` | messaging-style thread (customer left / store right / centred status events), pinned composer, pending reply state, attachments strip, status action menu |
| `feature/support/SupportScreen.kt` | **rewritten** as a thin alias of `ComplaintsListScreen` |

**Phase-4 files (T23 — store settings + profile):**

| File | Purpose |
|---|---|
| `domain/model/StoreSettings.kt` | `StoreSettings` + `StoreSettingsDraft`; `isNameSuspect` flags the live `"Fresh Mart"` name |
| `domain/model/OperatorProfile.kt` | server-resolved `isAdmin` + `roleLabel`; `storedRole` is display-only |
| `domain/repository/{StoreSettingsRepository,ProfileRepository}.kt` | contracts |
| `data/remote/dto/StoreSettingsDto.kt` | `StoreSettingsDto` (money via `MoneySerializer`) + editable-only `StoreSettingsUpdateDto` |
| `data/remote/mapper/SettingsMapper.kt` | `toDomain()`, `toWriteFields()`, `toDraft()` |
| `data/remote/SettingsRemoteSource.kt` | `readStoreSettings` / `updateStoreSettings` (`{ select() }`) / `readProfile` |
| `data/repository/{StoreSettingsRepositoryImpl,ProfileRepositoryImpl}.kt` | save returns the echoed row; profile returns `SessionExpired` when there is no session and **never** calls `is_admin()` in that case |
| `feature/settings/store/{StoreSettingsScreen,StoreSettingsViewModel,StoreSettingsUiState}.kt` | grouped cards + `StoreNameWarning` + pinned `SaveBar` (disabled offline) |
| `feature/settings/profile/{ProfileScreen,ProfileViewModel,ProfileUiState}.kt` | role badge "confirmed by the server (is_admin())"; destructive sign-out |

**Phase-5 files (T24 — offline cache + banner + read-retry):**

| File | Purpose |
|---|---|
| `data/local/CacheStore.kt` | `CacheStore` interface + `DataStoreCacheStore`; JSON snapshots via kotlinx-serialization. **No notion of a pending write** — the type shape makes "a mutation is never cached" structural |
| `data/local/SafeReadRetry.kt` | `ReadRetryScheduler` (**no method that could queue a mutation**) + `WorkManagerReadRetryScheduler` + `SafeReadWorker` |
| `core/network/ConnectivityObserver.kt` | `StateFlow<Boolean>` over `registerNetworkCallback` |
| `ui/components/Connectivity.kt` | `LocalOnlineStatus` (defaults `true`) |
| `ui/theme/Motion.kt` | `LocalReduceMotion` (defaults `false`) + `rememberReduceMotion()` |

**Phase-5 files (T25 — a11y / dark-mode QA):**

| File | Purpose |
|---|---|
| `app/src/test/.../ui/theme/ThemeContrastTest.kt` | WCAG AA audit for every text pair, both schemes (falsifiable) |
| `app/src/test/.../DesignTokenGuardTest.kt` | no hard-coded hex/dp/sp outside `ui/theme`; 48 dp target; 12 sp floor |

**Modified in Phase 4+5:** `app/build.gradle.kts` (+`datastore-preferences` 1.1.1,
+`work-runtime-ktx` 2.9.1), `data/remote/StorageDataSource.kt`
(+`uploadComplaintAttachment` / `signedComplaintUrl`, +`complaint-images` bucket),
`data/repository/{OrderRepositoryImpl,ProductRepositoryImpl}.kt` (optional cache +
retry; cache page 0 only; fall back only on `AppError.Network`),
`ui/components/StateScaffold.kt` (offline banner + reduced motion + `liveRegion`),
`ui/components/StatusBadge.kt` (+`ComplaintStatusBadge` / `complaintBadgeVisual`),
`ui/components/Skeletons.kt` (reduced motion; new `Sizing` placeholder tokens),
`ui/theme/{Color,Theme,Spacing}.kt` (contrast fixes, `OnDangerDark`, `internal`
schemes, placeholder tokens), `feature/{orders/detail,settings/store,support/thread}/*Screen.kt`
(mutations disabled offline), `MainActivity.kt` (provides `LocalOnlineStatus` +
`LocalReduceMotion`), `di/AppContainer.kt` (+`appScope` / `cacheStore` /
`connectivity` / `readRetryScheduler` / `refreshCachedReads`), `di/ViewModelFactory.kt`
(+4 ViewModels), `ui/navigation/{Routes,VedantNavHost}.kt` (+support/settings routes),
`data/repository/CatalogueRepositoryImplTest.kt` (fake storage gains the two new
members).

## FILES MODIFIED

Four customer-PWA files, to adopt the live `order_status` / `complaint_status`
enum names (owner decision). No other PWA file was touched.

| File | Change |
|---|---|
| `src/types/database.ts` | `OrderStatus` -> live enum names; `ComplaintStatus` `IN_REVIEW` -> `IN_PROGRESS` |
| `src/lib/format.ts` | `ORDER_STATUS_META` keys + `ORDER_STATUS_FLOW`; `COMPLAINT_STATUS_META` key |
| `src/components/orders/order-status-badge.tsx` | `STATUS_STYLES` keys + `?? STATUS_STYLES.PENDING` fallback |
| `src/app/orders/[id]/page.tsx` | `order.status === "PENDING"` (was `"ORDER_RECEIVED"`) |

Labels were preserved exactly — `PENDING` still reads "Order received",
`READY_FOR_DELIVERY` still reads "Ready to dispatch". Only the wire values moved.

## DATABASE CHANGES

Project `hyibaljxicojfoxbihdn`. **Five migrations applied, in this order:**

### 1. `fix_place_order_weight_pricing_and_slot` — VERIFIED

Two P0 production bugs:

1. **Checkout was 100% dead.** `place_order_app` called `public.place_order(...)`
   with **positional** args in the wrong order -> `42883`. Fixed by calling with
   **named arguments**.
2. **Every sub-1kg pack was overcharged.** `place_order` ignored
   `cart_items.weight_id` and priced every line at
   `least(price, discount_price)`. Verified overcharges: Capsicum 500g 44->80,
   Cucumber 500g 22->40, Ladyfinger 500g 33->60, Peas 500g 60.50->110.
   Now resolves the chosen `product_weights` row and applies the offer exactly
   like the client's `pickActiveOffer`/`priceFor`.

Also: 5-arg `place_order` was **not** `SECURITY DEFINER`; `delivery_slot_id`,
`original_total`, `final_total` were never populated; `weight_label` never
written. All fixed.

### 2. `admin_identity_roster_and_rls` — VERIFIED

`private.admin_users` + `private.is_admin()` + `public.is_admin()` mirror.
`admin_list_admins` / `admin_grant_admin` / `admin_revoke_admin` (refuses to
remove the last admin). Seeded owner **khansarfaraj.khan@gmail.com**
(`4bf3e5a5-2a93-44e7-b4f7-6e1e71180299`).

**Closed a live privilege-escalation hole:** `profiles_update_own` had only
`with_check (auth.uid() = id)`, and RLS `with_check` cannot see the OLD row — so
any signed-in customer could `PATCH /profiles {"role":"admin"}` and self-promote.
`handle_new_user` also hardcoded `admin@freshmart.local` -> admin. Both removed.

### 3. `admin_order_lifecycle_rpcs` — VERIFIED

- `record_order_status_history()` trigger — **this is why the customer timeline
  was permanently empty**: `notify_order_event` wrote the outbox but nothing ever
  wrote `order_status_history`. Backfilled.
- `private.order_transition_allowed(from,to)`, `admin_update_order_status`,
  `admin_reject_order_item`, `admin_restore_order_item`, `admin_slot_load`.

### 4. `fix_adjustment_sign_in_reject_restore` — VERIFIED

Fixed a **sign error in my own RPC**. I had written
`v_adjust := v_original - sum(rejected line totals)`, which is the *final total*,
not the adjustment — rejecting every item produced `adjustment_amount = +20.00`,
violating `CHECK (adjustment_amount <= 0)` with `23514`. Corrected to
`v_adjust := -sum(rejected line totals)`.

### 5. `storage_policies_slot_recurrence_admin_writes` — VERIFIED

- **7 storage policies.** `storage.objects` had **zero**, so every upload and
  read was denied. `product-images` public read + admin write;
  `complaint-images` owner-or-admin on the `<uid>/<file>` convention.
- **Recurring slots.** `private.generate_delivery_slots(days)` +
  admin-gated `public.ensure_delivery_slots(days)`, a unique index on
  `starts_at` (was allowing duplicates), backfilled **18 -> 42 slots**, horizon
  now **2026-10-24**, and a `pg_cron` job `vedant-delivery-slots`
  (`41 18 * * *` UTC = 00:11 IST) topping up a 14-day horizon daily.
- **14 admin write/read policies** for catalogue, support, settings, orders.

## TESTS RUN

### VERIFIED — checkout end to end (rolled-back txn, real customer through RLS)
Order `ORD-261011-ABD95`: Capsicum 500g **44** (was 80), Cucumber 500g x2
**22** each (was 40), Lemon 250g **25**; subtotal 113 / delivery 20 /
**total 133**; `original_total` = `final_total` = 133; both slot columns set;
address snapshot + generated `pincode` set; cart cleared; stock decremented;
customer read own order under RLS; outbox got admin `new_order` + customer
`order_placed`. **`BELOW_MIN_ORDER` correctly rejected an 88-rupee cart.**

### VERIFIED — admin authorization (incl. 3 escalation attempts)
`is_admin()` true for owner / **false** for a fresh non-admin; direct
`private.admin_users` read **denied (42501)**; `admin_grant_admin` by a non-admin
**denied (42501)**; non-admin editing own `full_name` **allowed** (no regression);
non-admin self-promoting -> **no effect** (role stayed `customer`); non-admin
promoting another user -> **no effect** (owner stayed `admin`).

### VERIFIED — full order lifecycle, 12/12 assertions on `ORD-261011-69DFE`
```
1  illegal PENDING->DELIVERED      REJECTED: cannot move an order from PENDING to DELIVERED
2  cancel without reason           REJECTED: a cancellation reason is required
3  PENDING->CONFIRMED              ok
4  reject item                     ok  original=152.00 adj=-132.00 final=20.00
5  double-reject                   REJECTED: that item is already rejected
6  restore item                    ok  adj=0.00 final=152.00
7  PROCESSING / READY_FOR_DELIVERY / OUT_FOR_DELIVERY / DELIVERED   ok (all 4)
8  DELIVERED->CANCELLED            REJECTED: cannot move an order from DELIVERED to CANCELLED
9  reject after DELIVERED          REJECTED: cannot adjust an order that is DELIVERED
```
History recorded a clean 6-step timeline; 9 correct customer notifications
(placed -> confirmed -> rejected -> restored -> rejected -> processing -> ready ->
out -> delivered). Money: `original 152.00 -> adjustment -132.00 -> final 20.00`
(delivery only), and it can never rise because the caller passes no amount.

### VERIFIED — storage, slots, cron
7 storage policies present; `cron.job` has `vedant-delivery-slots` active on
`41 18 * * *`; `slot_count` 42; horizon 2026-10-11 -> **2026-10-24**; 3 distinct
windows/day; **0 duplicate `starts_at`**.

### VERIFIED — customer PWA still builds
`pnpm exec tsc --noEmit` **0 errors** · `pnpm exec eslint .` **0 errors**
(17 warnings, all vendored `.agents/skills/**`, none in `src/`) ·
`pnpm run build` **22/22 routes + middleware**.

### NOT YET VERIFIED
Nothing on the backend. Android: **Phases 1–6 are green** — `assembleDebug`
produces a debug APK, `test` passes **410/410**, `lint` reports **0 errors**. But
**no admin mutation and no storage upload is verified against the live server**:
there are no admin credentials (R1), so the repositories' contracts are proven
only against a **fake PostgREST** / **fake remote source** and the ViewModels
against a **fake repository**. Still **[BLOCKED]** for live verification: the
`admin_update_order_status` / `admin_reject_order_item` / `admin_restore_order_item`
/ `ensure_delivery_slots` / `admin_slot_load` functions; every `products` /
`categories` / `product_weights` / `offers` / `delivery_slots` write; the
`product-images` upload; the `complaint_messages` reply, the `complaints` status
update and the **`complaint-images` upload** (the `<auth.uid()>/<file>` RLS
convention is asserted in `ComplaintRepositoryImplTest` but never exercised
against the live policy); every `store_settings` write; the `is_admin()`-resolved
profile role; the RLS-hidden columns; and the `profiles` embed.

**Physical UI / accessibility / animation verification is [BLOCKED without a
device]** — the T25 contrast, token-guard and typography checks are asserted in
unit tests (`ThemeContrastTest`, `DesignTokenGuardTest`), not observed on
hardware. Reduced-motion, the offline banner's live-region announcement, the 48 dp
touch targets in `TopAppBar`/icon buttons, and the complaint-thread composer have
**not** been verified on a real device or with a screen reader.

## TEST RESULTS

- Backend: 4 suites, all passing
- PWA: all 3 gates green
- Android: `assembleDebug` **green** (debug APK 23,831,166 B ≈ 22.7 MB, rebuilt
  2026-10-12) · `test` **410/410 pass, 0 failures, 0 errors, 0 skipped** (37
  suites) · `lint` **0 errors, 14 warnings** (12 GradleDependency version notices
  + 2 pre-existing launcher-resource notices — `ObsoleteSdkInt` on the redundant
  `mipmap-anydpi-v26` folder and `MonochromeLauncherIcon`; none from feature
  code) · `assembleDebugAndroidTest` **compiles green** (execution needs a device)

Literal: `./gradlew test assembleDebug lintDebug` → **BUILD SUCCESSFUL in 7m 24s**
(78 actionable tasks).

Test growth: Phase-1 baseline **74** → Phase 2 **129** (+55) → Phase 3 **247**
(+118) → Phase 4+5 **349** (+102) → Phase 6 **410** (+61: OBS-1 regex regression
+3, plus earlier test-suite additions).

### T25 accessibility audit — asserted in unit tests

`ThemeContrastTest` computes WCAG 2.1 relative luminance and asserts ≥4.5:1 for
every text/background pair the app renders, in **both** light and dark (body
text; badge labels are 12 sp so they are body text, not "large"). It is
falsifiable: the formula is pinned to the 21:1 black/white reference and three
negative controls assert the *retired* values really did fail. The audit caught
three live defects, all fixed in `ui/theme`:

| Pair | Before | After |
|---|---|---|
| `Success` on `SuccessBg` (12 sp badge) | 3.67:1 ✗ | **4.77:1** ✓ (`#157A47`) |
| `Warning` on `WarningBg` (12 sp badge) | 3.85:1 ✗ | **4.89:1** ✓ (`#9A5C04`) |
| dark `onError` on `error` (destructive button) | 1.70:1 ✗ | **7.72:1** ✓ (`OnDangerDark #690005`) |

`DesignTokenGuardTest` scans `src/main` and fails if any file outside `ui/theme`
contains a hard-coded `Color(0x…)` / `Color.<Named>` / `<n>.dp` / `<n>.sp`, and
asserts `Sizing.touchTarget == 48.dp` and every `VedantTypography` style ≥ 12 sp.
It too is falsifiable: the scanner is exercised against injected literals.

## KNOWN ISSUES

### Fixed this session
Three WCAG AA contrast failures found by the new `ThemeContrastTest` and fixed in
`ui/theme`: storefront `Success` (3.67:1) and `Warning` (3.85:1) were unreadable as
12 sp badge text on their own tints (→ `#157A47` 4.77:1 / `#9A5C04` 4.89:1), and
dark-mode `onError` was white on the light destructive fill (1.70:1 → `OnDangerDark`
`#690005` 7.72:1). Also: a T22 interface change broke `CatalogueRepositoryImplTest`'s
local `FakeStorage` (missing the two new `StorageDataSource` members) — fixed; and
two of my own new tests were wrong (`SupportMapperTest` assumed the status event
appends last when it actually sorts by instant; `ProfileRepositoryImplTest` asserted
a stored role the fake never supplied) — both corrected, production code unchanged.

### Fixed previously (session 3)
`order_status` enum mismatch (PWA side) · complaint `IN_REVIEW` mismatch ·
zero storage policies · slots expiring 2026-10-16 · checkout `42883` ·
weight-variant overcharging · `profiles` self-promotion ·
`handle_new_user` hardcoded admin · empty `order_status_history` ·
adjustment sign error.

### Open
1. **8 products have null `image_url`**: Beetroot, Capsicum (Green), Carrot,
   Cucumber, Grapes (Black), Lemon, Mint (Pudina), Potato.
2. **`graphify@1.0.0` in `package.json`** is broken/abandoned and imported
   nowhere. Should be removed.
3. **Store contact unset** — `NEXT_PUBLIC_STORE_PHONE/WHATSAPP/ADDRESS` unset, so
   Call/WhatsApp buttons stay hidden. Owner configuration, not a bug.
4. **`storage_path` convention for `complaint-images` is now `<uid>/<file>`** —
   the PWA's complaint-attachment upload must follow it or the RLS check fails.
   Not verified against the PWA's current upload code.
5. **`profiles.email` is null** for the seeded admin (PostgREST returned `""`);
   `auth.users.email` holds it. Cosmetic, but `/account` may look blank.
6. The old report's "Bug 1" (`order_items.quantity`) was **already fixed** — do
   not redo it. Its "Bug 3" (0 storage buckets) was **stale** — buckets existed.

## NEXT EXACT ACTION

**The Android client is feature-complete for the PRD's 29 capabilities
(T01–T28 done; T02 is the one BLOCKED probe). The only remaining work is
verification, which is gated on two things outside engineering:**

1. **Obtain an admin account (R1)** — provision an owner email in
   `private.admin_users`, provide email + password. This unblocks the live
   half of capabilities 1–7 and 9–19.
2. **Obtain a device/emulator (R2)** — create an AVD (`sdkmanager` + `avdmanager`)
   or attach a device over `adb`. This unblocks the instrumented suites and the
   physical a11y/dark-mode/motion checks.
3. With both, **execute `docs/E2E.md` top-to-bottom** and replace every
   "NOT EXECUTED" with an observed result. That is the single highest-value next
   move; it converts the bulk of the status report from
   IMPLEMENTED-BUT-UNVERIFIED to VERIFIED.

Do **not** invent new features — the client matches the PRD. If the owner wants
more, that is a new PRD, not a fix.

If, instead, the owner wants visible progress without R1/R2: the one concrete
client-side gap is the **dashboard counts wiring** (capability 3) —
`OrderRepository.dashboardCounts()` already exists but the tiles are not
connected. That is a small, self-contained task.

## IMPORTANT CONTEXT

- **Live project:** `hyibaljxicojfoxbihdn`. Docs describing
  `bmdmgycsrnffcgxmukxm` are about a different, older project.
- **`execute_sql` returns only the LAST result set** — aggregate into one
  `select jsonb_build_object(...)`.
- **Two `place_order` overloads exist on purpose.** The 3-arg
  `place_order(text,uuid,uuid)` is the app-facing wrapper the PWA calls; the
  5-arg is the internal worker and is revoked from `anon`/`authenticated` so
  PostgREST cannot ambiguously resolve it. **Do not re-grant it.**
- **`orders` has two slot columns.** `slot_id` (legacy) and `delivery_slot_id`
  (app contract). Write both; read either. `place_order` now does.
- Money is `numeric(12,2)`; the client mirrors it in integer paise
  (`src/lib/money.ts`). Keep server and client arithmetic in step.
- Slot pattern to preserve: Morning 08:00-10:00, Afternoon 12:00-14:00,
  Evening 17:00-19:00, capacity 25 each, Asia/Kolkata.

## DO NOT REPEAT

- **Harness gotcha that cost several attempts:** if a response contains both an
  `mcp__supabase__*` call **and any other tool call**, the Supabase call fails
  with `ZodError: expected string, received undefined`. **Issue exactly ONE tool
  call per response when calling Supabase MCP.** Non-Supabase tools batch fine.
- **Compose-test import gotcha (cost one compile cycle this session):**
  `rule.onAllNodes(...)` and `rule.onNodeWithText(...)` are members of the
  `ComposeContentTestRule` — do **not** add `import androidx.compose.ui.test.onAllNodes`
  (there is no such top-level symbol; it fails to resolve). `onNodeWithText`
  *does* need its import. When in doubt, copy the import block from an existing
  suite in `app/src/androidTest/`.
- Don't `count(*)` without a `FROM` clause — it returns 1, not a row count. This
  produced a false "1 order" reading; the table was empty.
- Don't create `temp table`s *before* `set local role authenticated` — the role
  switch loses ownership -> `42501 permission denied for table <tmp>`. Create
  temp tables *after* the role switch.
- Don't put DDL inside a `do $$ ... $$` block executed under
  `set local role authenticated` — it needs table ownership.
- Don't re-add `ORDER_RECEIVED`/`OWNER_ACCEPTED`/`PREPARING`/`READY` anywhere.
- Don't "fix" `order_items.quantity` -> `qty`; already done.
- Don't re-run `private.generate_delivery_slots` expecting duplicates — it is
  idempotent by design, and a unique index now enforces it.
- Don't re-fix `ErrorCopy.sanitize` — it is now an **allowlist** (42501, 22023,
  23514, 23505, 23503) + `PGRST\d+`, not `\d{5}`, and has regression tests. Do
  not "simplify" it back to a five-digit matcher.
- Don't re-add offline gating — all **nine** mutation surfaces already gate on
  `LocalOnlineStatus` (DEFECT-1 closed).

## DO NOT BREAK

- `src/lib/env.ts` rejecting service-role keys; `sw.js` never caching Supabase.
- Integer-paise math (`src/lib/money.ts`) and `priceFor`/`pickActiveOffer` parity
  with `place_order` (`src/lib/pricing.ts`).
- The single nested `PRODUCT_SELECT` (no N+1).
- `private.is_admin()` as the **only** admin gate. No client-side role checks.
- The `PENDING`/`CONFIRMED`/`PROCESSING`/`READY_FOR_DELIVERY` wire values — they
  are the database's, and both apps now agree on them.
- **Never** put `SUPABASE_SERVICE_ROLE_KEY` in the APK or the PWA.
- **Never fake success** (Android): every mutation returns the **re-fetched**
  server row (`OrderRepositoryImpl.refetch`); the ViewModel renders nothing new
  until the server answers, and on a rejection leaves the UI exactly where it was.
- **The UI transition list is a convenience, not a gate** — `OrderTransitions`
  decides which buttons to *offer*; `admin_update_order_status` decides what is
  *allowed*. If they disagree, the server wins and the app surfaces the rejection.
- **`final_total` is server truth** — rendered verbatim from the re-fetched order;
  the client never recomputes money and never sends an amount to the reject/restore
  RPCs (the DB enforces `adjustment_amount <= 0` and
  `final_total = original_total + adjustment_amount`).
- **`orders` has two slot columns** — read `delivery_slot_id`, fall back to
  `slot_id` (pair 6). The admin app **never writes an order row**.
- **No Supabase calls from composables** — the SDK is reachable only through the
  `data/` layer; screens talk to ViewModels, ViewModels to repository interfaces.
- **Only `ui/theme` tokens** — no hard-coded hex or dp in a composable.
- **The legacy-pair expansion lives only in `data/remote/mapper/*` and
  `data/remote/dto/*`** — no domain model, ViewModel or composable may name
  `price`, `max_order_qty`, `starts_at`, `glyph` or `accent` (pair 5: `icon` is
  **mirrored from** `glyph`, never read).
- **Every catalogue/slot write returns the PostgREST-echoed row** — no local
  post-write state is ever constructed; a 0-row update returns `null` →
  `NoSuchElementException` → `AppError.Server`. Removing a product photo uses the
  explicit `setToNull("image_url")` path (a null is dropped from a partial update
  by `explicitNulls = false`).
- **An image is only "set" once the server-confirmed URL is persisted on the row**
  — an upload reports real byte progress, exposes a retry on failure, and the
  bytes are retained until the product write echoes.
- **A mutation is never cached and never queued.** `CacheStore` has no notion of a
  pending write and `ReadRetryScheduler` has no method that could enqueue one, so
  this is structural, not a convention. Offline, mutations are **disabled**, not
  deferred; only *reads* cache (order/product page 0), and the fallback fires
  **only** on `AppError.Network`. **All nine mutation surfaces** read
  `LocalOnlineStatus` and set `enabled = <own precondition> && online` (additive —
  never coupled to loading/saving), with an `OfflineHint` shown when offline.
- **The `complaint-images` object path must begin with `<auth.uid()>/`** — the
  storage RLS policy reads the first path segment. Any other shape is rejected by
  the server, and the app must not silently retry it with a different shape.
- **The role shown on the profile is the server's `is_admin()`**, never
  `profiles.role`. A failed `is_admin()` is surfaced, never defaulted to "not an
  administrator".
- **`store_name = "Fresh Mart"` is shown as-is** and flagged by
  `StoreNameWarning`; the app must not silently rewrite it — the owner corrects it.
- **WCAG AA is a gate, not a preference.** `Success`/`Warning` are tuned for 12 sp
  text on their own tints and dark `onError` is `OnDangerDark`; `ThemeContrastTest`
  and `DesignTokenGuardTest` will fail if either drifts.

## COMMAND TO CONTINUE

```bash
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables"
# verify the PWA after any PWA change
pnpm exec tsc --noEmit && pnpm exec eslint . && pnpm run build
# verify the Android app
cd admin-android && ./gradlew assembleDebug test lint
```

Then open a new session and say:

> Read `admin-android/HANDOVER.md` and `admin-android/PROJECT_INDEX.md`, then do
> NEXT EXACT ACTION.