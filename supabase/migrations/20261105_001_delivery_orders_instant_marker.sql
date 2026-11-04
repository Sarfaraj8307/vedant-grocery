-- 20261105_001_delivery_orders_instant_marker.sql
--
-- Additive, read-only change to `delivery_orders()`: expose a derived
-- `is_instant` flag so the admin app can highlight instant/express deliveries
-- and start the ETA countdown in the delivery queue.
--
-- No schema change and no new privileges. An instant order is defined exactly as
-- the engine writes it (migration 20261103_001): both `orders.slot_id` and
-- `orders.delivery_slot_id` are NULL. The flag is computed from those two
-- columns at read time, so it can never drift from the source of truth.
--
-- The function's output table grows by one column; `CREATE OR REPLACE` cannot
-- change the return type, so we DROP then re-CREATE and re-apply the same
-- execute grants that 20261016_008 set. This is the only delivery-queue change;
-- the RPC body is otherwise identical to the original.
--
begin;

drop function if exists public.delivery_orders(boolean);

create or replace function public.delivery_orders(p_only_mine boolean default true)
returns table (
  order_id uuid, order_number text, status public.order_status,
  placed_at timestamptz, delivery_date date,
  customer_name text, customer_phone text, address_line1 text,
  address_city text, address_pincode text,
  item_count bigint, total numeric, final_total numeric,
  assigned_to uuid, assigned_to_name text,
  is_instant boolean
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
         (select count(*) from public.order_items i where i.order_id = o.id),
         o.total, o.final_total,
         o.assigned_to, u.full_name,
         (o.slot_id is null and o.delivery_slot_id is null)
  from public.orders o
  left join public.profiles u on u.id = o.assigned_to
  where (not p_only_mine
         or v_role <> 'DELIVERY'
         or o.assigned_to = (select auth.uid()))
    and o.status not in ('DELIVERED','CANCELLED')
  order by o.delivery_date asc nulls last, o.placed_at asc;
end $$;

revoke execute on function public.delivery_orders(boolean) from public, anon;
grant  execute on function public.delivery_orders(boolean) to authenticated, service_role;

commit;
