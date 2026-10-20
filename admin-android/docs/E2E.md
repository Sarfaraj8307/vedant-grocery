# E2E — End-to-end test plan (Vedant Admin, Android)

> # STATUS: **BLOCKED — NOT EXECUTED** (blocked on **R1 only**)
>
> Every step in this document has **NOT** been executed. This is a plan, not a
> result. It is blocked on **one** remaining prerequisite:
>
> | Blocker | What is missing | To unblock |
> |---|---|---|
> | **R1 — admin account** | No admin credentials exist in this environment (`auth.users` / `private.admin_users` are not provisioned for the operator here). Without a server-confirmed `is_admin()` the app correctly refuses at the gate, so no admin surface is reachable. | Provision a user in Supabase Auth with **"Auto Confirm User"** ticked, insert its UUID into `private.admin_users`, then provide email + password. |
> | ~~**R2 — device / emulator**~~ | ~~No AVD exists~~ | **CLEARED 2026-10-14.** An emulator is running with working networking, and `connectedDebugAndroidTest` reports **42/42 green**. Steps: `docs/EMULATOR_SETUP.md`. Live-sign-in evidence: `docs/INSTRUMENTED_TEST_RUN_2026-10-13.md` §11–§12. |
>
> **No claim in this file is VERIFIED.** The moment R1 exists, run the steps
> below top-to-bottom and replace each **Expected** with an observed result. Until
> then the honest label for every step is **NOT EXECUTED**.
>
> What *is* proven instead (and how) is recorded in
> `docs/PROJECT_STATUS_REPORT_2026-10-12-impl.md` and `docs/QA_REPORT.md`: the JVM
> suites (`./gradlew test`, 416 green) and the instrumented suites
> (`./gradlew connectedDebugAndroidTest`, 42/42 green **on a real emulator**)
> cover the logic/rendering contracts against fakes. They are **not** a substitute
> for the live run below.

---

## 0. Purpose

Prove the app's core promise **against the live backend**: a signed-in admin can
drive an order through its lifecycle and every mutation is **server-confirmed** —
"never fake success" is observed, not assumed. The scripted path (PRD P2-5 /
capability 28) is:

> sign in as admin → accept order → reject item → confirm server total → status advance

---

## 1. Preconditions

| # | Precondition | How to check |
|---|---|---|
| 1 | JDK 17 + Android SDK 35 present | `./gradlew assembleDebug` is green |
| 2 | Debug APK built | `app/build/outputs/apk/debug/app-debug.apk` exists |
| 3 | Device/emulator online | `adb devices` lists one device as `device` |
| 4 | Admin credentials available (R1) | An email that is a member of `private.admin_users` |
| 5 | At least one non-terminal order exists | Query `orders` where `status not in ('DELIVERED','CANCELLED')` |
| 6 | A second account that is authenticated but **not** admin (for step 10) | — |
| 7 | Network reachable to `hyibaljxicojfoxbihdn.supabase.co` | `curl -sI https://hyibaljxicojfoxbihdn.supabase.co` |

Set up:

```bash
cd admin-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.vedant.admin/.MainActivity
```

---

## 2. Steps

### A. Authentication and the admin gate

| # | Step | Expected result |
|---|---|---|
| A1 | Launch the app with no session | Splash spinner, then the **Sign in** screen |
| A2 | Enter the admin email + password, tap Sign in | Button shows an in-flight spinner; then the **Dashboard** |
| A3 | Kill the app and relaunch | It restores the session and lands on the Dashboard (no sign-in) |
| A4 | Sign out from Profile | Session cleared; back stack cleared; back from Sign in exits, never shows admin screens |
| A5 | Sign in with the **non-admin** account | The **Not authorized** refusal screen; **no admin data** rendered; sign-out is the only action |
| A6 | *(server check)* From a scratch shell, call `is_admin()` with the anon key and the non-admin token | `false` (the client never decides this) |

### B. Dashboard and orders list

| # | Step | Expected result |
|---|---|---|
| B1 | Open Dashboard | Counts/entry points render; "Manage orders" present |
| B2 | Open Orders | Paginated list; status filter chips; search box |
| B3 | Filter to `PENDING` | Only PENDING rows; count on the chip matches |
| B4 | Pull to refresh | Spinner, then re-fetched rows (stable keys, no flicker) |
| B5 | Toggle airplane mode, reopen a previously loaded list | Persistent "Offline — showing saved data" banner; cached rows shown; **no** mutation control is enabled |
| B6 | Restore network | Banner clears; live data resumes |

### C. Status workflow (server-confirmed)

| # | Step | Expected result |
|---|---|---|
| C1 | Open a `PENDING` order | Detail shows items, quantities, weights, line totals, **server** `final_total`, delivery info |
| C2 | Tap the status action | Only `OrderStatus.nextStatuses` are offered; an in-flight spinner appears; the button is disabled until the server answers |
| C3 | Confirm `PENDING → CONFIRMED` | The rendered status changes **only after** the re-fetched row arrives; history shows the new step |
| C4 | *(server check)* Re-read the order via REST | `status = CONFIRMED`; a history row exists |
| C5 | Attempt a transition the UI does not offer (e.g. via a modified client) | Server rejects; the app shows mapped copy and the **UI does not move** |

### D. Reject / restore an item and money authority

| # | Step | Expected result |
|---|---|---|
| D1 | On a non-terminal order, reject one line item | A reason is **required** before submit |
| D2 | Submit the rejection | In-flight; then totals update to the **server's** recomputed values |
| D3 | *(server check)* Re-read the order | `original_total`, `adjustment_amount ≤ 0`, `final_total = original_total + adjustment_amount` exactly match what the app rendered |
| D4 | Restore the item | Adjustment returns to `0`; `final_total` equals the server value again |
| D5 | Reject every item | `final_total` equals delivery-only; no `23514` (the client sent no amount) |

### E. Catalogue / slots / complaints / settings (spot checks)

| # | Step | Expected result |
|---|---|---|
| E1 | Toggle a product's availability from the hub | Moves only after the server confirms; a failed toggle leaves the switch where it was |
| E2 | Create/edit a product, upload an image | Upload shows real byte progress; the image is "set" only once the server-confirmed URL is on the row |
| E3 | Add/edit a category, weight variant, offer | Each write echoes the stored row before the UI updates |
| E4 | Create a slot; run "Generate recurring slots" | New slot appears; `ensure_delivery_slots` result is reflected |
| E5 | Open a complaint, reply, change status, attach an image | Reply shows pending then the stored message; the attachment path is `<auth.uid()>/<file>` and the upload succeeds under RLS |
| E6 | Edit store settings; note `store_name` | Save echoes the stored row; if `store_name = "Fresh Mart"` the warning is shown and **not** silently rewritten |
| E7 | Open Profile | Role badge reads server-resolved `is_admin()`; the value is never read from `profiles.role` |

### F. Offline safety (the invariant)

| # | Step | Expected result |
|---|---|---|
| F1 | Go offline; attempt every mutation surface (status, reject, product save, category save, offer save, weight save, slot save, availability toggle) | Each control is **disabled** with an "Unavailable offline" hint. Nothing is queued or faked. |
| F2 | Go offline; confirm a read still works from cache | Cached data shown; banner present |
| F3 | *(server check)* Confirm no write landed while offline | Re-read the affected rows — they are unchanged |

---

## 3. Pass / fail criteria

- **PASS** only if every *Expected* above is **observed** against the live backend
  with R1 + R2 satisfied, and every "server check" matches the app's rendering.
- A single mismatch of money, status, or a "the UI moved before the server
  answered" observation is a **FAIL** (violates "never fake success").
- Record the exact command and its literal output for each server check.

## 4. Result log (fill in only when executed)

| Step | Observed | Verdict | Command / evidence |
|---|---|---|---|
| A1–A6 | — | **NOT EXECUTED** | — |
| B1–B6 | — | **NOT EXECUTED** | — |
| C1–C5 | — | **NOT EXECUTED** | — |
| D1–D5 | — | **NOT EXECUTED** | — |
| E1–E7 | — | **NOT EXECUTED** | — |
| F1–F3 | — | **NOT EXECUTED** | — |

> This table is intentionally empty. Filling it in without running the steps would
> be fabricating a result — the one thing this project forbids.
