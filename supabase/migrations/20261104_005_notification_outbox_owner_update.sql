-- ============================================================================
-- 20261104_005_notification_outbox_owner_update.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- ----------------------------------------------------------------------------
-- The customer inbox's "Mark all read" button updates `read_at` on
-- `notification_outbox`. The table only ever had a SELECT policy, so that
-- UPDATE was denied by RLS and the button failed. This restores the owner
-- UPDATE policy the legacy `notifications` table had: a customer may only
-- touch their own rows.
-- ============================================================================

drop policy if exists notification_outbox_owner_update on public.notification_outbox;

create policy notification_outbox_owner_update
  on public.notification_outbox
  for update to authenticated
  using ( user_id = auth.uid() )
  with check ( user_id = auth.uid() );
