-- ============================================================================
-- 20261022_002_reporting_transparency.sql
-- Project: Vedant Fruits and Vegetables
-- ----------------------------------------------------------------------------
-- GOALS:
--   1. admin_sales_range(p_from date, p_to date) -- the same breakdown as
--      admin_sales_today but for any date range, so reporting is not limited
--      to "today's numbers". Returns one row per day in the range.
--   2. admin_order_history(p_from date, p_to date, p_limit int, p_offset int)
--      -- detailed order history with full transparency: order id, status,
--      placed_at, final_total, subtotal, delivery_fee, adjustment_amount,
--      item_count, customer name. For auditing and reconciliation.
--   Both gated on private.can_manage_users() (same as admin_sales_today).
-- ============================================================================

begin;

-- 1. admin_sales_range(p_from, p_to) -----------------------------------------
--    Returns one row per day in [p_from, p_to], inclusive. Same columns as
--    admin_sales_today so the Analytics screen can use the same DTO.
CREATE OR REPLACE FUNCTION public.admin_sales_range(
  p_from date DEFAULT null,
  p_to date DEFAULT null
)
RETURNS TABLE (
  order_date date,
  orders bigint,
  gross numeric,
  discounts numeric,
  adjustments numeric,
  refunds numeric,
  delivery_revenue numeric,
  net numeric,
  aov numeric
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
declare
  v_from date := coalesce(p_from, ((now() at time zone 'Asia/Kolkata')::date - 6));
  v_to   date := coalesce(p_to, (now() at time zone 'Asia/Kolkata')::date);
begin
  if not private.can_manage_users() then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  -- Clamp the range to 90 days to keep the query bounded.
  if v_to - v_from > 90 then
    v_to := v_from + 90;
  end if;

  RETURN QUERY
  SELECT
    -- generate_series over `date` yields `timestamp`, so the cast is required:
    -- without it the row type does not match the declared `order_date date`.
    gen_date.day::date,
    COALESCE(orders_summary.orders, 0)::bigint,
    COALESCE(orders_summary.gross, 0),
    COALESCE(orders_summary.discounts, 0),
    COALESCE(orders_summary.adjustments, 0),
    COALESCE(refunds_summary.refunds, 0),
    COALESCE(orders_summary.delivery_revenue, 0),
    COALESCE(orders_summary.net, 0),
    CASE
      WHEN COALESCE(orders_summary.orders, 0) > 0
      THEN COALESCE(orders_summary.net, 0) / orders_summary.orders
      ELSE 0
    END
  FROM generate_series(v_from, v_to, interval '1 day') AS gen_date(day)
  LEFT JOIN (
    SELECT
      (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date AS order_date,
      count(*) AS orders,
      COALESCE(sum(o.subtotal), 0) AS gross,
      COALESCE(sum(o.subtotal - o.total + o.delivery_fee), 0) AS discounts,
      COALESCE(sum(o.adjustment_amount), 0) AS adjustments,
      COALESCE(sum(o.delivery_fee), 0) AS delivery_revenue,
      COALESCE(sum(o.final_total), 0) AS net
    FROM public.orders o
    WHERE o.status <> 'CANCELLED'
      AND (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN v_from AND v_to
    GROUP BY (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date
  ) orders_summary ON orders_summary.order_date = gen_date.day::date
  LEFT JOIN (
    SELECT
      (o2.placed_at AT TIME ZONE 'Asia/Kolkata')::date AS order_date,
      COALESCE(sum(o2.total), 0) AS refunds
    FROM public.orders o2
    WHERE o2.status = 'CANCELLED'
      AND (o2.placed_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN v_from AND v_to
    GROUP BY (o2.placed_at AT TIME ZONE 'Asia/Kolkata')::date
  ) refunds_summary ON refunds_summary.order_date = gen_date.day::date
  ORDER BY gen_date.day::date;
END $$;

REVOKE EXECUTE ON FUNCTION public.admin_sales_range(date, date) FROM public, anon;
GRANT  EXECUTE ON FUNCTION public.admin_sales_range(date, date) TO authenticated, service_role;


-- 2. admin_order_history(p_from, p_to, p_limit, p_offset) --------------------
--    Detailed order list with full transparency for reporting/auditing.
--    Returns one row per order: id, status, placed_at, final_total, subtotal,
--    delivery_fee, adjustment_amount, item_count, customer_name.
CREATE OR REPLACE FUNCTION public.admin_order_history(
  p_from date DEFAULT null,
  p_to date DEFAULT null,
  p_limit int DEFAULT 50,
  p_offset int DEFAULT 0
)
RETURNS TABLE (
  order_id uuid,
  order_status public.order_status,
  placed_at timestamptz,
  final_total numeric,
  subtotal numeric,
  delivery_fee numeric,
  adjustment_amount numeric,
  item_count bigint,
  customer_name text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
declare
  v_from date := coalesce(p_from, ((now() at time zone 'Asia/Kolkata')::date - 6));
  v_to   date := coalesce(p_to, (now() at time zone 'Asia/Kolkata')::date);
  v_limit int  := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset int := greatest(coalesce(p_offset, 0), 0);
begin
  if not private.can_manage_users() then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  RETURN QUERY
  SELECT
    o.id,
    o.status,
    o.placed_at,
    o.final_total,
    o.subtotal,
    o.delivery_fee,
    o.adjustment_amount,
    COALESCE(item_counts.ic, 0),
    COALESCE(p.full_name, '')::text
  FROM public.orders o
  LEFT JOIN (
    -- `order_items` is the placed-order line table. `cart_items` is the
    -- pre-checkout cart, which `place_order` empties — counting it would report
    -- zero items for every completed order.
    SELECT oi.order_id, count(*)::bigint AS ic
    FROM public.order_items oi
    GROUP BY oi.order_id
  ) item_counts ON item_counts.order_id = o.id
  LEFT JOIN public.profiles p ON p.id = o.user_id
  WHERE (o.placed_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN v_from AND v_to
  ORDER BY o.placed_at DESC
  LIMIT v_limit OFFSET v_offset;
END $$;

REVOKE EXECUTE ON FUNCTION public.admin_order_history(date, date, int, int) FROM public, anon;
GRANT  EXECUTE ON FUNCTION public.admin_order_history(date, date, int, int) TO authenticated, service_role;

commit;
