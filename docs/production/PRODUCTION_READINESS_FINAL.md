# Production Readiness — Final Scorecard — 2026-10-20

> Every line is PASS / FAIL / BLOCKED / ACCEPTED RISK. No bare "DONE".

## Verdict

# NOT PRODUCTION READY

Backend and customer web are production-grade. Three items block release: **PAT rotation (human
action)**, **the Android delivery/staff UI**, and **release engineering** (git, CI, backups,
signing). Everything below is measured.

---

## 1. Architecture — PASS (with a documented deviation)

| Requirement | Status | Evidence |
|---|---|---|
| Web and Android separate, no cross-contamination | **PASS** | Android source never inside `src/`; no schema duplicated |
| Shared Supabase backend | **PASS** | Both clients use `hyibaljxicojfoxbihdn` |
| Customer = web, Admin/Delivery = Android | **PASS** | Confirmed; no customer catalogue built into the admin app |
| Folder names `web-app/` / `android-app/` | **ACCEPTED RISK** | Web is at repo root, Android is `admin-android/`. Renaming would break tsconfig/next/playwright/vitest aliases for zero functional gain; brief §2 forbids unnecessary moves |

## 2. Security — PASS (one accepted platform limit)

| Requirement | Status | Evidence |
|---|---|---|
| Customer cannot modify order status/total | **PASS** | `orders_update_own` dropped; UPDATE → 0 rows |
| Customer cannot create arbitrary orders | **PASS** | `orders_insert_own` dropped; INSERT → `42501` |
| Customer cannot modify/delete order items | **PASS** | Both policies dropped; → `42501` |
| SUPPORT cannot do ADMIN/DELIVERY work | **PASS** | 5 RPCs re-gated; SUPPORT → `42501` |
| Customer A cannot read Customer B | **PASS** | 24-probe matrix, 0 rows on every cross-account read |
| Suspended customer cannot order | **PASS** | `42501` before business logic |
| Client never trusted for pricing | **PASS** | Prices from `product_weights`/`offers`; client sends ids/qty only |
| Serviceability enforced server-side | **PASS** | `service_areas` + guard in `place_order_app` → `22023` |
| No secrets in repo | **PASS** | PAT redacted, test password scrubbed, `.env.local` publishable-only |
| **PAT rotated** | **BLOCKED** | Dashboard-only action; no Management API endpoint. **Requires the owner** |
| Leaked-password protection | **ACCEPTED RISK** | HTTP 402 — Pro-plan feature, no SQL path |

## 3. Database — PASS

| Requirement | Status | Evidence |
|---|---|---|
| Migrations for every change | **PASS** | 9 migrations, all recorded in `schema_migrations` |
| RLS on every table | **PASS** | 0 tables without RLS |
| `search_path` pinned on every DEFINER fn | **PASS** | 0 unpinned across `public` + `private` |
| Advisor findings reviewed | **PASS** | 92 → 36, each remaining one documented |
| Money as exact numeric | **PASS** | `numeric(12,2)`; no floating point |
| Order state machine prevents illegal transitions | **PASS** | `DELIVERED → PROCESSING` returns false |
| `ASSIGNED` / `REJECTED` / `FAILED_DELIVERY` states | **FAIL** | Enum has neither; assignment is a column (`assigned_to`), not a state |
| Store acceptance reason + history + audit | **PASS** | `store_status_history` + `admin_set_order_acceptance` (new this pass) |

## 4. Web — PASS

| Requirement | Status | Evidence |
|---|---|---|
| Typecheck / lint / production build | **PASS** | `tsc` 0, `eslint` 0 errors, `next build` 26 routes |
| Storefront preserved (grid, search, cart, checkout, Buy Again) | **PASS** | Not rewritten |
| Store status visible to the customer | **PASS** | Banner verified in a real browser: closed → shown, open → gone |
| Checkout blocked while closed | **PASS** | `readyToPlace` gated + `submitOrder` pre-check + `place_order` authority |
| Browsing still works while closed | **PASS** | Catalogue rendered in the closed-state screenshot |
| Address GPS capture + map pin | **FAIL** | Not built; needs a geocoding/maps key (external credential) |
| Delivery tracking page | **FAIL** | Not built |

## 5. Android — FAIL

| Requirement | Status | Evidence |
|---|---|---|
| Unit tests / debug build | **PASS** | 428 tests, `assembleDebug` SUCCESS |
| Connection states (Online/Offline/Syncing/Synced/Error) | **PASS** | `ConnectionState` + `ConnectionMonitor`, 9 tests |
| CALL CUSTOMER via ACTION_DIAL | **PASS** | `CallCustomerButton`; no `CALL_PHONE` permission |
| Customer phone surfaced (and no longer shown as an address line) | **PASS** | Mapper fix + 3 tests |
| **Delivery screen** (assigned orders, actions) | **FAIL** | Not built. DB side complete: `delivery_orders()` verified returning customer name/phone/address/items/totals |
| **NAVIGATE** button | **FAIL** | Not built (needs `geo:` intent; coordinates exist on `addresses`) |
| Delivery issue reporting | **FAIL** | Not built |
| Multi-staff management UI | **FAIL** | Not built. DB side complete: `owner_grant_role`/`owner_revoke_role`, last-OWNER guard |
| Welcome screen | **FAIL** | App opens Splash → SignIn (functionally equivalent, not the specified screen) |
| Release build / AAB / signing | **FAIL** | Only debug built; no signing config inspected |

## 6. Delivery — FAIL (backend PASS, UI FAIL)

| Requirement | Status |
|---|---|
| Assignment model + RPC | **PASS** — `orders.assigned_to`, `admin_assign_delivery()`, `delivery_orders()` |
| DELIVERY sees only its own assignments | **PASS** — verified: returns own row; SUPPORT → `42501` |
| Customer contact without `profiles` access | **PASS** — `address_snapshot` carries name + phone |
| Delivery UI | **FAIL** |

## 7. Location — FAIL

| Requirement | Status |
|---|---|
| Lat/lng columns | **PASS** — already on `addresses` |
| GPS capture, accuracy handling, reverse geocoding, map pin | **FAIL** — not built |
| Coordinate validation server-side | **FAIL** — no lat/lng validation exists |
| **Blocker** | Reverse geocoding and a map picker need a Maps API key, which is a credential the owner must supply. Faking it is explicitly forbidden (brief §58) |

## 8. Realtime — FAIL

| Requirement | Status |
|---|---|
| Realtime publication | **FAIL** — `supabase_realtime` contains **no tables** |
| Customer sees status changes live | **FAIL** |
| Admin sees new orders live | **FAIL** |
| Filtered subscriptions | **FAIL** |

## 9. Testing — PARTIAL

| Suite | Status |
|---|---|
| Web typecheck / lint / build | **PASS** |
| Android unit tests (428) | **PASS** |
| Android debug build | **PASS** |
| Security probes (24) | **PASS** |
| Order-acceptance probes (4 + store-closed proof) | **PASS** |
| Store-status browser verification (closed + open) | **PASS** |
| Full 33-step E2E | **FAIL** — fixtures deleted; needs a real staff account |
| Regression suite after E2E | **FAIL** — blocked by the above |

## 10. Performance — NOT MEASURED

| Requirement | Status |
|---|---|
| Advisor performance findings | **PASS** — 66 → 16 (all unused-index on near-empty tables) |
| Load testing (100–1000 products, 1000+ orders) | **FAIL** — not run |
| Query plan review | **FAIL** — not run |

## 11. Accessibility — NOT MEASURED

| Requirement | Status |
|---|---|
| New UI has labels / roles / touch targets | **PASS** — banner has `role="status"`, `aria-live`, 48dp Android buttons |
| Full WCAG 2.2 AA audit | **FAIL** — not run this pass |

## 12. Monitoring — FAIL

No error tracking, crash reporting, slow-query monitoring or log drain. `audit_logs` exists and is
populated by privileged RPCs — that is the only observability today.

## 13. Backup / recovery — FAIL

No documented backup, restore, rollback or migration-recovery procedure. A pre-deletion data
backup was taken (`.logs/production-cleanup-backup-2026-10-20.json`) but that is not a backup strategy.

## 14. Release engineering — FAIL

| Requirement | Status |
|---|---|
| Git repository | **FAIL** — none |
| CI/CD | **FAIL** — none |
| Android release signing / AAB | **FAIL** |
| Deployment procedure | **FAIL** |

---

## 15. Remaining risks

| # | Severity | Risk | Mitigation |
|---|---|---|---|
| 1 | **Critical** | Development PAT still valid | **Owner must rotate it now** |
| 2 | **High** | No git repository | `git init`, commit, add a remote |
| 3 | **High** | Android delivery/staff UI missing | Build against the verified RPCs |
| 4 | **High** | E2E not run end-to-end | Run with a real staff account |
| 5 | **High** | No realtime | `ALTER PUBLICATION supabase_realtime ADD TABLE public.orders` + filtered subscriptions |
| 6 | **Medium** | GPS/map flow absent | Needs a Maps API key from the owner |
| 7 | **Medium** | No release signing | Add keystore + AAB |
| 8 | **Medium** | No backup/recovery doc | Enable PITR, document restore |
| 9 | **Medium** | No observability | Sentry/Crashlytics + log drain |
| 10 | **Low** | 16 unused indexes | Revisit after real traffic |
| 11 | **Low** | `vedant@gmail.com` unconfirmed account | Confirm ownership, then remove or leave |
| 12 | **Low** | `.next-old` 2.2 GB on disk | Delete manually (sandbox guard blocks it) |

---

## What this pass actually delivered

**Order acceptance (§9/§36) — complete vertical slice, verified end-to-end:**

- Migration `20261016_009`: `acceptance_reason`, `acceptance_updated_at`,
  `store_status_history` (RLS: `can_manage_store` read-only, no client writes),
  `admin_set_order_acceptance(boolean, text)` — gated on `can_manage_store()`, **reason required
  when closing**, writes both history and `audit_logs`.
- Probes: OWNER closes with reason → history + audit written; no reason → `22023`; non-staff →
  `42501`; anon → permission denied. Closed store + real cart → **`22023: store closed`**; reopen
  → passes that gate. Enforcement preserved in `place_order`.
- Web: `src/lib/store-status.ts` (fail-open on read error, because `place_order` is the
  authority), `StoreClosedBanner` on the home page, checkout gated in three layers.
- **Browser-verified**: closed → banner with reason and "you can still browse"; reopened →
  banner absent. Screenshot read at 390 px.

**Also this pass:** CALL CUSTOMER, customer-phone extraction (fixing phone-as-address-line),
PAT redaction, test-password scrub, fixture account/data deletion with backup.
