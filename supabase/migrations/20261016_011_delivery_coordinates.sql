-- ============================================================================
-- 20261016_011_delivery_coordinates.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-20.
-- ----------------------------------------------------------------------------
-- WHY: `delivery_orders()` returned a text address only. Brief §17 requires the
-- courier's NAVIGATE action to prefer **latitude/longitude over an ambiguous
-- text address** — "12 Test Street, Pune" can resolve to the wrong end of a
-- street, a coordinate cannot.
--
-- WHERE THE COORDINATES COME FROM:
--   `orders.address_snapshot` does NOT carry lat/lng (it stores name, phone,
--   line1, city, pincode at placement time). The coordinates live on the
--   `addresses` row the order points at, via `orders.address_id`. This migration
--   LEFT JOINs that row and returns `latitude`/`longitude` as nullable.
--
-- NULLABILITY IS DELIBERATE: an address saved before coordinates were captured
-- has none, and `address_id` is `ON DELETE SET NULL`. The Android client must
-- therefore fall back to a text-address navigation intent rather than fabricating
-- a coordinate (brief §58 forbids fake GPS).
--
-- The function is REPLACED, not extended in place: Postgres cannot change a
-- function's OUT columns with CREATE OR REPLACE, so it is dropped first. The
-- signature, gate and ACL are unchanged.
-- ============================================================================

begin;

drop function if exists public.delivery_orders(boolean);

create function public.delivery_orders(p_only_mine boolean default true)
returns table (
  order_id uuid, order_number text, status public.order_status,
  placed_at timestamptz, delivery_date date,
  customer_name text, customer_phone text, address_line1 text,
  address_city text, address_pincode text,
  latitude double precision, longitude double precision,
  item_count bigint, total numeric, final_total numeric,
  assigned_to uuid, assigned_to_name text
)
language plpgsql stable security definer set search_path = '' as $$
declare v_role public.staff_role;
begin
  if not private.can_manage_orders() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  v_role := private.role_of();
  return query
  select o.id, o.order_number, o.status, o.placed_at, o.delivery_date,
         nullif(o.address_snapshot->>'name',''),
         nullif(o.address_snapshot->>'phone',''),
         nullif(o.address_snapshot->>'line1',''),
         nullif(o.address_snapshot->>'city',''),
         nullif(o.address_snapshot->>'pincode',''),
         a.latitude,
         a.longitude,
         (select count(*) from public.order_items i where i.order_id = o.id),
         o.total, o.final_total,
         o.assigned_to, u.full_name
  from public.orders o
  left join public.addresses a on a.id = o.address_id
  left join public.profiles  u on u.id = o.assigned_to
  where (not p_only_mine
         or v_role <> 'DELIVERY'
         or o.assigned_to = (select auth.uid()))
    and o.status not in ('DELIVERED','CANCELLED')
  order by o.delivery_date asc nulls last, o.placed_at asc;
end $$;

revoke execute on function public.delivery_orders(boolean) from public, anon;
grant  execute on function public.delivery_orders(boolean) to authenticated, service_role;

commit;
