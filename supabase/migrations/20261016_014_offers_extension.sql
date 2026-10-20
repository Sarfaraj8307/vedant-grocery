-- ============================================================================
-- 20261016_014_offers_extension.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-21.
-- ----------------------------------------------------------------------------
-- FINDING (brief §20/§21): the `offers` table is product-scoped only —
--   `id, product_id, title, price, discount_percent, starts_at, ends_at,
--    is_active, created_at, updated_at`.
-- It cannot express a minimum order, a discount cap, a usage limit, a
-- per-customer limit, a customer-specific offer, or a segment offer. All 10
-- existing offers are product offers and MUST keep working untouched.
--
-- DESIGN
--   * Additive only: nothing dropped or renamed, `product_id` stays nullable so
--     an order-scoped or customer-scoped offer can have none.
--   * A `scope` discriminator decides which target column must be present,
--     enforced by a CHECK constraint rather than by convention in application
--     code — that way a bad row cannot be written by any client.
--   * `place_order` is NOT touched. It already applies product offers through
--     the existing `discount_percent` path, and changing how pricing works
--     without a proven reason would risk real customer money. The new columns
--     are honoured by a future release once a custom-offer engine is built;
--     this migration makes the schema capable of it.
--   * Existing rows get `scope='PRODUCT'` and the target columns stay null, so
--     behaviour is bit-identical for the current catalogue.
-- ============================================================================

begin;

-- 1. Discriminator + targeting ---------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_type t join pg_enum e on e.enumtypid = t.oid
    where t.typname = 'offer_scope'
  ) then
    create type public.offer_scope as enum ('PRODUCT', 'CATEGORY', 'ORDER', 'CUSTOMER', 'SEGMENT');
  end if;
end $$;

alter table public.offers
  add column if not exists scope        public.offer_scope not null default 'PRODUCT',
  add column if not exists category_id  uuid references public.categories(id) on delete set null,
  add column if not exists customer_id  uuid references auth.users(id) on delete cascade,
  add column if not exists segment      text,
  -- Money and limits. Nullable, because a product offer has none of these.
  add column if not exists min_order_amount      numeric(12,2),
  add column if not exists max_discount_amount   numeric(12,2),
  add column if not exists usage_limit           integer,
  add column if not exists per_customer_limit    integer,
  -- Audit + presentation
  add column if not exists reason     text,
  add column if not exists created_by uuid references auth.users(id) on delete set null;

-- `product_id` was NOT NULL; it must become nullable so an order-scoped offer
-- can have none. Existing product offers keep their id, so this is safe.
alter table public.offers
  alter column product_id drop not null;

-- 2. Integrity ------------------------------------------------------------
-- Scope decides which target column is required. Enforced in the database so no
-- client can write an inconsistent offer.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'offers_target_matches_scope') then
    alter table public.offers add constraint offers_target_matches_scope check (
      case scope
        when 'PRODUCT'  then product_id is not null and category_id is null and customer_id is null and segment is null
        when 'CATEGORY' then category_id is not null and product_id is null and customer_id is null and segment is null
        when 'ORDER'    then product_id is null and category_id is null and customer_id is null and segment is null
        when 'CUSTOMER' then customer_id is not null and product_id is null and category_id is null and segment is null
        when 'SEGMENT'  then segment is not null and product_id is null and category_id is null and customer_id is null
        else false
      end
    );
  end if;

  if not exists (select 1 from pg_constraint where conname = 'offers_discount_percent_range') then
    alter table public.offers add constraint offers_discount_percent_range check (
      discount_percent >= 0 and discount_percent <= 100
    );
  end if;

  if not exists (select 1 from pg_constraint where conname = 'offers_min_order_non_negative') then
    alter table public.offers add constraint offers_min_order_non_negative check (
      min_order_amount is null or min_order_amount >= 0
    );
  end if;

  if not exists (select 1 from pg_constraint where conname = 'offers_max_discount_non_negative') then
    alter table public.offers add constraint offers_max_discount_non_negative check (
      max_discount_amount is null or max_discount_amount >= 0
    );
  end if;

  -- A cap below zero is a bug; and a discount cap cannot exceed the min order
  -- it applies to, which would make the offer nonsensical.
  if not exists (select 1 from pg_constraint where conname = 'offers_cap_sane') then
    alter table public.offers add constraint offers_cap_sane check (
      max_discount_amount is null
      or min_order_amount is null
      or max_discount_amount <= min_order_amount
    );
  end if;

  if not exists (select 1 from pg_constraint where conname = 'offers_usage_limits_positive') then
    alter table public.offers add constraint offers_usage_limits_positive check (
      (usage_limit is null or usage_limit > 0)
      and (per_customer_limit is null or per_customer_limit > 0)
      -- A per-customer limit cannot exceed the global one.
      and (per_customer_limit is null or usage_limit is null or per_customer_limit <= usage_limit)
    );
  end if;

  -- The window must be ordered.
  if not exists (select 1 from pg_constraint where conname = 'offers_window_ordered') then
    alter table public.offers add constraint offers_window_ordered check (
      starts_at is null or ends_at is null or starts_at < ends_at
    );
  end if;
end $$;

-- 3. Indexes for what a checkout actually looks up -------------------------
-- The live query is: active offers whose window contains now(), for a given
-- product/category/customer/segment.
create index if not exists offers_active_scope_idx
  on public.offers (scope, is_active)
  where is_active;

create index if not exists offers_customer_idx
  on public.offers (customer_id)
  where is_active and customer_id is not null;

create index if not exists offers_segment_idx
  on public.offers (segment)
  where is_active and segment is not null;

create index if not exists offers_category_idx
  on public.offers (category_id)
  where is_active and category_id is not null;

-- 4. RLS: unchanged surface ------------------------------------------------
-- `offers` already has a public SELECT and a `can_manage_catalogue` write policy.
-- The new columns are covered by the same table-level policy. No new policy.

-- 5. Backfill -------------------------------------------------------------
-- Any pre-existing row that somehow has a null scope impossible (the column has a
-- NOT NULL default), but a row with a product_id and a stray customer_id would
-- violate the new constraint. Surface rather than silently mutate.
do $$
declare bad int;
begin
  select count(*) into bad from public.offers
   where scope = 'PRODUCT' and (category_id is not null or customer_id is not null or segment is not null);
  if bad > 0 then
    raise exception '% existing offer row(s) carry a non-product target; refusing to guess', bad;
  end if;
end $$;

commit;
