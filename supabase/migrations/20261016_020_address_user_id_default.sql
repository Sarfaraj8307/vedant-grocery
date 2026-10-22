-- ============================================================================
-- 20261016_020_address_user_id_default.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- FINDING (user-reported, and it had never worked): adding an address from the
-- app failed with "We could not save that address. Please try again."
--
-- ROOT CAUSE — reproduced through PostgREST as a real signed-in customer:
--   POST /rest/v1/addresses → 42501
--   "new row violates row-level security policy for table \"addresses\""
--
--   * the only policy on `addresses` is `addresses_owner_all` (FOR ALL) with
--     `WITH CHECK (auth.uid() = user_id)`
--   * `addresses.user_id` is NOT NULL and had **no default**
--   * `createAddress()` in `src/services/address-service.ts` never sent `user_id`
--
--   So `user_id` was null on every insert, `auth.uid() = null` is not true, and
--   the policy rejected the row. The client then flattened the 42501 into a
--   generic "try again" toast, which is why this looked like a transient error
--   rather than a hard bug.
--
-- FIX (defence in depth, both sides):
--   1. This migration gives the column a `DEFAULT auth.uid()`, so the row fills
--      itself from the caller's JWT. The policy then trivially passes, and ANY
--      client — current app, future app, a manual REST call — behaves correctly.
--   2. The client now also sends `user_id` explicitly. Belt and braces: if the
--      default is ever dropped, the insert still works, and the intent is
--      visible at the call site instead of being implicit in the schema.
--
-- SECURITY NOTE: `DEFAULT auth.uid()` cannot be abused to write another user's
-- address. The default only applies when the column is OMITTED; an explicit
-- `user_id` is still checked against `auth.uid()` by the same policy, so
-- spoofing someone else's id still returns 42501. Verified after applying.
-- ============================================================================

begin;

alter table public.addresses
  alter column user_id set default auth.uid();

commit;
