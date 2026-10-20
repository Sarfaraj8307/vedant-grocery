-- ============================================================================
-- 20261016_012_staff_roster.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-20.
-- ----------------------------------------------------------------------------
-- WHY: the multi-staff *backend* was complete — `owner_grant_role`,
-- `owner_revoke_role` and the last-OWNER guard — but there was no way to READ
-- the roster. `admin_list_admins()` existed yet its EXECUTE was revoked in
-- migration 002 when it was superseded, so the Android staff screen had nothing
-- to list.
--
-- Gate: `private.can_manage_roster()` (OWNER only). Reading who holds which
-- role is itself sensitive — it tells an attacker who to target — so an ADMIN,
-- MANAGER or SUPPORT must not see it.
--
-- Returns the joined email from `auth.users` because the operator picks people
-- by email, not by UUID. `profiles.email` is also populated, but auth is the
-- authority: a profile row can lag behind a changed login address.
-- ============================================================================

begin;

create or replace function public.admin_list_staff()
returns table (
  user_id      uuid,
  email        text,
  full_name    text,
  role         public.staff_role,
  granted_at   timestamptz,
  granted_by   uuid,
  note         text,
  is_active    boolean
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not private.can_manage_roster() then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  return query
  select r.user_id,
         u.email::text,
         p.full_name,
         r.role,
         r.granted_at,
         r.granted_by,
         r.note,
         r.is_active
  from private.user_roles r
  left join auth.users      u on u.id = r.user_id
  left join public.profiles p on p.id = r.user_id
  where r.is_active
  order by
    -- OWNER first, then ADMIN, then the operational roles; newest grant last.
    case r.role
      when 'OWNER'   then 1
      when 'ADMIN'   then 2
      when 'MANAGER' then 3
      when 'DELIVERY' then 4
      when 'SUPPORT' then 5
      else 6
    end,
    r.granted_at;
end $$;

revoke execute on function public.admin_list_staff() from public, anon;
grant  execute on function public.admin_list_staff() to authenticated, service_role;

commit;
