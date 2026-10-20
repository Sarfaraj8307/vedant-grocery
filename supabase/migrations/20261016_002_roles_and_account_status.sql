-- ============================================================================
-- 20261016_002_roles_and_account_status.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T02 — role architecture + account-status model.
--   Part A: staff_role enum, private.user_roles (seeded from private.admin_users),
--           capability helpers, private.is_admin() shim, owner roster RPCs,
--           retirement of the legacy roster RPCs (grants removed, bodies kept).
--   Part B: account_status enum, profiles status columns, account_status_history
--           + audit_logs tables (RLS, read-only for clients), admin_set_account_status.
--   Part C: account-status enforcement in public.place_order_app (only the guard
--           is added; the rest of the body is reproduced byte-for-byte).
-- Idempotent-safe: every DDL is guarded (duplicate_object / if not exists /
-- create-or-replace / drop-policy-if-exists).
-- ----------------------------------------------------------------------------

begin;

-- ===========================================================================
-- PART A — ROLES
-- ===========================================================================

-- A.1  staff_role enum ------------------------------------------------------
do $$ begin
  create type public.staff_role as enum ('OWNER','ADMIN','MANAGER','DELIVERY','SUPPORT');
exception when duplicate_object then null;
end $$;

-- A.2  private.user_roles ---------------------------------------------------
create table if not exists private.user_roles (
  user_id     uuid primary key references auth.users(id) on delete cascade,
  role        public.staff_role not null,
  granted_by  uuid references auth.users(id),
  granted_at  timestamptz not null default now(),
  note        text,
  is_active   boolean not null default true
);
create index if not exists user_roles_role_idx on private.user_roles(role) where is_active;
-- cover the granted_by FK so no unindexed_foreign_keys finding is raised
create index if not exists user_roles_granted_by_idx on private.user_roles(granted_by);

-- A.3  seed from the legacy private.admin_users -----------------------------
--      earliest row = OWNER, the rest = ADMIN.
insert into private.user_roles (user_id, role, granted_at, note)
select a.user_id,
       case when a.user_id = '4bf3e5a5-2a93-44e7-b4f7-6e1e71180299'::uuid
            then 'OWNER'::public.staff_role else 'ADMIN'::public.staff_role end,
       a.created_at, a.note
from private.admin_users a
on conflict (user_id) do nothing;

-- A.4  capability helpers (SECURITY DEFINER, search_path pinned, fully qualified)
create or replace function private.role_of(p_user uuid default null)
returns public.staff_role
language sql stable security definer set search_path = '' as $$
  select r.role from private.user_roles r
  where r.user_id = coalesce(p_user, (select auth.uid())) and r.is_active
  limit 1;
$$;

create or replace function private.has_role(p_roles public.staff_role[])
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from private.user_roles r
    where r.user_id = (select auth.uid()) and r.is_active and r.role = any(p_roles)
  );
$$;

create or replace function private.is_owner()              returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER']::public.staff_role[]); $$;
create or replace function private.is_staff()              returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN','MANAGER','DELIVERY','SUPPORT']::public.staff_role[]); $$;
create or replace function private.can_manage_roster()     returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER']::public.staff_role[]); $$;
create or replace function private.can_manage_users()      returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN']::public.staff_role[]); $$;
create or replace function private.can_manage_catalogue()  returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN','MANAGER']::public.staff_role[]); $$;
create or replace function private.can_manage_orders()     returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN','MANAGER','DELIVERY']::public.staff_role[]); $$;
create or replace function private.can_view_orders()       returns boolean language sql stable security definer set search_path='' as $$ select private.is_staff(); $$;
create or replace function private.can_manage_complaints() returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN','MANAGER','SUPPORT']::public.staff_role[]); $$;
create or replace function private.can_manage_store()      returns boolean language sql stable security definer set search_path='' as $$ select private.has_role(array['OWNER','ADMIN']::public.staff_role[]); $$;

-- A.5  compatibility shim: private.is_admin() = is_staff() ------------------
create or replace function private.is_admin()
returns boolean language sql stable security definer set search_path = '' as $$
  select private.is_staff();
$$;

-- A.6  grants on the private helpers: RLS policies evaluate as the caller, so
--      `authenticated` needs EXECUTE. PUBLIC/anon are revoked (defence in depth,
--      consistent with the T01 hardening); the schema is not API-exposed.
revoke execute on function
  private.role_of(uuid), private.has_role(public.staff_role[]),
  private.is_owner(), private.is_staff(), private.can_manage_roster(),
  private.can_manage_users(), private.can_manage_catalogue(), private.can_manage_orders(),
  private.can_view_orders(), private.can_manage_complaints(), private.can_manage_store(),
  private.is_admin()
from public, anon;

grant execute on function
  private.role_of(uuid), private.has_role(public.staff_role[]),
  private.is_owner(), private.is_staff(), private.can_manage_roster(),
  private.can_manage_users(), private.can_manage_catalogue(), private.can_manage_orders(),
  private.can_view_orders(), private.can_manage_complaints(), private.can_manage_store(),
  private.is_admin()
to authenticated;

-- A.7  owner-only roster RPCs -----------------------------------------------
create or replace function public.owner_grant_role(
  p_email text, p_role public.staff_role, p_note text default null
) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_uid uuid;
begin
  if not private.can_manage_roster() then
    raise exception 'owner authorization required' using errcode = '42501';
  end if;
  if p_role is null then
    raise exception 'role is required' using errcode = '22023';
  end if;
  select u.id into v_uid from auth.users u where lower(u.email) = lower(trim(p_email)) limit 1;
  if v_uid is null then
    raise exception 'no account exists for that email' using errcode = '22023';
  end if;
  insert into private.user_roles (user_id, role, granted_by, note)
  values (v_uid, p_role, (select auth.uid()), p_note)
  on conflict (user_id) do update
    set role = excluded.role, granted_by = excluded.granted_by,
        granted_at = now(), note = excluded.note, is_active = true;
  update public.profiles set role = (case when p_role = 'OWNER' or p_role = 'ADMIN'
                                     then 'admin' else 'customer' end)::public.user_role
  where id = v_uid;
  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after)
  values ((select auth.uid()), private.role_of(), 'grant_role', 'user', v_uid::text,
          jsonb_build_object('role', p_role, 'note', p_note));
  return v_uid;
end $$;

create or replace function public.owner_revoke_role(p_user_id uuid)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not private.can_manage_roster() then
    raise exception 'owner authorization required' using errcode = '42501';
  end if;
  if (select count(*) from private.user_roles where role = 'OWNER' and is_active) <= 1
     and (select role from private.user_roles where user_id = p_user_id) = 'OWNER' then
    raise exception 'cannot revoke the last remaining owner' using errcode = '22023';
  end if;
  delete from private.user_roles where user_id = p_user_id;
  update public.profiles set role = 'customer'::public.user_role where id = p_user_id;
  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id)
  values ((select auth.uid()), private.role_of(), 'revoke_role', 'user', p_user_id::text);
end $$;

-- A.8  grants on the new public RPCs: authenticated + service_role only
--      (matches the existing RPC convention; no PUBLIC/anon => no new anon finding).
revoke execute on function
  public.owner_grant_role(text, public.staff_role, text),
  public.owner_revoke_role(uuid)
from public, anon;

grant execute on function
  public.owner_grant_role(text, public.staff_role, text),
  public.owner_revoke_role(uuid)
to authenticated, service_role;

-- A.9  retire the superseded roster RPCs (bodies kept; grants removed) ------
revoke execute on function public.admin_grant_admin(text,text) from authenticated, anon, public;
revoke execute on function public.admin_revoke_admin(uuid)      from authenticated, anon, public;
revoke execute on function public.admin_list_admins()           from authenticated, anon, public;

-- ===========================================================================
-- PART B — ACCOUNT STATUS
-- ===========================================================================

-- B.1  account_status enum --------------------------------------------------
do $$ begin
  create type public.account_status as enum ('ACTIVE','SUSPENDED','BLOCKED','DELETED');
exception when duplicate_object then null;
end $$;

-- B.2  profiles status columns ---------------------------------------------
alter table public.profiles
  add column if not exists account_status    public.account_status not null default 'ACTIVE',
  add column if not exists status_reason     text,
  add column if not exists status_updated_at timestamptz,
  add column if not exists status_updated_by uuid references auth.users(id),
  add column if not exists status_expires_at timestamptz;
create index if not exists profiles_account_status_idx on public.profiles(account_status);
-- cover the status_updated_by FK so no unindexed_foreign_keys finding is raised
create index if not exists profiles_status_updated_by_idx on public.profiles(status_updated_by);

-- B.3  history + audit tables ----------------------------------------------
create table if not exists public.account_status_history (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  old_status public.account_status,
  new_status public.account_status not null,
  reason     text not null,
  actor_id   uuid references auth.users(id),
  actor_role public.staff_role,
  expires_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists account_status_history_user_idx on public.account_status_history(user_id, created_at desc);
-- cover the actor_id FK so no unindexed_foreign_keys finding is raised
create index if not exists account_status_history_actor_idx on public.account_status_history(actor_id);

create table if not exists public.audit_logs (
  id          bigint generated always as identity primary key,
  actor_id    uuid references auth.users(id),
  actor_role  public.staff_role,
  action      text not null,
  entity_type text not null,
  entity_id   text,
  before      jsonb,
  after       jsonb,
  reason      text,
  created_at  timestamptz not null default now()
);
create index if not exists audit_logs_entity_idx  on public.audit_logs(entity_type, entity_id, created_at desc);
create index if not exists audit_logs_actor_idx   on public.audit_logs(actor_id, created_at desc);
create index if not exists audit_logs_created_idx on public.audit_logs(created_at desc);

-- B.4  RLS on the new tables (read-only for clients; writes via DEFINER RPCs)
alter table public.account_status_history enable row level security;
alter table public.audit_logs              enable row level security;

drop policy if exists ash_select on public.account_status_history;
create policy ash_select on public.account_status_history for select to authenticated
  using ( user_id = (select auth.uid()) or private.can_manage_users() );

drop policy if exists audit_select on public.audit_logs;
create policy audit_select on public.audit_logs for select to authenticated
  using ( private.can_manage_users() );

-- B.5  admin_set_account_status RPC ----------------------------------------
create or replace function public.admin_set_account_status(
  p_user_id uuid, p_status public.account_status, p_reason text,
  p_expires_at timestamptz default null
) returns public.profiles
language plpgsql security definer set search_path = '' as $$
declare v_old public.account_status; v_row public.profiles;
begin
  if not private.can_manage_users() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  if p_status = 'DELETED' and not private.is_owner() then
    raise exception 'only the owner may delete an account' using errcode = '42501';
  end if;
  if nullif(trim(coalesce(p_reason,'')),'') is null then
    raise exception 'a reason is required' using errcode = '22023';
  end if;
  select account_status into v_old from public.profiles where id = p_user_id for update;
  if not found then raise exception 'user not found' using errcode = '22023'; end if;

  update public.profiles
     set account_status = p_status, status_reason = p_reason,
         status_updated_at = now(), status_updated_by = (select auth.uid()),
         status_expires_at = p_expires_at
   where id = p_user_id returning * into v_row;

  insert into public.account_status_history
    (user_id, old_status, new_status, reason, actor_id, actor_role, expires_at)
  values (p_user_id, v_old, p_status, p_reason, (select auth.uid()), private.role_of(), p_expires_at);

  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, before, after, reason)
  values ((select auth.uid()), private.role_of(), 'set_account_status', 'user', p_user_id::text,
          jsonb_build_object('account_status', v_old), jsonb_build_object('account_status', p_status), p_reason);

  -- hard auth ban only for DELETED (elevated privilege -> done here as DEFINER)
  if p_status = 'DELETED' then
    update auth.users set banned_until = 'infinity' where id = p_user_id;
  elsif p_status in ('ACTIVE','SUSPENDED') then
    update auth.users set banned_until = null where id = p_user_id and banned_until is not null;
  end if;

  return v_row;
end $$;

-- B.6  grants on admin_set_account_status (authenticated + service_role only) -
revoke execute on function public.admin_set_account_status(uuid, public.account_status, text, timestamptz) from public, anon;
grant  execute on function public.admin_set_account_status(uuid, public.account_status, text, timestamptz) to authenticated, service_role;

-- ===========================================================================
-- PART C — enforce account status at checkout (public.place_order_app)
--   Body reproduced byte-for-byte from the live definition; the ONLY change is
--   the status guard inserted immediately after the v_uid-is-null check.
-- ===========================================================================
create or replace function public.place_order_app(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_uid uuid := auth.uid();
  v_items jsonb;
  v_delivery_date date;
  v_idempotency uuid;
  v_result jsonb;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if exists (
    select 1 from public.profiles p
    where p.id = v_uid
      and ( p.account_status = 'BLOCKED'
         or p.account_status = 'DELETED'
         or (p.account_status = 'SUSPENDED'
             and (p.status_expires_at is null or p.status_expires_at > now())) )
  ) then
    raise exception 'account is not permitted to place orders' using errcode = '42501';
  end if;
  if p_request_id is null or char_length(p_request_id) < 8 or char_length(p_request_id) > 128 then
    raise exception 'invalid request id' using errcode = '22023';
  end if;

  -- deterministic idempotency_key from (user_id, request_id) so replay is stable
  v_idempotency := md5(v_uid::text || ':' || p_request_id)::uuid;

  -- delivery_date: use delivery_slots.starts_at date if available, else current_date
  select (starts_at at time zone 'Asia/Kolkata')::date into v_delivery_date
  from public.delivery_slots where id = p_delivery_slot_id;
  if v_delivery_date is null then
    v_delivery_date := current_date;
  end if;
  if v_delivery_date not in (current_date, current_date + 1) then
    v_delivery_date := current_date;
  end if;

  -- build p_items from cart_items, carrying weight_id so the server prices the
  -- exact pack size the customer chose.
  select coalesce(jsonb_agg(jsonb_build_object(
           'product_id', product_id,
           'weight_id', weight_id,
           'qty', quantity)), '[]'::jsonb)
    into v_items
  from public.cart_items where user_id = v_uid;

  if jsonb_array_length(v_items) = 0 then
    raise exception 'cart is empty' using errcode = '22023';
  end if;

  -- NAMED arguments: immune to parameter-order drift.
  v_result := public.place_order(
    p_idempotency_key => v_idempotency,
    p_address_id      => p_address_id,
    p_slot_id         => p_delivery_slot_id,
    p_delivery_date   => v_delivery_date,
    p_items           => v_items
  );

  if v_result ? 'error' then
    case v_result->>'error'
      when 'UNAUTHENTICATED'     then raise exception 'authentication required' using errcode = '42501';
      when 'STORE_CLOSED'        then raise exception 'store closed' using errcode = '22023';
      when 'INVALID_DATE'        then raise exception 'delivery slot unavailable' using errcode = '22023';
      when 'INVALID_ADDRESS'     then raise exception 'address unavailable' using errcode = '22023';
      when 'INVALID_SLOT'        then raise exception 'delivery slot unavailable' using errcode = '22023';
      when 'SLOT_FULL'           then raise exception 'delivery slot full' using errcode = '22023';
      when 'PRODUCT_UNAVAILABLE' then raise exception 'cart item unavailable' using errcode = '22023';
      when 'WEIGHT_UNAVAILABLE'  then raise exception 'cart item unavailable' using errcode = '22023';
      when 'OUT_OF_STOCK'        then raise exception 'cart item unavailable' using errcode = '22023';
      when 'QTY_EXCEEDED'        then raise exception 'cart item unavailable' using errcode = '22023';
      when 'BELOW_MIN_ORDER'     then raise exception 'below minimum order' using errcode = '22023';
      else raise exception '%', v_result->>'error' using errcode = '22023';
    end case;
  end if;

  if v_result ? 'order_id' then
    -- clear the cart on success
    delete from public.cart_items where user_id = v_uid;
    return (v_result->>'order_id')::uuid;
  end if;

  raise exception 'order failed' using errcode = '22023';
end;
$function$;

commit;
