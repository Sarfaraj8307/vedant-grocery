-- ============================================================================
-- 20261016_004_indexes_and_reporting.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Task T04 — Index remediation + admin search/reporting RPCs.
-- ----------------------------------------------------------------------------
-- PART A — indexes
--   ADD 5 covering indexes for the 5 P1 `unindexed_foreign_keys`
--   (cart_items.product_id/weight_id, complaint_messages.author_id,
--    orders.address_id/delivery_slot_id).
--
--   DROP exactly 3 demonstrably-unused indexes. Evidence gathered before the
--   drop (2026-10-16):
--     * pg_stat_user_indexes.idx_scan = 0 for all three.
--     * `grep -rn pincode src/ admin-android/` -> only the `addresses` table and
--       one mapper test reference it; nothing filters `orders.pincode`.
--     * `events` is not read by the customer web app or the Android admin app,
--       and the table has 0 rows (no writer).
--   => orders_pincode_idx, events_created_idx, events_event_idx are safe to drop.
--
--   KEEP every other index (incl. the dormant `notifications_user_idx` on the
--   legacy `notifications` table — consolidating that table is a separate
--   decision, not an index decision).
--
--   NOT DONE: the §9.3 `pg_trgm` GIN index on profiles.full_name. `pg_trgm` is
--   not installed and 4 profiles is textbook over-indexing. DEFERRED OPTION —
--   add only if admin search latency demands it at scale:
--     create extension if not exists pg_trgm;
--     create index profiles_name_trgm_idx on public.profiles using gin (full_name gin_trgm_ops);
--
--   Expected advisor movement: `unindexed_foreign_keys` 5 -> 0;
--   `unused_index` rises slightly (the new FK indexes on near-empty tables are
--   unused until traffic exists). A P1 closed for a P3 — intended trade.
--
-- PART B — reporting RPCs (all server-side; the frontend never sums).
--   Three SECURITY DEFINER, internally-gated, STABLE functions. Fully-qualified
--   object names; ACL convention = authenticated + service_role only.
--
--   TWO BUGS IN THE DESIGN'S SQL, FIXED HERE (see inline notes):
--     1. admin_sales_today.refunds was structurally always 0 (the CTE filtered
--        out CANCELLED, then summed CANCELLED). Now computed from cancelled
--        orders in a separate subquery.
--     2. admin_dashboard_counts.delivered_today used `placed_at` (wrong). Now
--        uses `delivery_date = today` (there is no delivered_at column).
-- ============================================================================

begin;

-- ===========================================================================
-- PART A — indexes
-- ===========================================================================

-- A.1  covering indexes for the 5 unindexed foreign keys ---------------------
create index if not exists cart_items_product_idx        on public.cart_items(product_id);
create index if not exists cart_items_weight_idx         on public.cart_items(weight_id);
create index if not exists complaint_messages_author_idx on public.complaint_messages(author_id);
create index if not exists orders_address_idx            on public.orders(address_id);
create index if not exists orders_delivery_slot_idx      on public.orders(delivery_slot_id);

-- A.2  drop the 3 verified-unused indexes ------------------------------------
drop index if exists public.orders_pincode_idx;   -- idx_scan=0; no client filters orders.pincode
drop index if exists public.events_created_idx;   -- idx_scan=0; events table unread + empty
drop index if exists public.events_event_idx;     -- idx_scan=0; events table unread + empty

-- ===========================================================================
-- PART B — reporting RPCs
-- ===========================================================================

-- B.1  admin_dashboard_counts() — gate private.is_staff() --------------------
--      Every counter is a real query. `delivered_today` keys on delivery_date
--      (the committed delivery day, set from the slot at placement) because
--      there is no delivered_at column; using placed_at would mis-bucket an
--      order placed on a prior day but delivered today. [ASSUMPTION]
create or replace function public.admin_dashboard_counts()
returns table (
  pending bigint, active bigint, ready bigint, out_for_delivery bigint,
  delivered_today bigint, cancelled bigint, todays_orders bigint,
  todays_sales numeric, pending_complaints bigint, open_complaints bigint
)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.is_staff() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  return query
  select
    (select count(*) from public.orders o where o.status = 'PENDING'),
    (select count(*) from public.orders o where o.status in ('CONFIRMED','PROCESSING')),
    (select count(*) from public.orders o where o.status = 'READY_FOR_DELIVERY'),
    (select count(*) from public.orders o where o.status = 'OUT_FOR_DELIVERY'),
    (select count(*) from public.orders o
       where o.status = 'DELIVERED'
         and o.delivery_date = (now() at time zone 'Asia/Kolkata')::date),
    (select count(*) from public.orders o where o.status = 'CANCELLED'),
    (select count(*) from public.orders o
       where (o.placed_at at time zone 'Asia/Kolkata')::date = (now() at time zone 'Asia/Kolkata')::date),
    (select coalesce(sum(o.final_total),0) from public.orders o
       where o.status <> 'CANCELLED'
         and (o.placed_at at time zone 'Asia/Kolkata')::date = (now() at time zone 'Asia/Kolkata')::date),
    (select count(*) from public.complaints c where c.status in ('OPEN','IN_PROGRESS')),
    (select count(*) from public.complaints c where c.status = 'OPEN');
end $$;

revoke execute on function public.admin_dashboard_counts() from public, anon;
grant  execute on function public.admin_dashboard_counts() to authenticated, service_role;

-- B.2  admin_sales_today(p_date) — gate private.can_manage_users() -----------
--      `refunds` FIX: computed from CANCELLED orders (same placed-on-`d` cohort
--      as the rest of the row) via a separate subquery — the design's CTE
--      filtered them out, making the column structurally 0. Accounting mapping
--      is [ASSUMPTION] (owner confirms in T07).
create or replace function public.admin_sales_today(p_date date default null)
returns table (
  order_date date, orders bigint, gross numeric, discounts numeric, adjustments numeric,
  refunds numeric, delivery_revenue numeric, net numeric, aov numeric
)
language plpgsql stable security definer set search_path = '' as $$
declare
  d date := coalesce(p_date, (now() at time zone 'Asia/Kolkata')::date);
begin
  if not private.can_manage_users() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  return query
  select
    d,
    count(*)::bigint,
    coalesce(sum(o.subtotal),0),                             -- gross line value
    coalesce(sum(o.subtotal - o.total + o.delivery_fee),0),  -- list vs charged (discounts)
    coalesce(sum(o.adjustment_amount),0),                    -- <= 0 (rejections)
    (select coalesce(sum(o2.total),0) from public.orders o2
       where o2.status = 'CANCELLED'
         and (o2.placed_at at time zone 'Asia/Kolkata')::date = d),  -- refunds (FIX)
    coalesce(sum(o.delivery_fee),0),                         -- delivery revenue
    coalesce(sum(o.final_total),0),                          -- net
    coalesce(round(avg(o.final_total),2),0)                  -- AOV
  from public.orders o
  where o.status <> 'CANCELLED'
    and (o.placed_at at time zone 'Asia/Kolkata')::date = d;
end $$;

revoke execute on function public.admin_sales_today(date) from public, anon;
grant  execute on function public.admin_sales_today(date) to authenticated, service_role;

-- B.3  admin_search_users(p_query, p_filter, p_limit, p_offset) --------------
--      gate can_manage_users() OR can_manage_complaints(); limit clamped 1..100,
--      offset >= 0. Filters: ACTIVE|SUSPENDED|BLOCKED|HAS_ACTIVE_ORDER|
--      HAS_COMPLAINT|NEW_CUSTOMER. No trigram index (deferred, see header).
create or replace function public.admin_search_users(
  p_query text default null,
  p_filter text default null,
  p_limit int default 30,
  p_offset int default 0
)
returns table (
  user_id uuid, full_name text, phone text, email text, role public.user_role,
  account_status public.account_status, orders_count bigint, last_order_at timestamptz,
  open_complaints bigint, created_at timestamptz
)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.can_manage_users() and not private.can_manage_complaints() then
    raise exception 'not authorized' using errcode = '42501';
  end if;
  return query
  select p.id, p.full_name, p.phone, p.email, p.role, p.account_status,
         (select count(*) from public.orders o where o.user_id = p.id),
         (select max(o.placed_at) from public.orders o where o.user_id = p.id),
         (select count(*) from public.complaints c
            where c.user_id = p.id and c.status in ('OPEN','IN_PROGRESS')),
         p.created_at
  from public.profiles p
  where
    (p_query is null or p_query = '' or
       p.full_name ilike '%'||p_query||'%'
       or p.phone ilike '%'||p_query||'%'
       or p.email ilike '%'||p_query||'%'
       or p.id::text = p_query
       or exists (select 1 from public.orders o
                    where o.user_id = p.id
                      and (o.order_number ilike '%'||p_query||'%' or o.id::text = p_query)))
    and (p_filter is null
       or (p_filter = 'ACTIVE'           and p.account_status = 'ACTIVE')
       or (p_filter = 'SUSPENDED'        and p.account_status = 'SUSPENDED')
       or (p_filter = 'BLOCKED'          and p.account_status = 'BLOCKED')
       or (p_filter = 'HAS_ACTIVE_ORDER' and exists (select 1 from public.orders o
              where o.user_id = p.id and o.status not in ('DELIVERED','CANCELLED')))
       or (p_filter = 'HAS_COMPLAINT'    and exists (select 1 from public.complaints c
              where c.user_id = p.id))
       or (p_filter = 'NEW_CUSTOMER'     and p.created_at > now() - interval '30 days'))
  order by p.created_at desc
  limit least(greatest(p_limit,1),100) offset greatest(p_offset,0);
end $$;

revoke execute on function public.admin_search_users(text, text, int, int) from public, anon;
grant  execute on function public.admin_search_users(text, text, int, int) to authenticated, service_role;

commit;
