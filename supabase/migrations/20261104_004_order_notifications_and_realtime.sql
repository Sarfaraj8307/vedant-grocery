-- ============================================================================
-- 20261104_004_order_notifications_and_realtime.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- ----------------------------------------------------------------------------
-- GOALS:
--   1. Give the customer **readable, phase-specific** order notifications.
--      The old `notify_order_event()` wrote a generic body ("Status updated.")
--      for every status change. The owner asked for friendly per-phase wording:
--      confirmed -> "your order is confirmed by the store", packing -> "your
--      order is packed", delivery -> "your order is being delivered".
--   2. Publish `notification_outbox` over Supabase Realtime so the website can
--      show the notification live in the browser the moment the admin advances
--      an order (the customer's `OrderNotificationToast` subscribes to it).
--
-- DESIGN NOTES:
--   * Realtime needs `replica identity full` on the table so the `new` payload
--     carries `audience`/`user_id` for the client-side filter, and RLS on the
--     table (already present) restricts delivery to the subscribed user's own
--     rows.
--   * The trigger function is replaced in full (idempotent `create or replace`).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Rework notify_order_event() — friendly per-phase customer copy
-- ----------------------------------------------------------------------------
create or replace function public.notify_order_event()
returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  v_title text;
  v_body text;
begin
  if tg_op = 'INSERT' then
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('admin', null, 'new_order', 'New order ' || new.order_number,
            'Order placed for ' || new.delivery_date, jsonb_build_object('order_id', new.id));
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('customer', new.user_id, 'order_placed', 'Order ' || new.order_number || ' placed',
            'We received your order. The store will confirm it shortly.',
            jsonb_build_object('order_id', new.id));
    return new;
  end if;

  if tg_op = 'UPDATE' and old.status is distinct from new.status then
    case new.status
      when 'CONFIRMED' then
        v_title := 'Order ' || new.order_number || ' confirmed';
        v_body := 'Your order is confirmed by the store.';
      when 'PROCESSING' then
        v_title := 'Order ' || new.order_number || ' being packed';
        v_body := 'Your order is being packed.';
      when 'READY_FOR_DELIVERY' then
        v_title := 'Order ' || new.order_number || ' packed';
        v_body := 'Your order is packed and ready for delivery.';
      when 'OUT_FOR_DELIVERY' then
        v_title := 'Order ' || new.order_number || ' out for delivery';
        v_body := 'Your order is being delivered.';
      when 'DELIVERED' then
        v_title := 'Order ' || new.order_number || ' delivered';
        v_body := 'Your order has been delivered. Thank you for shopping with us.';
      when 'CANCELLED' then
        v_title := 'Order ' || new.order_number || ' cancelled';
        v_body := coalesce(nullif(trim(new.cancel_reason), ''), 'Your order was cancelled.');
      else
        v_title := 'Order ' || new.order_number || ': ' || new.status;
        v_body := 'Status updated.';
    end case;

    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('customer', new.user_id, 'order_status', v_title, v_body,
            jsonb_build_object('order_id', new.id, 'status', new.status));
  end if;

  return new;
end $$;

-- ----------------------------------------------------------------------------
-- 2. Publish notification_outbox over Realtime
-- ----------------------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'notification_outbox'
  ) then
    alter publication supabase_realtime add table public.notification_outbox;
  end if;
end $$;

-- Whole-row payloads so the client-side audience/user_id filter works.
alter table public.notification_outbox replica identity full;

-- ----------------------------------------------------------------------------
-- 3. Index for the customer inbox lookup (audience + owner + newest first)
-- ----------------------------------------------------------------------------
create index if not exists notification_outbox_customer_idx
  on public.notification_outbox (audience, user_id, created_at desc);
