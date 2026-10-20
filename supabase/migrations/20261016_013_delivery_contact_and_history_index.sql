-- ============================================================================
-- 20261016_013_delivery_contact_and_history_index.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-20. Findings by QA (严过关), re-verified live by the lead.
-- ----------------------------------------------------------------------------
-- FINDING 1 — `delivery_orders` returned a null customer name for every order.
--   It read `o.address_snapshot->>'name'`, but the order engine
--   (`public.place_order(uuid,uuid,uuid,date,jsonb)`) builds the snapshot with
--   the key `'full_name'` (source verified: the `jsonb_build_object('label', …,
--   'full_name', v_addr.full_name, …)` in the INSERT). **There is no `name`
--   key.** So a courier's queue showed a blank "Customer" line on every row.
--
--   Fix: read `'full_name'`. Not a rename-and-hope — the old key is kept as a
--   fallback so any row inserted by an older path (or directly by an admin)
--   still resolves.
--
-- FINDING 2 — coordinates were lost when the address row was deleted.
--   `delivery_orders` took `latitude`/`longitude` only from the `addresses`
--   LEFT JOIN. `orders.address_id` is `ON DELETE SET NULL`, so deleting an
--   address set it to null and the join returned nothing — the courier's
--   NAVIGATE button then fell back to an ambiguous text address even though the
--   order had carried real coordinates at placement time.
--
--   Fix: prefer the live `addresses` row, fall back to the snapshot's own
--   `latitude`/`longitude`, which the engine also writes. The snapshot is the
--   authoritative record of what was captured when the order was placed.
--
-- FINDING 3 — `store_status_history.actor_id_fkey` had no index.
--   Introduced by migration 009 (this pass), confirmed by the Advisor's
--   `unindexed_foreign_keys` lint. A single index fixes it; the table is tiny
--   today but "show me every closure this actor made" would seq-scan it.
--
-- The function is REPLACED, not altered: Postgres cannot change a function's OUT
-- columns with CREATE OR REPLACE, so it is dropped first. The signature, gate
-- and ACL are unchanged.
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
         -- `full_name` is what the order engine writes; `name` is kept as a
         -- fallback for rows written by an older path or an admin's direct insert.
         coalesce(
           nullif(o.address_snapshot->>'full_name', ''),
           nullif(o.address_snapshot->>'name', '')
         ),
         nullif(o.address_snapshot->>'phone', ''),
         nullif(o.address_snapshot->>'line1', ''),
         nullif(o.address_snapshot->>'city', ''),
         nullif(o.address_snapshot->>'pincode', ''),
         -- Live address wins; the snapshot carries what was captured at placement
         -- time, so a deleted address (`ON DELETE SET NULL`) no longer loses the pin.
         coalesce(a.latitude, (o.address_snapshot->>'latitude')::double precision),
         coalesce(a.longitude, (o.address_snapshot->>'longitude')::double precision),
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

-- FINDING 3: index the FK migration 009 forgot.
create index if not exists store_status_history_actor_id_idx
  on public.store_status_history (actor_id);

commit;
