-- ============================================================================
-- 20261104_002_instant_delivery_2day_eta.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- GOALS:
--   1. Add admin-editable Instant Delivery ETA columns to store_settings
--      (instant_delivery_eta_min / instant_delivery_eta_max, minutes), default
--      30 / 45, with a CHECK enforcing 1 <= min <= max <= 1440.
--   2. Enable Instant Delivery on the live store_settings row (id = 1). The
--      website checkout chain was already fully wired; the option was simply
--      invisible because this flag was false. This is the visibility fix.
--   3. Reduce the rolling slot horizon from 3 IST days to 2 (today + tomorrow):
--        * auto_ensure_delivery_slots() -> private.generate_delivery_slots(2)
--        * ensure_delivery_slots() default p_days 10 -> 2
--      Without this, the website's best-effort top-up would recreate the
--      surplus days on the very next page load.
--   4. Delete EVERY delivery_slots row outside the 2-day IST window, SAFELY:
--        * archive every deleted slot row (all columns) and every affected
--          order->slot link BEFORE deleting;
--        * detach orders.slot_id (ON DELETE NO ACTION) and delivery_slot_id
--          (ON DELETE SET NULL) BEFORE deleting, so the DELETE cannot fail;
--        * NEVER delete from orders. delivery_date, totals, status and
--          address_snapshot are left untouched.
--   5. Tighten place_order (5-arg) and place_order_app (3-arg) delivery-date
--      whitelist from (today, +1, +2) to (today, +1). The instant-delivery
--      NULL-slot gate and the scheduled-slot lock/capacity path are unchanged.
--
-- Idempotent throughout. The whole file is ONE transaction (begin/commit) and
-- is intended to be applied to the LIVE project as a single query.
-- ============================================================================

begin;

-- ===========================================================================
-- 1. Admin-editable Instant Delivery ETA (minutes)
-- ===========================================================================
alter table public.store_settings
  add column if not exists instant_delivery_eta_min integer not null default 30;
alter table public.store_settings
  add column if not exists instant_delivery_eta_max integer not null default 45;

comment on column public.store_settings.instant_delivery_eta_min is
  'Admin-editable lower bound (minutes) of the Instant Delivery ETA shown at checkout.';
comment on column public.store_settings.instant_delivery_eta_max is
  'Admin-editable upper bound (minutes) of the Instant Delivery ETA shown at checkout.';

-- Idempotent CHECK: drop-if-exists then add. The defaults (30, 45) satisfy it,
-- so a plain (validated) CHECK is safe on the existing rows.
alter table public.store_settings
  drop constraint if exists store_settings_instant_delivery_eta_chk;
alter table public.store_settings
  add constraint store_settings_instant_delivery_eta_chk
  check (
    instant_delivery_eta_min >= 1
    and instant_delivery_eta_min <= instant_delivery_eta_max
    and instant_delivery_eta_max <= 1440
  );

-- ===========================================================================
-- 2. Visibility fix: turn Instant Delivery ON for the single settings row
-- ===========================================================================
update public.store_settings
   set instant_delivery_enabled = true
 where id = 1;

-- ===========================================================================
-- 3. Rolling horizon 3 days -> 2 days (today + tomorrow, IST)
-- ===========================================================================
create or replace function public.auto_ensure_delivery_slots()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
begin
  return private.generate_delivery_slots(2);
end $$;

create or replace function public.ensure_delivery_slots(
  -- NULL (not CURRENT_DATE) so the body can resolve "today" in IST.
  p_from date DEFAULT NULL,
  p_days integer DEFAULT 2
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_windows constant jsonb := '[
    {"name":"Morning",  "start":"08:00", "end":"10:00", "sort":1},
    {"name":"Afternoon","start":"12:00", "end":"14:00", "sort":2},
    {"name":"Evening",  "start":"17:00", "end":"19:00", "sort":3}
  ]'::jsonb;
  v_capacity constant int := 25;
  v_days  int := least(greatest(coalesce(p_days, 2), 1), 60);
  v_anchor date := coalesce(p_from, (now() at time zone 'Asia/Kolkata')::date);
  v_date  date;
  v_w     jsonb;
  v_created int := 0;
  v_start timestamptz;
  v_end   timestamptz;
  i       int;
begin
  if not private.can_manage_catalogue() then
    raise exception 'admin authorization required' using errcode = '42501';
  end if;

  for i in 0 .. v_days - 1 loop
    v_date := v_anchor + i;

    for v_w in select * from jsonb_array_elements(v_windows) loop
      v_start := ((v_date::text || ' ' || (v_w->>'start'))::timestamp
                    at time zone 'Asia/Kolkata');
      v_end   := ((v_date::text || ' ' || (v_w->>'end'))::timestamp
                    at time zone 'Asia/Kolkata');

      -- Never generate a window that has already fully passed.
      continue when v_end <= now();

      -- Idempotent: skip if a slot with this starts_at already exists.
      if exists (select 1 from public.delivery_slots s where s.starts_at = v_start) then
        continue;
      end if;

      insert into public.delivery_slots
        (name, start_time, end_time, max_orders, capacity, is_active, sort_order, starts_at, ends_at)
      values
        (v_w->>'name',
         (v_w->>'start')::time,
         (v_w->>'end')::time,
         v_capacity, v_capacity, true, (v_w->>'sort')::int,
         v_start, v_end);

      v_created := v_created + 1;
    end loop;
  end loop;

  return v_created;
end $$;

-- Keep the original ACLs exactly as they were (CREATE OR REPLACE preserves
-- them; these statements make that guarantee explicit and are idempotent).
revoke execute on function public.ensure_delivery_slots(date, integer) from public, anon;
grant  execute on function public.ensure_delivery_slots(date, integer) to authenticated, service_role;

revoke execute on function public.auto_ensure_delivery_slots() from public, anon;
grant  execute on function public.auto_ensure_delivery_slots() to authenticated, service_role;

-- ===========================================================================
-- 4. SAFE slot cleanup — archive first, detach links, then delete
-- ===========================================================================
-- now() is transaction-stable, so `v_today` and every inline IST-day test
-- below resolve to exactly the same 2-day window for the whole transaction.
do $$
declare
  v_today date := (now() at time zone 'Asia/Kolkata')::date;
  v_archived_slots  int := 0;
  v_archived_links  int := 0;
  v_detached_orders int := 0;
  v_deleted_slots   int := 0;
begin
  -- Archive tables are created BEFORE anything is deleted. `like` copies every
  -- column of delivery_slots (all columns, in the same order). No primary key
  -- is copied, so the dedup guards below can never collide.
  create table if not exists public.delivery_slots_archive_20261104
    (like public.delivery_slots);

  create table if not exists public.order_slot_links_archive_20261104 (
    order_id         uuid not null,
    slot_id          uuid,
    delivery_slot_id uuid,
    delivery_date    date
  );

  -- 4a. Full copy of every slot row that is about to be deleted. Guarded so a
  --     re-run never inserts a duplicate row.
  insert into public.delivery_slots_archive_20261104
    (id, name, start_time, end_time, max_orders, is_active, sort_order,
     starts_at, ends_at, capacity, created_at, updated_at)
  select s.id, s.name, s.start_time, s.end_time, s.max_orders, s.is_active,
         s.sort_order, s.starts_at, s.ends_at, s.capacity, s.created_at, s.updated_at
    from public.delivery_slots s
   where (s.starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
     and not exists (
       select 1 from public.delivery_slots_archive_20261104 a where a.id = s.id
     );
  get diagnostics v_archived_slots = row_count;

  -- 4b. Archive the order->slot links that will be detached. This captures the
  --     ORIGINAL values before the UPDATEs. One row per affected order; guarded
  --     so a re-run never duplicates it.
  insert into public.order_slot_links_archive_20261104
    (order_id, slot_id, delivery_slot_id, delivery_date)
  select o.id, o.slot_id, o.delivery_slot_id, o.delivery_date
    from public.orders o
   where (
     o.slot_id in (
       select id from public.delivery_slots
        where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
     )
     or o.delivery_slot_id in (
       select id from public.delivery_slots
        where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
     )
   )
     and not exists (
       select 1 from public.order_slot_links_archive_20261104 a where a.order_id = o.id
     );
  get diagnostics v_archived_links = row_count;

  -- Distinct orders whose links are about to be detached (for the notice).
  select count(*) into v_detached_orders
    from public.orders o
   where o.slot_id in (
       select id from public.delivery_slots
        where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
     )
      or o.delivery_slot_id in (
       select id from public.delivery_slots
        where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
     );

  -- 4c. DETACH BEFORE DELETE.
  --     orders_slot_id_fkey is ON DELETE NO ACTION, so slot_id MUST be nulled
  --     explicitly or the DELETE would fail with an FK violation.
  --     orders_delivery_slot_id_fkey is ON DELETE SET NULL; we null it anyway
  --     so the final state does not depend on the FK action. NO order row is
  --     ever deleted: delivery_date, totals, status and address_snapshot stay.
  update public.orders
     set slot_id = null
   where slot_id in (
     select id from public.delivery_slots
      where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
   );

  update public.orders
     set delivery_slot_id = null
   where delivery_slot_id in (
     select id from public.delivery_slots
      where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1)
   );

  -- 4d. Delete every slot outside the 2-day IST window.
  delete from public.delivery_slots
   where (starts_at at time zone 'Asia/Kolkata')::date not in (v_today, v_today + 1);
  get diagnostics v_deleted_slots = row_count;

  raise notice
    '[20261104_002] ist_today=%, deleted_slots=%, detached_orders=%, archived_slots=%, archived_links=%, orders_remaining=%',
    v_today, v_deleted_slots, v_detached_orders, v_archived_slots,
    v_archived_links, (select count(*) from public.orders);
end $$;

-- ===========================================================================
-- 5. Engine: public.place_order (5-arg) — 2-day date whitelist
--    Body is byte-for-byte the current live function (20261103_001); ONLY the
--    delivery-date whitelist is tightened from (today,+1,+2) to (today,+1).
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

  -- 2. Validate date (today/tomorrow, IST-anchored) — rolling 2-day horizon.
  if p_delivery_date is null or p_delivery_date not in (v_today, v_today + 1) then
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
-- 6. Customer-facing wrapper: public.place_order_app (3-arg) — 2-day clamp
--    ONLY the delivery-date clamp is tightened (+2 -> +1). Everything else,
--    including instant delivery (no slot -> today IST), is unchanged.
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
  -- Allowed window is today/tomorrow (IST, the rolling 2-day horizon); anything
  -- outside clamps to today, so a stale slot id can never push an order beyond
  -- the 2-day horizon.
  select (starts_at at time zone 'Asia/Kolkata')::date into v_delivery_date
  from public.delivery_slots where id = p_delivery_slot_id;
  if v_delivery_date is null then
    v_delivery_date := v_today;
  elsif v_delivery_date < v_today or v_delivery_date > v_today + 1 then
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
