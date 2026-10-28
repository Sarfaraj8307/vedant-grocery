-- =============================================================================
-- 20261028_001_staff_invite_bruteforce_guard.sql
-- =============================================================================
-- Hardens `public.verify_staff_otp`, which is callable with the publishable
-- anon key by design (an invitee has no session yet) and can grant OWNER/ADMIN.
--
-- ## The hole
-- The invite code is six digits, bcrypt-hashed, and valid for 15 minutes. The
-- function had no attempt counter, so an anonymous caller could guess without
-- limit for the whole window. bcrypt makes each guess expensive, but "expensive"
-- is roughly 100 ms: a few thousand guesses fit inside 15 minutes, and the
-- attacker parallelises freely because nothing is recorded server-side. The
-- prize is a staff role, so this was worth closing.
--
-- ## The fix
--   • `failed_attempts` / `locked_until` on `public.admin_invites`.
--   • Five wrong codes lock the invite for an hour. Since the invite itself
--     expires in 15 minutes, a locked invite is effectively dead and the owner
--     must re-invite — which is the correct outcome for someone guessing.
--   • "no such invite" and "wrong code" now return the **same** message. The
--     old pair let an anonymous caller ask "does this address have a pending
--     invite?" one probe at a time.
--
-- Signature, return type and grants are unchanged, so the admin app needs no
-- change. Errors still use SQLSTATE 22023.
-- =============================================================================

begin;

alter table public.admin_invites
    add column if not exists failed_attempts integer not null default 0;
alter table public.admin_invites
    add column if not exists locked_until timestamptz;

comment on column public.admin_invites.failed_attempts is
    'Wrong codes entered against this invite. Reset is unnecessary: a consumed or locked invite is dead.';
comment on column public.admin_invites.locked_until is
    'Set after 5 wrong codes. While in the future, verify_staff_otp refuses every attempt.';

create or replace function public.verify_staff_otp(p_email text, p_otp text)
returns boolean
language plpgsql
security definer
set search_path to 'public', 'private', 'extensions'
as $function$
declare
    -- One message for "no invite" and for "wrong code". Distinguishing them
    -- turns this anon-callable function into an address-enumeration oracle.
    c_invalid constant text := 'That code is invalid or has expired. Ask the owner for a new invite.';
    c_max_attempts constant integer := 5;

    v_inv public.admin_invites;
    v_uid uuid;
    v_wait_minutes integer;
begin
    if nullif(trim(coalesce(p_email, '')), '') is null then
        raise exception 'an email is required' using errcode = '22023';
    end if;
    if nullif(trim(coalesce(p_otp, '')), '') is null then
        raise exception 'a one-time code is required' using errcode = '22023';
    end if;

    select inv.* into v_inv
      from public.admin_invites inv
     where lower(inv.email) = lower(btrim(p_email))
       and inv.consumed_at is null
       and inv.expires_at > now()
     order by inv.created_at desc
     limit 1;

    if not found then
        raise exception '%', c_invalid using errcode = '22023';
    end if;

    if v_inv.locked_until is not null and v_inv.locked_until > now() then
        v_wait_minutes := greatest(
            1,
            ceil(extract(epoch from (v_inv.locked_until - now())) / 60)::integer
        );
        raise exception 'Too many incorrect codes. Ask the owner for a new invite, or try again in % minutes.',
            v_wait_minutes
            using errcode = '22023';
    end if;

    if v_inv.token_hash is null or v_inv.token_hash <> crypt(p_otp, v_inv.token_hash) then
        update public.admin_invites inv
           set failed_attempts = inv.failed_attempts + 1,
               locked_until = case
                   when inv.failed_attempts + 1 >= c_max_attempts
                   then now() + interval '1 hour'
                   else inv.locked_until
               end
         where inv.id = v_inv.id;

        raise exception '%', c_invalid using errcode = '22023';
    end if;

    select u.id into v_uid
      from auth.users u
     where lower(u.email) = lower(btrim(p_email))
     limit 1;

    -- The person must exist as an auth user first. In practice they sign up in
    -- the app (or the owner seeds them) and then enter the code.
    if v_uid is null then
        raise exception 'create the account for this email first, then enter the code'
            using errcode = '22023';
    end if;

    update public.admin_invites
       set consumed_at = now()
     where id = v_inv.id;

    insert into private.user_roles (user_id, role, granted_by, note)
    values (v_uid, v_inv.role, null, 'otp invite')
    on conflict (user_id) do update
        set role       = excluded.role,
            is_active  = true,
            granted_at = now(),
            note       = excluded.note;

    update public.profiles
       set role = case when v_inv.role in ('OWNER', 'ADMIN')
                       then 'admin'::public.user_role
                       else 'customer'::public.user_role
                  end
     where id = v_uid;

    insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after)
    values (v_uid, v_inv.role, 'activate_invite', 'user', v_uid::text,
            jsonb_build_object('role', v_inv.role::text));

    return true;
end;
$function$;

-- Grants are unchanged, but restate them so a `create or replace` on a
-- different search_path cannot quietly drop the invitee's access.
revoke all on function public.verify_staff_otp(text, text) from public;
grant execute on function public.verify_staff_otp(text, text) to anon, authenticated;

commit;
