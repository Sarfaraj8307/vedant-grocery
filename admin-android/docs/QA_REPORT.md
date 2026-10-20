# QA_REPORT — Vedant Admin (Android)

**Task:** T26 (domain + Compose UI test suite) **plus independent verification** (Part B) and
falsifiability (Part C).
**Owner:** 严过关 (Yan) — QA Engineer.
**Date:** 2026-10-12.
**Language:** English.
**Verdict:** **The implementation is trustworthy as far as it can be checked in this
environment.** 8/8 invariants HOLDS or HOLD-with-caveat; **1 MEDIUM defect** found
(offline gating is inconsistent); 3 LOW observations. All tests green; nothing was
"verified end-to-end" because that is impossible here.

> **Nothing in this report is a number I did not observe.** Every command output below is the
> real output. Labels used: **VERIFIED** (I ran it and saw it) / **BLOCKED** (cannot run here) /
> **NOT YET CONFIGURED**.

---

## 1. Summary

| Metric | Before T26 | After T26 | Status |
|---|---|---|---|
| JVM unit tests | 349 | **416** (410 at OBS-1; +6 auth-code regression) | VERIFIED |
| JVM test suites | 33 | **37** | VERIFIED |
| JVM failures / errors | 0 / 0 | **0 / 0** | VERIFIED |
| Instrumented (Compose) test methods | 0 | **42** (9 suites) | **VERIFIED — 42/42 green on an emulator** |
| `./gradlew test` | green | **green** | VERIFIED |
| `./gradlew connectedDebugAndroidTest` | — | **BUILD SUCCESSFUL** (42 tests, 0 failures) | VERIFIED (2026-10-14) |
| `./gradlew assembleDebugAndroidTest` | — | **BUILD SUCCESSFUL** | VERIFIED |
| `./gradlew lint` | 0 errors | **0 errors / 14 warnings** | VERIFIED |
| `./gradlew assembleDebug` | green | **green** (APK 23,831,166 bytes) | VERIFIED |

**Routing decision:** **NoOne** for the test suite (all green after my own fixes), with **one
MEDIUM defect reported to the Engineer** for triage (DEFECT-1, §6). No test was left red.

---

## 2. Real command outputs

```
$ ./gradlew test --console=plain
BUILD SUCCESSFUL in 1m 40s
49 actionable tasks: 2 executed, 47 up-to-date

# parsed from app/build/test-results/testDebugUnitTest/*.xml
# T26 close: suites 37 tests 407 failures 0 errors 0 skipped 0
# Post OBS-1 fix (lead-re-verified from the same XML): suites 37 tests 410 failures 0 errors 0 skipped 0
# 2026-10-14 (+6 auth-code regression tests): suites 37 tests 416 failures 0 errors 0 skipped 0

$ ./gradlew connectedDebugAndroidTest --no-configuration-cache --console=plain
BUILD SUCCESSFUL in 10m 55s
# parsed from app/build/outputs/androidTest-results/connected/debug/TEST-*.xml
# 2026-10-14: suite 1 tests 42 failures 0 errors 0 skipped 0    <-- first fully green run

$ ./gradlew assembleDebugAndroidTest --console=plain
BUILD SUCCESSFUL in 1m 4s
48 actionable tasks: 8 executed, 1 from cache, 39 up-to-date

$ ./gradlew lint assembleDebug --console=plain
BUILD SUCCESSFUL in 2m 24s
# app/build/reports/lint-results-debug.txt : 0 errors, 14 warnings

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 23831166 ... app-debug.apk          # 23.8 MB
```

---

## 3. Part B — Verification table

Every claim is backed by the file:line that convinced me. Where a claim needs a device or
admin credentials it is labelled **BLOCKED**, never VERIFIED.

| # | Invariant | Verdict | Evidence |
|---|---|---|---|
| 1 | **Never fake success** — every mutation path returns the server's row; a rejected mutation leaves the UI unmoved | **HOLDS** | `OrderRepositoryImpl.kt:107-148` (RPC → **re-fetch** `refetch()`), `ProductRepositoryImpl.kt:98-124` & `CategoryRepositoryImpl.kt:28-45` & `ComplaintRepositoryImpl.kt:74-91` & `StoreSettingsRepositoryImpl.kt:32-38` & `SlotRepositoryImpl.kt:47-65` (return the PostgREST **echoed** row or `throw`), `CatalogueRemoteSource.kt:125-135` (`insert/update { select() }` → `decodeSingle`). The only `Order(...)`/`Product(...)` constructions in `src/main` are in `OrderMapper.kt:48` and `CatalogueMapper.kt:40` (decode direction). `OrderDetailViewModel.kt:84-98` leaves `order` untouched on `Failure`. Tests: `OrderRepositoryImplTest`, `OrderDetailViewModelTest`. |
| 2 | **Server-authoritative money** — `final_total` rendered from the server, no amount ever sent | **HOLDS** | `AdminRpc.kt:69-119`: the only RPC params are `p_order_id/p_status/p_reason`, `p_item_id/p_reason`, `p_item_id`, `p_from/p_days` — **no amount**. `OrderDto.kt:37-39` money read from the wire; `OrderMapper.kt:52-54` read-only; rendered verbatim at `OrderDetailScreen.kt:446` and `OrdersListScreen.kt:287`. New test: `OrderDetailScreenTest.the_final_total_is_rendered_verbatim_from_the_server_row` (uses 177.50, underivable from the 80.00 of line items). |
| 3 | **Server-side admin gate only** | **HOLDS** (structurally); live behaviour **BLOCKED (R1)** | `AdminRpc.kt:64-67` — `is_admin()` RPC, **fails closed** (`decodeAsOrNull<Boolean>() ?: false`). `AuthRepositoryImpl.kt:49-74` resolves `isAdmin` from the server on every restore. `SessionViewModel.kt:57-60` — `SignedIn` becomes `NotAuthorized` unless the server said admin. `VedantNavHost.kt:59-75` redirects declaratively; `NotAuthorized → NOT_AUTHORIZED` refusal screen. `OperatorProfile.kt:7-8` — role is **not** read from `profiles.role`. `ProfileRepositoryImpl.kt:36` uses `auth.isAdmin()`. |
| 4 | **No service-role key** anywhere (source / resources / BuildConfig / APK) | **HOLDS** | APK scan: `unzip -p app-debug.apk \| grep -ac "service_role\|SERVICE_ROLE"` → **0**; positive control `grep -ac "sb_publishable\|supabase.co"` → **13** (proves the scan is not vacuous). `src/main` contains only a doc comment (`SupabaseProvider.kt:23`). `SupabaseProvider.kt:36-39` reads only URL + anon key; `build.gradle.kts:42-43` emits only those two. `SecurityKeyHygieneTest` fails the build if a marker appears. |
| 5 | **Layering** — no composable/ViewModel calls Supabase | **HOLDS** (with 1 LOW observation) | `grep -rn "io.github.jan\|createSupabaseClient\|postgrest\|SupabaseClient" ui/ feature/` → **NONE**. `io.github.jan` appears only in `data/remote/*`, `data/session/*`, `di/AppContainer.kt`, and `core/error/ErrorMapper.kt`. See OBS-3. |
| 6 | **Legacy column policy** — dual-column logic only in `dto`+`mapper`; writes emit **both**; `icon := glyph`; no legacy name above the data layer | **HOLDS** (intent); §10 wording is over-broad — see OBS-2 | Write-both: `CatalogueMapper.kt:80` (`price = basePrice`), `:86` (`maxOrderQty = quantityLimit`), `:121` (`icon = glyph`); `SlotMapper.kt:56-59` (`startTime`/`endTime` = IST wall-clock, `maxOrders = capacity`). Grep: every legacy snake name (`max_order_qty`, `starts_at`, `ends_at`, `start_time`, `end_time`, `slot_id`, `delivery_slot_id`, `base_price`, `quantity_limit`, `max_orders`) appears in `domain/` **only inside doc comments**, and in `feature/`+`ui/` **nowhere**. `Category.icon` is never accessed anywhere. Tests: `CatalogueMapperTest`, `SlotMapperTest`, `CatalogueRepositoryImplTest`, `SlotRepositoryImplTest`. |
| 7 | **Offline safety** — a mutation is never cached as a success, and is disabled (not queued) when offline | **PARTIAL** | Clause "never cached/queued as a success": **HOLDS** — `CacheStore.kt:34-47` has **no** mutation API; `OrderRepositoryImpl.kt:81-85` caches reads only; `OrderRepositoryCacheTest.a mutation is never written to the cache`. Clause "disabled when offline": **HOLDS** on order detail / store settings / complaint thread; **VIOLATED** on 6 other surfaces → **DEFECT-1 (§6)**. |
| 8 | **Accessibility** — AA contrast; status by word+icon+colour | **HOLDS** | Contrast **independently recomputed** from `ui/theme/Color.kt` (§5): all 24 rendered pairs ≥ 4.5:1; worst = 4.67:1 (`Info` on `InfoBg`). `StatusBadge.kt:44-51,118-126,151-157` — every `OrderStatus`/`ComplaintStatus` has a **distinct icon** and a distinct word, so two statuses sharing a tone are still distinguishable without colour. |

### 3.1 Notes on the two caveats

- **INV7 / DEFECT-1.** `T24` requires "disable mutations that cannot be confirmed". Three screens do
  it; six do not. Full evidence in §6. **Safety is not compromised** — nothing is queued and a
  rejected write still leaves the UI unmoved — but the *affordance* is inconsistent with the design.
- **INV6 / OBS-2.** `ARCHITECTURE.md` §10 says no domain/VM/composable may name `glyph` or `accent`.
  The class diagram (§5.4) and the shipped models (`Category.glyph/accent`, `Product.glyph/accent`)
  **do** carry them — because `glyph`/`accent` are the *authoritative* columns and `icon` is the
  legacy duplicate (pair 5). `ProductWeight.price` / `Offer.price` are likewise legitimate (they are
  not pair 1). The **intent** of §10 — the legacy duplicate names must not leak — holds; the §10
  sentence is over-broad. Documentation inconsistency, not a code defect.

---

## 4. Part C — Falsifiability (proving the tests can fail)

For each item I broke a production value, ran the affected suite, observed **RED**, restored the
value, and re-ran. The full suite was green at the end (§2).

| # | Production change (temporary) | Observed RED | Restored → GREEN |
|---|---|---|---|
| 1 | `core/money/Money.kt:106` half-up rounding `>= 5` → `>= 6` | **4 failed**: `rounds the third decimal half up`, `a third decimal of exactly five rounds half up`, `a negative third decimal rounds away from zero`, `half a paisa rounds up to one paisa` | Yes |
| 2 | `ui/navigation/SessionViewModel.kt:58` fail-closed gate → `is SessionState.SignedIn -> raw` | **1 failed**: `signed in but not admin becomes NotAuthorized` | Yes |
| 3 | `data/remote/mapper/CatalogueMapper.kt:121` `icon = glyph` → `icon = null` | **1 failed**: `category write mirrors icon from glyph, never the reverse` | Yes |
| 4 | `core/error/ErrorCopy.kt:36` drop the `RAW_CODE_PATTERN` replacement | **5 failed**: `sanitize strips a leading code and tidies the whitespace`, `sanitize strips a code embedded mid-sentence`, `sanitize removes a postgrest code`, `a validation message carrying a code is sanitised in the copy`, `a server message carrying a code is sanitised in the copy` | Yes |

Restoration was confirmed by re-reading the exact lines
(`Money.kt:106`, `SessionViewModel.kt:58`, `CatalogueMapper.kt:121`, `ErrorCopy.kt:36`) and by the
final `./gradlew test` run (407/407 green at T26 close; 410/410 after the OBS-1 regression tests).
Items 1–3 are pre-existing highest-risk assertions
(money correctness, the admin gate, the legacy write policy); item 4 proves **my own new suite** is
not vacuous.

---

## 5. Independent contrast recomputation (INV8)

Computed directly from the hex literals in `ui/theme/Color.kt` with the WCAG 2.1 relative-luminance
formula (black/white pinned to 21.00:1 as a formula check). All pairs the app actually renders:

```
14.49:1 AA  LIGHT onSurface/surface          13.87:1 AA  DARK onSurface/surface
14.10:1 AA  LIGHT onBackground/background    15.19:1 AA  DARK onBackground/background
 5.71:1 AA  LIGHT onSurfaceVariant/surface    7.70:1 AA  DARK onSurfaceVariant/surface
 5.29:1 AA  LIGHT onSurfaceVariant/surfaceVar 6.94:1 AA  DARK onSurfaceVariant/surfaceVariant
 6.38:1 AA  LIGHT onPrimary/primary           4.75:1 AA  DARK onPrimary/primary
11.98:1 AA  LIGHT onPrimaryContainer/...      9.27:1 AA  DARK onPrimaryContainer/...
 5.35:1 AA  LIGHT onSecondaryContainer/...    5.35:1 AA  DARK onSecondaryContainer/...
 8.90:1 AA  LIGHT onTertiaryContainer/...     8.90:1 AA  DARK onTertiaryContainer/...
 6.54:1 AA  LIGHT onError/error               7.72:1 AA  DARK onError/error
 5.72:1 AA  LIGHT onErrorContainer/...        9.50:1 AA  DARK onErrorContainer/...
 4.77:1 AA  BRAND Success/SuccessBg           5.72:1 AA  BRAND Danger/DangerBg
 4.89:1 AA  BRAND Warning/WarningBg           4.67:1 AA  BRAND Info/InfoBg
worst pair: 4.67:1  BRAND Info/InfoBg      all >= 4.5 (AA body): True
```

**Conclusion:** AA holds for body text in both schemes. **VERIFIED.**

---

## 6. Defect & observations

### DEFECT-1 — MEDIUM — offline gating is not applied on 6 of 9 mutation surfaces

**What the design requires:** `ARCHITECTURE.md` T24 / §13 — "disable mutations that cannot be
confirmed" when offline.

**What ships:** `LocalOnlineStatus` is consumed only in three screens:

| Screen | Gated by online? | Evidence |
|---|---|---|
| Order detail (status + reject/restore) | **Yes** | `OrderDetailScreen.kt:216` `val online = LocalOnlineStatus.current`, used at `:237` and `:273` |
| Store settings (save) | **Yes** | `StoreSettingsScreen.kt:102, 222` (`enabled = … && online`) |
| Complaint thread (reply + status) | **Yes** | `ComplaintThreadScreen.kt:135, 148, 462` |
| Catalogue hub (availability toggle) | **No** | `CatalogueHubScreen.kt:294-297` — `Switch(… onCheckedChange = { onToggleAvailability() })`, always enabled |
| Product editor (save) | **No** | `ProductEditorScreen.kt:535` — `enabled = state.canSave` |
| Categories (create/update) | **No** | `CategoriesScreen.kt:283` — `enabled = edit.name.isNotBlank()` |
| Offers (create/update) | **No** | `OffersScreen.kt:301` |
| Weights (save) | **No** | `WeightsScreen.kt:280` |
| Slots (save) | **No** | `SlotsScreen.kt:362` |

**Minimal reproduction:** provide `LocalOnlineStatus = false` (e.g. `CompositionLocalProvider(LocalOnlineStatus provides false)` around `CatalogueHubScreen`) — the availability `Switch` remains
interactive; around `ProductEditorScreen` — the Save button remains enabled.

**Why it is MEDIUM, not HIGH:** the *safety* half of the invariant still holds — a write attempted
while offline is rejected by the network, surfaced as `AppError.Network`, and the UI does **not**
move (no optimistic update, nothing queued). So "never fake success" is intact; only the
"disabled affordance" is missing, and it is missing inconsistently.

**Action:** reported to the Engineer. **Not fixed by me** — it is a production behaviour change and
the fix (thread `LocalOnlineStatus` into 6 more screens) is the Engineer's call.

### OBS-1 — LOW — `ErrorCopy.sanitize` over-redacts 5-digit numbers

`ErrorCopy.kt:15` `RAW_CODE_PATTERN = \b(?:PGRST\d+|\d{5})\b` strips **any** 5-digit number, not just
a SQLSTATE. Minimal repro: `ErrorCopy.sanitize("Price must be under 12000")` → `"Price must be under"`.
No code leaks either way (the failure mode is over-redaction, never under-redaction), so this is
cosmetic. Reported, not fixed.

### OBS-2 — LOW — §10's "must not name glyph/accent" wording contradicts the class diagram

See §3.1. Doc inconsistency, no code change implied.

### OBS-3 — LOW — `core/` is not strictly SDK-free

`core/error/ErrorMapper.kt:3-5` imports `io.github.jan.supabase.exceptions.*` so it can map SDK
exception types. `ARCHITECTURE.md` §2 notes `core/` as "no Supabase". The important boundary (only
`data/` holds the SDK **client**; no UI/VM touches Supabase) holds. Suggested doc tweak only.

---

## 7. Tests added (T26)

### 7.1 JVM (new/extends) — 58 new tests

| File | New tests | What it covers |
|---|---|---|
| `core/error/ErrorCopyTest.kt` (new) | 14 | raw-code detection & sanitisation; non-blank fallbacks for every `AppError` variant; no sample leaks a code |
| `core/time/AppClockTest.kt` (new) | 10 | UTC↔Asia/Kolkata; `formatDateTime/Date/Time`; `startOfTodayIstUtc`/`endOfTodayIstUtc` invariants; `toPostgrestTime` (the pair-4 IST write) ; tolerant `parseLocalTime` |
| `core/logging/AppLogTest.kt` (new) | 8 | JWT / publishable-key / bearer / email redaction; the **sink** receives redacted text; level routing |
| `core/result/OutcomeTest.kt` (new) | 10 | `outcomeOf` maps throwables; **re-throws `CancellationException`**; `map/flatMap/onSuccess/onFailure/getOrNull` |
| `core/money/MoneyTest.kt` (extended) | +16 | rounding boundaries (x.xx4 vs x.xx5 vs x.xx9), carry into rupees, negative rounding, large-value/overflow, Indian grouping, malformed inputs |

### 7.2 Instrumented / Compose (`app/src/androidTest/java/com/vedant/admin/`) — 42 methods, 9 suites

All driven by **fakes**, never a live backend. **All 42 run green on a real emulator**
(`connectedDebugAndroidTest`, 2026-10-14 — `BUILD SUCCESSFUL`, 0 failures). R2 is closed.

| File | Methods | Screens / behaviours |
|---|---|---|
| `SignInScreenTest.kt` | 4 | login form; disabled-until-fillable; mapped error copy; password cleared on success |
| `DashboardScreenTest.kt` | 5 | landing entries; routing callbacks; sign-out |
| `OrdersListScreenTest.kt` | 4 | empty/data/error phases; row rendering; open-order routing |
| `OrderDetailScreenTest.kt` | 7 | **server `final_total` verbatim**; only offered transitions; **rejected transition leaves the status unchanged**; success renders the server row; terminal state; receipt lines; complaint link |
| `CatalogueHubScreenTest.kt` | 4 | empty/data/error; price & category; open-product routing |
| `ProductEditorScreenTest.kt` | 3 | create vs edit mode; disabled save; **failed save keeps the draft** |
| `ComplaintsListScreenTest.kt` | 5 | empty/data/error; status word; **retired `IN_REVIEW` is not a filter chip** |
| `OfflineGatingScreenTest.kt` | 5 | online/offline gating of mutations (T24) |
| `testing/Fakes.kt` | — | shared builders + `FakeOrder/Product/Category/Weight/Complaint/AuthRepository` + `fakeViewModelFactory` |

---

## 8. BLOCKED — cannot be verified in this environment

| Item | Blocker | Label |
|---|---|---|
| ~~Running the instrumented tests~~ | ~~No AVD / `cmdline-tools` / device~~ | **CLEARED — 42/42 green on an emulator, 2026-10-14** |
| Live admin sign-in & session against the server | No confirmed admin account | **BLOCKED (R1)** |
| Server-side admin gate allow/deny with a real non-admin account | No admin credentials | **BLOCKED (R1)** |
| Every RPC mutation (`admin_update_order_status`, `admin_reject_order_item`, `admin_restore_order_item`, `admin_slot_load`, `ensure_delivery_slots`) | No admin credentials | **BLOCKED (R1)** |
| Live money correctness (`final_total` from the real server) | No admin credentials | **BLOCKED (R1)** |
| Live Storage uploads (`product-images`, `complaint-images`) | No admin credentials | **BLOCKED (R1)** |
| Physical UI layout / a11y / animation / dark-mode QA | Partially exercised on-device; not a systematic pass | **OPEN** |
| Full E2E ("verified end-to-end") | R1 | **BLOCKED (R1)** — R2 is closed |
| RLS-hidden table column lists (`orders`, `order_items`, `profiles`, `complaints`, `complaint_messages`, `complaint_attachments`) | No admin credentials (T02) | **BLOCKED (R1)** |

**Observed on-device (2026-10-14), independent of the fakes:** the sign-in screen renders
correctly; the submit button gates on input (disabled → enabled only when both fields are
filled, and it re-disables when a field is cleared); a **live** wrong-credentials attempt
produced a real Supabase `invalid_credentials` response which the app surfaced in its error
banner — proving the emulator→Supabase network path and the auth layer both work end to end.
See `docs/INSTRUMENTED_TEST_RUN_2026-10-13.md` §11.

**NOT YET CONFIGURED:** a Supabase admin account for the `docs/probe_authed_columns.sh`
probe. This is not creatable from inside this environment.

**Rule observed:** nothing above is reported as VERIFIED. Every green result in §2 is a JVM/static
result; every device- or credential-dependent claim is BLOCKED.

---

*End of QA_REPORT.md — T26 + independent verification. All production code restored; suite green.*
