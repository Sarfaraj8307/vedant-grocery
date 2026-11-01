-- =============================================================================
-- 20261101_002_fix_support_insert_defaults.sql
-- =============================================================================
-- "We could not send that message (42501)" on Health & Support.
--
-- Three columns are NOT NULL with no default, but the web client inserts
-- without them — it sends only `subject`, `description`, `order_id`:
--
--   public.complaints.user_id            NOT NULL, no default
--   public.complaints.type               NOT NULL, no default
--   public.complaint_messages.author_id  NOT NULL, no default
--
-- `complaints_insert` requires `auth.uid() = user_id`, so a NULL user_id fails
-- the WITH CHECK first and the customer sees 42501 ("new row violates row-level
-- security policy") rather than a NOT NULL error. Reproduced exactly that way
-- against the live database before changing anything.
--
-- Deriving these from the session is the correct fix, not teaching every client
-- to send them: the value is never the client's to choose, and the policies
-- already re-check it. `type` has no counterpart in the form, so it defaults to
-- 'OTHER' — the enum's own "unspecified" member — rather than inventing a
-- category the customer never picked.
--
-- The second half closes an impersonation hole: `complaint_messages_insert`
-- checked only that the caller owns the parent thread, so a customer could post
-- with `author_role = 'ADMIN'` and appear to be the shop. `services/
-- support-service.ts` claims this was already prevented; it was not.
-- =============================================================================

begin;

-- The customer is always the caller. A client that *does* send user_id still has
-- to match `auth.uid()` or the policy rejects it, so this adds no privilege.
alter table public.complaints
    alter column user_id set default auth.uid();

alter table public.complaints
    alter column type set default 'OTHER'::public.complaint_type;

alter table public.complaint_messages
    alter column author_id set default auth.uid();

comment on column public.complaints.type is
    'Complaint category. Defaults to OTHER because the web support form does not ask for one.';

-- Replaces the ownership-only check with one that also pins the author role:
-- a customer writes CUSTOMER on their own thread, staff write ADMIN.
drop policy if exists complaint_messages_insert on public.complaint_messages;
create policy complaint_messages_insert on public.complaint_messages
    for insert to authenticated
    with check (
        ( author_role = 'ADMIN' and private.can_manage_complaints() )
        or (
            author_role = 'CUSTOMER'
            and exists (
                select 1
                  from public.complaints c
                 where c.id = complaint_messages.complaint_id
                   and c.user_id = (select auth.uid())
            )
        )
    );

commit;
