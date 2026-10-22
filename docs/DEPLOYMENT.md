# Deployment

## Live

| | |
|---|---|
| **Production URL** | https://vedant-grocery.vercel.app |
| **Vercel project** | `khansarfarajkhan-8498/vedant-grocery` |
| **Region** | Vercel default (functions run closest to the visitor) |
| **Supabase project** | `hyibaljxicojfoxbihdn`, region `ap-south-1` (Mumbai) |

Verified 2026-10-22 by direct `curl` **without** a Vercel auth token (HTTP 200), so the
production alias is genuinely public. `Deployment Protection` is enabled on the project but
does not gate the production alias — only preview deployments.

## Environment variables (Vercel → Production)

| Name | Value | Secret? |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | `https://hyibaljxicojfoxbihdn.supabase.co` | No — ships in the browser bundle |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | `sb_publishable__…` | No — same reason |
| `NEXT_PUBLIC_SITE_URL` | `https://vedant-grocery.vercel.app` | No |

`NEXT_PUBLIC_SITE_URL` is **not optional**. Without it, canonical links, OpenGraph URLs and the
`GroceryStore` JSON-LD all point at `http://localhost:3000` — which shipped in the first deploy
and had to be corrected. If the domain changes, update this variable and redeploy.

**Never** set a service-role key or a PAT as a `NEXT_PUBLIC_*` variable. Anything prefixed
`NEXT_PUBLIC_` is readable by every visitor.

## Deploying

```bash
cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables"
vercel deploy --prod --archive=tgz --name=vedant-grocery
```

`--name` is required: the directory name contains spaces, and Vercel rejects a derived project
name (`Project names … must be lowercase`).

### `.vercelignore` matters

Without it the CLI archived **1.8 GB** — `node_modules` plus a stale 2.2 GB `.next-old`.
The file excludes those. **Its patterns are root-anchored on purpose**: a bare `supabase`
matches at any depth and also excluded `src/lib/supabase/`, which broke the build with
`Can't resolve '@/lib/supabase/client'`.

## Post-deploy verification

```bash
# public
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app/categories
curl -s -o /dev/null -w "%{http_code}\n" "https://vedant-grocery.vercel.app/search?q=tomato"

# auth-gated routes must 307 to /login, not 200
curl -s -o /dev/null -w "%{http_code}\n" https://vedant-grocery.vercel.app/orders
```

Expected, measured on 2026-10-22:

| Route | Code |
|---|---|
| `/`, `/categories`, `/offers`, `/search?q=tomato`, `/login`, `/signup`, `/cart`, `/products/<id>` | 200 |
| `/account`, `/orders`, `/support` | **307** (to login — correct) |

Then confirm the data layer actually reached Supabase, not a cached shell:

```bash
curl -s https://vedant-grocery.vercel.app | grep -o '/products/[a-f0-9-]\{36\}' | sort -u | wc -l
# expected: 12
curl -s https://vedant-grocery.vercel.app | grep -c 'localhost:3000'
# expected: 0
```

## Rollback

See `ROLLBACK.md`. Short version: `vercel rollback` to the previous deployment, or redeploy any
earlier commit — the app is stateless apart from Supabase.

## Known deployment limitations

- No custom domain. `vedant-grocery.vercel.app` is the production URL. Adding a domain is a
  dashboard step plus a `NEXT_PUBLIC_SITE_URL` change.
- No CI gate on deploy. `.github/workflows/ci.yml` exists but there is no remote, so it has never
  run. Deploys are manual and currently unverified by CI.
- Preview deployments require Vercel authentication.
