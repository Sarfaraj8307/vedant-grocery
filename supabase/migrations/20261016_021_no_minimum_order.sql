-- ============================================================================
-- 20261016_021_no_minimum_order.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- OWNER DECISION: customers may order at any amount. There is no minimum basket.
--
-- The engine (`place_order(uuid,uuid,uuid,date,jsonb)`) raises `BELOW_MIN_ORDER`
-- when the basket subtotal is under `store_settings.min_order_amount`, which
-- `place_order_app` surfaces as `22023 'below minimum order'`. It was set to
-- ₹100, so a perfectly valid small order was refused at checkout.
--
-- Reproduced live before this change:
--   cart line of 2 units → place_order → 400 `22023 below minimum order`
--   after setting the value to 0 → 200 with an order id
--
-- Setting it to 0 is the correct fix rather than deleting the check: the
-- mechanism stays available (an owner may want a minimum again seasonally), and
-- the engine already treats "subtotal < 0" as unsatisfiable, so 0 disables it
-- without touching pricing logic or any function definition.
--
-- DELIVERY CHARGES ARE UNAFFECTED and remain owner-editable from the Android
-- app (Store settings → Delivery fee / Free delivery threshold). That path was
-- verified: an ADMIN PATCH on `store_settings.delivery_fee` returns 200 and the
-- new value is used by the next `place_order`.
-- ============================================================================

begin;

update public.store_settings
   set min_order_amount = 0
 where id = 1
   and min_order_amount <> 0;

commit;
