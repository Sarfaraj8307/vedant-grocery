-- ============================================================================
-- 20261016_010_realtime.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-20.
-- ----------------------------------------------------------------------------
-- FINDING (brief §10 / §38 — the integration objective):
--   The `supabase_realtime` publication existed but contained **no tables**
--   (`pg_publication_tables` → 0 rows), and no client subscribed to anything
--   (`grep -r "\.channel(" src/` → 0 hits). So "admin sees the new order" and
--   "customer sees the status change" were both false: every screen was a
--   manual refresh away from the truth.
--
-- SCOPE — deliberately narrow:
--   * `orders`              — the customer's own row (RLS filters it) and the
--                             admin/delivery queue.
--   * `order_status_history` — the timeline, so a status change is visible even
--                             if the `orders` UPDATE is missed.
--
--   Nothing else is published. `profiles`, `addresses`, `complaints`,
--   `audit_logs` and `private.*` stay OFF the wire: a subscription must never be
--   a way to watch other people's data, and RLS on Realtime only protects rows
--   that the subscriber is already allowed to read — publishing a table is still
--   unnecessary exposure.
--
-- REPLICA IDENTITY FULL on `orders`:
--   With the default identity, the `new` record in a Realtime UPDATE payload
--   carries only the primary key plus changed columns. Clients filter on
--   `user_id` / `assigned_to`, which would then be absent and the filter would
--   silently match nothing. FULL makes the whole row available. The cost is
--   more WAL per update — acceptable at this store's volume (single-digit
--   orders per hour), and it is the documented Supabase recommendation for
--   filtered subscriptions.
-- ============================================================================

begin;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'orders'
  ) then
    alter publication supabase_realtime add table public.orders;
  end if;

  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'order_status_history'
  ) then
    alter publication supabase_realtime add table public.order_status_history;
  end if;
end $$;

-- Whole-row payloads so client-side filters on user_id / assigned_to work.
alter table public.orders replica identity full;

commit;
