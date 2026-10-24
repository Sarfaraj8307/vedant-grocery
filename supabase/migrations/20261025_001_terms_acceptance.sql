-- =============================================================================
-- 20261025_001 — Terms acceptance (consent) record
-- =============================================================================
-- WHY THIS TABLE EXISTS
--
-- The signup form already has an "I agree to the terms and privacy policy"
-- checkbox, but ticking it proved nothing: the tick was never persisted, so
-- there was no evidence that any given customer had actually accepted the
-- terms before their data was stored.
--
-- This table is that evidence. One row per (customer, terms version), written
-- the moment they accept, never updated and never deleted by the customer.
--
-- DESIGN DECISIONS
--
-- • A row per version, not a boolean on `profiles`. When the terms change the
--   customer re-consents and a second row is added; the earlier acceptance is
--   still there with its own timestamp. Overwriting a boolean would destroy
--   exactly the record that matters.
-- • `accepted_at` defaults to now() **on the server**, so the timestamp cannot
--   be back-dated by the client.
-- • No UPDATE and no DELETE policy, and the privileges are revoked. Consent is
--   evidence: a customer who could edit the row could rewrite history. Erasure
--   requests are handled by deleting the account (the FK cascades), which is a
--   different, deliberate act.
-- • `source` records *where* consent was given — at signup, on the Google
--   return, or from the post-login prompt. Useful when someone asks "when did
--   this customer agree, and how".
-- =============================================================================

begin;

create table if not exists public.terms_acceptances (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  terms_version text not null,
  accepted_at timestamptz not null default now(),
  source text not null default 'web'
    constraint terms_acceptances_source_check
    check (source in ('signup', 'google', 'login', 'account')),
  constraint terms_acceptances_user_version_key unique (user_id, terms_version)
);

comment on table public.terms_acceptances is
  'Immutable audit trail of terms acceptance. One row per customer per terms version.';

comment on column public.terms_acceptances.terms_version is
  'Must match TERMS_VERSION in src/lib/terms.ts. A new version requires re-consent.';

-- The gate asks "has this customer accepted the current version?" on every
-- authenticated render, so this lookup must stay cheap.
create index if not exists terms_acceptances_user_version_idx
  on public.terms_acceptances (user_id, terms_version);

create index if not exists terms_acceptances_user_accepted_idx
  on public.terms_acceptances (user_id, accepted_at desc);

alter table public.terms_acceptances enable row level security;

-- A customer may read their own acceptance history (the account page shows the
-- date they accepted) and add a row for themselves. Nothing else.
drop policy if exists terms_acceptances_select_own on public.terms_acceptances;
create policy terms_acceptances_select_own on public.terms_acceptances
  for select to authenticated
  using ( (select auth.uid()) = user_id );

drop policy if exists terms_acceptances_insert_own on public.terms_acceptances;
create policy terms_acceptances_insert_own on public.terms_acceptances
  for insert to authenticated
  with check ( (select auth.uid()) = user_id );

-- Table-level privileges. Supabase's default privileges grant everything on new
-- public tables to anon and authenticated, so these are stated explicitly:
-- anon gets nothing, authenticated gets exactly SELECT + INSERT.
revoke all on public.terms_acceptances from anon;
revoke all on public.terms_acceptances from authenticated;
grant select, insert on public.terms_acceptances to authenticated;

commit;
