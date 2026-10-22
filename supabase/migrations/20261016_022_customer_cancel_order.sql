-- ============================================================================
-- 20261016_022_customer_cancel_order.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- OWNER REQUIREMENT: a customer may cancel their own order **only until it is
-- being packed**. After that the order is too far along and they must contact
-- the owner instead. The same window governs any customer-side modification.
--
-- STATUS MODEL
--   PENDING -> CONFIRMED -> PROCESSING -> READY_FOR_DELIVERY -> OUT_FOR_DELIVERY
--                                                          -> DELIVERED / CANCELLED
--
-- `PROCESSING` is "being packed". Reading the requirement as "cancel while the
-- shop can still put the items back on the shelf", the cancellable window is
-- PENDING, CONFIRMED and PROCESSING. From READY_FOR_DELIVERY onwards the goods
-- are packed and allocated, so cancellation is refused and the UI tells the
-- customer to contact the owner.
--
-- That cutoff lives in ONE place — `private.order_customer_may_cancel(status)` —
-- so changing it (for example, to stop at CONFIRMED) is a one-line edit rather
-- than a hunt through the codebase.
--
-- STOCK
--   `place_order` decrements `products.stock` for every line, and neither
--   `admin_reject_order_item` nor `admin_restore_order_item` puts it back
--   (verified). So a cancellation MUST restore it, including rejected lines —
--   those were never credited either. Without this, cancelling would silently
--   destroy inventory.
--
-- `orders.cancelled_at` and `orders.cancel_reason` already existed and are now
-- actually populated.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. The single source of truth for the cutoff.
-- ---------------------------------------------------------------------------
create or replace function private.order_customer_may_cancel(p_status public.order_status)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_status in ('PENDING', 'CONFIRMED', 'PROCESSING');
$$;

revoke execute on function private.order_customer_may_cancel(public.order_status)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2. Customer cancellation.
-- ---------------------------------------------------------------------------
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
  v_order       public.orders;
  v_prev_status public.order_status;
  v_reason      text := nullif(btrim(coalesce(p_reason, '')), '');
begin
  if (select auth.uid()) is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  -- Lock the row so a concurrent admin status change cannot interleave: without
  -- FOR UPDATE an admin could move the order to OUT_FOR_DELIVERY between the
  -- check below and the update, and we would cancel a dispatched order.
  select * into v_order
    from public.orders
   where id = p_order_id
   for update;

  if not found then
    raise exception 'order not found' using errcode = '22023';
  end if;

  -- Ownership is checked explicitly rather than relying on RLS: this function is
  -- SECURITY DEFINER, so it runs with the owner's rights and must gate itself.
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

  -- Captured BEFORE the update, otherwise the history row would read
  -- CANCELLED -> CANCELLED and lose what the order actually was.
  v_prev_status := v_order.status;

  -- Put the goods back on the shelf. Rejected lines are included: rejection
  -- never credited them back, so cancelling is the only chance to.
  update public.products p
     set stock = p.stock + u.qty
    from (
      select product_id, sum(qty)::int as qty
        from public.order_items
       where order_id = p_order_id
       group by product_id
    ) u
   where p.id = u.product_id;

  update public.orders
     set status        = 'CANCELLED',
         cancelled_at  = now(),
         cancel_reason = v_reason,
         updated_at    = now()
   where id = p_order_id
   returning * into v_order;

  insert into public.order_status_history (order_id, old_status, new_status, changed_by, reason)
  values (p_order_id, v_prev_status, 'CANCELLED', (select auth.uid()), v_reason);

  return v_order;
end $$;

revoke execute on function public.customer_cancel_order(uuid, text) from public, anon;
grant  execute on function public.customer_cancel_order(uuid, text) to authenticated, service_role;

commit;
