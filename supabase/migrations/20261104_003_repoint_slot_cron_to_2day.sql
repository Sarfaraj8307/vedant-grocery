-- ============================================================================
-- 20261104_003_repoint_slot_cron_to_2day.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- BLOCKER FIX (QA Round 1): LIVE has an active pg_cron job
--   jobid=1  jobname='vedant-delivery-slots'  schedule='41 18 * * *' UTC
--            (00:11 IST daily)  active=true  database=postgres username=postgres
--   command = `select private.generate_delivery_slots(14);`
-- Migration 20261104_002 reduced the *website* generator to 2 days and deleted
-- all 87 surplus slots, but never touched cron.job. At the next fire the job
-- would regenerate 14 IST days (~39 out-of-window slots) and silently undo the
-- cleanup, so the 2-day state was not durable.
--
-- THIS MIGRATION repoints that job to the single source of truth,
-- `public.auto_ensure_delivery_slots()` (which already calls
-- `private.generate_delivery_slots(2)`). The job stays ACTIVE and its schedule
-- is unchanged — daily rolling generation is still wanted.
--
-- Design points:
--   * GUARD on `pg_extension`, and EVERY reference to `cron.job` is inside a
--     dynamic EXECUTE string. On a database without pg_cron the guarded branch
--     is never entered, so `cron.job` is never planned and this is a safe no-op.
--   * Uses the official `cron.alter_job()` API rather than a raw
--     `UPDATE cron.job`. This matters: on this project the management-API role
--     is NOT the owner of cron.job, and a raw UPDATE fails with
--     `42501: permission denied for table job`. `cron.alter_job` is SECURITY
--     DEFINER and permitted. It also fires the `cron_job_cache_invalidate`
--     AFTER UPDATE trigger on cron.job, which notifies the pg_cron background
--     worker so the very next fire uses the new command.
--   * Repoint by JOBNAME (stable across jobids), plus a sweep that catches any
--     renamed/duplicate job still calling the long-horizon generator.
--   * IDEMPOTENT: the loop only visits rows whose command differs, and the
--     named job is created only when missing (no duplicates).
--   * Only `command` is changed: passing no other argument to `cron.alter_job`
--     leaves schedule / database / username / active untouched.
--
-- One transaction (begin/commit), applied to LIVE as a single query.
-- ============================================================================

begin;

do $$
declare
  v_command constant text := 'select public.auto_ensure_delivery_slots();';
  v_job record;
  v_updated int := 0;
  v_has_job boolean := false;
begin
  -- GUARD: if pg_cron is not installed, none of the dynamic statements below
  -- are reached, so cron.job is never planned (PL/pgSQL prepares SQL lazily).
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    raise notice '[20261104_003] pg_cron not installed; nothing to repoint.';
  else
    -- Dynamic query, so cron.job is never statically planned. `USING` keeps
    -- the LIKE wildcards literal and avoids any format-string escaping.
    for v_job in execute
      'select jobid, jobname from cron.job
        where (jobname = $1 or command ilike $2 or command ilike $3)
          and command is distinct from $4'
      using 'vedant-delivery-slots',
            '%generate_delivery_slots(%',
            '%auto_ensure_delivery_slots%',
            v_command
    loop
      -- SECURITY DEFINER official API (a raw UPDATE is denied here). NULL
      -- parameters leave every other job attribute unchanged.
      perform cron.alter_job(v_job.jobid, command => v_command);
      v_updated := v_updated + 1;
    end loop;

    -- Fresh project / job removed: recreate the daily 00:11 IST job (active).
    execute 'select exists (select 1 from cron.job where jobname = $1)'
      into v_has_job
      using 'vedant-delivery-slots';
    if not v_has_job then
      perform cron.schedule(
        'vedant-delivery-slots', '41 18 * * *', v_command
      );
      v_updated := v_updated + 1;
    end if;

    raise notice '[20261104_003] repointed % slot cron row(s) to: %', v_updated, v_command;
  end if;
end $$;

commit;
