-- 20261105_002_orders_notify_trigger.sql
--
-- Attach `notify_order_event()` to the `orders` table.
--
-- The function (which writes phase-specific rows to `notification_outbox` for
-- both the admin and the customer) has existed since 20261016_005, but the
-- trigger that would fire it was never created in the migrations, so order
-- notifications never actually fired. This is the missing bridge.
--
-- Semantics:
--   * AFTER INSERT          -> "New order" for the admin + "order placed" for the
--                              customer.
--   * AFTER UPDATE OF status -> a per-phase customer notification (confirmed,
--                              being packed, packed, out for delivery, delivered,
--                              cancelled).
--
-- It is AFTER, so it runs after the row is committed; writing to
-- `notification_outbox` cannot feed back into `orders`, so there is no
-- recursion risk.
--
begin;

drop trigger if exists orders_notify on public.orders;

create trigger orders_notify
  after insert or update of status on public.orders
  for each row
  execute function public.notify_order_event();

commit;
