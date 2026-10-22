-- ============================================================================
-- 20261016_018_index_new_foreign_keys.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- FINDING: the Advisor's `unindexed_foreign_keys` lint flagged two foreign keys
-- that migrations 014 and 015 introduced:
--   * public.offers.created_by            (offers_created_by_fkey)
--   * public.wallet_transactions.actor_id (wallet_transactions_actor_id_fkey)
--
-- WHY IT MATTERS: an unindexed FK makes the *referenced* side's DELETE slow (it
-- must scan the child table to check for dependents), and any "everything this
-- actor did" query a sequential scan. Both are cheap to fix now and awkward later.
--
-- These are partial indexes where the column is usually null: most offers are
-- system-created and most wallet rows are system-sourced, so indexing the nulls
-- would be dead weight.
-- ============================================================================

begin;

create index if not exists offers_created_by_idx
  on public.offers (created_by)
  where created_by is not null;

create index if not exists wallet_transactions_actor_idx
  on public.wallet_transactions (actor_id)
  where actor_id is not null;

commit;
