-- ============================================================================
-- 20261016_019_relax_address_label.sql
-- Project: hyibaljxicojfoxbihdn (Grocery Order App)
-- Author: platform remediation (owner-authorised)
-- Written 2026-10-22.
-- ----------------------------------------------------------------------------
-- FINDING (user-reported): adding an address failed with
--   "We could not save that address. Please try again."
--
-- ROOT CAUSE — two sources of truth that disagreed:
--   * the database enforced  CHECK (label = ANY ('Home','Work','Other'))
--   * the application validated label as any trimmed string, 1..40 chars
--     (`src/lib/validation.ts` → addressSchema.label)
--   * and the form's own placeholder literally read "Home, Office…"
--
-- So a customer who followed the UI's suggestion and typed **Office** hit
-- `23514 addresses_label_check`. Reproduced live: label='Office' → 23514,
-- label='Work' → inserted. The generic toast then hid the real reason.
--
-- FIX: drop the enumeration. A grocery customer legitimately wants "Office",
-- "Parents", "Hostel", "Shop" — the three-value list was arbitrary. Replace it
-- with what actually matters: non-empty and short enough to render.
--
-- This is PERMISSIVE: every existing row still satisfies the new constraint, so
-- no data is touched and no write can newly fail that previously succeeded.
-- ============================================================================

begin;

-- Drop the enum-style constraint if it exists (name confirmed from the live
-- constraint violation message).
alter table public.addresses
  drop constraint if exists addresses_label_check;

-- Replace with a shape constraint that matches the app's own validation.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'addresses_label_shape') then
    alter table public.addresses add constraint addresses_label_shape check (
      length(btrim(label)) between 1 and 40
    );
  end if;
end $$;

commit;
