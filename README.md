# Vedant Fruit & Vegetables — customer storefront

The customer-facing grocery storefront for Vedant Fruit & Vegetables: browse the
daily catalogue, build a basket, pick a delivery slot and track the order. It is
an installable PWA built on Next.js and Supabase.

The **admin app** (Android, Kotlin/Compose) lives in its own repository:
[`vedant-admin-android`](https://github.com/Sarfaraj8307/vedant-admin-android).

## Stack

| Layer | Choice |
|---|---|
| Framework | Next.js 16.3 (App Router, Turbopack) |
| UI | React 19, Tailwind CSS |
| Data | Supabase — Postgres, Auth, Row Level Security |
| Tests | Vitest (unit), Playwright config (e2e) |
| Hosting | Vercel (see `docs/DEPLOYMENT.md`) |

## Getting started

```bash
pnpm install
cp .env.example .env.local     # then fill in the Supabase values
pnpm dev                       # http://localhost:3000
```

`.env.local` needs only the **publishable** Supabase keys. See the security note
below — that is a deliberate constraint, not an oversight.

## Scripts

| Command | What it does |
|---|---|
| `pnpm dev` | Development server |
| `pnpm build` / `pnpm start` | Production build and serve |
| `pnpm test` | Unit tests (Vitest) |
| `pnpm lint` | ESLint |
| `pnpm auth:check` | Reports which Supabase auth providers are actually enabled |

## Database

Schema changes live in `supabase/migrations/` and are applied **in filename
order**. They are not applied automatically — a fresh environment needs them run
against the project before the app will work.

Two things worth knowing before you run anything:

- **The migrations are the schema.** There is no `seed.sql`; they create the
  tables, RLS policies and the `SECURITY DEFINER` functions the app calls.
- **Some features depend on a specific migration.** For example the terms consent
  gate stays inactive until `20261025_001_terms_acceptance.sql` has been applied —
  it fails open rather than locking customers out of a broken deploy.

## Security model

This is a customer-only application, and the architecture assumes the browser is
hostile:

- Only publishable (anon) Supabase keys are shipped. No service-role key, no
  database password, no admin API key.
- **Authorization is enforced in Postgres** — Row Level Security plus
  `SECURITY DEFINER` functions — not in the client. The server-side helpers in
  `src/lib/auth.ts` exist for good UX (redirects, personalised headers) and are
  explicitly *not* the security boundary.
- Every table is scoped to `auth.uid()`, so a compromised client still cannot
  read another customer's orders, addresses or basket.

## Repository layout

```
src/app/            routes (App Router)
src/components/     UI, grouped by feature
src/services/       data access (auth, cart, orders, catalogue)
src/lib/            shared logic, Supabase clients, formatting
supabase/migrations schema, RLS and SQL functions
docs/               architecture, backend contract, deployment, known limitations
```

`docs/` is worth reading before making changes — in particular
`docs/BACKEND_CONTRACT.md` (what the database guarantees) and
`docs/KNOWN_LIMITATIONS.md` (what is deliberately unfinished, and why).
