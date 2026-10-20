-- ============================================================================
-- 20261016_008_delivery_assignment_and_rpc_gates.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task: delivery workflow (§55) + finish the role matrix (§2.4).
-- ----------------------------------------------------------------------------
-- FINDING 1 (RPC gates too broad):
--   Five order/catalogue RPCs gate on private.is_admin(), which since T02 is a
--   shim for is_staff() = OWNER|ADMIN|MANAGER|DELIVERY|SUPPORT. The design's
--   permission matrix (§2.4) does NOT allow SUPPORT to:
--       * change order status        (OWNER/ADMIN/MANAGER/DELIVERY only)
--       * reject / restore an item   (OWNER/ADMIN/MANAGER/DELIVERY only)
--       * view slot load             (OWNER/ADMIN/MANAGER/DELIVERY only)
--       * manage delivery slots      (OWNER/ADMIN/MANAGER only)
--   So every one of those was callable by SUPPORT. Re-gated below to the
--   specific capability. Each body is copied verbatim from the live database
--   with ONLY the gate expression replaced -- no other change.
--
-- FINDING 2 (no delivery assignment):
--   §55 requires delivery staff to see "assigned orders". There was no notion
--   of assignment at all. Added orders.assigned_to plus two RPCs.
--
-- NOTE on customer contact (§47/§55):
--   orders.address_snapshot already stores {name, phone, line1, city, pincode}
--   at placement time, so delivery staff get the customer's contact details
--   from the order itself. DELIVERY does NOT need to read public.profiles
--   (which T03 correctly removed) -- verified live on order ORD-TEST-1001.
-- ============================================================================

begin;

-- 1. Re-gate the five RPCs (bodies verbatim, gate expression replaced) --------

-- --- admin_reject_order_item: is_admin() -> private.can_manage_orders()
CREATE OR REPLACE FUNCTION public.admin_reject_order_item(p_item_id uuid, p_reason text)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_item    public.order_items%rowtype;
  v_order   public.orders%rowtype;
  v_reason  text := nullif(trim(coalesce(p_reason, '')), '');
  v_original numeric(12,2);
  v_adjust   numeric(12,2);
  v_final    numeric(12,2);
begin
  if not private.can_manage_orders() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  if p_item_id is null then
    raise exception 'item id is required' using errcode = '22023';
  end if;

  if v_reason is null then
    raise exception 'a rejection reason is required' using errcode = '22023';
  end if;

  if char_length(v_reason) > 500 then
    raise exception 'reason is too long (max 500 characters)' using errcode = '22023';
  end if;

  -- Lock the order first, then the item, so concurrent adjustments serialise.
  select o.* into v_order
  from public.orders o
  join public.order_items i on i.order_id = o.id
  where i.id = p_item_id
  for update of o;

  if not found then
    raise exception 'order item not found' using errcode = '22023';
  end if;

  if v_order.status in ('DELIVERED', 'CANCELLED') then
    raise exception 'cannot adjust an order that is %', v_order.status using errcode = '22023';
  end if;

  select * into v_item from public.order_items where id = p_item_id for update;
  if v_item.rejected then
    raise exception 'that item is already rejected' using errcode = '22023';
  end if;

  update public.order_items
  set rejected = true, rejection_reason = v_reason
  where id = p_item_id;

  -- original_total is the pre-adjustment bill; freeze it on first adjustment.
  v_original := coalesce(v_order.original_total, v_order.total);

  -- The adjustment is the NEGATIVE of the rejected value, so it is always <= 0
  -- and the bill can never rise.
  v_adjust := -coalesce((
    select sum(oi.line_total) from public.order_items oi
    where oi.order_id = v_order.id and oi.rejected
  ), 0);

  v_final := v_original + v_adjust;

  if v_final < 0 then
    raise exception 'adjustment would make the total negative' using errcode = '22023';
  end if;

  update public.orders
  set original_total    = v_original,
      adjustment_amount = v_adjust,
      final_total       = v_final,
      adjustment_reason = v_reason,
      adjusted_by       = coalesce(auth.uid()::text, 'admin'),
      adjusted_at       = now()
  where id = v_order.id
  returning * into v_order;

  insert into public.notification_outbox (audience, user_id, type, title, body, data)
  values ('customer', v_order.user_id, 'order_item_rejected',
          'Item unavailable on order ' || v_order.order_number,
          v_item.product_name || ' could not be supplied: ' || v_reason
            || '. Your bill has been revised to ' || v_final::text || '.',
          jsonb_build_object('order_id', v_order.id, 'item_id', p_item_id,
                             'final_total', v_final, 'adjustment_amount', v_adjust));

  return v_order;
end $function$;

-- --- admin_restore_order_item: is_admin() -> private.can_manage_orders()
CREATE OR REPLACE FUNCTION public.admin_restore_order_item(p_item_id uuid)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_item  public.order_items%rowtype;
  v_order public.orders%rowtype;
  v_original numeric(12,2);
  v_adjust   numeric(12,2);
  v_final    numeric(12,2);
begin
  if not private.can_manage_orders() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  select o.* into v_order
  from public.orders o
  join public.order_items i on i.order_id = o.id
  where i.id = p_item_id
  for update of o;

  if not found then
    raise exception 'order item not found' using errcode = '22023';
  end if;

  if v_order.status in ('DELIVERED', 'CANCELLED') then
    raise exception 'cannot adjust an order that is %', v_order.status using errcode = '22023';
  end if;

  select * into v_item from public.order_items where id = p_item_id for update;
  if not v_item.rejected then
    raise exception 'that item is not rejected' using errcode = '22023';
  end if;

  update public.order_items
  set rejected = false, rejection_reason = null
  where id = p_item_id;

  v_original := coalesce(v_order.original_total, v_order.total);

  v_adjust := -coalesce((
    select sum(oi.line_total) from public.order_items oi
    where oi.order_id = v_order.id and oi.rejected
  ), 0);

  v_final := v_original + v_adjust;

  update public.orders
  set original_total    = v_original,
      adjustment_amount = v_adjust,
      final_total       = v_final,
      adjustment_reason = case when v_adjust = 0 then null else adjustment_reason end,
      adjusted_by       = coalesce(auth.uid()::text, 'admin'),
      adjusted_at       = now()
  where id = v_order.id
  returning * into v_order;

  insert into public.notification_outbox (audience, user_id, type, title, body, data)
  values ('customer', v_order.user_id, 'order_item_restored',
          'Item restored on order ' || v_order.order_number,
          v_item.product_name || ' has been added back to your order. Your bill is now '
            || v_final::text || '.',
          jsonb_build_object('order_id', v_order.id, 'item_id', p_item_id,
                             'final_total', v_final, 'adjustment_amount', v_adjust));

  return v_order;
end $function$;

-- --- admin_slot_load: is_admin() -> private.can_manage_orders()
CREATE OR REPLACE FUNCTION public.admin_slot_load(p_from date DEFAULT CURRENT_DATE, p_days integer DEFAULT 7)
 RETURNS TABLE(slot_id uuid, name text, starts_at timestamp with time zone, ends_at timestamp with time zone, capacity integer, is_active boolean, orders_taken bigint, remaining integer)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if not private.can_manage_orders() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  return query
    select s.id, s.name, s.starts_at, s.ends_at,
           coalesce(s.capacity, s.max_orders, 0)::int as capacity,
           s.is_active,
           (select count(*) from public.orders o
             where (o.delivery_slot_id = s.id or o.slot_id = s.id)
               and o.status <> 'CANCELLED') as orders_taken,
           greatest(0, coalesce(s.capacity, s.max_orders, 0)
             - (select count(*) from public.orders o
                 where (o.delivery_slot_id = s.id or o.slot_id = s.id)
                   and o.status <> 'CANCELLED'))::int as remaining
    from public.delivery_slots s
    where (s.starts_at is null
           or (s.starts_at >= p_from::timestamptz
               and s.starts_at < (p_from + greatest(p_days,1))::timestamptz))
    order by s.starts_at nulls last, s.sort_order, s.name;
end $function$;

-- --- admin_update_order_status: is_admin() -> private.can_manage_orders()
CREATE OR REPLACE FUNCTION public.admin_update_order_status(p_order_id uuid, p_status order_status, p_reason text DEFAULT NULL::text)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_order public.orders%rowtype;
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
begin
  if not private.can_manage_orders() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  if p_order_id is null or p_status is null then
    raise exception 'order id and status are required' using errcode = '22023';
  end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then
    raise exception 'order not found' using errcode = '22023';
  end if;

  if v_order.status = p_status then
    raise exception 'order is already %', p_status using errcode = '22023';
  end if;

  if not private.order_transition_allowed(v_order.status, p_status) then
    raise exception 'cannot move an order from % to %', v_order.status, p_status using errcode = '22023';
  end if;

  if p_status = 'CANCELLED' and v_reason is null then
    raise exception 'a cancellation reason is required' using errcode = '22023';
  end if;

  if v_reason is not null and char_length(v_reason) > 500 then
    raise exception 'reason is too long (max 500 characters)' using errcode = '22023';
  end if;

  update public.orders
  set status        = p_status,
      cancelled_at  = case when p_status = 'CANCELLED' then now() else cancelled_at end,
      cancel_reason = case when p_status = 'CANCELLED' then v_reason else cancel_reason end
  where id = p_order_id
  returning * into v_order;

  -- history row + customer notification are written by triggers on public.orders
  return v_order;
end $function$;

-- --- ensure_delivery_slots: is_admin() -> private.can_manage_catalogue()
CREATE OR REPLACE FUNCTION public.ensure_delivery_slots(p_days integer DEFAULT 10)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if not private.can_manage_catalogue() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;
  return private.generate_delivery_slots(p_days);
end $function$;

-- 2. Delivery assignment ------------------------------------------------------
alter table public.orders
  add column if not exists assigned_to uuid references auth.users(id);

create index if not exists orders_assigned_to_idx on public.orders(assigned_to)
  where assigned_to is not null;

-- 3. Assigned orders, with the customer contact the courier needs -------------
--    DELIVERY sees only its own assignments; OWNER/ADMIN/MANAGER see all, so a
--    dispatcher can reallocate. Gate: can_manage_orders() (excludes SUPPORT).
create or replace function public.delivery_orders(p_only_mine boolean default true)
returns table (
  order_id uuid, order_number text, status public.order_status,
  placed_at timestamptz, delivery_date date,
  customer_name text, customer_phone text, address_line1 text,
  address_city text, address_pincode text,
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
         (select count(*) from public.order_items i where i.order_id = o.id),
         o.total, o.final_total,
         o.assigned_to, u.full_name
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

-- 4. Assign a delivery run ----------------------------------------------------
--    Any order-management role may assign (dispatcher duty), but the target
--    must itself hold an order-management role -- assigning to a customer or
--    to SUPPORT would hand them order status control they should not have.
create or replace function public.admin_assign_delivery(p_order_id uuid, p_assigned_to uuid)
returns public.orders
language plpgsql security definer set search_path = '' as $$
declare v_order public.orders;
begin
  if not private.can_manage_orders() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  if p_assigned_to is not null and not private.has_role(
       array['OWNER','ADMIN','MANAGER','DELIVERY']::public.staff_role[]) then
    raise exception 'assignee cannot manage orders' using errcode = '42501';
  end if;
  update public.orders set assigned_to = p_assigned_to where id = p_order_id
    returning * into v_order;
  if not found then
    raise exception 'order not found' using errcode = '22023';
  end if;
  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after)
  values ((select auth.uid()), private.role_of(), 'assign_delivery', 'order', p_order_id::text,
          jsonb_build_object('assigned_to', p_assigned_to));
  return v_order;
end $$;

revoke execute on function public.admin_assign_delivery(uuid, uuid) from public, anon;
grant  execute on function public.admin_assign_delivery(uuid, uuid) to authenticated, service_role;

commit;
