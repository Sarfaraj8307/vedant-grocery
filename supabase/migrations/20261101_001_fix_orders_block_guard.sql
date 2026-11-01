-- =============================================================================
-- 20261101_001_fix_orders_block_guard.sql
-- =============================================================================
-- `private.orders_block_guard()` read `new.customer_id`. `public.orders` has no
-- such column — the customer is `user_id` — so the BEFORE INSERT trigger raised
-- 42703 on **every** insert:
--
--     ERROR: 42703: record "new" has no field "customer_id"
--     CONTEXT: PL/pgSQL function private.orders_block_guard() line 3 at IF
--
-- `place_order` swallows nothing here, so the exception propagated out of the
-- RPC and the customer saw "We could not place your order just now" — a total
-- checkout outage, invisible to typecheck, lint and the build because the fault
-- is entirely inside Postgres.
--
-- The condition is also widened to match `place_order_app`'s own gate, which
-- already refuses BLOCKED / DELETED accounts and SUSPENDED ones whose window has
-- not expired. Two guards that disagree is how one of them ends up dead.
--
-- `is_blocked` is kept because it is still a column on `public.profiles` and
-- older rows use it.
-- =============================================================================

begin;

create or replace function private.orders_block_guard()
returns trigger
language plpgsql
security definer
set search_path to ''
as $function$
begin
    if exists (
        select 1
          from public.profiles p
         where p.id = new.user_id
           and (
                 p.is_blocked
              or p.account_status = 'BLOCKED'
              or p.account_status = 'DELETED'
              or ( p.account_status = 'SUSPENDED'
                   and (p.status_expires_at is null or p.status_expires_at > now()) )
           )
    ) then
        raise exception 'this customer is blocked from ordering' using errcode = '23514';
    end if;

    return new;
end;
$function$;

commit;
