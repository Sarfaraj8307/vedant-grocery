# PRD — Vedant Admin (Android)

**Store-owner operations console for Vedant Fruit & Vegetables**

| Field | Value |
|---|---|
| Product | Vedant Admin — native Android operations console |
| Owner (PM) | 许清楚 (Xu) |
| Status | Draft v1 — for architecture & implementation planning |
| Date | 2026-10-12 |
| Language | English |
| Platform | Native Android — Kotlin 2.0.20, Jetpack Compose (Material 3), single `:app` module |
| Toolchain | AGP 8.7.3 / Gradle 8.12 / compileSdk 35 / minSdk 26 / JDK 17 |
| Backend | Supabase project `hyibaljxicojfoxbihdn` (shared with the customer PWA) |
| Sibling app | Customer PWA (Next.js) in the repo root `../` — **no code coupling; backend contract only** |

---

## 0. Governing Principle — "Live truth beats stale docs"

The requester's master prompt states: *"Treat the live database contract and the current implementation as more authoritative than stale documentation."* This PRD adopts that as a **binding principle** for the whole project:

1. **The live Supabase schema is the contract.** Where `docs/BACKEND_CONTRACT.md`, `src/types/database.ts`, `HANDOVER.md` or `PROJECT_INDEX.md` disagree with the live database, the live database wins and the doc is a defect to be fixed (see Workstream **P0-0**, Phase 0).
2. **Never fake success.** A mutation is "done" only when the server confirms it. No optimistic success for order-state, status, or money changes.
3. **Admin authorization is server-side only** (`private.is_admin()`). UI visibility is *not* authorization.
4. **The database owns the wire values.** Postgres enum names (`order_status`, `complaint_status`, …) are the values on the wire. The client mirrors them exactly; wording lives in labels, never in enum names.
5. **Money is server-authoritative.** `adjustment_amount <= 0` and `final_total = original_total + adjustment_amount` are DB-enforced. The client never computes an amount it then sends.

### 0.1 Documentation debt (verified, must be remediated)

| Source | Defect | Authoritative value |
|---|---|---|
| `docs/BACKEND_CONTRACT.md` | Cites **wrong project** `bmdmgycsrnffcgxmukxm` | `hyibaljxicojfoxbihdn` |
| `docs/BACKEND_CONTRACT.md` | Cites **retired** `order_status` names (`ORDER_RECEIVED`, `OWNER_ACCEPTED`, `PREPARING`, `READY`, …) | `PENDING \| CONFIRMED \| PROCESSING \| READY_FOR_DELIVERY \| OUT_FOR_DELIVERY \| DELIVERED \| CANCELLED` |
| `docs/BACKEND_CONTRACT.md` | `complaint_status` = `OPEN \| IN_REVIEW \| RESOLVED \| CLOSED` | Live = `OPEN \| IN_PROGRESS \| RESOLVED \| CLOSED` |
| `src/types/database.ts` (PWA) | Hand-maintained, missing live columns | Regenerate from live schema |
| `HANDOVER.md` / `PROJECT_INDEX.md` | Claim "no Android source files exist" | 7 Kotlin files exist; debug APK builds |

**Live columns enumerated directly (authoritative), with legacy/duplicate pairs flagged:**

- `categories`: accent, created_at, glyph, **icon**, id, image_url, is_active, name, **slug**, sort_order, updated_at
- `products`: accent, availability, base_price, category_id, created_at, description, **discount_price**, glyph, **has_offer**, id, image_url, **is_active**, **is_featured**, **max_order_qty**, name, **price**, quantity_limit, **slug**, **stock**, **unit**, unit_label, updated_at
- `product_weights`: created_at, grams, id, is_available, label, price, product_id, updated_at
- `offers`: created_at, discount_percent, ends_at, id, is_active, price, product_id, starts_at, title, updated_at
- `delivery_slots`: capacity, created_at, **end_time**, ends_at, id, is_active, **max_orders**, **name**, **sort_order**, **start_time**, starts_at, updated_at
- `store_settings`: delivery_fee, free_delivery_threshold, id, is_open, min_order_amount, phone, store_name, updated_at *(store_name currently reads "Fresh Mart")*

**Legacy/duplicate column pairs (a systemic hazard, not a one-off):**

| New / intended column | Legacy duplicate | Same pattern as |
|---|---|---|
| `products.base_price` | `products.price` | — |
| `products.quantity_limit` | `products.max_order_qty` | — |
| `delivery_slots.capacity` | `delivery_slots.max_orders` | — |
| `delivery_slots.start_time` / `end_time` | `delivery_slots.starts_at` / `ends_at` | — |
| `orders.delivery_slot_id` | `orders.slot_id` | known dual slot column |

**Rule for the client:** write both members of every pair; read the *new* column and fall back to the legacy one. The read/write policy per pair is an architect deliverable (see Open Questions Q2).

`orders`, `profiles`, `complaints`, `order_items`, `complaint_messages` correctly return **zero rows** to anon (RLS working — no leak). Their full column lists are **UNVERIFIABLE without an authenticated admin session** and must be confirmed by the architect (Phase 0 exit gate).

---

## 1. Product Overview & Goals

### 1.1 What the app is

A **native Android app used by the store owner and staff** to run day-to-day operations of Vedant Fruit & Vegetables: receive orders, move them through a status workflow, adjust bills (with server-authoritative money), manage the catalogue (categories, products, weights, offers, availability), manage delivery slots, handle complaints, and configure the store — all against the same Supabase backend the customer PWA writes to.

It is **not** a customer app and **not** a web extension. It is a distinct, installable operations tool for the phone in the owner's pocket.

### 1.2 Product goals (orthogonal)

| # | Goal | Why it matters |
|---|---|---|
| **G1** | **Run the order day from a phone.** Accept, progress, and complete/cancel orders without opening a laptop. | The owner works standing up, on the shop floor. Orders are time-critical and same-day. |
| **G2** | **Make every mutation trustworthy.** Status, rejection, and money changes are only shown as done when the server confirms; money math is server-owned. | One wrong total or phantom "delivered" destroys customer trust and the business's books. |
| **G3** | **Keep the catalogue and storefront in sync.** Manage products, weights, offers, availability, slots, and store settings that the customer PWA reads. | The admin app is the only authoring surface; drift here is visible to every customer. |

### 1.3 Non-goals (explicit)

- No customer-facing features, no admin routes/PINs in the PWA, no coupling Compose code to the web app.
- No service-role key or any privileged secret in the APK — ever.
- No client-side authorization as the gate (server `private.is_admin()` only).
- No payment processing inside the app (money is a server-owned field, not a checkout).
- No multi-tenant / multi-store support in this release.

---

## 2. Personas & User Stories

### 2.1 Personas

| Persona | Description | Access | Priority |
|---|---|---|---|
| **Owner (primary)** | Vedant store owner/operator. Runs orders, owns catalogue and settings. | Full admin (in `private.admin_users`) | P0 |
| **Staff / packer (future)** | Shop staff who pack and dispatch orders. | Authenticated but *scoped*; server enforces what they may do | P1 (roles), P2 (granular scoping) |
| **Support handler (future)** | Handles complaints and customer messages. | Authenticated; complaint scope | P2 |

> The app is single-role today (admin vs not-admin, decided server-side). Future staff roles must not require a client-side role model that could be trusted for authorization — the server remains the gate.

### 2.2 User stories

| ID | As a … | I want … | So that … | Prio |
|---|---|---|---|---|
| US-01 | Owner | to sign in with email + password and be told plainly if I'm not an admin | I know whether I can operate, without a hidden button lying to me | P0 |
| US-02 | Owner | to see today's orders and their status at a glance on a dashboard | I can triage the day in seconds | P0 |
| US-03 | Owner | to open an order, see its items and bill, and advance its status | I can process it end to end | P0 |
| US-04 | Owner | to be told the status change failed if the server rejects it | I never act on a state the backend disagrees with | P0 |
| US-05 | Owner | to reject an unavailable item and see the corrected total the *server* computed | the customer is charged correctly | P0 |
| US-06 | Owner | to restore a rejected item | I can correct a mistake | P1 |
| US-07 | Owner | to add/edit products, categories, weights, and offers | the storefront reflects reality | P1 |
| US-08 | Owner | to toggle product availability and store open/closed | I stop taking orders I can't fulfil | P1 |
| US-09 | Owner | to manage delivery slots and see their load | I don't over-book a slot | P1 |
| US-10 | Owner | to read and resolve complaints | customers get answers | P1 |
| US-11 | Owner | to view/update profile & session and sign out | the session is under my control | P1 |
| US-12 | Staff (future) | to see only the actions the server allows me | authorization is never a UI trick | P2 |

---

## 3. Prioritised Requirement Pool

Priority legend — **P0 Must have** (minimum for a genuinely usable end-to-end app) · **P1 Should have** · **P2 Nice to have**.
"Verified" tags: **[V]** = verifiable in this environment · **[U]** = UNVERIFIABLE here (needs device/admin creds) — see §8.

### 3.1 P0 — Minimum usable end-to-end (auth → admin gate → orders → status mutation → server confirmation)

| Req | Capability (#) | Requirement | Verifiable |
|---|---|---|---|
| P0-0 | Docs (29) | Reconcile all schema docs to live DB; add a generated schema snapshot as the single contract source. | [V] (offline) |
| P0-1 | Auth (1) | Email+password sign-in via Supabase Auth; session persisted; sign-out clears local session. | [V] logic / [U] live sign-in |
| P0-2 | Admin authz (2) | After sign-in, resolve admin status via server (`private.is_admin()` / admin RPC); non-admins see an explicit "not authorized" screen, never a usable shell. | [V] logic / [U] live |
| P0-3 | Dashboard (3) | Landing screen: order counts by status (today), store open/closed, quick entry to orders. | [V] UI / [U] live data |
| P0-4 | Orders (4) | Paginated order list with status filter and search; pull-to-refresh; server-sorted. | [V] UI/logic / [U] live data |
| P0-5 | Order detail (5) | Items, quantities, weights, line totals, adjustment, final total, delivery info, complaint link. | [V] UI / [U] live data |
| P0-6 | Order status workflow (6) | Advance status strictly along `order_status` transitions via `admin_update_order_status`; UI offers only allowed next states; server rejection surfaces as an error and the UI does **not** move. | [V] state machine / [U] live RPC |
| P0-7 | Server-authoritative adjustments (8) | Reject/restore and any bill adjustment call the server; **final total is whatever the server returns** — the client never computes the amount it sends. | [V] logic / [U] live RPC |
| P0-8 | Never fake success (constraint) | Order/status/money mutations show a pending state, block double-submit, and only reflect the server-confirmed result. | [V] logic |
| P0-9 | Error handling (20) | Central error taxonomy: map `42501` → "You don't have permission…", `22023` → validation copy, network/timeout → retry affordance. No raw codes to the user. | [V] |
| P0-10 | Loading/empty states (21) | Every data screen models `loading / data / empty / error` explicitly. | [V] |
| P0-11 | Server-authoritative key hygiene | APK carries only publishable key; assert no service-role key in `BuildConfig`/resources/code. | [V] |

### 3.2 P1 — Makes the app a full operations tool

| Req | Capability (#) | Requirement | Verifiable |
|---|---|---|---|
| P1-1 | Item rejection/restoration (7) | `admin_reject_order_item` / `admin_restore_order_item`, reason capture, item-level UI, server-confirmed totals. | [V] logic / [U] live |
| P1-2 | Catalogue (9) | Catalogue hub: browse/search products by category. | [V] UI / [U] live |
| P1-3 | Categories (10) | List/create/edit categories (name, slug, glyph/icon, accent, sort_order, is_active, image). | [V] UI / [U] live |
| P1-4 | Products (11) | Create/edit product: name, slug, category, unit/unit_label, base price, description, image, is_active, is_featured, stock, availability, quantity_limit. **Write both members of legacy pairs.** | [V] UI / [U] live |
| P1-5 | Weight variants (12) | Manage `product_weights` (grams, label, price, is_available) per product. | [V] UI / [U] live |
| P1-6 | Product availability (13) | Toggle product availability / stock quickly from the list. | [V] UI / [U] live |
| P1-7 | Product image upload (14) | Upload to Supabase Storage; complaint-style path rule where applicable; progress + failure recovery. | [V] logic / [U] live |
| P1-8 | Offers (15) | Manage `offers` (title, discount_percent, price, starts_at, ends_at, is_active); reflect `has_offer`/`discount_price`. | [V] UI / [U] live |
| P1-9 | Delivery slots (16) | Manage `delivery_slots` (name, start/end time, capacity, sort_order, is_active); view load via `admin_slot_load`; `ensure_delivery_slots`. | [V] UI / [U] live |
| P1-10 | Complaints/support (17) | List complaints, open thread, change `complaint_status` (`OPEN \| IN_PROGRESS \| RESOLVED \| CLOSED`), reply via `complaint_messages`; image paths `<auth.uid()>/<filename>`. | [V] UI / [U] live |
| P1-11 | Store settings (18) | Edit `store_settings` (store_name, phone, is_open, delivery_fee, free_delivery_threshold, min_order_amount). **Flag `store_name = "Fresh Mart"` as wrong and fixable.** | [V] UI / [U] live |
| P1-12 | Profile/session (19) | Show signed-in identity/role, update profile, sign out, session expiry handling. | [V] UI / [U] live |
| P1-13 | Dark mode (26) | Light + dark from the existing token set; **no dynamic colour**; status colours fixed and meaningful. | [V] UI |
| P1-14 | Accessibility (25) | 48dp touch targets, 12sp floor, status by word+icon+colour (never colour alone), contrast ≥ 4.5:1 body / 3:1 large. | [V] UI |

### 3.3 P2 — Polish, resilience, and assurance

| Req | Capability (#) | Requirement | Verifiable |
|---|---|---|---|
| P2-1 | Offline/resilience (22) | Room cache for last-known orders/catalogue; WorkManager for queued retries of safe reads; explicit "offline — showing cached data" banner; mutations **never** queued as if succeeded. | [V] logic |
| P2-2 | Animations (23) | `AnimatedVisibility` / `animate*AsState` / `AnimatedContent`; motion never delays an operational action; respects reduced-motion. | [V] UI |
| P2-3 | Scrolling (24) | Stable keys in lists, `LazyColumn` best practice, preserve scroll position, no jank on long catalogues. | [V] UI |
| P2-4 | Automated testing (27) | Domain unit tests (state machine, money, validation, mappers, repo with fake data source) + Compose UI tests (login, dashboard, order list/detail, accept, reject item, catalogue, product edit, complaints). | [V] JVM / [U] instrumented |
| P2-5 | End-to-end verification (28) | Scripted E2E: sign in as admin → accept order → reject item → confirm server total → status advance. | **[U] here** (no device + no creds) |
| P2-6 | Production documentation (29) | README, release/build notes, contract doc, NEXT EXACT ACTION + DO NOT REPEAT, handoff. | [V] |

### 3.4 Capability coverage check (all 29)

1 Auth→P0-1 · 2 Admin authz→P0-2 · 3 Dashboard→P0-3 · 4 Orders→P0-4 · 5 Order detail→P0-5 · 6 Status workflow→P0-6 · 7 Rejection/restoration→P1-1 · 8 Server-authoritative adjustments→P0-7 · 9 Catalogue→P1-2 · 10 Categories→P1-3 · 11 Products→P1-4 · 12 Weight variants→P1-5 · 13 Availability→P1-6 · 14 Image upload→P1-7 · 15 Offers→P1-8 · 16 Delivery slots→P1-9 · 17 Complaints→P1-10 · 18 Store settings→P1-11 · 19 Profile/session→P1-12 · 20 Error handling→P0-9 · 21 Loading/empty→P0-10 · 22 Offline→P2-1 · 23 Animations→P2-2 · 24 Scrolling→P2-3 · 25 Accessibility→P1-14 · 26 Dark mode→P1-13 · 27 Automated testing→P2-4 · 28 E2E verification→P2-5 · 29 Production docs→P2-6 (+P0-0).

---

## 4. Phased Delivery Plan

The work **cannot ship in one pass**: the client stack does not exist yet, the schema docs are wrong, and the highest-risk definition of done ("verified end-to-end") is blocked by missing device + credentials. Phases are gated — a phase is not "done" until its exit criterion is met and *verifiable*.

| Phase | Theme | Key deliverables | Exit criterion | Verifiable here? |
|---|---|---|---|---|
| **Phase 0** | Audit & reconciliation | Fix all stale docs; regenerate schema snapshot from live DB; architect confirms `orders`/`profiles`/`complaints`/`order_items`/`complaint_messages` columns via authenticated session; decide read/write policy for every legacy/duplicate column pair. | One authoritative contract doc + schema snapshot exists; zero known doc/schema conflicts; dual-column policy documented. | **Yes** (offline + REST) except authed column enumeration → **[U]** |
| **Phase 1** | Client, DI, auth, gate | Supabase client (publishable key only), repository layer, DI container, navigation graph, `AuthRepository`, sign-in screen, server-side admin gate, profile/session. | A signed-in admin reaches an empty-but-real dashboard; a non-admin is cleanly refused **by the server**; sign-out clears session. | **[U] live** (needs creds) — logic/UI verifiable [V] |
| **Phase 2** | Orders (the P0 core) | Order list, order detail, status workflow via RPC, reject/restore, server-authoritative totals, error taxonomy, loading/empty states. | Full order lifecycle runs against live backend with **no fake success**: a rejected transition shows an error and the UI stays put; totals equal the server's. | **[U] live** — state machine & repo logic [V] |
| **Phase 3** | Catalogue & slots | Categories, products, weights, offers, availability toggles, image upload, delivery slots + load. | Create → edit → publish a product with a weight variant and an offer; it is visible to the customer PWA. Slot load matches server. | **[U] live** (UI [V]) |
| **Phase 4** | Support, settings, profile | Complaints list/thread/status, store settings, profile/session polish. | Complaint can be opened, replied to, and resolved; store settings persist and are readable by the PWA. | **[U] live** (UI [V]) |
| **Phase 5** | Polish: offline, animation, a11y, dark mode | Room cache, WorkManager read-retry, offline banner, motion, accessibility pass, dark mode QA. | Accessibility checklist green; offline shows cached data with banner and never a fake success; reduced-motion respected. | **[V] UI/logic** |
| **Phase 6** | Docs, testing, handoff | Full domain unit suite + Compose UI tests, E2E script, README/build/release notes, NEXT EXACT ACTION + DO NOT REPEAT. | `./gradlew test lint assembleDebug` green; E2E script exists and is **honestly labelled** VERIFIED or BLOCKED. | tests [V]; E2E run **[U]** |

### 4.1 What is genuinely verifiable at each gate

- **Verifiable offline (any phase):** Gradle build, unit tests, lint, static checks, contract/schema diffing via REST with the publishable key, UI rendering (Compose preview/screenshot tests where possible), error-taxonomy mapping.
- **Blocked without a device:** any real touch/layout/a11y verification, animations at runtime, dark-mode visual QA on hardware.
- **Blocked without an admin account:** sign-in, admin gate against live server, every RPC mutation, live E2E.

**Definition of Done is phase-relative:** a phase may be **IMPLEMENTED** and **TESTED** (unit) while its **live VERIFIED** status is **BLOCKED**. That distinction must be recorded honestly in every status update (per CLAUDE.md testing rules).

---

## 5. UI/UX Requirements

### 5.1 Screen inventory

| # | Screen | Purpose | Loading | Empty | Error |
|---|---|---|---|---|---|
| S1 | Splash / session check | Restore session, decide route | Spinner | — | Retry sign-in |
| S2 | Sign in | Email+password auth | Button spinner | — | Invalid creds / network copy |
| S3 | Not authorized | Signed in but not admin | — | — | Explain + sign out |
| S4 | Dashboard | Today's counts by status, store open/closed | Skeleton | "No orders yet" | Inline retry |
| S5 | Orders list | Filter/search/paginate orders | Skeleton list | "No orders match" | Inline retry + stale banner |
| S6 | Order detail | Items, bill, adjustments, actions | Skeleton | — | Inline retry; action errors inline |
| S7 | Reject item sheet | Pick item + reason | — | — | Server rejection copy |
| S8 | Catalogue hub | Browse/search products | Skeleton | "No products" | Inline retry |
| S9 | Product editor | Create/edit product | — | — | Field + submit errors |
| S10 | Categories | List/create/edit categories | Skeleton | "No categories" | Inline retry |
| S11 | Weight variants | Manage weights per product | Skeleton | "No variants" | Inline retry |
| S12 | Offers | Manage offers | Skeleton | "No offers" | Inline retry |
| S13 | Delivery slots | Manage slots + load | Skeleton | "No slots" | Inline retry |
| S14 | Complaints list | Open complaints | Skeleton | "No complaints" | Inline retry |
| S15 | Complaint thread | Messages + status change | Skeleton | "No messages" | Inline retry |
| S16 | Store settings | Edit store config | Skeleton | — | Field + submit errors |
| S17 | Profile / session | Identity, role, sign out | Skeleton | — | Inline retry |

### 5.2 Cross-screen state model

Every data screen exposes an explicit immutable `XUiState` with **`loading / data / empty / error`** — no implicit nulls. One-shot events (navigate, snackbar) are separate from state. Offline shows a persistent banner ("Offline — showing saved data") and disables mutation actions that cannot be confirmed.

### 5.3 Accessibility (non-negotiable)

| Requirement | Spec |
|---|---|
| Touch targets | ≥ **48dp** (`Sizing.touchTarget`); never shrink for density |
| Text floor | **12sp** minimum for anything a person must read |
| Status semantics | Conveyed by **word + icon shape + colour** — never colour alone (WCAG 1.4.1) |
| Contrast | Body ≥ 4.5:1, large ≥ 3:1; verified in light **and** dark |
| Dynamic colour | **Disabled** — status colour must mean exactly one thing everywhere |
| Motion | Never delays an operational action; respects reduced-motion settings |
| Content description | Every icon-only action has a content description |

### 5.4 Design system usage

Use `ui/theme/` tokens only — never hard-code a hex or a dp. Palette roles: **Leaf** (primary/success), **Marigold** (attention/in-progress), **Berry** (destructive/cancelled), plus neutral "warm paper" scale and semantic status roles (`Success/Warning/Danger/Info` + `*Bg`). Tokens mirror the PWA `@theme` values in `src/app/globals.css`; add new roles to the PWA token set first.

---

## 6. Non-Functional Requirements

| Area | Requirement |
|---|---|
| **Performance** | Cold start < 2.5s on a mid-range device; order list first paint < 1s with cache; list scrolling 60fps with stable keys; no main-thread network/DB. |
| **Offline / resilience** | Room caches last-known orders/catalogue; reads degrade gracefully with a banner; WorkManager retries *safe reads only*; mutations are never queued to look successful. |
| **Error taxonomy** | `42501` → "You don't have permission to do that."; `22023` → field-level validation copy; network/timeout → "Couldn't reach the store — retry"; unknown → generic + log. Never surface raw Postgres codes. |
| **Security** | Publishable key only in APK; server-side authz (`private.is_admin()`) is the sole gate; UI visibility ≠ authorization; storage paths `<auth.uid()>/<filename>`; no secrets in logs/resources. |
| **Money integrity** | `adjustment_amount <= 0`; `final_total = original_total + adjustment_amount`; both DB-enforced; client displays server-returned values only. |
| **Data contract** | Postgres enum names are wire values; unknown enum values decode tolerantly (never crash a list). Dual/legacy columns follow the Phase 0 policy (write both, read new + fallback). |
| **Testing** | Domain unit tests for state machine, money, validation, mappers, repositories (fake data source); Compose UI tests for the flows in P2-4; gates `./gradlew test lint assembleDebug`. |
| **Observability** | Structured logging of mutation attempts/results with correlation to server response; no PII beyond what the operation needs. |
| **Maintainability** | Single `:app` module; UDF; no business logic in composables; no Supabase calls from UI; hand-rolled DI seam replaceable by Hilt. |

---

## 7. Assumptions, Risks & Open Questions

### 7.1 Assumptions

- A1. The live Supabase schema is the single source of truth and is stable during delivery.
- A2. An admin account will be provided for verification (currently **not** available).
- A3. The customer PWA continues to read the same columns the admin app writes.
- A4. The publishable key in `local.properties` remains valid.
- A5. Single-store, single-role (admin/not-admin) is sufficient for this release.

### 7.2 Risks (ranked)

| ID | Risk | Severity | Impact | Mitigation |
|---|---|---|---|---|
| **R1** | **No admin credentials** — `auth.users` empty, `private.admin_users` empty (as of 2026-10-07). | **Critical** | Blocks live sign-in, admin gate, and **every** RPC mutation; makes "verified end-to-end" impossible. | Obtain a real admin account; until then mark all live paths **BLOCKED**, keep them IMPLEMENTED+TESTED. **Single biggest threat to DoD.** |
| **R2** | **No emulator/device** — no AVD, no `cmdline-tools`, `adb` not on PATH. | High | No physical UI/a11y/animation verification. | Compose screenshot tests + JVM tests; provision `cmdline-tools`+AVD or a physical device for Phase 5/6 QA. |
| **R3** | **Schema documentation is stale/conflicting** (wrong project, retired enums, wrong complaint status). | High | Wrong contract → wrong client → silent breakage. | Phase 0 reconciliation; generated schema snapshot as contract source; docs are defects. |
| **R4** | **Legacy/duplicate column pairs** (5 known) plus unverified `orders`/`order_items` columns. | High | Reads/writes drift; orders silently malformed. | Phase 0 read/write policy; write-both-read-new-fallback; authed column enumeration. |
| **R5** | **"Never fake success" mis-implemented** (optimistic UI). | High | Owner acts on states the backend rejects; wrong money shown. | Pending-state UX; block double-submit; render only server-confirmed results; code review gate. |
| **R6** | **`store_name = "Fresh Mart"`** — wrong value live. | Low | Storefront shows wrong name. | Correct via store-settings screen; add to Phase 4 checks. |
| **R7** | Scope creep beyond the 29 capabilities / premature modularisation. | Medium | Slips the schedule. | Phases are gated; single module; defer P2. |

### 7.3 Open questions

| ID | Question | Owner | Blocks |
|---|---|---|---|
| Q1 | Can we get a working **admin account** for verification? If not, what is the accepted DoD? | Requester | R1, Phase 1–6 live gates |
| Q2 | For each legacy/duplicate column pair, what is the authoritative read column and do we write both? | Architect | Phase 0 exit, Phase 2/3 |
| Q3 | Full column lists for `orders`, `order_items`, `profiles`, `complaints`, `complaint_messages`? | Architect (authed session) | Phase 2/4 |
| Q4 | Will `cmdline-tools`/AVD be provisioned, or is a physical device available? | Requester | R2, Phase 5/6 |
| Q5 | Is `complaint-images` (or equivalent) the storage bucket, and are paths `<auth.uid()>/<filename>`? | Architect | Phase 3/4 |
| Q6 | Are staff roles in scope now, or strictly future? | Requester | §2.1, P2-6 |
| Q7 | Confirm `private.is_admin()` is the only gate and expose any additional admin RPCs. | Architect | Phase 1 |

---

## 8. Verifiable vs Unverifiable in This Environment

### 8.1 VERIFIABLE now (offline / JVM / REST with publishable key)

- Gradle build, `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.
- Domain unit tests: order state machine, money arithmetic, validation, mappers, repository logic with a fake data source.
- Error-taxonomy mapping (code → copy) as pure logic.
- Static assertions: no service-role key anywhere; publishable key only.
- Contract reconciliation: enumerate live columns via REST; diff against docs; generate schema snapshot.
- Compose UI rendering via screenshot/preview tests (where the harness supports it).
- Documentation completeness (README, contract doc, NEXT EXACT ACTION / DO NOT REPEAT).

### 8.2 UNVERIFIABLE here (blocked)

| Item | Blocker | What would unblock it |
|---|---|---|
| Admin sign-in & session against live server | No admin account (R1) | Provisioned admin email+password |
| Server-side admin gate behaviour (allow/deny) | No admin account (R1) | Provisioned admin account + a non-admin account |
| Every RPC mutation (status, reject/restore, slot load, grants) | No admin account (R1) | Provisioned admin account |
| Live money correctness (`final_total`) | No admin account (R1) | Provisioned admin account |
| Physical UI layout / a11y / animation / dark-mode QA | No emulator/device (R2) | AVD or physical device |
| Instrumented (on-device) tests | No emulator/device (R2) | AVD or physical device |
| Full E2E ("verified end-to-end") | R1 + R2 | Admin account **and** a device |

**Rule:** every status report must label each item **IMPLEMENTED / TESTED / VERIFIED / BLOCKED / NOT YET CONFIGURED**. Nothing in §8.2 may be reported as VERIFIED until its blocker is cleared.

---

## 9. Traceability

| Product goal | Primary requirements | Phases |
|---|---|---|
| G1 Run the order day from a phone | P0-3, P0-4, P0-5, P0-6, P1-1 | 1–2 |
| G2 Every mutation is trustworthy | P0-7, P0-8, P0-9, P0-11 | 0, 2 |
| G3 Catalogue/storefront in sync | P1-2 … P1-11 | 3–4 |
| Foundation (trust in the contract) | P0-0 | 0 |
| Quality & handoff | P1-13, P1-14, P2-1 … P2-6 | 5–6 |

---

*End of PRD v1. This document is the product contract for the architecture (SOP stage 2) and implementation (SOP stage 3). Where it conflicts with live truth, live truth wins and this document is updated.*
