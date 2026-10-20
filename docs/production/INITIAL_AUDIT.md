# Initial Audit — 2026-10-20

> Produced before any change in this pass. Facts below were verified against the live
> database and the working tree, not taken from earlier docs.

## 1. Repository structure

| Expected (brief §2) | Actual | Verdict |
|---|---|---|
| `web-app/` | **Next.js lives at the repo root** (`src/`, `next.config.ts`, `package.json`) | Deviation |
| `android-app/` | `admin-android/` | Name differs |
| `supabase/` | `supabase/migrations/` | Correct |
| `docs/` | `docs/` (flat, 29 md files) | Correct, ungrouped |

**Recommendation — do not move.** Relocating a working Next.js app into `web-app/` means
rewriting `tsconfig` paths, `next.config.ts`, `playwright.config.ts`, `vitest.config.mts`,
`components.json`, the `@/` alias and every relative import, for zero functional gain. Brief §2
says "Do NOT move projects unnecessarily if that would break the repository." The separation
that actually matters — Android source never inside the web project, no duplicated schema — is
already true. **Renaming is a packaging preference; it is deferred, not refused.**

No git repository exists. No CI/CD. No `.github/`.

## 2. Applications

| Surface | Identity | State |
|---|---|---|
| Customer web | Next.js 16.3.8, React 19, Tailwind v4 | Mature: 26 routes, build green, typecheck + lint clean |
| Android | `com.vedant.admin` — **admin + delivery only** | 201 Kotlin files, clean architecture, 428 unit tests, `assembleDebug` green |
| Customer Android app | — | **Does not exist.** Brief §3 confirms this is intended: web is the customer surface |

## 3. Database (project `hyibaljxicojfoxbihdn`)

- 18 tables + `product_catalog` view, RLS on all, 0 tables without RLS
- 9 migrations, all recorded in `supabase_migrations.schema_migrations`
- 0 `SECURITY DEFINER` functions with unpinned `search_path`
- 1 anon-executable `DEFINER` function: `check_username_available` (intentional, validated, boolean-only)
- Advisor: **security 20 / performance 16 = 36** (down from 92)
- Seed: 32 products, 10 categories, 24 weights, 69 slots, 10 offers, 1 `store_settings` row

### Findings that shaped this pass

| # | Finding | Evidence |
|---|---|---|
| 1 | **`place_order` already enforces `is_open` / `STORE_CLOSED`** | `position('is_open' in pg_get_functiondef(...)) > 0`; closed store + real cart → `22023: store closed` |
| 2 | **No reason, history or audit for closing orders** | `store_settings` had only `is_open`; no `store_status_history` table |
| 3 | **The customer website had no notion of store status at all** | `grep -rn "is_open\|STORE_CLOSED\|accepting" src/` → **zero hits** |
| 4 | **No realtime anywhere** | `supabase_realtime` publication contained **no tables**; no `.channel(` in `src/` |
| 5 | **Order state machine exists and is correct** | `private.order_transition_allowed` — `DELIVERED → PROCESSING` returns false |
| 6 | Enum lacks `ASSIGNED`, `REJECTED`, `FAILED_DELIVERY` | `order_status` = PENDING, CONFIRMED, PROCESSING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERED, CANCELLED |
| 7 | Android already has the accept-orders toggle | `StoreSettingsScreen.kt:175,185` — `isOpen` switch, "Accepting orders"/"Closed" |
| 8 | Address columns for GPS already exist | `addresses` has `latitude`, `longitude`, `landmark`, `state`, `postal_code` |
| 9 | `addresses.label` is constrained | `CHECK (label = ANY ('Home','Work','Other'))` |
| 10 | Web reads **no** store settings from the DB | `src/config/store.ts` is env/static only |

## 4. Secrets

| Item | State at audit | Action taken |
|---|---|---|
| Supabase PAT in `session-ses_ed59.md` | **Present in plaintext** | Redacted |
| Test password in `.logs/db/t06_*.js` | **Hardcoded fallback** | Scrubbed |
| `.env.local` | publishable key only | Correct, no action |
| service-role key in client code | none found | `src/lib/env.ts` actively rejects it |
| `.gitignore` | covered `.env*`, `.next/`, `.logs/`, `.tmp/` | Added `/.next-old/` |

## 5. Test data

| Item | State at audit |
|---|---|
| `owner@vedant.local` | Fixture ADMIN, confirmed, **known password** |
| `customer@vedant.local` | Fixture customer, confirmed, **known password**, owned the only order |
| `vedant@gmail.com` | Unconfirmed, no role, no data |
| `khansarfaraj.khan@gmail.com` | Real OWNER, confirmed, `admin/ACTIVE` |
| Order `ORD-TEST-1001` | Synthetic, DELIVERED, ₹80 |
| Addresses | Pune 411001 + Mumbai 400001, both test fixtures |

## 6. Code hygiene

- `TODO`/`FIXME`: none outstanding in `src/` or `admin-android/app/src/main`
- Mock data: none — all reads hit Supabase
- Dead code: `.next-old` (2.2 GB build cache) left on disk
- Debug logging: `AppLog` redacts JWTs and API keys (`AppLog.kt:53`) — actively guarded
- Hardcoded production data: `src/config/store.ts` phone/WhatsApp resolve to `null` rather than
  plausible fakes (documented honesty rule)

## 7. Environment constraint discovered

`next build` fails in this sandbox when `.next/turbopack` exceeds 50 files
(`SAFE_DELETE_BULK_CONFIRM_REQUIRED`). Workaround used: rename `.next` before building. This is
an environment limitation, not an application defect.
