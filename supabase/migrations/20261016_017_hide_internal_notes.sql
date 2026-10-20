-- ============================================================================
-- 20261016_017_hide_internal_notes.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-21.
-- ----------------------------------------------------------------------------
-- FINDING — introduced by migration 015, caught by a probe before release.
--
-- Migration 015 added `profiles.internal_notes`. RLS is a **row-level** control, so
-- it decides which *rows* a caller may see — never which *columns* inside an
-- allowed row. A probe proved the leak: a signed-in user selecting their own
-- profile row received `internal_notes: "SUPER SECRET: VIP discount approved"`
-- back. An admin note about a customer would be readable by that customer, which
-- brief §18 explicitly forbids.
--
-- WHY MY FIRST ATTEMPT FAILED (documented so it is not repeated)
-- I first wrote `REVOKE SELECT (internal_notes) ON public.profiles FROM …`. That
-- is a **column-level** revoke and it cannot remove a privilege granted at the
-- **table** level — and `anon`/`authenticated` hold a whole-table `SELECT`. The
-- revoke was a silent no-op and the leak was still live. The correct sequence is:
--
--   1. REVOKE SELECT ON the whole table (removes the table-level grant)
--   2. GRANT SELECT (every column EXCEPT internal_notes)  (column-level, explicit)
--
-- VERIFIED at three levels after applying:
--   * SQL: `select id, full_name, role, account_status from profiles` → returns the row
--   * SQL: `select internal_notes from profiles` → `42501 permission denied`
--   * REST: `?select=id,full_name,role` → 200 with data
--   * REST: `?select=id,internal_notes` → `42501`
--
-- MAINTENANCE NOTE: every future column added to `profiles` will be invisible to
-- `anon`/`authenticated` until it is added to the GRANT list below. That is the
-- safe failure direction — a new column silently missing is far better than a
// private one silently exposed — but it will surface as "column does not exist"
// for customers, so grep this file when adding a profile column.
-- ============================================================================

begin;

-- 1. Remove the table-level SELECT that made a column-level revoke useless.
revoke select on public.profiles from anon, authenticated;

-- 2. Re-grant every column except the private one.
--    If a column is ever added to `profiles`, add it here.
grant select (
  id, full_name, phone, role, is_active, created_at, updated_at,
  username, first_name, last_name, email,
  account_status, status_reason, status_updated_at, status_updated_by, status_expires_at
) on public.profiles to authenticated, anon;

commit;
