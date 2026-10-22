# Known Limitations

Genuine unresolved items. Nothing here is softened into "partial" when it is a gap.

## Blocks release

| # | Limitation | Consequence | Who |
|---|---|---|---|
| 1 | **No Android release keystore** | Cannot produce a signed AAB. Only `assembleDebug` has run. | **Human** |
| 2 | **No crash/error observability** | A production crash or client exception is invisible. | Agent |
| 3 | **Backups never restore-tested** | Recovery time and data-loss window are unknown. | Both |

## Missing functionality

| # | Limitation | State |
|---|---|---|
| 4 | Customer-management UI | RPCs `admin_search_users`, `admin_set_account_status` exist and are gated. No Android screen. |
| 5 | Reporting dashboard | RPCs `admin_dashboard_counts`, `admin_sales_today` exist. No screen. |
| 6 | Staff management UI | RPCs exist; screen removed at the owner's request ("one admin, everyone else a customer"). `admin_list_staff()` is now dead code. |
| 7 | Draggable map pin | Address capture previews a marker; it cannot be dragged. Needs `leaflet`. |
| 8 | Customer delivery-tracking page | Customer sees status; no map or progress view. |
| 9 | Wallet spending | Deliberate. The ledger exists; orders do not read it. Applying a balance at checkout needs its own pricing verification. |
| 10 | Custom-offer redemption | Schema supports customer/segment offers. No engine applies them at checkout — `place_order` still prices product offers only. |

## Quality gaps

| # | Limitation |
|---|---|
| 11 | Playwright suite (5 specs) predates GPS, order acceptance and realtime. |
| 12 | 9 Android `androidTest` files predate the delivery queue and appearance system. |
| 13 | No WCAG 2.2 AA audit. Individual components have labels/roles; the whole has not been audited. |
| 14 | No load test. Advisor performance findings are all `unused_index` on a database with **0 orders** — that says nothing about behaviour under load. |
| 15 | No Core Web Vitals data from real devices. |
| 16 | CI workflow exists but has **never run** — no git remote is configured. |

## Operational

| # | Limitation |
|---|---|
| 17 | No custom domain. `vedant-grocery.vercel.app` is the production URL. |
| 18 | Demo accounts `admin@vedant.local` and `demo@vedant.local` still exist and **must be deleted before launch**. |
| 19 | `auth_leaked_password_protection` is off (HTTP 402 — Pro-plan feature). Accepted risk. |
| 20 | 26 Advisor `unused_index` findings. Individually reviewed; expected on a near-empty database. Do not drop them before real traffic. |

## Deliberate non-goals

- **No customer Android app.** Web is the customer surface, per the frozen architecture.
- **No fake wallet, no fake GPS, no fake realtime.** Where something is unfinished it is listed
  above rather than stubbed to look complete.

## Auth email links on the free tier

Email confirmation links use Supabase's **PKCE** flow. The PKCE verifier is a cookie bound to the
browser that started the signup, so a link opened on a **different device or browser** cannot
complete the automatic sign-in.

**The account is still confirmed** — Supabase's verify endpoint sets `email_confirmed_at` before
redirecting. Only the auto-sign-in fails, so the customer simply signs in normally. The callback
now says exactly that instead of echoing `PKCE code verifier not found in storage`.

The proper fixes, in order of preference:

1. **Configure custom SMTP** (free tier allows it). That unlocks template editing, and the
   template can then use `{{ .TokenHash }}` → `/auth/callback?token_hash=…&type=signup`, which the
   callback already handles via `verifyOtp` and which needs **no verifier**, so it works on any
   device. This is the real solution.
2. Upgrade the plan for template editing.
3. Accept the current behaviour: confirmed, then sign in.

Blocked today: `PATCH /v1/projects/{ref}/config/auth` returns
`400 — "Email template modification is not available for free tier projects using the default
email provider."` So option 1 requires SMTP credentials that only the owner can supply.
