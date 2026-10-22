-- ============================================================================
-- 20261016_023_cancel_history_no_duplicate.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- DEFECT IN MIGRATION 022, caught by verifying the result instead of the return
-- value. `customer_cancel_order` inserted into `order_status_history` itself —
-- but `public.orders` already has:
--
--   orders_history  AFTER INSERT OR UPDATE OF status
--                   -> record_order_status_history()
--
-- so every cancellation wrote the history row TWICE:
--
--   null    -> PENDING    "Order placed"
--   PENDING -> CANCELLED  "Ordered by mistake"   <- from the trigger
--   PENDING -> CANCELLED  "Ordered by mistake"   <- from my manual insert
--
-- The RPC returned the correct row and every assertion I made about status,
-- stock and reason passed — the duplicate was only visible by reading the
-- history table afterwards. A duplicated entry would show as a repeated step in
-- the customer's order timeline.
--
-- FIX: drop the manual insert and let the trigger own it. `record_order_status_history`
-- already reads `cancel_reason` (proved: the trigger-written row carried
-- "Ordered by mistake"), so nothing is lost.
--
-- Also verified while here: `orders_notify` fires on the same UPDATE, so the
-- customer's realtime notification works without any extra code.
-- ============================================================================

begin;

create or replace function public.customer_cancel_order(
  p_order_id uuid,
  p_reason   text
)
returns public.orders
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order  public.orders;
  v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
begin
  if (select auth.uid()) is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  -- FOR UPDATE so an admin cannot move the order past the cutoff between the
  -- check below and the update.
  select * into v_order
    from public.orders
   where id = p_order_id
   for update;

  if not found then
    raise exception 'order not found' using errcode = '22023';
  end if;

  -- SECURITY DEFINER runs with the owner's rights, so ownership is checked here
  -- rather than relying on RLS.
  if v_order.user_id <> (select auth.uid()) then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  if not private.order_customer_may_cancel(v_order.status) then
    raise exception 'this order can no longer be cancelled — please contact the store'
      using errcode = '22023';
  end if;

  if v_reason is null then
    raise exception 'a reason is required to cancel an order' using errcode = '22023';
  end if;

  -- Put the goods back on the shelf, rejected lines included: rejection never
  -- credited them back, so cancelling is the only chance to.
  update public.products p
     set stock = p.stock + u.qty
    from (
      select product_id, sum(qty)::int as qty
        from public.order_items
       where order_id = p_order_id
       group by product_id
    ) u
   where p.id = u.product_id;

  -- The `orders_history` trigger writes `order_status_history` for this UPDATE,
  -- and `orders_notify` raises the customer notification. Writing history here
  -- as well produced a duplicate row.
  update public.orders
     set status        = 'CANCELLED',
         cancelled_at  = now(),
         cancel_reason = v_reason,
         updated_at    = now()
   where id = p_order_id
   returning * into v_order;

  return v_order;
end $$;

revoke execute on function public.customer_cancel_order(uuid, text) from public, anon;
grant  execute on function public.customer_cancel_order(uuid, text) to authenticated, service_role;

commit;
