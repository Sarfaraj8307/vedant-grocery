-- ============================================================================
-- 20261016_005_definer_hardening.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T05 — SECURITY DEFINER hardening (final DB security task).
-- ----------------------------------------------------------------------------
-- GOAL: every `public` function has `SET search_path = ''` (empty) so a
--   hijackable `search_path` cannot redirect an unqualified object reference to
--   an attacker-controlled schema. All bodies below were already fully
--   qualified (`public.*`, `private.*`, `auth.*`); built-ins resolve via
--   `pg_catalog`, which is always searched. So the transform is: pin the path.
--
-- NOT GATED ON PURPOSE (do not "fix" these — the audit's regex flagged them):
--   * place_order / place_order_app — customer-facing order placement; they
--     already gate on authentication (`v_uid is null` -> 42501). Adding a role
--     gate would break checkout. search_path pin only.
--   * handle_new_user, notify_complaint_event, notify_order_event,
--     record_order_status_history, sync_address_pincode, sync_profile_phone —
--     TRIGGER functions (run as the table owner during DML); must NOT carry a
--     role gate, and are not RPC-reachable (EXECUTE revoked in T01).
--   * set_updated_at, gen_order_number — SECURITY INVOKER; pin for hygiene only.
--
-- HARDENED: check_username_available — the last P0 anon entry. It is genuine
--   public signup functionality (the form checks availability before the user
--   exists), so it must stay anon-callable; it must stay SECURITY DEFINER
--   because `anon` has no SELECT on `public.profiles` (an INVOKER version would
--   always return false). Hardening: input validated (null / <3 / >32 / not
--   `^[a-z0-9_]+$` -> 22023), exact case-insensitive EQUALITY (never LIKE), and
--   returns boolean only (no username/id echo). Converted sql -> plpgsql to
--   allow the validation RAISE; signature (arg + return types) unchanged.
--
-- ACLs are deliberately NOT touched: `create or replace` preserves them, and
--   re-issuing grants risks regressing the T01/T02/T03 ACLs.
--
-- Idempotent: create or replace function throughout.
-- ============================================================================

begin;

-- ===========================================================================
-- 1. check_username_available — harden (anon-callable by design)
-- ===========================================================================
create or replace function public.check_username_available(p_username text)
returns boolean
language plpgsql
stable security definer
set search_path = ''
as $$
declare
  v_username text := lower(trim(p_username));
begin
  -- Reject malformed input BEFORE touching the table, so the function cannot be
  -- used as an arbitrary-scan oracle.
  if p_username is null
     or v_username is null
     or char_length(v_username) < 3
     or char_length(v_username) > 32
     or v_username !~ '^[a-z0-9_]+$' then
    raise exception 'invalid username' using errcode = '22023';
  end if;
  -- Exact, case-insensitive equality (never a pattern match). Boolean only.
  return not exists (
    select 1 from public.profiles p where lower(p.username) = v_username
  );
end $$;

-- ===========================================================================
-- 2. Gated admin RPCs — pin search_path only
-- ===========================================================================
create or replace function public.admin_grant_admin(p_email text, p_note text default null::text)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid;
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  select u.id into v_uid from auth.users u where lower(u.email) = lower(trim(p_email)) limit 1;
  if v_uid is null then
    raise exception 'no account exists for that email' using errcode = '22023';
  end if;

  insert into private.admin_users (user_id, note) values (v_uid, p_note)
  on conflict (user_id) do update set note = excluded.note;

  -- keep profiles.role in step so any role-based reader agrees
  update public.profiles set role = 'admin'::public.user_role where id = v_uid;

  return v_uid;
end $$;

create or replace function public.admin_list_admins()
returns table(user_id uuid, email text, full_name text, note text, created_at timestamp with time zone)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  return query
    select a.user_id,
           coalesce(p.email, u.email::text, '') as email,
           coalesce(p.full_name, '') as full_name,
           a.note,
           a.created_at
    from private.admin_users a
    left join public.profiles p on p.id = a.user_id
    left join auth.users u on u.id = a.user_id
    order by a.created_at;
end $$;

create or replace function public.admin_reject_order_item(p_item_id uuid, p_reason text)
returns public.orders
language plpgsql security definer set search_path = ''
as $$
declare
  v_item    public.order_items%rowtype;
  v_order   public.orders%rowtype;
  v_reason  text := nullif(trim(coalesce(p_reason, '')), '');
  v_original numeric(12,2);
  v_adjust   numeric(12,2);
  v_final    numeric(12,2);
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  if p_item_id is null then
    raise exception 'item id is required' using errcode = '22023';
  end if;

  if v_reason is null then
    raise exception 'a rejection reason is required' using errcode = '22023';
  end if;

  if char_length(v_reason) > 500 then
    raise exception 'reason is too long (max 500 characters)' using errcode = '22023';
  end if;

  -- Lock the order first, then the item, so concurrent adjustments serialise.
  select o.* into v_order
  from public.orders o
  join public.order_items i on i.order_id = o.id
  where i.id = p_item_id
  for update of o;

  if not found then
    raise exception 'order item not found' using errcode = '22023';
  end if;

  if v_order.status in ('DELIVERED', 'CANCELLED') then
    raise exception 'cannot adjust an order that is %', v_order.status using errcode = '22023';
  end if;

  select * into v_item from public.order_items where id = p_item_id for update;
  if v_item.rejected then
    raise exception 'that item is already rejected' using errcode = '22023';
  end if;

  update public.order_items
  set rejected = true, rejection_reason = v_reason
  where id = p_item_id;

  -- original_total is the pre-adjustment bill; freeze it on first adjustment.
  v_original := coalesce(v_order.original_total, v_order.total);

  -- The adjustment is the NEGATIVE of the rejected value, so it is always <= 0
  -- and the bill can never rise.
  v_adjust := -coalesce((
    select sum(oi.line_total) from public.order_items oi
    where oi.order_id = v_order.id and oi.rejected
  ), 0);

  v_final := v_original + v_adjust;

  if v_final < 0 then
    raise exception 'adjustment would make the total negative' using errcode = '22023';
  end if;

  update public.orders
  set original_total    = v_original,
      adjustment_amount = v_adjust,
      final_total       = v_final,
      adjustment_reason = v_reason,
      adjusted_by       = coalesce(auth.uid()::text, 'admin'),
      adjusted_at       = now()
  where id = v_order.id
  returning * into v_order;

  insert into public.notification_outbox (audience, user_id, type, title, body, data)
  values ('customer', v_order.user_id, 'order_item_rejected',
          'Item unavailable on order ' || v_order.order_number,
          v_item.product_name || ' could not be supplied: ' || v_reason
            || '. Your bill has been revised to ' || v_final::text || '.',
          jsonb_build_object('order_id', v_order.id, 'item_id', p_item_id,
                             'final_total', v_final, 'adjustment_amount', v_adjust));

  return v_order;
end $$;

create or replace function public.admin_restore_order_item(p_item_id uuid)
returns public.orders
language plpgsql security definer set search_path = ''
as $$
declare
  v_item  public.order_items%rowtype;
  v_order public.orders%rowtype;
  v_original numeric(12,2);
  v_adjust   numeric(12,2);
  v_final    numeric(12,2);
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  select o.* into v_order
  from public.orders o
  join public.order_items i on i.order_id = o.id
  where i.id = p_item_id
  for update of o;

  if not found then
    raise exception 'order item not found' using errcode = '22023';
  end if;

  if v_order.status in ('DELIVERED', 'CANCELLED') then
    raise exception 'cannot adjust an order that is %', v_order.status using errcode = '22023';
  end if;

  select * into v_item from public.order_items where id = p_item_id for update;
  if not v_item.rejected then
    raise exception 'that item is not rejected' using errcode = '22023';
  end if;

  update public.order_items
  set rejected = false, rejection_reason = null
  where id = p_item_id;

  v_original := coalesce(v_order.original_total, v_order.total);

  v_adjust := -coalesce((
    select sum(oi.line_total) from public.order_items oi
    where oi.order_id = v_order.id and oi.rejected
  ), 0);

  v_final := v_original + v_adjust;

  update public.orders
  set original_total    = v_original,
      adjustment_amount = v_adjust,
      final_total       = v_final,
      adjustment_reason = case when v_adjust = 0 then null else adjustment_reason end,
      adjusted_by       = coalesce(auth.uid()::text, 'admin'),
      adjusted_at       = now()
  where id = v_order.id
  returning * into v_order;

  insert into public.notification_outbox (audience, user_id, type, title, body, data)
  values ('customer', v_order.user_id, 'order_item_restored',
          'Item restored on order ' || v_order.order_number,
          v_item.product_name || ' has been added back to your order. Your bill is now '
            || v_final::text || '.',
          jsonb_build_object('order_id', v_order.id, 'item_id', p_item_id,
                             'final_total', v_final, 'adjustment_amount', v_adjust));

  return v_order;
end $$;

create or replace function public.admin_revoke_admin(p_user_id uuid)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  -- Guard against locking every admin out of the system.
  if (select count(*) from private.admin_users) <= 1 then
    raise exception 'cannot revoke the last remaining admin' using errcode = '22023';
  end if;

  delete from private.admin_users where user_id = p_user_id;
  update public.profiles set role = 'customer'::public.user_role where id = p_user_id;
end $$;

create or replace function public.admin_slot_load(p_from date default current_date, p_days integer default 7)
returns table(slot_id uuid, name text, starts_at timestamp with time zone, ends_at timestamp with time zone, capacity integer, is_active boolean, orders_taken bigint, remaining integer)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  return query
    select s.id, s.name, s.starts_at, s.ends_at,
           coalesce(s.capacity, s.max_orders, 0)::int as capacity,
           s.is_active,
           (select count(*) from public.orders o
             where (o.delivery_slot_id = s.id or o.slot_id = s.id)
               and o.status <> 'CANCELLED') as orders_taken,
           greatest(0, coalesce(s.capacity, s.max_orders, 0)
             - (select count(*) from public.orders o
                 where (o.delivery_slot_id = s.id or o.slot_id = s.id)
                   and o.status <> 'CANCELLED'))::int as remaining
    from public.delivery_slots s
    where (s.starts_at is null
           or (s.starts_at >= p_from::timestamptz
               and s.starts_at < (p_from + greatest(p_days,1))::timestamptz))
    order by s.starts_at nulls last, s.sort_order, s.name;
end $$;

create or replace function public.admin_update_order_status(p_order_id uuid, p_status public.order_status, p_reason text default null::text)
returns public.orders
language plpgsql security definer set search_path = ''
as $$
declare
  v_order public.orders%rowtype;
  v_reason text := nullif(trim(coalesce(p_reason, '')), '');
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  if p_order_id is null or p_status is null then
    raise exception 'order id and status are required' using errcode = '22023';
  end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then
    raise exception 'order not found' using errcode = '22023';
  end if;

  if v_order.status = p_status then
    raise exception 'order is already %', p_status using errcode = '22023';
  end if;

  if not private.order_transition_allowed(v_order.status, p_status) then
    raise exception 'cannot move an order from % to %', v_order.status, p_status using errcode = '22023';
  end if;

  if p_status = 'CANCELLED' and v_reason is null then
    raise exception 'a cancellation reason is required' using errcode = '22023';
  end if;

  if v_reason is not null and char_length(v_reason) > 500 then
    raise exception 'reason is too long (max 500 characters)' using errcode = '22023';
  end if;

  update public.orders
  set status        = p_status,
      cancelled_at  = case when p_status = 'CANCELLED' then now() else cancelled_at end,
      cancel_reason = case when p_status = 'CANCELLED' then v_reason else cancel_reason end
  where id = p_order_id
  returning * into v_order;

  -- history row + customer notification are written by triggers on public.orders
  return v_order;
end $$;

create or replace function public.ensure_delivery_slots(p_days integer default 10)
returns integer
language plpgsql security definer set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;
  return private.generate_delivery_slots(p_days);
end $$;

-- ===========================================================================
-- 3. Order placement — pin search_path only (bodies already fully qualified)
-- ===========================================================================
create or replace function public.place_order(p_idempotency_key uuid, p_address_id uuid, p_slot_id uuid, p_delivery_date date, p_items jsonb)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_settings record;
  v_slot record;
  v_slot_count int;
  v_item jsonb;
  v_product record;
  v_weight public.product_weights%rowtype;
  v_offer public.offers%rowtype;
  v_weight_id uuid;
  v_qty int;
  v_list numeric(12,2);
  v_price numeric(12,2);
  v_unit_label text;
  v_weight_label text;
  v_subtotal numeric(12,2) := 0;
  v_fee numeric(12,2);
  v_order public.orders%rowtype;
  v_addr public.addresses%rowtype;
  v_lines jsonb := '[]'::jsonb;
  v_existing jsonb;
  v_order_id uuid;
begin
  if v_uid is null then
    return jsonb_build_object('error', 'UNAUTHENTICATED');
  end if;

  if p_idempotency_key is null then
    return jsonb_build_object('error', 'VALIDATION');
  end if;

  -- 1. Idempotent replay (unique-violation below is also caught as replay)
  select to_jsonb(o) into v_existing from public.orders o
  where o.idempotency_key = p_idempotency_key;
  if v_existing is not null then
    return jsonb_build_object('order_id', v_existing->>'id', 'order_number', v_existing->>'order_number', 'replay', true);
  end if;

  select * into v_settings from public.store_settings where id = 1;
  if not found or not v_settings.is_open then
    return jsonb_build_object('error', 'STORE_CLOSED');
  end if;

  -- 2. Validate date (today/tomorrow only)
  if p_delivery_date is null or p_delivery_date not in (current_date, current_date + 1) then
    return jsonb_build_object('error', 'INVALID_DATE');
  end if;

  -- 3. Address ownership
  select * into v_addr from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then
    return jsonb_build_object('error', 'INVALID_ADDRESS');
  end if;

  -- 4. Slot: active + capacity (row-locked)
  select * into v_slot from public.delivery_slots s
  where s.id = p_slot_id and s.is_active
  for update;
  if not found then
    return jsonb_build_object('error', 'INVALID_SLOT');
  end if;
  select count(*) into v_slot_count from public.orders o
  where o.slot_id = p_slot_id and o.delivery_date = p_delivery_date and o.status <> 'CANCELLED';
  if v_slot_count >= v_slot.max_orders then
    return jsonb_build_object('error', 'SLOT_FULL');
  end if;

  -- 5. Validate all items first (lock rows, server-side pricing, buffer lines)
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 or jsonb_array_length(p_items) > 50 then
    return jsonb_build_object('error', 'VALIDATION');
  end if;

  for v_item in select * from jsonb_array_elements(p_items) loop
    begin
      v_qty := (v_item->>'qty')::int;
      v_weight_id := nullif(v_item->>'weight_id', '')::uuid;
    exception when others then
      return jsonb_build_object('error', 'VALIDATION');
    end;
    if v_qty is null or v_qty < 1 or v_qty > 50 then
      return jsonb_build_object('error', 'VALIDATION');
    end if;

    select * into v_product from public.products p
    where p.id = (v_item->>'product_id')::uuid and p.is_active
    for update;
    if not found then
      return jsonb_build_object('error', 'PRODUCT_UNAVAILABLE');
    end if;
    if v_qty > v_product.max_order_qty then
      return jsonb_build_object('error', 'QTY_EXCEEDED', 'product_id', v_product.id, 'max', v_product.max_order_qty);
    end if;
    if v_qty > v_product.stock then
      return jsonb_build_object('error', 'OUT_OF_STOCK', 'product_id', v_product.id, 'stock', v_product.stock);
    end if;

    -- Resolve the chosen pack size. This is the fix for the weight-pricing bug.
    if v_weight_id is not null then
      select * into v_weight from public.product_weights w
      where w.id = v_weight_id and w.product_id = v_product.id;
      if not found then
        return jsonb_build_object('error', 'WEIGHT_UNAVAILABLE', 'product_id', v_product.id);
      end if;
      if not v_weight.is_available then
        return jsonb_build_object('error', 'WEIGHT_UNAVAILABLE', 'product_id', v_product.id);
      end if;
      v_list := v_weight.price;
      v_weight_label := v_weight.label;
    else
      v_list := coalesce(v_product.base_price, v_product.price);
      v_weight_label := '';
    end if;
    v_unit_label := coalesce(nullif(v_product.unit_label, ''), v_product.unit, 'pc');

    -- Live offer, picked exactly like the client pickActiveOffer().
    select * into v_offer from public.offers o
    where o.product_id = v_product.id
      and o.is_active
      and (o.starts_at is null or o.starts_at <= now())
      and (o.ends_at is null or o.ends_at > now())
    order by o.created_at desc
    limit 1;

    v_price := v_list;
    if v_offer.id is not null then
      if v_offer.price is not null then
        v_price := least(v_price, v_offer.price);
      end if;
      if v_offer.discount_percent is not null then
        v_price := least(v_price, round(v_list * (1 - v_offer.discount_percent / 100.0), 2));
      end if;
    end if;

    v_subtotal := v_subtotal + v_price * v_qty;
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object('product_id', v_product.id, 'product_name', v_product.name,
                         'unit', v_unit_label, 'unit_price', v_price, 'qty', v_qty,
                         'weight_label', v_weight_label)
    );
  end loop;

  if v_subtotal < v_settings.min_order_amount then
    return jsonb_build_object('error', 'BELOW_MIN_ORDER', 'min', v_settings.min_order_amount);
  end if;

  v_fee := case when v_subtotal >= v_settings.free_delivery_threshold then 0 else v_settings.delivery_fee end;

  -- 6. Insert order + items + stock decrements (all-or-nothing).
  --    delivery_slot_id is the app-contract column; slot_id is the legacy one.
  --    Both are written so either reader agrees.
  insert into public.orders (
    order_number, user_id, status, delivery_date, slot_id, delivery_slot_id,
    address_snapshot, subtotal, delivery_fee, delivery_charge, total,
    original_total, final_total, adjustment_amount, idempotency_key
  )
  values (
    public.gen_order_number(), v_uid, 'PENDING', p_delivery_date, p_slot_id, p_slot_id,
    jsonb_strip_nulls(jsonb_build_object(
      'label', v_addr.label, 'full_name', v_addr.full_name, 'phone', v_addr.phone,
      'house_number', v_addr.house_number, 'street', v_addr.street,
      'landmark', v_addr.landmark, 'line1', v_addr.line1, 'line2', v_addr.line2,
      'city', v_addr.city, 'state', v_addr.state,
      'pincode', v_addr.pincode, 'postal_code', v_addr.postal_code,
      'latitude', v_addr.latitude, 'longitude', v_addr.longitude
    )),
    v_subtotal, v_fee, v_fee, v_subtotal + v_fee,
    v_subtotal + v_fee, v_subtotal + v_fee, 0, p_idempotency_key
  )
  on conflict (idempotency_key) do nothing
  returning id into v_order_id;

  if v_order_id is null then
    -- concurrent duplicate insert won the race
    select to_jsonb(o) into v_existing from public.orders o where o.idempotency_key = p_idempotency_key;
    return jsonb_build_object('order_id', v_existing->>'id', 'order_number', v_existing->>'order_number', 'replay', true);
  end if;

  insert into public.order_items (order_id, product_id, product_name, unit, unit_price, qty, weight_label)
  select v_order_id, (l->>'product_id')::uuid, l->>'product_name', l->>'unit',
         (l->>'unit_price')::numeric, (l->>'qty')::int, coalesce(l->>'weight_label', '')
  from jsonb_array_elements(v_lines) as l;

  update public.products p
  set stock = p.stock - u.qty
  from (
    select (l->>'product_id')::uuid as pid, (l->>'qty')::int as qty
    from jsonb_array_elements(v_lines) as l
  ) u
  where p.id = u.pid;

  select * into v_order from public.orders o where o.id = v_order_id;

  return jsonb_build_object(
    'order_id', v_order.id,
    'order_number', v_order.order_number,
    'subtotal', v_order.subtotal,
    'delivery_fee', v_order.delivery_fee,
    'total', v_order.total,
    'final_total', v_order.final_total
  );
exception
  when unique_violation then
    return jsonb_build_object('error', 'DUPLICATE_OR_RETRY');
end $$;

create or replace function public.place_order(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)
returns uuid
language sql security definer set search_path = ''
as $$ select public.place_order_app(p_request_id, p_address_id, p_delivery_slot_id) $$;

create or replace function public.place_order_app(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)
returns uuid
language plpgsql security definer set search_path = ''
as $$
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
end $$;

-- ===========================================================================
-- 4. Trigger functions — pin search_path only (NO role gate)
-- ===========================================================================
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  insert into public.profiles (id, full_name, phone, role)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    nullif(new.phone, ''),
    'customer'::public.user_role
  )
  on conflict (id) do nothing;
  return new;
end $$;

create or replace function public.notify_complaint_event()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('admin', null, 'new_complaint', 'New complaint (' || new.type || ')',
            left(new.description, 120), jsonb_build_object('complaint_id', new.id));
  elsif tg_op = 'UPDATE' and old.status is distinct from new.status then
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('customer', new.user_id, 'complaint_update', 'Complaint update: ' || new.status,
            coalesce(new.admin_reply, ''), jsonb_build_object('complaint_id', new.id));
  end if;
  return new;
end $$;

create or replace function public.notify_order_event()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('admin', null, 'new_order', 'New order ' || new.order_number,
            'Order placed for ' || new.delivery_date, jsonb_build_object('order_id', new.id));
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('customer', new.user_id, 'order_placed', 'Order ' || new.order_number || ' placed',
            'We received your order.', jsonb_build_object('order_id', new.id));
  elsif tg_op = 'UPDATE' and old.status is distinct from new.status then
    insert into public.notification_outbox (audience, user_id, type, title, body, data)
    values ('customer', new.user_id, 'order_status', 'Order ' || new.order_number || ': ' || new.status,
            'Status updated.', jsonb_build_object('order_id', new.id, 'status', new.status));
  end if;
  return new;
end $$;

create or replace function public.record_order_status_history()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.order_status_history (order_id, old_status, new_status, changed_by, reason)
    values (new.id, null, new.status::text, coalesce(auth.uid()::text, 'system'), 'Order placed');
    return new;
  end if;

  if tg_op = 'UPDATE' and old.status is distinct from new.status then
    insert into public.order_status_history (order_id, old_status, new_status, changed_by, reason)
    values (new.id, old.status::text, new.status::text,
            coalesce(auth.uid()::text, 'system'),
            case when new.status = 'CANCELLED' then new.cancel_reason else null end);
  end if;
  return new;
end $$;

create or replace function public.sync_address_pincode()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  -- If postal_code provided, sync to pincode (6-char); if line1 missing, build from street+house_number
  if new.postal_code is not null and new.postal_code <> '' then
    new.pincode := rpad(substr(new.postal_code, 1, 6), 6, '0');
    -- ensure pincode is exactly 6 digits
    if new.pincode !~ '^[0-9]{6}$' then
      -- fallback to a valid placeholder, validation layer already ensures 5-10 digits
      new.pincode := rpad(substr(regexp_replace(new.postal_code, '[^0-9]', '', 'g'), 1, 6), 6, '0');
    end if;
  end if;
  if (new.line1 is null or new.line1 = '') and (new.street is not null and new.street <> '') then
    new.line1 := coalesce(new.house_number, '') || ' ' || new.street;
    new.line1 := trim(new.line1);
    if new.line1 = '' then new.line1 := new.label; end if;
  end if;
  if new.line1 is null or new.line1 = '' then
    new.line1 := coalesce(new.label, 'Home');
  end if;
  if new.pincode is null or new.pincode = '' then
    new.pincode := '000000';
  end if;
  if new.city is null or new.city = '' then
    new.city := 'Unknown';
  end if;
  return new;
end;
$$;

create or replace function public.sync_profile_phone()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.phone is not null and new.raw_user_meta_data->>'phone' is not null then
    update public.profiles set phone = new.raw_user_meta_data->>'phone' where id = new.id and phone is null;
  end if;
  return new;
end $$;

-- ===========================================================================
-- 5. SECURITY INVOKER hygiene — pin search_path only (no gate needed)
-- ===========================================================================
create or replace function public.set_updated_at()
returns trigger
language plpgsql set search_path = ''
as $$
begin new.updated_at = now(); return new; end $$;

create or replace function public.gen_order_number()
returns text
language sql set search_path = ''
as $$
  select 'ORD-' || to_char(now(), 'YYMMDD') || '-' ||
         upper(substr(md5(random()::text || clock_timestamp()::text), 1, 5))
$$;

commit;
