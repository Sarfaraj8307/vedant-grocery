-- ============================================================================
-- 20261016_016_delivery_issues.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: Android engineer (owner-authorised, brief §25)
-- Task: REPORT DELIVERY ISSUE — the courier app must be able to escalate a
--       problem on an assigned delivery, and an admin must be able to see it.
-- ----------------------------------------------------------------------------
-- DESIGN
--   * Append-only log: no UPDATE/DELETE policies are created, so once an issue
--     is written it cannot be edited or removed by any client.
--   * A DELIVERY user may INSERT only for orders assigned to them
--     (orders.assigned_to = auth.uid()); OWNER/ADMIN may insert for any order.
--     MANAGER/SUPPORT and every other role are refused by the gate.
--   * SELECT is OWNER/ADMIN only ("admin/owner can read all"). A customer has
--     no policy at all — the table is invisible to them (and to anon).
--   * The actor is the authenticated user (default auth.uid()); a client
--     cannot forge `reported_by` because column defaults are used and RLS
--     re-checks the row. `reason` is mandatory server-side (NOT NULL + a
--     non-blank CHECK), so "a reason is required for every type" holds even
--     for a tampered client.
--
-- SECURITY NOTES
--   * Every auth.uid() is wrapped as (select auth.uid()) so policies hit an
--     InitPlan (consistent with the T03 restructure; avoids auth_rls_initplan
--     findings).
--   * Private helpers (private.has_role) are SECURITY DEFINER and only
--     EXECUTE-granted to `authenticated`; the policies below are `to
--     authenticated`, so anon never evaluates one.
--   * CHECK constraint rather than enum: the issue taxonomy is client-driven
--     and may grow; a CHECK keeps the wire values stable and greppable.
--
-- Idempotent-safe: create-table-if-not-exists; drop-policy-if-exists before
--   every create; grant statements are idempotent.
-- ============================================================================

begin;

-- 1. The log table -----------------------------------------------------------
create table if not exists public.delivery_issues (
  id           uuid primary key default gen_random_uuid(),
  order_id     uuid not null references public.orders(id) on delete cascade,
  reported_by  uuid not null default auth.uid() references auth.users(id) on delete cascade,
  issue_type   text not null
                 check (issue_type in (
                   'customer_unavailable',
                   'address_issue',
                   'phone_unreachable',
                   'payment_issue',
                   'damaged_item',
                   'other'
                 )),
  reason       text not null
                 check (char_length(reason) between 1 and 500)
                 check (btrim(reason) <> ''),
  created_at   timestamptz not null default now()
);

comment on table public.delivery_issues is
  'Append-only delivery escalations reported by couriers (§25). Insert-only for clients; readable by OWNER/ADMIN.';

create index if not exists delivery_issues_order_idx on public.delivery_issues(order_id, created_at desc);
create index if not exists delivery_issues_reporter_idx on public.delivery_issues(reported_by, created_at desc);

-- 2. RLS ---------------------------------------------------------------------
alter table public.delivery_issues enable row level security;

-- One policy per (cmd, role): no permissive overlap (T03 invariant).

drop policy if exists delivery_issues_insert on public.delivery_issues;
create policy delivery_issues_insert on public.delivery_issues
  for insert to authenticated
  with check (
    -- Gate: only DELIVERY / ADMIN / OWNER may ever insert.
    private.has_role(array['DELIVERY','ADMIN','OWNER']::public.staff_role[])
    and (
      -- ADMIN/OWNER may report on any order; a courier only on their own.
      private.has_role(array['ADMIN','OWNER']::public.staff_role[])
      or exists (
        select 1 from public.orders o
        where o.id = delivery_issues.order_id
          and o.assigned_to = (select auth.uid())
      )
    )
  );

drop policy if exists delivery_issues_select_admin on public.delivery_issues;
create policy delivery_issues_select_admin on public.delivery_issues
  for select to authenticated
  using (private.has_role(array['ADMIN','OWNER']::public.staff_role[]));

-- Deliberately NO update/delete policies: the log is append-only (clients
-- cannot edit or retract an issue once written).

-- 3. Grants (idempotent; matches Supabase's public-schema defaults) ----------
grant select, insert on public.delivery_issues to authenticated;
revoke update, delete on public.delivery_issues from authenticated;

commit;
