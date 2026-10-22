# Rollback

## Web

The app is stateless — all state is in Supabase. Rolling back is a redeploy of a previous build.

```bash
# Fastest: promote the previous production deployment
vercel rollback

# Or redeploy a specific commit
git checkout <good-commit>
vercel deploy --prod --archive=tgz --name=vedant-grocery
git checkout master
```

**No database rollback is needed for a web rollback** — the app never writes schema.

## Android

Users hold installed APKs; you cannot roll them back. Instead:

1. Keep the previous `versionCode` build available for direct distribution.
2. Ship a forward fix. The backend must therefore stay **backward-compatible** with the
   previous app version for at least one release cycle.

This is why migrations here are additive: dropping a column or renaming one breaks installed
clients that you cannot recall.

## Database

There are **no down-migrations**. Each migration is wrapped in `begin; … commit;`, so a
migration either fully applies or does not apply at all — but "applied" is not "reversible".

### Preferred: forward fix

Write migration `N+1` that reverses the effect. This is safer than a down-migration because it
is reviewable, recorded in `supabase_migrations.schema_migrations`, and cannot be half-run.

### If a migration must be undone immediately

1. **Stop the bleeding first** — if the change broke authorization, restore the previous policy
   definition before anything else. Security regressions outrank schema tidiness.
2. Take a backup (`BACKUP_AND_RESTORE.md`) — you are about to make destructive changes.
3. Apply a compensating migration. Delete the ledger row only for a migration that *never*
   succeeded, never for one that did:

```sql
-- only when the migration genuinely failed to apply
delete from supabase_migrations.schema_migrations where version = '20261016_0NN';
```

4. Re-run the security probes in `PRODUCTION_READINESS.md`.

### Per-migration reversibility

| Migration | Reversible? | Notes |
|---|---|---|
| 001–006 | Partly | Dropped policies/indexes would have to be recreated from the file |
| 007 | Yes | Re-add the `order_items_update`/`delete` policies — **but that reopens a closed hole. Do not.** |
| 008–013 | Yes | Additive columns/functions; `drop function` + `alter table … drop column` |
| 014 | Yes | Additive; `product_id` NOT NULL is restorable only if no nulls exist |
| 015 | Yes | `drop table wallet_transactions, wallet_accounts` |
| 016 | Yes | `drop table delivery_issues` |
| 017 | **Careful** | Re-granting table-level `SELECT` on `profiles` **re-exposes `internal_notes`** |

**Migrations 007 and 017 are deliberately not safely reversible** — reverting them restores a
known vulnerability. If you must revert either, replace it with a compensating change rather
than an undo.

## Data loss scenarios

| Scenario | Recovery |
|---|---|
| Bad deploy | `vercel rollback` (seconds) |
| Bad migration | Compensating migration + security re-probe |
| Accidental row deletion | Restore from backup — **RPO unknown, see `BACKUP_AND_RESTORE.md`** |
| Dropped table | Restore from backup |
| Leaked credential | Rotate it; check `audit_logs` for use |
