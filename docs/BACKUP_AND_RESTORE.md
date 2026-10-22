# Backup and Restore

> **A backup that has never been restored is not a backup.** Nothing in this document has been
> restore-tested yet — that is stated plainly rather than implied by a confident-sounding doc.

## Current state

| Item | State |
|---|---|
| Automated backups | **Not configured.** The Supabase project is on a plan where daily backups may not be retained. Verify in Dashboard → Database → Backups. |
| PITR (point-in-time recovery) | **Not enabled** — a paid add-on. |
| Manual dump | Possible today via `pg_dump` (see below). |
| Storage objects | Product images live in a Supabase Storage bucket. **Not covered by a database dump.** |
| Migration rollback | No down-migrations. See `ROLLBACK.md` for the compensating-change approach. |

**This is the single largest operational gap.** If the database is lost right now, recovery
depends on whether Supabase's own backup retention applies to this plan.

## What must be backed up

1. **Postgres schema + data** — every table in `public` and `private`.
2. **`auth.users`** — customers and staff. *Not* included in a `public`-schema dump.
3. **Storage objects** — product images.
4. **`supabase/migrations/*.sql`** — already in git, so the schema is reproducible even without a dump.

## Taking a backup

### Option A — Supabase dashboard (simplest, no credentials on disk)

Dashboard → Database → Backups → **Create backup**. Do this before any migration.

### Option B — `pg_dump` from a trusted machine

Get the connection string from Dashboard → Project Settings → Database. It contains the
database password — treat it as a secret and never commit it.

```bash
pg_dump "postgresql://postgres:<PASSWORD>@db.hyibaljxicojfoxbihdn.supabase.co:5432/postgres" \
  --no-owner --no-privileges --format=custom \
  --file "vedant-$(date +%Y%m%d-%H%M).dump"
```

Restore into a **separate** project or local Postgres — never straight over production:

```bash
pg_restore --no-owner --no-privileges --clean --if-exists \
  --dbname "postgresql://postgres:<PASSWORD>@<target-host>:5432/postgres" \
  "vedant-YYYYMMDD-HHMM.dump"
```

### Option C — schema only (fast, proves the migrations are complete)

```bash
pg_dump "postgresql://…" --schema-only --no-owner --format=plain --file schema.sql
```

Diffing that against a fresh `supabase/migrations` replay tells you whether the migration
series is genuinely complete. **This is worth running before every release.**

## Restore procedure (untested — verify before you rely on it)

1. Create a new Supabase project (or a local Postgres).
2. Replay migrations in order: `supabase/migrations/20261016_001` … `017`.
3. Restore the dump.
4. Recreate Storage buckets and re-upload objects.
5. Point `NEXT_PUBLIC_SUPABASE_URL` / `_PUBLISHABLE_KEY` at the new project and redeploy.
6. Re-run the security probes in `PRODUCTION_READINESS.md` — **a restored database that has lost
   its RLS policies is worse than no restore.**

## Recovery objectives (proposed, not yet met)

| Metric | Target | Today |
|---|---|---|
| RPO (max data loss) | 24 h | Unknown — depends on plan retention |
| RTO (time to restore) | 2 h | Unmeasured |

## To close this gap

1. Confirm what backup retention the current plan actually gives you.
2. Enable PITR if it is available on the plan.
3. Run one full restore into a scratch project and time it. Record the result here.
4. Add a scheduled `pg_dump` to object storage outside Supabase.
5. Include the Storage bucket in the backup story.
