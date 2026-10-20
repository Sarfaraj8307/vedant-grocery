-- ============================================================================
-- 20261016_007_serviceability_and_close_item_mutation.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T14 — TWO ceilings on the order path:
--   (1) DB-enforced SERVICEABILITY  (public.service_areas + guard in
--       public.place_order_app);
--   (2) close the order_items UPDATE/DELETE surface
--       (drop order_items_update + order_items_delete).
-- ----------------------------------------------------------------------------
-- FINDING 1 (serviceability, reproduced live, rollback-scoped):
--   The "do we deliver here?" rule lived only at the application layer
--   (src/lib/serviceability.ts + the checkout Server Action). An authenticated
--   customer could bypass it with a hand-crafted PostgREST call to the
--   SECURITY DEFINER RPC:
--       select public.place_order_app('<request>', '<out-of-area-address>', '<slot>');
--   and an order was created for an address we do not serve. The RPC had no
--   serviceability knowledge at all.
--
-- FINDING 2 (order-item mutation, reproduced live, rollback-scoped):
--   Two permissive policies let a customer mutate the line items of their OWN
--   placed order directly through PostgREST:
--       * public.order_items :: order_items_update  (qual/with_check: own order)
--       * public.order_items :: order_items_delete  (qual: own order)
--   A simulated customer ran
--       update public.order_items set unit_price = 0.01, line_total = 0.01
--       where order_id = '<their own order>';
--   and it SUCCEEDED — corrupting order history and reporting. Severity is
--   lower than the INSERT hole closed in 20261016_006 because no trigger
--   recomputes orders.subtotal/total/final_total from order_items (verified),
--   so the customer cannot lower their own bill — but the history is still
--   falsifiable, which the master brief §17 forbids (only trusted workflow
--   operations may mutate order records).
--
-- FIX 1: add public.service_areas (city, state, pincode_prefixes text[],
--   is_active) seeded with Pune / Maharashtra / {'411'} — the DB mirror of
--   src/config/service-area.ts and the concrete answer to brief §35 ("prepare
--   the database architecture for future expansion to other cities"). A new
--   private helper `private.address_is_serviceable(city, pincode)` reproduces
--   the client rule, and public.place_order_app raises
--   22023 'delivery area not served' before any order work.
--   The 3-arg public.place_order(text,uuid,uuid) is a thin wrapper that calls
--   place_order_app, so it is covered too. The 5-arg engine
--   public.place_order(uuid,uuid,uuid,date,jsonb) is NOT reachable by
--   `authenticated` (EXECUTE is granted only to postgres/service_role), so the
--   guard in place_order_app closes every customer-reachable path.
--
-- FIX 2: drop order_items_update + order_items_delete. Line-item mutation then
--   has exactly ONE path: public.admin_reject_order_item /
--   public.admin_restore_order_item, both SECURITY DEFINER owned by `postgres`.
--   Because public.order_items is owned by `postgres` and RELFORCEROWSECURITY is
--   false, those DEFINER bodies bypass RLS on their own UPDATEs — so dropping
--   the client policies does NOT affect the admin workflow.
--
-- PROOF (rollback-scoped, see docs/SUPABASE_FINDING_REGISTER.md §T14):
--   serviceability: out-of-area place_order_app -> 22023 'delivery area not
--     served'; in-area place_order_app -> SUCCEEDED (order_id returned).
--   item mutation: customer direct UPDATE/DELETE on order_items -> 42501;
--     admin_reject_order_item -> rejected=true; admin_restore_order_item ->
--     rejected=false (both still work).
--
-- NO OTHER POLICY OR ACL IS CHANGED: this migration only creates one new table,
--   one new index, one new trigger, one new private helper and one new policy
--   (all on new objects), and drops exactly the two named policies.
--
-- Idempotent: `if not exists` / `or replace` / `drop ... if exists` — safe to
--   re-run.
-- ============================================================================

begin;

-- ===========================================================================
-- PART 1 — SERVICEABILITY
-- ===========================================================================

create table if not exists public.service_areas (
  id               uuid primary key default gen_random_uuid(),
  city             text not null,
  state            text not null,
  pincode_prefixes text[] not null default '{}'::text[],
  is_active        boolean not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.service_areas is
  'Delivery areas the shop serves (brief §35). `pincode_prefixes` is matched as
   a prefix against the address pincode; an empty array means the whole city is
   covered. Mirrors src/config/service-area.ts so the database and the app agree
   on where we deliver, and gives future expansion (a new city) a data home.';

-- One row per (city, state) regardless of spelling/case.
create unique index if not exists service_areas_city_state_uniq
  on public.service_areas (lower(btrim(city)), lower(btrim(state)));

drop trigger if exists service_areas_updated on public.service_areas;
create trigger service_areas_updated
  before update on public.service_areas
  for each row execute function public.set_updated_at();

-- Seed Pune / Maharashtra / {'411'} — idempotent, never duplicates.
insert into public.service_areas (city, state, pincode_prefixes, is_active)
select 'Pune', 'Maharashtra', array['411']::text[], true
where not exists (
  select 1
  from public.service_areas
  where lower(btrim(city)) = 'pune' and lower(btrim(state)) = 'maharashtra'
);

alter table public.service_areas enable row level security;

-- Reference data: any signed-in customer may read the areas we serve; admins
-- additionally see inactive rows. (anon has no policy, so it sees nothing —
-- the same shape as public.orders.)
drop policy if exists service_areas_select on public.service_areas;
create policy service_areas_select on public.service_areas
  for select to authenticated
  using (is_active or private.can_view_orders());

-- The pure serviceability rule, mirroring src/lib/serviceability.ts:
--   1. a recognised city is authoritative — a present pincode must agree with it;
--   2. failing that, a pincode inside an active area still resolves;
--   3. otherwise, not served.
create or replace function private.address_is_serviceable(p_city text, p_pincode text)
returns boolean
language plpgsql
stable
security definer
set search_path to ''
as $fn$
declare
  v_city     text := regexp_replace(lower(btrim(coalesce(p_city, ''))), '\s+', ' ', 'g');
  v_pin      text := regexp_replace(coalesce(p_pincode, ''), '[^0-9]', '', 'g');
  v_prefixes text[];
begin
  -- 1. City match wins; a present pincode must then fall inside the area.
  select sa.pincode_prefixes into v_prefixes
  from public.service_areas sa
  where sa.is_active and lower(btrim(sa.city)) = v_city
  limit 1;

  if found then
    if v_pin = '' or cardinality(v_prefixes) = 0 then
      return true;
    end if;
    return exists (
      select 1 from unnest(v_prefixes) pfx where v_pin like pfx || '%'
    );
  end if;

  -- 2. No city match: a served pincode still resolves on its own.
  if v_pin <> '' then
    return exists (
      select 1
      from public.service_areas sa
      where sa.is_active
        and ( cardinality(sa.pincode_prefixes) = 0
              or exists (select 1 from unnest(sa.pincode_prefixes) pfx
                         where v_pin like pfx || '%') )
    );
  end if;

  -- 3. Nothing matched.
  return false;
end
$fn$;

comment on function private.address_is_serviceable(text, text) is
  'True when (city, pincode) falls inside an active public.service_areas row.
   Mirrors the client rule in src/lib/serviceability.ts: a recognised city is
   authoritative for its pincode; failing that, a served pincode resolves alone.';

-- Internal helper only — never callable by a client role (matches the hygiene
-- of the other private.* helpers, e.g. set_updated_at in 20261016_001).
revoke execute on function private.address_is_serviceable(text, text)
  from public, anon, authenticated;

-- place_order_app — unchanged except for the serviceability guard, inserted
-- after validation and before any order work.
create or replace function public.place_order_app(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)
 returns uuid
 language plpgsql
 security definer
 set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_items jsonb;
  v_delivery_date date;
  v_idempotency uuid;
  v_result jsonb;
  v_city text;
  v_pincode text;
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

  -- SERVICEABILITY (brief §35): refuse an out-of-area delivery address before any
  -- order work. The checkout Server Action already blocks this in the UI, but a
  -- hand-crafted RPC call bypasses the UI — this is the authoritative gate.
  -- (When the address is absent or not owned by the caller we stay silent and
  -- let place_order report INVALID_ADDRESS, so that path is unchanged.)
  if p_address_id is not null then
    select
      coalesce(a.city, ''),
      coalesce(nullif(btrim(a.postal_code), ''), btrim(a.pincode::text), '')
    into v_city, v_pincode
    from public.addresses a
    where a.id = p_address_id and a.user_id = v_uid;

    if found and not private.address_is_serviceable(v_city, v_pincode) then
      raise exception 'delivery area not served' using errcode = '22023';
    end if;
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
end $function$;

-- ===========================================================================
-- PART 2 — ORDER-ITEM MUTATION
-- ===========================================================================

drop policy if exists order_items_update on public.order_items;
drop policy if exists order_items_delete on public.order_items;

commit;
