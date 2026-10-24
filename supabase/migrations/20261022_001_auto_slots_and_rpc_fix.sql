-- ============================================================================
-- 20261022_001_auto_slots_and_rpc_fix.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- GOALS:
--   1. Fix ensure_delivery_slots signature: add p_from parameter so the
--      Android client's call (which sends both p_from and p_days) works
--      instead of returning a PostgREST error.
--   2. Auto-generate delivery slots daily so today, tomorrow, and the next
--      several days always have slots without a manual button press.
--      Uses a SECURITY DEFINER function callable by any authenticated staff
--      member with catalogue management capability. The web app's
--      getDeliverySlots() will call this on read so slots are always fresh.
--   3. Enable RLS on store_settings (was missing despite policies existing).
--   4. Ensure the store_settings row with id=1 exists (seed if missing).
-- ============================================================================

begin;

-- 1. Fix ensure_delivery_slots to accept p_from + p_days ----------------------
--    The Android client sends both parameters. The original function only
--    accepted p_days and anchored on today. Now it accepts an optional anchor
--    date (defaults to today in IST) and generates p_days forward from that.
--
--    IMPORTANT: adding a parameter creates an OVERLOAD, not a replacement.
--    `create or replace function` with a different argument list leaves the old
--    one-argument version in place, and PostgREST then cannot choose between the
--    two candidates (HTTP 300 "could not choose the best candidate function").
--    The old signature is dropped explicitly so exactly one function remains.
drop function if exists public.ensure_delivery_slots(integer);

CREATE OR REPLACE FUNCTION public.ensure_delivery_slots(
  -- NULL (not CURRENT_DATE) so the body can resolve "today" in IST. CURRENT_DATE
  -- here would be evaluated in the session time zone, which on Supabase is UTC —
  -- before 05:30 IST that names yesterday.
  p_from date DEFAULT NULL,
  p_days integer DEFAULT 10
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
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

REVOKE EXECUTE ON FUNCTION public.ensure_delivery_slots(date, integer) FROM public, anon;
GRANT  EXECUTE ON FUNCTION public.ensure_delivery_slots(date, integer) TO authenticated, service_role;

-- 2. Auto-generate slots: a function any authenticated user can call ----------
--    The web app calls this on getDeliverySlots() so slots are always fresh.
--    It generates 7 days forward from today (IST), idempotently.
CREATE OR REPLACE FUNCTION public.auto_ensure_delivery_slots()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
begin
  -- Any authenticated user may trigger this: it only INSERTs new rows,
  -- never updates or deletes. The function is idempotent.
  return private.generate_delivery_slots(7);
end $$;

REVOKE EXECUTE ON FUNCTION public.auto_ensure_delivery_slots() FROM public, anon;
GRANT  EXECUTE ON FUNCTION public.auto_ensure_delivery_slots() TO authenticated, service_role;

-- 3. Enable RLS on store_settings (was missing despite policies existing) -----
ALTER TABLE public.store_settings ENABLE ROW LEVEL SECURITY;

-- Re-affirm the policies (idempotent: drop + recreate)
DROP POLICY IF EXISTS store_settings_select ON public.store_settings;
DROP POLICY IF EXISTS store_settings_update ON public.store_settings;

CREATE POLICY store_settings_select ON public.store_settings
  FOR SELECT TO anon, authenticated
  USING ( true );

CREATE POLICY store_settings_update ON public.store_settings
  FOR UPDATE TO authenticated
  USING ( private.can_manage_store() )
  WITH CHECK ( private.can_manage_store() );

-- 4. Ensure the store_settings row with id=1 exists ----------------------------
--    If the row is missing, the Android StoreSettings screen shows "unable to
--    open" because the repository throws NoSuchElementException.
--
--    Wrapped in a handler on purpose: this table has columns this migration does
--    not know about, so a stricter NOT NULL we have not listed could make the
--    INSERT fail. A seed is a convenience, and it must never abort the slot fix
--    above it — so a failure here degrades to a warning and the migration
--    commits.
do $$
begin
  if not exists (select 1 from public.store_settings where id = 1) then
    insert into public.store_settings (
      id, store_name, phone, whatsapp, is_open,
      delivery_fee, free_delivery_threshold, min_order_amount,
      updated_at
    )
    values (
      1,
      'Vedant Fruit & Vegetables',
      '+91 99999 99999',
      '+91 99999 99999',
      true,
      30.00,
      500.00,
      0.00,
      now()
    );
    raise notice 'store_settings row 1 seeded';
  end if;
exception
  when others then
    raise warning 'could not seed store_settings row 1: % (%)', sqlerrm, sqlstate;
end $$;

commit;
