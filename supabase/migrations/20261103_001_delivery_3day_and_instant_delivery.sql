-- ============================================================================
-- 20261103_001_delivery_3day_and_instant_delivery.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- GOALS:
--   1. Add store_settings.instant_delivery_enabled (boolean, default false),
--      visible on the anon/authenticated row. The admin app toggles it through
--      the normal store_settings UPDATE path (RLS-gated on
--      private.can_manage_store()).
--   2. Extend the delivery-date window from today/tomorrow to today/tomorrow/
--      day-after (the rolling 3-day IST horizon), so the day-after is no longer
--      silently clamped away.
--   3. Support Instant Delivery: when p_delivery_slot_id IS NULL, place the
--      order WITHOUT a delivery slot, allowed only when
--      instant_delivery_enabled AND is_open. The 5-arg engine's behaviour for
--      scheduled slots is otherwise unchanged byte-for-byte.
--
-- In instant orders the order's delivery_slot_id and slot_id columns are both
-- written as NULL (the engine writes p_slot_id into both), so the absence of a
-- slot id is the marker that distinguishes an instant order. delivery_date for
-- instant orders is set to today in IST.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE FUNCTION throughout.
-- ============================================================================

begin;

-- ===========================================================================
-- 1. store_settings.instant_delivery_enabled
-- ===========================================================================
alter table public.store_settings
  add column if not exists instant_delivery_enabled boolean not null default false;

comment on column public.store_settings.instant_delivery_enabled is
  'When true and the store is open, checkout offers an Instant Delivery option '
  '(an order placed without a scheduled delivery slot).';

-- ===========================================================================
-- 2. Engine: public.place_order (5-arg) — date window + instant slot branch
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
  v_today date := (now() at time zone 'Asia/Kolkata')::date;
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

  -- 2. Validate date (today/tomorrow/day-after, IST-anchored)
  if p_delivery_date is null or p_delivery_date not in (v_today, v_today + 1, v_today + 2) then
    return jsonb_build_object('error', 'INVALID_DATE');
  end if;

  -- 3. Address ownership
  select * into v_addr from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then
    return jsonb_build_object('error', 'INVALID_ADDRESS');
  end if;

  -- 4. Slot: active + capacity (row-locked). A NULL slot means Instant Delivery,
  --    allowed only when the store has instant delivery enabled (store-open was
  --    already enforced above). Scheduled-slot behaviour is unchanged.
  if p_slot_id is null then
    if not coalesce(v_settings.instant_delivery_enabled, false) then
      return jsonb_build_object('error', 'INVALID_SLOT');
    end if;
  else
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
  --    Both are written so either reader agrees. For instant orders both stay
  --    NULL (p_slot_id is NULL), which is the marker for an instant order.
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

-- ===========================================================================
-- 3. Customer-facing wrapper: public.place_order_app (3-arg)
-- ===========================================================================
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
  v_today date := (now() at time zone 'Asia/Kolkata')::date;
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

  -- delivery_date: scheduled slot -> its IST day; instant (no slot) -> today IST.
  -- Allowed window is today/tomorrow/day-after (IST); anything outside clamps to
  -- today, so a stale slot id can never push an order beyond the 3-day horizon.
  select (starts_at at time zone 'Asia/Kolkata')::date into v_delivery_date
  from public.delivery_slots where id = p_delivery_slot_id;
  if v_delivery_date is null then
    v_delivery_date := v_today;
  elsif v_delivery_date < v_today or v_delivery_date > v_today + 2 then
    v_delivery_date := v_today;
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

commit;
