-- ============================================================================
-- 20261016_015_wallet_foundation.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-21.
-- ----------------------------------------------------------------------------
-- FINDING (brief §22): there is no wallet or credit concept at all, and the brief
-- is explicit that a future refund/credit must NOT be faked and must NOT be
-- represented by editing historical order totals. A proper append-only ledger is
-- the only design that survives an audit.
--
-- SCOPE — this is the FOUNDATION, not the feature
--   * `wallet_accounts` and `wallet_transactions` exist with real constraints.
--   * One gated RPC to credit a customer, so an admin can make someone whole.
--   * **No spending path.** Orders do not read the wallet in this migration.
--     Applying a balance at checkout is a separate release that needs its own
--     pricing verification; wiring it half-way would risk real money.
--
-- BALANCE IS DERIVED, NOT STORED-BY-TRUST
--   `wallet_accounts.balance` is a cached total updated in the same transaction
--   as the ledger insert, guarded by a CHECK that it cannot go negative. The
--   ledger is authoritative: the account row is a readable convenience. This is
--   the only shape that keeps "show me the customer's balance" a single-row read
--   while the history stays immutable.
-- ============================================================================

begin;

-- 1. One account per customer --------------------------------------------
create table if not exists public.wallet_accounts (
  customer_id  uuid primary key references auth.users(id) on delete cascade,
  balance      numeric(12,2) not null default 0 check (balance >= 0),
  updated_at   timestamptz  not null default now()
);

alter table public.wallet_accounts enable row level security;

-- A customer may read their own account only. No client write policies at all:
-- every mutation goes through the RPC below, which owns its own authorization.
drop policy if exists wallet_accounts_select_own on public.wallet_accounts;
create policy wallet_accounts_select_own on public.wallet_accounts
  for select to authenticated
  using ( customer_id = (select auth.uid()) );

-- 2. Append-only ledger ---------------------------------------------------
create table if not exists public.wallet_transactions (
  id           uuid primary key default gen_random_uuid(),
  customer_id  uuid not null references auth.users(id) on delete cascade,
  amount       numeric(12,2) not null check (amount > 0),
  direction    text not null check (direction in ('CREDIT','DEBIT')),
  reason       text not null,
  source       text not null check (source in ('ADMIN_COMPENSATION','REFUND','ORDER_PAYMENT','REVERSAL')),
  order_id     uuid references public.orders(id) on delete set null,
  actor_id     uuid references auth.users(id) on delete set null,
  created_at   timestamptz not null default now()
);

create index if not exists wallet_transactions_customer_idx
  on public.wallet_transactions (customer_id, created_at desc);
create index if not exists wallet_transactions_order_idx
  on public.wallet_transactions (order_id)
  where order_id is not null;

alter table public.wallet_transactions enable row level security;

drop policy if exists wallet_transactions_select_own on public.wallet_transactions;
create policy wallet_transactions_select_own on public.wallet_transactions
  for select to authenticated
  using ( customer_id = (select auth.uid()) );

-- 3. Internal notes on profiles (brief §18), hidden from customers ----------
alter table public.profiles
  add column if not exists internal_notes text;

-- 4. The credit RPC -------------------------------------------------------
-- Gated on `private.can_manage_store()` (OWNER/ADMIN/MANAGER). It refuses to
-- drive a balance negative, and it is the ONLY writer of both tables, so the
-- ledger and the cached balance can never disagree.
create or replace function public.admin_credit_wallet(
  p_customer_id uuid,
  p_amount      numeric,
  p_reason      text,
  p_source      text default 'ADMIN_COMPENSATION',
  p_order_id    uuid default null
)
returns public.wallet_accounts
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_account public.wallet_accounts;
  v_reason  text := nullif(trim(coalesce(p_reason, '')), '');
begin
  if not private.can_manage_store() then
    raise exception 'not authorized' using errcode = '42501';
  end if;

  if p_amount is null or p_amount <= 0 then
    raise exception 'amount must be greater than zero' using errcode = '22023';
  end if;

  if v_reason is null then
    raise exception 'a reason is required when crediting a wallet' using errcode = '22023';
  end if;

  if p_source not in ('ADMIN_COMPENSATION','REFUND','ORDER_PAYMENT','REVERSAL') then
    raise exception 'unknown credit source' using errcode = '22023';
  end if;

  -- A credit can only ever reference an order that exists, and only if the
  -- order belongs to the customer being credited.
  if p_order_id is not null then
    if not exists (select 1 from public.orders o where o.id = p_order_id and o.user_id = p_customer_id) then
      raise exception 'order does not belong to this customer' using errcode = '22023';
    end if;
  end if;

  insert into public.wallet_accounts (customer_id, balance)
  values (p_customer_id, 0)
  on conflict (customer_id) do nothing;

  insert into public.wallet_transactions
    (customer_id, amount, direction, reason, source, order_id, actor_id)
  values
    (p_customer_id, p_amount, 'CREDIT', v_reason, p_source, p_order_id, (select auth.uid()));

  -- Recompute from the ledger rather than trusting `p_amount` arithmetic, so a
  -- concurrent adjustment cannot be lost.
  update public.wallet_accounts wa
     set balance = (
           select coalesce(sum(case when t.direction = 'CREDIT' then t.amount else -t.amount end), 0)
             from public.wallet_transactions t
            where t.customer_id = wa.customer_id
         ),
         updated_at = now()
   where wa.customer_id = p_customer_id
   returning * into v_account;

  -- The CHECK constraint is the last line of defence; this makes the intent
  -- explicit and the error message honest.
  if v_account.balance < 0 then
    raise exception 'credit would make the balance negative' using errcode = '23514';
  end if;

  insert into public.audit_logs (actor_id, actor_role, action, entity_type, entity_id, after, reason)
  values ((select auth.uid()), private.role_of(), 'credit_wallet', 'customer', p_customer_id::text,
          jsonb_build_object('amount', p_amount, 'source', p_source, 'order_id', p_order_id), v_reason);

  return v_account;
end $$;

revoke execute on function public.admin_credit_wallet(uuid, numeric, text, text, uuid) from public, anon;
grant  execute on function public.admin_credit_wallet(uuid, numeric, text, text, uuid) to authenticated, service_role;

commit;
