-- ============================================================================
-- 20261016_009_order_acceptance.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-20.
-- ----------------------------------------------------------------------------
-- FINDING (brief §9 — "Admin must be able to stop accepting orders"):
--   `store_settings.is_open` already existed and `place_order` already refused
--   with STORE_CLOSED, so the *enforcement* was correct. What was missing:
--     * no reason for closing (an operator turning orders off with no recorded
--       reason is unauditable);
--     * no history — you could not answer "when did we close, who closed it,
--       and why";
--     * no dedicated RPC, so the toggle was a raw UPDATE on store_settings with
--       no audit trail;
--     * the customer website had NO notion of store status at all
--       (`grep -r "is_open\|STORE_CLOSED" src/` returned nothing), so a customer
--       only discovered the closure by having checkout fail.
--
-- DESIGN NOTES:
--   * `place_order`'s existing is_open/STORE_CLOSED check is PRESERVED — this
--     migration does not touch it. Enforcement stays server-side.
--   * `acceptance_updated_by` is deliberately NOT added to store_settings:
--     store_settings has a public `using (true)` SELECT policy, so a staff UUID
--     there would be world-readable. The actor is recorded in
--     store_status_history (RLS: can_manage_store only) and in audit_logs.
--   * Closing REQUIRES a reason; reopening does not. You must justify stopping
--     trade, you need not justify resuming it.
-- ============================================================================

begin;

-- 1. Reason + timestamp on the settings row (both safe to expose publicly) ----
alter table public.store_settings
  add column if not exists acceptance_reason      text,
  add column if not exists acceptance_updated_at  timestamptz;

-- 2. Append-only history of every acceptance change --------------------------
create table if not exists public.store_status_history (
  id          uuid primary key default gen_random_uuid(),
  accepting   boolean not null,
  reason      text,
  actor_id    uuid references auth.users(id),
  actor_role  public.staff_role,
  created_at  timestamptz not null default now()
);

create index if not exists store_status_history_created_idx
  on public.store_status_history (created_at desc);

alter table public.store_status_history enable row level security;

-- Staff who manage the store may read the history. No client INSERT/UPDATE/
-- DELETE policies at all: rows are written only by the DEFINER RPC below.
drop policy if exists store_status_history_select on public.store_status_history;
create policy store_status_history_select on public.store_status_history
  for select to authenticated
  using ( private.can_manage_store() );

-- 3. The one way to change acceptance ---------------------------------------
create or replace function public.admin_set_order_acceptance(
  p_accepting boolean,
  p_reason    text default null
)
returns public.store_settings
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
  v_row    public.store_settings;
begin
  if not private.can_manage_store() then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  if p_accepting is null then
    raise exception 'accepting is required' using errcode = '22023';
  end if;

  -- Closing trade must be justified; reopening need not be.
  if not p_accepting and v_reason is null then
    raise exception 'a reason is required when closing orders' using errcode = '22023';
  end if;

  update public.store_settings
     set is_open                = p_accepting,
         acceptance_reason      = v_reason,
         acceptance_updated_at  = now(),
         updated_at             = now()
   where id = 1
   returning * into v_row;

  if not found then
    raise exception 'store settings row is missing' using errcode = '22023';
  end if;

  insert into public.store_status_history (accepting, reason, actor_id, actor_role)
  values (p_accepting, v_reason, (select auth.uid()), private.role_of());

  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after, reason)
  values ((select auth.uid()), private.role_of(), 'set_order_acceptance', 'store_settings', '1',
          jsonb_build_object('accepting', p_accepting), v_reason);

  return v_row;
end $$;

revoke execute on function public.admin_set_order_acceptance(boolean, text) from public, anon;
grant  execute on function public.admin_set_order_acceptance(boolean, text) to authenticated, service_role;

commit;
