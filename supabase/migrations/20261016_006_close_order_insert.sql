-- ============================================================================
-- 20261016_006_close_order_insert.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T13 — close the direct-INSERT surface on public.orders / public.order_items
-- ----------------------------------------------------------------------------
-- FINDING (reproduced live, rollback-scoped):
--   Two permissive INSERT policies let any `authenticated` customer write order
--   rows directly through PostgREST, bypassing public.place_order entirely:
--     * public.orders       :: orders_insert_own   (with_check auth.uid() = user_id)
--     * public.order_items  :: order_items_insert  (with_check order owned by caller)
--   A simulated authenticated customer ran
--     insert into public.orders(order_number, user_id, status, delivery_date,
--                               address_snapshot, subtotal, total, idempotency_key)
--     values ('T13PROBE', auth.uid(), 'PENDING', current_date, '{}'::jsonb,
--             0.01, 0.01, gen_random_uuid());
--   and it SUCCEEDED — the client could mint an order with an arbitrary `total`
--   (0.01) and no server-side pricing. This violates the master brief §10 ("the
--   client cannot manipulate price/total") and the architecture documented in
--   src/services/order-service.ts (which already claims no INSERT policy exists
--   for customers — the DB had drifted from the contract).
--
-- FIX: drop both policies. Order placement then has exactly ONE write path:
--   public.place_order_app -> public.place_order, both SECURITY DEFINER owned by
--   `postgres`. Because public.orders / public.order_items are owned by `postgres`
--   and RELFORCEROWSECURITY is false, the DEFINER body bypasses RLS on its own
--   INSERTs — so dropping the client policies does NOT affect checkout.
--
-- PROOF (rollback-scoped, see docs/SUPABASE_FINDING_REGISTER.md §T13):
--   * policies PRESENT  -> direct INSERT (total=0.01) SUCCEEDED  (reproduced)
--   * policies DROPPED  -> direct INSERT -> 42501 "new row violates row-level
--                          security policy for table \"orders\""
--                       -> place_order_app -> SUCCEEDED (order_id returned)
--
-- NO OTHER WRITE PATH IS AFFECTED: verified by grep that every `orders` /
--   `order_items` reference in src/ (web) and admin-android/ (Kotlin) is a
--   SELECT; customer and admin writes go through SECURITY DEFINER RPCs.
--
-- Idempotent: `drop policy if exists` — safe to re-run.
-- ============================================================================

begin;

drop policy if exists orders_insert_own on public.orders;
drop policy if exists order_items_insert on public.order_items;

commit;
