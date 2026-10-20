# Release Checklist — 2026-10-20

## Verdict

# NOT PRODUCTION READY — 3 blockers remain

Two are yours to resolve (one credential action, one decision). One needs a Google Maps API key.

---

## Checklist

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | Test accounts removed | **PASS** | `owner@vedant.local` + `customer@vedant.local` deleted; only the real OWNER remains |
| 2 | Test data removed | **PASS** | orders 0, addresses 0, cart fixtures 0, complaints 0; backup at `.logs/production-cleanup-backup-2026-10-20.json` |
| 3 | **Supabase PAT rotated** | **BLOCKED — owner** | Dashboard-only action; no Management API endpoint. Redacted from the repo, but the token is still valid |
| 4 | Secrets audited | **PASS** | PAT redacted, test password scrubbed, `.workbuddy-ai/` untracked, `.env.local` publishable-only; `git grep` over all commits → clean |
| 5 | Real owner verified | **PASS** | `khansarfaraj.khan@gmail.com` confirmed, unbanned, `admin/ACTIVE`, staff `OWNER` |
| 6 | OWNER/ADMIN matrix | **PASS** | Only OWNER on the roster; last-OWNER guard proven (`22023`) |
| 7 | RLS on every table | **PASS** | 0 tables without RLS |
| 8 | Cross-customer isolation | **PASS** | 24 adversarial probes, 0 rows on every cross-account read |
| 9 | Order manipulation blocked | **PASS** | INSERT/UPDATE/DELETE on orders + items all `42501` |
| 10 | Order state transitions protected | **PASS** | `DELIVERED → PROCESSING` = false |
| 11 | Delivery permissions | **PASS** | DELIVERY sees own queue; SUPPORT → `42501` |
| 12 | Storage permissions | **PASS** | anon list `[]`, object URL 200, upload/delete 403 |
| 13 | Order acceptance control | **PASS** | RPC + history + audit; closed store + cart → `22023: store closed` |
| 14 | Customer sees store status | **PASS** | Browser-verified: closed → banner + reason; open → absent |
| 15 | Realtime | **PASS** | `orders` + `order_status_history` published, `REPLICA IDENTITY FULL`, filtered customer subscription |
| 16 | Android delivery UI | **PASS** | Queue screen with CALL (`ACTION_DIAL`), NAVIGATE (`geo:`), state-gated status actions |
| 17 | CALL CUSTOMER | **PASS** | Native dialer, no `CALL_PHONE` permission, hidden when no phone |
| 18 | Delivery coordinates | **PASS** | `delivery_orders()` returns lat/lng; verified `18.5204, 73.8567` |
| 19 | Multi-staff **backend** | **PASS** | `owner_grant_role` / `owner_revoke_role`, last-OWNER guard |
| 20 | Multi-staff **UI** | **FAIL** | Not built |
| 21 | GPS capture + map pin | **FAIL — blocked** | Needs a Maps API key. Columns (`latitude`/`longitude`) exist |
| 22 | Customer delivery tracking page | **FAIL** | Not built |
| 23 | Android release build / AAB | **FAIL** | `assembleDebug` green; no signing config |
| 24 | Web production build | **PASS** | 26 routes; `tsc` 0; `eslint` 0 errors |
| 25 | Android tests + build | **PASS** | 428 unit tests, `assembleDebug` |
| 26 | Full 33-step E2E | **FAIL** | Needs a real staff account (fixtures deleted by design) |
| 27 | Git repository | **PASS** | Initialised; 1 commit `4991e33`; 1196 files; no secrets in history |
| 28 | CI | **PASS** | `.github/workflows/ci.yml` — web typecheck/lint/test/build, Android tests/build, migration guard |
| 29 | Backups documented | **FAIL** | No PITR/restore procedure documented |
| 30 | Monitoring | **FAIL** | No crash reporting or error tracking; `audit_logs` only |
| 31 | Accessibility audit | **FAIL** | Not run |
| 32 | Performance/load testing | **FAIL** | Not run |
| 33 | Remaining Advisor findings reviewed | **PASS** | 36 (from 92); each documented |
| 34 | No debug config in release | **PASS** | `AppLog` redacts JWTs/keys; no test credentials in source |

**Score: 24 PASS · 9 FAIL · 2 BLOCKED**

---

## The 3 blockers

1. **Rotate the Supabase PAT** (owner). It is still valid. Nothing in the app depends on it —
   it was only used for migrations and probes. After rotating, confirm the old token returns 401.
2. **Decide the multi-staff UI.** The backend is complete and tested; only the Compose screen is
   missing. Say the word and it is a UI-only task against verified RPCs.
3. **Provide a Maps API key** (or accept manual address entry). GPS capture and a map pin are
   implementable, but reverse geocoding needs a key. Faking coordinates is forbidden by §58, so
   this is genuinely blocked rather than skipped.

---

## Verification commands

```bash
# Web
pnpm exec tsc --noEmit && pnpm exec eslint src && pnpm exec vitest run && pnpm run build

# Android
cd admin-android && ./gradlew :app:testDebugUnitTest :app:assembleDebug

# Database (Advisor)
# GET https://api.supabase.com/v1/projects/<ref>/advisors/{security,performance}
```

## Known environment limitations

- `next build` fails in this sandbox when `.next/turbopack` exceeds 50 files
  (`SAFE_DELETE_BULK_CONFIRM_REQUIRED`). Workaround: `mv .next .next-old` before building.
  `.next-old` is ~2.2 GB and gitignored; delete it manually.
- `curl` to `localhost` returns 502 through the sandbox proxy even when the app logs `200`.
  Use Playwright's local browser for runtime verification.
- `pnpm dev` started with `( … & )` dies with its parent shell; use a managed background task.
