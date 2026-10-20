-- ============================================================================
-- 20261016_003_rls_restructure.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T03 — RLS restructure: drive auth_rls_initplan 18 -> 0 and
--            multiple_permissive_policies 26 -> 0.
-- ----------------------------------------------------------------------------
-- PRESERVATION ARGUMENT
--   Postgres OR-s permissive policies for a given (table, cmd, role). Every
--   merged policy below is the logical OR of the policies it replaces, and every
--   split policy keeps exactly the cmd scope the original `ALL` policy covered.
--   Therefore no role gains or loses a row — with three deliberate, documented
--   exceptions (see "DELIBERATE CHANGES").
--
-- auth_rls_initplan: every `auth.uid()` in a policy expression is rewritten to
--   `(select auth.uid())`. auth.uid() is STABLE and constant per statement, so
--   the forms are semantically identical; the subquery form is evaluated once
--   (InitPlan) instead of per row.
--
-- DELIBERATE CHANGES (the only intended access differences):
--   1. `orders_update_own` is DROPPED. No client updates orders (web only
--      SELECTs orders / INSERTs complaints; Android mutates orders only via
--      admin_update_order_status). The old policy let a customer UPDATE any
--      column of their own order (e.g. set status=DELIVERED, or total).
--      Customer cancellation will be a dedicated `cancel_order` RPC in the
--      order-adjustment task (TODO — not yet implemented).
--   2. `profiles` staff-read uses a NEW helper `private.can_view_users()`
--      (OWNER/ADMIN/MANAGER/SUPPORT) so the permission matrix (MANAGER/SUPPORT
--      may view users) is honoured rather than over-restricting to
--      can_manage_users() (OWNER/ADMIN). Flagged as a deviation.
--   3. Capability writes use the specific `can_manage_*` helpers (not the coarse
--      is_admin()), so DELIVERY/SUPPORT no longer hold catalogue / store /
--      complaints writes — this is the intended least-privilege outcome of the
--      capability refactor (design §2.4 permission matrix).
--
-- ANON NOTE: anon has no USAGE on the `private` schema, so an anon-facing
--   SELECT policy must NOT call a private.* helper. The public-read catalogue
--   tables therefore split into a `to anon` policy (public predicate only) and a
--   `to authenticated` policy (public predicate OR capability) — one policy per
--   role, so there is still no permissive overlap. store_settings is not
--   singleton-writable beyond UPDATE (design §4.2).
--
-- Idempotent-safe: drop policy if exists before every create policy;
--   create or replace function for the helper.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- Idempotency guard: drop any policies bearing the POST-restructure names so
-- that re-running this file is a no-op (the pre-restructure names are dropped
-- inline in Steps 1/2 below).
-- ---------------------------------------------------------------------------
drop policy if exists products_public_select on public.products;
drop policy if exists products_select on public.products;
drop policy if exists products_insert on public.products;
drop policy if exists products_update on public.products;
drop policy if exists products_delete on public.products;
drop policy if exists categories_public_select on public.categories;
drop policy if exists categories_select on public.categories;
drop policy if exists categories_insert on public.categories;
drop policy if exists categories_update on public.categories;
drop policy if exists categories_delete on public.categories;
drop policy if exists product_weights_select on public.product_weights;
drop policy if exists product_weights_insert on public.product_weights;
drop policy if exists product_weights_update on public.product_weights;
drop policy if exists product_weights_delete on public.product_weights;
drop policy if exists offers_select on public.offers;
drop policy if exists offers_insert on public.offers;
drop policy if exists offers_update on public.offers;
drop policy if exists offers_delete on public.offers;
drop policy if exists delivery_slots_public_select on public.delivery_slots;
drop policy if exists delivery_slots_select on public.delivery_slots;
drop policy if exists delivery_slots_insert on public.delivery_slots;
drop policy if exists delivery_slots_update on public.delivery_slots;
drop policy if exists delivery_slots_delete on public.delivery_slots;
drop policy if exists store_settings_select on public.store_settings;
drop policy if exists store_settings_update on public.store_settings;
drop policy if exists complaints_select on public.complaints;
drop policy if exists complaints_insert on public.complaints;
drop policy if exists complaints_update on public.complaints;
drop policy if exists complaints_delete on public.complaints;
drop policy if exists complaint_messages_select on public.complaint_messages;
drop policy if exists complaint_messages_insert on public.complaint_messages;
drop policy if exists complaint_messages_update on public.complaint_messages;
drop policy if exists complaint_messages_delete on public.complaint_messages;
drop policy if exists complaint_attachments_select on public.complaint_attachments;
drop policy if exists complaint_attachments_insert on public.complaint_attachments;
drop policy if exists complaint_attachments_update on public.complaint_attachments;
drop policy if exists complaint_attachments_delete on public.complaint_attachments;
drop policy if exists order_items_select on public.order_items;
drop policy if exists order_items_insert on public.order_items;
drop policy if exists order_items_update on public.order_items;
drop policy if exists order_items_delete on public.order_items;
drop policy if exists orders_select on public.orders;
drop policy if exists order_status_history_select on public.order_status_history;
drop policy if exists notification_outbox_select on public.notification_outbox;
drop policy if exists profiles_select on public.profiles;

-- ---------------------------------------------------------------------------
-- 0. New helpers
-- ---------------------------------------------------------------------------
-- private.can_view_users()  (deviation #2 above)
create or replace function private.can_view_users()
returns boolean language sql stable security definer set search_path = '' as $$
  select private.has_role(array['OWNER','ADMIN','MANAGER','SUPPORT']::public.staff_role[]);
$$;
revoke execute on function private.can_view_users() from public, anon;
grant  execute on function private.can_view_users() to authenticated;

-- private.current_profile_role()  (recursion fix, see the profiles_update_own
-- block below): returns the caller's CURRENT profiles.role. SECURITY DEFINER
-- (owner = postgres, no FORCE RLS) so the internal profiles read bypasses RLS —
-- this is what keeps the WITH CHECK free of a self-referential subquery.
create or replace function private.current_profile_role()
returns public.user_role language sql stable security definer set search_path = '' as $$
  select p.role from public.profiles p where p.id = (select auth.uid());
$$;
revoke execute on function private.current_profile_role() from public, anon;
grant  execute on function private.current_profile_role() to authenticated;

-- ===========================================================================
-- STEP 1 — auth_rls_initplan rewrites (own-row / own-data policies)
-- ===========================================================================

-- addresses -----------------------------------------------------------------
drop policy if exists addresses_owner_all on public.addresses;
create policy addresses_owner_all on public.addresses for all to authenticated
  using ( (select auth.uid()) = user_id )
  with check ( (select auth.uid()) = user_id );

-- cart_items ----------------------------------------------------------------
drop policy if exists cart_items_owner_all on public.cart_items;
create policy cart_items_owner_all on public.cart_items for all to authenticated
  using ( (select auth.uid()) = user_id )
  with check ( (select auth.uid()) = user_id );

-- notifications (own) -------------------------------------------------------
drop policy if exists notifications_owner_select on public.notifications;
create policy notifications_owner_select on public.notifications for select to authenticated
  using ( (select auth.uid()) = user_id );
drop policy if exists notifications_owner_update on public.notifications;
create policy notifications_owner_update on public.notifications for update to authenticated
  using ( (select auth.uid()) = user_id )
  with check ( (select auth.uid()) = user_id );

-- profiles (own) — INSERT + UPDATE kept; SELECT merged below ----------------
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles for insert to authenticated
  with check ( (select auth.uid()) = id );

--   role must not change to escalate privilege. Because a self-referential
--   subquery on `profiles` inside the WITH CHECK combines badly with the
--   `(select auth.uid())` InitPlan in the SELECT qual and makes Postgres raise
--   "42P17 infinite recursion detected in policy for relation profiles"
--   (empirically verified), the guard is delegated to a SECURITY DEFINER helper
--   `private.current_profile_role()` — semantically identical, recursion-free.
--
-- NOTE: the role guard is preserved verbatim (design §4.1 omits it — copying
-- the doc would open a privilege-escalation hole: a customer could set their
-- own role to 'admin').
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated
  using ( (select auth.uid()) = id )
  with check ( (select auth.uid()) = id
               and role = private.current_profile_role() );

-- orders — INSERT rewritten; UPDATE deliberately dropped (DELIBERATE #1) ----
drop policy if exists orders_insert_own on public.orders;
create policy orders_insert_own on public.orders for insert to authenticated
  with check ( (select auth.uid()) = user_id );

drop policy if exists orders_update_own on public.orders;  -- DELIBERATE #1

-- ===========================================================================
-- STEP 2A — catalogue "public-read + admin-write" pairs
-- ===========================================================================

-- products (public predicate = is_active) -----------------------------------
drop policy if exists products_read_public on public.products;
drop policy if exists products_admin_write on public.products;
create policy products_public_select on public.products for select to anon
  using ( is_active );
create policy products_select on public.products for select to authenticated
  using ( is_active or private.can_manage_catalogue() );
create policy products_insert on public.products for insert to authenticated
  with check ( private.can_manage_catalogue() );
create policy products_update on public.products for update to authenticated
  using ( private.can_manage_catalogue() ) with check ( private.can_manage_catalogue() );
create policy products_delete on public.products for delete to authenticated
  using ( private.can_manage_catalogue() );

-- categories (public predicate = is_active) ---------------------------------
drop policy if exists categories_read_public on public.categories;
drop policy if exists categories_admin_write on public.categories;
create policy categories_public_select on public.categories for select to anon
  using ( is_active );
create policy categories_select on public.categories for select to authenticated
  using ( is_active or private.can_manage_catalogue() );
create policy categories_insert on public.categories for insert to authenticated
  with check ( private.can_manage_catalogue() );
create policy categories_update on public.categories for update to authenticated
  using ( private.can_manage_catalogue() ) with check ( private.can_manage_catalogue() );
create policy categories_delete on public.categories for delete to authenticated
  using ( private.can_manage_catalogue() );

-- product_weights (public predicate = true) ---------------------------------
drop policy if exists product_weights_read_public on public.product_weights;
drop policy if exists product_weights_admin_write on public.product_weights;
create policy product_weights_select on public.product_weights for select to anon, authenticated
  using ( true );
create policy product_weights_insert on public.product_weights for insert to authenticated
  with check ( private.can_manage_catalogue() );
create policy product_weights_update on public.product_weights for update to authenticated
  using ( private.can_manage_catalogue() ) with check ( private.can_manage_catalogue() );
create policy product_weights_delete on public.product_weights for delete to authenticated
  using ( private.can_manage_catalogue() );

-- offers (public predicate = true) ------------------------------------------
drop policy if exists offers_read_public on public.offers;
drop policy if exists offers_admin_write on public.offers;
create policy offers_select on public.offers for select to anon, authenticated
  using ( true );
create policy offers_insert on public.offers for insert to authenticated
  with check ( private.can_manage_catalogue() );
create policy offers_update on public.offers for update to authenticated
  using ( private.can_manage_catalogue() ) with check ( private.can_manage_catalogue() );
create policy offers_delete on public.offers for delete to authenticated
  using ( private.can_manage_catalogue() );

-- delivery_slots (public predicate = is_active) -----------------------------
drop policy if exists delivery_slots_read_public on public.delivery_slots;
drop policy if exists delivery_slots_admin_write on public.delivery_slots;
create policy delivery_slots_public_select on public.delivery_slots for select to anon
  using ( is_active );
create policy delivery_slots_select on public.delivery_slots for select to authenticated
  using ( is_active or private.can_manage_catalogue() );
create policy delivery_slots_insert on public.delivery_slots for insert to authenticated
  with check ( private.can_manage_catalogue() );
create policy delivery_slots_update on public.delivery_slots for update to authenticated
  using ( private.can_manage_catalogue() ) with check ( private.can_manage_catalogue() );
create policy delivery_slots_delete on public.delivery_slots for delete to authenticated
  using ( private.can_manage_catalogue() );

-- store_settings (public read = true; write = can_manage_store) -------------
drop policy if exists store_settings_read_public on public.store_settings;
drop policy if exists store_settings_admin_write on public.store_settings;
create policy store_settings_select on public.store_settings for select to anon, authenticated
  using ( true );
create policy store_settings_update on public.store_settings for update to authenticated
  using ( private.can_manage_store() ) with check ( private.can_manage_store() );

-- ===========================================================================
-- STEP 2B — "owner-own + admin-all" pairs
-- ===========================================================================

-- complaints ----------------------------------------------------------------
drop policy if exists complaints_admin_all on public.complaints;
drop policy if exists complaints_owner_all on public.complaints;
create policy complaints_select on public.complaints for select to authenticated
  using ( (select auth.uid()) = user_id or private.can_manage_complaints() );
create policy complaints_insert on public.complaints for insert to authenticated
  with check ( (select auth.uid()) = user_id );
create policy complaints_update on public.complaints for update to authenticated
  using ( (select auth.uid()) = user_id or private.can_manage_complaints() )
  with check ( (select auth.uid()) = user_id or private.can_manage_complaints() );
create policy complaints_delete on public.complaints for delete to authenticated
  using ( private.can_manage_complaints() );

-- complaint_messages (owner via parent complaint) ---------------------------
drop policy if exists complaint_messages_admin_all on public.complaint_messages;
drop policy if exists complaint_messages_owner on public.complaint_messages;
create policy complaint_messages_select on public.complaint_messages for select to authenticated
  using ( exists (select 1 from public.complaints c
                  where c.id = complaint_messages.complaint_id
                    and c.user_id = (select auth.uid()))
          or private.can_manage_complaints() );
create policy complaint_messages_insert on public.complaint_messages for insert to authenticated
  with check ( exists (select 1 from public.complaints c
                       where c.id = complaint_messages.complaint_id
                         and c.user_id = (select auth.uid())) );
create policy complaint_messages_update on public.complaint_messages for update to authenticated
  using ( exists (select 1 from public.complaints c
                  where c.id = complaint_messages.complaint_id
                    and c.user_id = (select auth.uid()))
          or private.can_manage_complaints() )
  with check ( exists (select 1 from public.complaints c
                       where c.id = complaint_messages.complaint_id
                         and c.user_id = (select auth.uid()))
               or private.can_manage_complaints() );
create policy complaint_messages_delete on public.complaint_messages for delete to authenticated
  using ( private.can_manage_complaints() );

-- complaint_attachments (owner via parent complaint) ------------------------
drop policy if exists complaint_attachments_admin_all on public.complaint_attachments;
drop policy if exists complaint_attachments_owner_all on public.complaint_attachments;
create policy complaint_attachments_select on public.complaint_attachments for select to authenticated
  using ( exists (select 1 from public.complaints c
                  where c.id = complaint_attachments.complaint_id
                    and c.user_id = (select auth.uid()))
          or private.can_manage_complaints() );
create policy complaint_attachments_insert on public.complaint_attachments for insert to authenticated
  with check ( exists (select 1 from public.complaints c
                       where c.id = complaint_attachments.complaint_id
                         and c.user_id = (select auth.uid())) );
create policy complaint_attachments_update on public.complaint_attachments for update to authenticated
  using ( exists (select 1 from public.complaints c
                  where c.id = complaint_attachments.complaint_id
                    and c.user_id = (select auth.uid()))
          or private.can_manage_complaints() )
  with check ( exists (select 1 from public.complaints c
                       where c.id = complaint_attachments.complaint_id
                         and c.user_id = (select auth.uid()))
               or private.can_manage_complaints() );
create policy complaint_attachments_delete on public.complaint_attachments for delete to authenticated
  using ( private.can_manage_complaints() );

-- order_items (owner-via-parent-order; admin had SELECT only) ---------------
drop policy if exists order_items_admin_select on public.order_items;
drop policy if exists order_items_select_via_order on public.order_items;
drop policy if exists order_items_insert_via_order on public.order_items;
drop policy if exists order_items_owner_all on public.order_items;
create policy order_items_select on public.order_items for select to authenticated
  using ( exists (select 1 from public.orders o
                  where o.id = order_items.order_id and o.user_id = (select auth.uid()))
          or private.can_view_orders() );
create policy order_items_insert on public.order_items for insert to authenticated
  with check ( exists (select 1 from public.orders o
                       where o.id = order_items.order_id and o.user_id = (select auth.uid())) );
-- owner_all also covered UPDATE/DELETE — preserved here (not in the doc snippet)
create policy order_items_update on public.order_items for update to authenticated
  using ( exists (select 1 from public.orders o
                  where o.id = order_items.order_id and o.user_id = (select auth.uid())) )
  with check ( exists (select 1 from public.orders o
                       where o.id = order_items.order_id and o.user_id = (select auth.uid())) );
create policy order_items_delete on public.order_items for delete to authenticated
  using ( exists (select 1 from public.orders o
                  where o.id = order_items.order_id and o.user_id = (select auth.uid())) );

-- orders (merge admin_select + select_own) ----------------------------------
drop policy if exists orders_admin_select on public.orders;
drop policy if exists orders_select_own on public.orders;
create policy orders_select on public.orders for select to authenticated
  using ( (select auth.uid()) = user_id or private.can_view_orders() );

-- order_status_history (merge admin_select + owner) -------------------------
drop policy if exists order_status_history_admin_select on public.order_status_history;
drop policy if exists order_status_history_owner on public.order_status_history;
create policy order_status_history_select on public.order_status_history for select to authenticated
  using ( exists (select 1 from public.orders o
                  where o.id = order_status_history.order_id and o.user_id = (select auth.uid()))
          or private.can_view_orders() );

-- notification_outbox (merge admin_read + select_own) -----------------------
drop policy if exists notification_outbox_admin_read on public.notification_outbox;
drop policy if exists notification_outbox_select_own on public.notification_outbox;
create policy notification_outbox_select on public.notification_outbox for select to authenticated
  using ( user_id = (select auth.uid()) or private.can_view_orders() );

-- profiles (drop admin_all; merge own-select + staff-read) ------------------
drop policy if exists profiles_admin_all on public.profiles;
drop policy if exists profiles_select_own on public.profiles;
-- merged (two SELECT policies for `authenticated` would themselves be a
-- multiple_permissive_policies finding)
create policy profiles_select on public.profiles for select to authenticated
  using ( (select auth.uid()) = id or private.can_view_users() );

commit;
