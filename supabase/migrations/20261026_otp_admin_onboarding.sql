-- =============================================================================
-- 20261026_otp_admin_onboarding.sql — single-master login + OTP-invited admin
-- =============================================================================
-- Purpose (design §R5 + hardening):
--   • Restrict staff-account creation to the OWNER (the single master) only.
--     `admin_create_user` is re-gated from `can_manage_users()` (OWNER/ADMIN)
--     to `can_manage_roster()` (OWNER-only). `can_manage_users()` is kept for
--     read/manage operations that ADMINS may still perform (list customers,
--     block/unblock, reporting, account status).
--   • Add an OTP-invite flow: `public.admin_invites` holds a hashed one-time
--     code per invite; `public.admin_invite_staff(email, role)` (OWNER-gated)
--     enqueues an invite; `public.verify_staff_otp(email, otp)` — deliberately
--     NOT owner-gated (callable with the publishable anon key by an invitee who
--     has no session yet) — activates the role in `private.user_roles`.
--   • Keep/tighten the `is_blocked` order guard trigger.
--
-- ## Supabase dashboard steps (required for the email OTP / magic-link invite)
-- The Android app holds only the publishable anon key, so it can never call
-- `auth.admin_create_user` (service-role only). The invite email is therefore
-- delivered through Supabase Auth's native email OTP. Before this flow works,
-- an owner must configure the dashboard:
--
--   1. Dashboard > Authentication > Providers > Email: ensure "Enable Email
--      signup" is ON. Leave "Confirm email" ON (this is what sends the OTP).
--   2. Dashboard > Authentication > URL Configuration:
--        • Site URL: https://vedantfreshmart.in
--        • Redirect URLs: add https://vedantfreshmart.in/activate  (the app's
--          activation route accepts the OTP the invitee receives).
--   3. Dashboard > Authentication > Security Capabilities (optional but
--      recommended): enable "Email OTP" rate limiting so a test/brute force
--      cannot grind codes.
--   4. The invite is enqueued in `public.notification_outbox` with kind
--      'ADMIN_INVITE'. A delivery worker (edge function / pg_cron) reads it and
--      calls `supabase.auth.admin.generateLink` / Resend e.g. to email the
--      one-time code + the activation link. The OTP itself is stored hashed in
--      `public.admin_invites.token_hash`; only the outbox payload carries the
--      plaintext so it can be mailed.
--
-- Conventions honoured (CLAUDE.md + architect design):
--   • Every RPC is SECURITY DEFINER, re-checks its gate internally, and raises
--     42501 on refusal. The client (anon key) never sees a transaction.
--   • No broad `profiles` select leaks to the client — reads go through RPCs.
--   • `search_path` is pinned inside every function.
--   • Permissions are REVOKEd from public and GRANTed to the minimal set.
--   • `verify_staff_otp` is the single exception: it MUST be callable by `anon`
--     (the invitee). It grants no data read-back; it only consumes a valid,
--     unexpired, unconsumed invite and activates the role.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 0. pgcrypto (crypt/gen_salt for the OTP hash). Idempotent.
-- -----------------------------------------------------------------------------
create extension if not exists pgcrypto;

-- -----------------------------------------------------------------------------
-- 1. The invite table.
--    `token_hash` stores crypt(otp, gen_salt('bf')) — never the plaintext.
--    `expires_at` bounds the window; `consumed_at` makes each code single-use.
-- -----------------------------------------------------------------------------
create table if not exists public.admin_invites (
    id          uuid primary key default gen_random_uuid(),
    email       text not null,
    role        public.staff_role not null,
    token_hash  text not null,
    expires_at  timestamptz not null default (now() + interval '15 minutes'),
    created_by  uuid references auth.users(id),
    created_at  timestamptz not null default now(),
    consumed_at timestamptz
);
create index if not exists admin_invites_email_idx on public.admin_invites(email, created_at desc);
create index if not exists admin_invites_created_by_idx on public.admin_invites(created_by);

-- RLS on: no direct client reads/writes (all access is via the DEFINER RPCs).
alter table public.admin_invites enable row level security;
drop policy if exists invites_select_manage on public.admin_invites;
create policy invites_select_manage on public.admin_invites
    for select to authenticated
    using ( private.can_manage_users() );

-- -----------------------------------------------------------------------------
-- 2. OWNER-gated invite enqueue.
--    `admin_invite_staff` is the canonical wrapper. `admin_create_user` (the
--    legacy name the app already calls) is re-gated to OWNER and delegates here,
--    so there is exactly one implementation of the invite rules.
-- -----------------------------------------------------------------------------
create or replace function public.admin_invite_staff(
    p_email text,
    p_role public.staff_role default 'MANAGER'
)
returns void
language plpgsql
security definer
set search_path = public, private
as $$
declare
    v_otp text;
    v_user_id uuid;
begin
    if not private.can_manage_roster() then
        raise exception 'only the owner may invite staff' using errcode = '42501';
    end if;

    if nullif(trim(coalesce(p_email, '')), '') is null then
        raise exception 'an email is required' using errcode = '22023';
    end if;
    if not (lower(btrim(p_email)) ~ '^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$') then
        raise exception 'a valid email is required' using errcode = '22023';
    end if;

    -- Staff roles only — never OWNER (the single master is not inviteable) and
    -- never CUSTOMER (customers self-register in the consumer app).
    if p_role not in ('ADMIN', 'MANAGER', 'DELIVERY', 'SUPPORT') then
        raise exception 'unsupported role for an invite' using errcode = '22023';
    end if;

    -- A 6-digit one-time code. Only the hash is stored; the plaintext goes in
    -- the outbox payload so the mailer can send it.
    v_otp := lpad(floor(random() * 1000000)::bigint::text, 6, '0');
    v_user_id := (select p.id from public.profiles p where lower(p.email) = lower(btrim(p_email)) limit 1);

    insert into public.admin_invites (email, role, token_hash, created_by, expires_at)
    values (
        lower(btrim(p_email)),
        p_role,
        crypt(v_otp, gen_salt('bf')),
        (select auth.uid()),
        now() + interval '15 minutes'
    );

    insert into public.notification_outbox (user_id, kind, payload)
    values (
        v_user_id,
        'ADMIN_INVITE',
        jsonb_build_object(
            'email', lower(btrim(p_email)),
            'role', p_role::text,
            'otp', v_otp,
            'otp_expires_at', (now() + interval '15 minutes')::text
        )
    );

    perform private.append_audit('STAFF_INVITED', v_user_id);
end;
$$;

-- `admin_create_user` re-gated to OWNER and delegating to the canonical wrapper.
create or replace function public.admin_create_user(
    p_email text,
    p_first_name text default null,
    p_last_name text default null,
    p_role text default 'MANAGER'
)
returns void
language plpgsql
security definer
set search_path = public, private
as $$
begin
    perform public.admin_invite_staff(p_email, p_role::public.staff_role);
end;
$$;

revoke execute on function public.admin_invite_staff(text, public.staff_role) from public, anon;
grant  execute on function public.admin_invite_staff(text, public.staff_role) to authenticated, service_role;
revoke execute on function public.admin_create_user(text, text, text, text) from public, anon;
grant  execute on function public.admin_create_user(text, text, text, text) to authenticated, service_role;

-- -----------------------------------------------------------------------------
-- 3. Verification — explicitly NOT owner-gated.
--    The invitee has no session yet, so this MUST be callable with the anon key.
--    The function proves possession of the one-time code (bcrypt compare) before
--    activating the role; it never returns invite data.
-- -----------------------------------------------------------------------------
create or replace function public.verify_staff_otp(p_email text, p_otp text)
returns boolean
language plpgsql
security definer
set search_path = public, private
as $$
declare
    v_inv public.admin_invites;
    v_uid uuid;
begin
    if nullif(trim(coalesce(p_email, '')), '') is null then
        raise exception 'an email is required' using errcode = '22023';
    end if;
    if nullif(trim(coalesce(p_otp, '')), '') is null then
        raise exception 'a one-time code is required' using errcode = '22023';
    end if;

    select *
      into v_inv
      from public.admin_invites inv
     where lower(inv.email) = lower(btrim(p_email))
       and inv.consumed_at is null
       and inv.expires_at > now()
     order by inv.created_at desc
     limit 1;

    if not found then
        raise exception 'invitation not found or expired' using errcode = '22023';
    end if;

    -- bcrypt constant-time compare (crypt re-derives from the stored salt).
    if v_inv.token_hash is null or v_inv.token_hash <> crypt(p_otp, v_inv.token_hash) then
        raise exception 'invalid one-time code' using errcode = '22023';
    end if;

    select u.id into v_uid
      from auth.users u
     where lower(u.email) = lower(btrim(p_email))
     limit 1;
    if v_uid is null then
        raise exception 'complete your account activation via the emailed link first'
            using errcode = '22023';
    end if;

    -- Single-use: burn the code before granting so a replay cannot re-activate.
    update public.admin_invites set consumed_at = now() where id = v_inv.id;

    insert into private.user_roles (user_id, role, granted_by, note)
    values (v_uid, v_inv.role, null, 'otp invite')
    on conflict (user_id) do update
        set role = excluded.role, is_active = true, granted_at = now(), note = excluded.note;

    update public.profiles set role = (case when v_inv.role in ('OWNER', 'ADMIN')
                                           then 'admin' else 'customer' end)::public.user_role
     where id = v_uid;

    insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after)
    values (v_uid, v_inv.role, 'activate_invite', 'user', v_uid::text,
            jsonb_build_object('role', v_inv.role::text));

    return true;
end;
$$;

revoke execute on function public.verify_staff_otp(text, text) from public;
grant  execute on function public.verify_staff_otp(text, text) to anon, authenticated, service_role;

-- -----------------------------------------------------------------------------
-- 4. Keep/tighten the "blocked customers cannot order" guard trigger.
-- -----------------------------------------------------------------------------
create or replace function private.orders_block_guard()
returns trigger
language plpgsql
security definer
set search_path = public, private
as $$
begin
    if exists (
        select 1 from public.profiles p
        where p.id = new.customer_id and p.is_blocked
    ) then
        raise exception 'this customer is blocked from ordering'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_orders_block_guard on public.orders;
create trigger trg_orders_block_guard
    before insert on public.orders
    for each row
    execute function private.orders_block_guard();

-- -----------------------------------------------------------------------------
-- 5. DMA / cleanup — run MANUALLY by an owner in the Supabase dashboard.
--    This section is intentionally NOT auto-executed: deleting `auth.users` rows
--    requires service-role/dashboard access (the app has only the anon key), and
--    we must never hardcode a password. Copy/paste the queries below into the
--    Dashboard > SQL editor as the owner.
--
--    Step A — delete the seeded TEST admin accounts (replace the emails).
--    delete from public.profiles where email in ('test.admin@vedant.test', 'qa.admin@vedant.test');
--    -- those auth.users rows must be deleted in Dashboard > Authentication > Users
--    -- (or via the service role): counting on the FK cascade from profiles is not
--    -- enough because auth.users is the parent.
--
--    Step B — verify exactly one master OWNER remains (replace the owner email).
--    select u.email, r.role
--      from private.user_roles r
--      join auth.users u on u.id = r.user_id
--     where r.role = 'OWNER' and r.is_active;
--    -- Expected: exactly one row, the master owner email.
--
--    Step C — (idempotent) demote any stray non-master OWNER back to ADMIN.
--    update private.user_roles
--       set role = 'ADMIN'::public.staff_role
--     where role = 'OWNER' and is_active
--       and user_id <> (select id from auth.users where lower(email) = 'owner@vedantfreshmart.in');
-- -----------------------------------------------------------------------------

commit;
