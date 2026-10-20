-- ============================================================================
-- 20261016_001_p0_security_hardening.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Findings addressed (SUPABASE_FINDING_REGISTER.md / advisor):
--   P0  rls_policy_always_true .......... public.events :: events_insert_any
--   P0  anon_security_definer_function_executable (trigger-only functions)
--   P0  authenticated_security_definer_function_executable (trigger-only fns)
--   P1  public_bucket_allows_listing .... storage.objects :: product_images_public_read
--   P1  auth_leaked_password_protection . (applied via /config/auth, not SQL)
-- ----------------------------------------------------------------------------
-- Design notes (all verified against the LIVE database, 2026-10-16):
--  * events has 0 rows and NO writer in either client. There is no user column,
--    so the row cannot be attributed or bounded; the unrestricted INSERT is
--    therefore removed rather than weakened. If anonymous telemetry is wanted
--    later it must be written by a bounded server-side path (rate-limited Edge
--    Function / constrained RPC), never a WITH CHECK (true) policy.
--  * The trigger functions revoked below are verified TRIGGER functions
--    (pg_trigger): handle_new_user, notify_order_event, notify_complaint_event,
--    record_order_status_history, sync_address_pincode, sync_profile_phone,
--    set_updated_at. Triggers execute with the table owner's privileges and do
--    NOT require EXECUTE grants, so revoking them removes an RPC attack surface
--    without changing trigger behaviour. (Six are SECURITY DEFINER;
--    set_updated_at is SECURITY INVOKER.)
--  * public.gen_order_number() is NOT a trigger function (SECURITY INVOKER) and
--    is deliberately KEPT executable by `authenticated` and `service_role`
--    (both hold explicit EXECUTE grants); only PUBLIC + anon are revoked.
--  * public.is_admin() is deliberately KEPT executable by authenticated: it is
--    referenced by RLS policies (orders_admin_select, *_admin_*), which are
--    evaluated as the querying role. Revoking it would break admin access.
--    DO NOT revoke EXECUTE on public.is_admin() in this migration.
--  * private.is_admin()/generate_delivery_slots()/order_transition_allowed() are
--    reached only from SECURITY DEFINER wrappers (public.is_admin() is DEFINER
--    owned by postgres), so PUBLIC/anon/authenticated EXECUTE is revoked.
--  * product-images is a PUBLIC bucket: object URLs are served by
--    /storage/v1/object/public/... which bypasses RLS. Dropping the broad SELECT
--    policy stops clients enumerating the whole bucket while leaving image URLs
--    working. Admin INSERT/UPDATE/DELETE policies are untouched.
-- ============================================================================

begin;

-- 1. events: remove unrestricted INSERT -------------------------------------
--    (the only INSERT policy; events then has RLS on with no policy => all
--    client inserts are denied).
drop policy if exists events_insert_any on public.events;

-- 2. Revoke EXECUTE on trigger-only functions (PUBLIC + anon + authenticated) -
revoke execute on function public.handle_new_user()            from public, anon, authenticated;
revoke execute on function public.notify_order_event()          from public, anon, authenticated;
revoke execute on function public.notify_complaint_event()      from public, anon, authenticated;
revoke execute on function public.record_order_status_history() from public, anon, authenticated;
revoke execute on function public.sync_address_pincode()        from public, anon, authenticated;
revoke execute on function public.sync_profile_phone()          from public, anon, authenticated;
revoke execute on function public.set_updated_at()              from public, anon, authenticated;

-- 3. gen_order_number(): NOT a trigger function. Revoke only PUBLIC + anon;
--    keep the explicit `authenticated` and `service_role` grants.
revoke execute on function public.gen_order_number()            from public, anon;

-- 4. private-schema helpers: not part of the exposed API --------------------
revoke execute on function private.is_admin()                                  from public, anon, authenticated;
revoke execute on function private.generate_delivery_slots(integer)            from public, anon, authenticated;
revoke execute on function private.order_transition_allowed(public.order_status, public.order_status) from public, anon, authenticated;

-- 5. storage: stop whole-bucket listing on the public product-images bucket --
--    (public object URLs bypass RLS, so display is unaffected).
drop policy if exists product_images_public_read on storage.objects;

commit;
