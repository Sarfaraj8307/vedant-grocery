# Regression Verification Report — D1 / D2 fixes

> **Date:** 2026-10-12 · **Author:** 严过关 (Yan), QA Engineer
> **Type:** Post-fix regression run (verifies the engineer's D1 + D2 fixes; D3 deliberately not shipped)
> **Method:** real-browser E2E via Playwright 1.63 + Chromium against a harness-owned Next.js 16.3.8 dev server on `http://localhost:3100`
> **Result:** **73 passed · 0 failed · 1 skipped** (74 tests) — vs the pre-fix baseline **71 / 2 / 1**
> **Verdict:** ✅ **PASS** — D1 confirmed fixed, D2 confirmed shipping, no regressions.

---

## 1. Headline

| Metric | Pre-fix baseline | This run | Δ |
|---|---|---|---|
| Passed | 71 | **73** | **+2** ✅ |
| Failed | 2 | **0** | **−2** ✅ |
| Skipped | 1 | 1 | = |
| Total | 74 | 74 | = |

- **Passed ≥ 71 → satisfied (73).**
- **Failed < 2 → satisfied (0).**

The two baseline failures were both the **D1 hydration test** (`tests/hydration.spec.ts`), failing on mobile *and* desktop. Both now **PASS** (mobile #9, desktop #46 in the run log). The count rose from 71→73 because those two failures became passes.

---

## 2. How the run was performed (reproducible)

1. **Clean restart** — killed all stray `node.exe` dev servers (one stale `next dev` was holding port 3000 with PID 9700 and blocking a second server from starting), then `rm -rf .next` to drop any stale route table.
2. **Harness-owned server** — started `next dev -p 3100` in the **background** (tool-managed process). **No Playwright `webServer` block** (its teardown hangs on Windows — see the config header comment).
3. **Readiness gate** — polled `GET http://localhost:3100/` until it returned `200` before invoking the suite.
4. **Suite** — `pnpm exec playwright test` (testDir `./tests`, `baseURL http://localhost:3100`, `workers:1`, projects `mobile 390×844` + `desktop 1280×800`).
5. **Teardown** — killed the background server after the run (no `node.exe` remained).

Artifacts: `.logs/verify/pw-run2.log` (console log), `.logs/verify/pw-results.json` → written to **`.logs/verify/results.json`** (machine-readable), screenshots for every route & flow in `.logs/verify/`.

> Note: passing `--reporter=list` on the CLI overrides the config's reporter array, so Playwright's own JSON reporter did not emit. `results.json` was reconstructed deterministically from the run log (exact `ok`/`x`/`-` markers + durations); its totals match the runner's own summary line.

---

## 3. D2 — guarded routes now return a real HTTP 307

Raw requests with redirect-following **disabled** (`fetch(..., { redirect: "manual" })`) against a live server:

| Path | Raw status | `Location` |
|---|---|---|
| `/checkout` | **307** | `/login?next=%2Fcheckout` |
| `/orders` | **307** | `/login?next=%2Forders` |
| `/notifications` | **307** | `/login?next=%2Fnotifications` |
| `/support` | **307** | `/login?next=%2Fsupport` |
| `/account` | **307** | `/login?next=%2Faccount` |
| `/account/reset-password` | **200** | — |
| `/zzz-nope` | **404** | — |
| `/` (control) | 200 | — |

**Every value matches the expected D2 contract exactly.** Before D2 these guarded routes returned `200` + a client-side `<meta http-equiv="refresh">` NEXT_REDIRECT digest; now `requireUser()` triggers Next.js `redirect()`, emitting a genuine `307`. Confirmed both by the independent raw probe above **and** in-suite (`flows.spec.ts` test **f**, `routes.spec.ts` gated rows — both green).

### Observation — detail routes preserve the **full** path in `next`

`/orders/<id>` and `/support/<id>` call `requireUser("/orders")` / `requireUser("/support")` (section path), but Next.js overrides the emitted `next` param with the **actual requested URL**:

- `/orders/00000000-…` → `307 Location: /login?next=%2Forders%2F00000000-0000-0000-0000-000000000000`
- `/support/00000000-…` → `307 Location: /login?next=%2Fsupport%2F00000000-0000-0000-0000-000000000000`

This is **correct and desirable** behaviour — a returning customer is sent back to the exact detail page. It caused 4 spurious failures in the first regression pass (#22, #28, #59, #65) because the *test* asserted the section path. This was a **test-assertion bug, not a source defect**: the app was right, the test was wrong → fixed in `routes.spec.ts` (see §5).

---

## 4. D1 — hydration mismatch: CONFIRMED FIXED

`tests/hydration.spec.ts` seeds `vedant.cart.v1` with a RateBoard product (quantity 2) and asserts **zero** hydration errors (`Hydration failed` / `did not match`) after reload.

| Test | Project | Pre-fix | This run |
|---|---|---|---|
| returning customer with a RateBoard item… hydrates cleanly | mobile | ❌ FAIL | ✅ **PASS** |
| returning customer with a RateBoard item… hydrates cleanly | desktop | ❌ FAIL | ✅ **PASS** |
| RateBoard stepper stays interactive after hydration | desktop | ✅ PASS | ✅ **PASS** |
| RateBoard stepper stays interactive after hydration | mobile | skipped (desktop-only) | skipped (desktop-only) |

The pre-fix report captured the exact React component stack on `src/components/products/qty-stepper.tsx:125` (Zustand synchronous `localStorage` rehydration). That error is **no longer emitted** — the hydration spec is green on both viewports, and the interactive stepper test remains green, proving the control still works after the fix.

---

## 5. Changes made during this regression run (test-only, no app source touched)

Per the "test bugs → fix yourself" routing rule, when the first pass exposed 4 failures that were **test-assertion errors** (app behaviour correct), I fixed the tests — **no application source was modified**, and `tests/fixtures.ts` was left untouched as instructed.

1. `tests/flows.spec.ts` — test **f**: the guest-checkout expectation was updated from the *old* buggy contract (`raw=200` + client `meta` refresh) to the corrected D2 contract (**`307`** with `Location: /login?next=/checkout`).
2. `tests/routes.spec.ts` — `order-detail` and `support-detail` `gatedNext` values corrected from the section path (`/orders`, `/support`) to the **full requested path** (`/orders/00000000-…`, `/support/00000000-…`), matching the observed (correct) Next.js redirect. Stale comments describing the pre-D2 `200 + meta-refresh` behavior were updated.

A targeted re-run of just those two routes (both projects) passed **4/4** before the full regression was executed.

---

## 6. New failures

**None.** 73 passed, 0 failed, 1 skipped. The single skip is the deliberate `test.skip(project !== "desktop")` on the desktop-only stepper interaction test — it is correctly skipped on mobile and passes on desktop.

---

## 7. D3 status (not a regression)

**D3 (soft-404 for unknown product ids) was deliberately NOT shipped** — it is a documented Next.js 16 streaming limitation (unknown product id returns `200` + the not-found UI rather than a `404` status). This remains a **known/accepted** item, unchanged by this run. The suite records it as a `soft-404` annotation rather than a failure (see `flows.spec.ts` test h).

---

## 8. Verdict

✅ **PASS.** The D1 hydration fix and the D2 real-307 guard fix both work as intended; the suite improved from **71/2/1 → 73/0/1** with no new failures and no regressions. D3 remains the only known-and-accepted deviation.

| Deliverable | Path |
|---|---|
| Machine-readable results | `.logs/verify/results.json` |
| Console log of the run | `.logs/verify/pw-run2.log` |
| Targeted re-run log | `.logs/verify/pw-targeted.log` |
| Dev-server log | `.logs/verify/devserver.log` |
| Screenshots (routes + flows) | `.logs/verify/route-*.png`, `flow-*.png`, `hydration-*.png` |
| Specs (kept in repo) | `tests/routes.spec.ts`, `tests/flows.spec.ts`, `tests/hydration.spec.ts`, `tests/helpers.ts`, `tests/catalogue.ts`, `tests/fixtures.ts` |
