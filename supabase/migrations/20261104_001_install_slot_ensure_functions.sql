-- ============================================================================
-- 20261104_001_install_slot_ensure_functions.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- The live project never received 20261022_001 (auto_slots_and_rpc_fix): it is
-- missing public.auto_ensure_delivery_slots() and only has the stale one-arg
-- public.ensure_delivery_slots(integer). Because the storefront calls
-- auto_ensure_delivery_slots() best-effort on every slot read, that call was a
-- silent no-op and the rolling top-up never ran from the website.
--
-- This migration installs the missing functions idempotently:
--   * DROPs the stale `ensure_delivery_slots(integer)` signature first, so the
--     two-arg version below is the ONLY one and PostgREST never sees an
--     overload-ambiguity (the exact problem 20261022_001 documented).
--   * CREATEs `ensure_delivery_slots(p_from date DEFAULT NULL, p_days integer
--     DEFAULT 10)` with the body verbatim from 20261022_001: IST-anchored,
--     idempotent, never creates a fully-passed window, SECURITY DEFINER,
--     search_path='', admin-gated via private.can_manage_catalogue().
--   * CREATEs `auto_ensure_delivery_slots()` (SECURITY DEFINER, search_path='')
--     which calls private.generate_delivery_slots(7) — callable by any
--     authenticated user (idempotent, insert-only).
--   * REVOKEs public/anon; GRANTs authenticated + service_role on both.
--
-- Idempotent: DROP IF EXISTS + CREATE OR REPLACE + REVOKE/GRANT throughout.
-- ============================================================================

begin;

-- 1. Drop the stale one-arg signature ----------------------------------------
drop function if exists public.ensure_delivery_slots(integer);

-- 2. Two-arg ensure_delivery_slots (verbatim body from 20261022_001) ---------
create or replace function public.ensure_delivery_slots(
  -- NULL (not CURRENT_DATE) so the body can resolve "today" in IST. CURRENT_DATE
  -- here would be evaluated in the session time zone, which on Supabase is UTC —
  -- before 05:30 IST that names yesterday.
  p_from date DEFAULT NULL,
  p_days integer DEFAULT 10
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_windows constant jsonb := '[
    {"name":"Morning",  "start":"08:00", "end":"10:00", "sort":1},
    {"name":"Afternoon","start":"12:00", "end":"14:00", "sort":2},
    {"name":"Evening",  "start":"17:00", "end":"19:00", "sort":3}
  ]'::jsonb;
  v_capacity constant int := 25;
  v_days  int := least(greatest(coalesce(p_days, 10), 1), 60);
  v_anchor date := coalesce(p_from, (now() at time zone 'Asia/Kolkata')::date);
  v_date  date;
  v_w     jsonb;
  v_created int := 0;
  v_start timestamptz;
  v_end   timestamptz;
  i       int;
begin
  if not private.can_manage_catalogue() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  for i in 0 .. v_days - 1 loop
    v_date := v_anchor + i;

    for v_w in select * from jsonb_array_elements(v_windows) loop
      v_start := ((v_date::text || ' ' || (v_w->>'start'))::timestamp
                    at time zone 'Asia/Kolkata');
      v_end   := ((v_date::text || ' ' || (v_w->>'end'))::timestamp
                    at time zone 'Asia/Kolkata');

      -- Never generate a window that has already fully passed.
      continue when v_end <= now();

      -- Idempotent: skip if a slot with this starts_at already exists.
      if exists (select 1 from public.delivery_slots s where s.starts_at = v_start) then
        continue;
      end if;

      insert into public.delivery_slots
        (name, start_time, end_time, max_orders, capacity, is_active, sort_order, starts_at, ends_at)
      values
        (v_w->>'name',
         (v_w->>'start')::time,
         (v_w->>'end')::time,
         v_capacity, v_capacity, true, (v_w->>'sort')::int,
         v_start, v_end);

      v_created := v_created + 1;
    end loop;
  end loop;

  return v_created;
end $$;

-- 3. auto_ensure_delivery_slots (callable by any authenticated user) ---------
create or replace function public.auto_ensure_delivery_slots()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
begin
  return private.generate_delivery_slots(7);
end $$;

-- 4. ACLs ---------------------------------------------------------------------
revoke execute on function public.ensure_delivery_slots(date, integer) from public, anon;
grant  execute on function public.ensure_delivery_slots(date, integer) to authenticated, service_role;

revoke execute on function public.auto_ensure_delivery_slots() from public, anon;
grant  execute on function public.auto_ensure_delivery_slots() to authenticated, service_role;

commit;
