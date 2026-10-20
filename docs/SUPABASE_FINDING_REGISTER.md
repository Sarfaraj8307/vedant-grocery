# Supabase Finding Register - project `hyibaljxicojfoxbihdn`

> Source: three Supabase Advisor exports supplied by the owner (Security, RLS/performance, Indexing), copied verbatim to `docs/supabase-lint/`.
> Generated: 2026-10-16. Total findings: 98 - every one is tracked below and none may be closed without a recorded verification.

## Summary by lint

| Lint | Count | Level | Severity | Category |
|---|---|---|---|---|
| `multiple_permissive_policies` | 26 | WARN | P2 | ["PERFORMANCE"] |
| `unused_index` | 21 | INFO | P3 | ["PERFORMANCE"] |
| `authenticated_security_definer_function_executable` | 18 | WARN | P1 | ["SECURITY"] |
| `auth_rls_initplan` | 18 | WARN | P2 | ["PERFORMANCE"] |
| `anon_security_definer_function_executable` | 7 | WARN | P0 | ["SECURITY"] |
| `unindexed_foreign_keys` | 5 | INFO | P1 | ["PERFORMANCE"] |
| `auth_leaked_password_protection` | 1 | WARN | P1 | ["SECURITY"] |
| `rls_policy_always_true` | 1 | WARN | P0 | ["SECURITY"] |
| `public_bucket_allows_listing` | 1 | WARN | P1 | ["SECURITY"] |

## anon_security_definer_function_executable - 7 finding(s) - severity P0

| # | Object | Detail |
|---|---|---|
| 1 | `fn check_username_available(p_username text)` | Function `public.check_username_available(p_username text)` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/check_username_available`. Revoke `EXECUTE` or switch it to `SECURITY INVO... |
| 2 | `fn handle_new_user()` | Function `public.handle_new_user()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/handle_new_user`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not intentional. |
| 3 | `fn notify_complaint_event()` | Function `public.notify_complaint_event()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/notify_complaint_event`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not... |
| 4 | `fn notify_order_event()` | Function `public.notify_order_event()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/notify_order_event`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not intenti... |
| 5 | `fn record_order_status_history()` | Function `public.record_order_status_history()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/record_order_status_history`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if t... |
| 6 | `fn sync_address_pincode()` | Function `public.sync_address_pincode()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/sync_address_pincode`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not int... |
| 7 | `fn sync_profile_phone()` | Function `public.sync_profile_phone()` can be executed by the `anon` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/sync_profile_phone`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not intenti... |

## auth_leaked_password_protection - 1 finding(s) - severity P1

| # | Object | Detail |
|---|---|---|
| 1 | `` | Supabase Auth prevents the use of compromised passwords by checking against HaveIBeenPwned.org. Enable this feature to enhance security. |

## auth_rls_initplan - 18 finding(s) - severity P2

| # | Object | Detail |
|---|---|---|
| 1 | `profiles` | Table \`public.profiles\` has a row level security policy \`profiles_insert_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the is... |
| 2 | `profiles` | Table \`public.profiles\` has a row level security policy \`profiles_select_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the is... |
| 3 | `profiles` | Table \`public.profiles\` has a row level security policy \`profiles_update_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the is... |
| 4 | `complaints` | Table \`public.complaints\` has a row level security policy \`complaints_owner_all\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the... |
| 5 | `complaint_attachments` | Table \`public.complaint_attachments\` has a row level security policy \`complaint_attachments_owner_all\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance... |
| 6 | `notification_outbox` | Table \`public.notification_outbox\` has a row level security policy \`notification_outbox_select_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at... |
| 7 | `addresses` | Table \`public.addresses\` has a row level security policy \`addresses_owner_all\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the i... |
| 8 | `cart_items` | Table \`public.cart_items\` has a row level security policy \`cart_items_owner_all\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the... |
| 9 | `orders` | Table \`public.orders\` has a row level security policy \`orders_insert_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the issue ... |
| 10 | `orders` | Table \`public.orders\` has a row level security policy \`orders_select_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the issue ... |
| 11 | `orders` | Table \`public.orders\` has a row level security policy \`orders_update_own\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve the issue ... |
| 12 | `order_status_history` | Table \`public.order_status_history\` has a row level security policy \`order_status_history_owner\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at sc... |
| 13 | `order_items` | Table \`public.order_items\` has a row level security policy \`order_items_insert_via_order\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Re... |
| 14 | `order_items` | Table \`public.order_items\` has a row level security policy \`order_items_owner_all\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Resolve t... |
| 15 | `order_items` | Table \`public.order_items\` has a row level security policy \`order_items_select_via_order\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Re... |
| 16 | `complaint_messages` | Table \`public.complaint_messages\` has a row level security policy \`complaint_messages_owner\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale.... |
| 17 | `notifications` | Table \`public.notifications\` has a row level security policy \`notifications_owner_select\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Re... |
| 18 | `notifications` | Table \`public.notifications\` has a row level security policy \`notifications_owner_update\` that re-evaluates current_setting() or auth.<function>() for each row. This produces suboptimal query performance at scale. Re... |

## authenticated_security_definer_function_executable - 18 finding(s) - severity P1

| # | Object | Detail |
|---|---|---|
| 1 | `fn admin_grant_admin(p_email text, p_note text)` | Function `public.admin_grant_admin(p_email text, p_note text)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_grant_admin`. Revoke `EXECUTE` or switch it to `SECURITY... |
| 2 | `fn admin_list_admins()` | Function `public.admin_list_admins()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_list_admins`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not ... |
| 3 | `fn admin_reject_order_item(p_item_id uuid, p_reason text)` | Function `public.admin_reject_order_item(p_item_id uuid, p_reason text)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_reject_order_item`. Revoke `EXECUTE` or switch... |
| 4 | `fn admin_restore_order_item(p_item_id uuid)` | Function `public.admin_restore_order_item(p_item_id uuid)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_restore_order_item`. Revoke `EXECUTE` or switch it to `SECUR... |
| 5 | `fn admin_revoke_admin(p_user_id uuid)` | Function `public.admin_revoke_admin(p_user_id uuid)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_revoke_admin`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER`... |
| 6 | `fn admin_slot_load(p_from date, p_days integer)` | Function `public.admin_slot_load(p_from date, p_days integer)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_slot_load`. Revoke `EXECUTE` or switch it to `SECURITY I... |
| 7 | `fn admin_update_order_status(p_order_id uuid, p_status public.order_status, p_reason text)` | Function `public.admin_update_order_status(p_order_id uuid, p_status public.order_status, p_reason text)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/admin_update_order_... |
| 8 | `fn check_username_available(p_username text)` | Function `public.check_username_available(p_username text)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/check_username_available`. Revoke `EXECUTE` or switch it to `SECU... |
| 9 | `fn ensure_delivery_slots(p_days integer)` | Function `public.ensure_delivery_slots(p_days integer)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/ensure_delivery_slots`. Revoke `EXECUTE` or switch it to `SECURITY IN... |
| 10 | `fn handle_new_user()` | Function `public.handle_new_user()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/handle_new_user`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not inte... |
| 11 | `fn is_admin()` | Function `public.is_admin()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/is_admin`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is not intentional. |
| 12 | `fn notify_complaint_event()` | Function `public.notify_complaint_event()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/notify_complaint_event`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if th... |
| 13 | `fn notify_order_event()` | Function `public.notify_order_event()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/notify_order_event`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is no... |
| 14 | `fn place_order(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)` | Function `public.place_order(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/place_order`. Revoke `EXECUTE` o... |
| 15 | `fn place_order_app(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)` | Function `public.place_order_app(p_request_id text, p_address_id uuid, p_delivery_slot_id uuid)` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/place_order_app`. Revoke `EX... |
| 16 | `fn record_order_status_history()` | Function `public.record_order_status_history()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/record_order_status_history`. Revoke `EXECUTE` or switch it to `SECURITY INVO... |
| 17 | `fn sync_address_pincode()` | Function `public.sync_address_pincode()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/sync_address_pincode`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that i... |
| 18 | `fn sync_profile_phone()` | Function `public.sync_profile_phone()` can be executed by the `authenticated` role as a `SECURITY DEFINER` function via `/rest/v1/rpc/sync_profile_phone`. Revoke `EXECUTE` or switch it to `SECURITY INVOKER` if that is no... |

## multiple_permissive_policies - 26 finding(s) - severity P2

| # | Object | Detail |
|---|---|---|
| 1 | `categories` | Table \`public.categories\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{categories_admin_write,categories_read_public}\` |
| 2 | `complaint_attachments` | Table \`public.complaint_attachments\` has multiple permissive policies for role \`authenticated\` for action \`DELETE\`. Policies include \`{complaint_attachments_admin_all,complaint_attachments_owner_all}\` |
| 3 | `complaint_attachments` | Table \`public.complaint_attachments\` has multiple permissive policies for role \`authenticated\` for action \`INSERT\`. Policies include \`{complaint_attachments_admin_all,complaint_attachments_owner_all}\` |
| 4 | `complaint_attachments` | Table \`public.complaint_attachments\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{complaint_attachments_admin_all,complaint_attachments_owner_all}\` |
| 5 | `complaint_attachments` | Table \`public.complaint_attachments\` has multiple permissive policies for role \`authenticated\` for action \`UPDATE\`. Policies include \`{complaint_attachments_admin_all,complaint_attachments_owner_all}\` |
| 6 | `complaint_messages` | Table \`public.complaint_messages\` has multiple permissive policies for role \`authenticated\` for action \`DELETE\`. Policies include \`{complaint_messages_admin_all,complaint_messages_owner}\` |
| 7 | `complaint_messages` | Table \`public.complaint_messages\` has multiple permissive policies for role \`authenticated\` for action \`INSERT\`. Policies include \`{complaint_messages_admin_all,complaint_messages_owner}\` |
| 8 | `complaint_messages` | Table \`public.complaint_messages\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{complaint_messages_admin_all,complaint_messages_owner}\` |
| 9 | `complaint_messages` | Table \`public.complaint_messages\` has multiple permissive policies for role \`authenticated\` for action \`UPDATE\`. Policies include \`{complaint_messages_admin_all,complaint_messages_owner}\` |
| 10 | `complaints` | Table \`public.complaints\` has multiple permissive policies for role \`authenticated\` for action \`DELETE\`. Policies include \`{complaints_admin_all,complaints_owner_all}\` |
| 11 | `complaints` | Table \`public.complaints\` has multiple permissive policies for role \`authenticated\` for action \`INSERT\`. Policies include \`{complaints_admin_all,complaints_owner_all}\` |
| 12 | `complaints` | Table \`public.complaints\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{complaints_admin_all,complaints_owner_all}\` |
| 13 | `complaints` | Table \`public.complaints\` has multiple permissive policies for role \`authenticated\` for action \`UPDATE\`. Policies include \`{complaints_admin_all,complaints_owner_all}\` |
| 14 | `delivery_slots` | Table \`public.delivery_slots\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{delivery_slots_admin_write,delivery_slots_read_public}\` |
| 15 | `notification_outbox` | Table \`public.notification_outbox\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{notification_outbox_admin_read,notification_outbox_select_own}\` |
| 16 | `offers` | Table \`public.offers\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{offers_admin_write,offers_read_public}\` |
| 17 | `order_items` | Table \`public.order_items\` has multiple permissive policies for role \`authenticated\` for action \`INSERT\`. Policies include \`{order_items_insert_via_order,order_items_owner_all}\` |
| 18 | `order_items` | Table \`public.order_items\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{order_items_admin_select,order_items_owner_all,order_items_select_via_order}\` |
| 19 | `order_status_history` | Table \`public.order_status_history\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{order_status_history_admin_select,order_status_history_owner}\` |
| 20 | `orders` | Table \`public.orders\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{orders_admin_select,orders_select_own}\` |
| 21 | `product_weights` | Table \`public.product_weights\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{product_weights_admin_write,product_weights_read_public}\` |
| 22 | `products` | Table \`public.products\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{products_admin_write,products_read_public}\` |
| 23 | `profiles` | Table \`public.profiles\` has multiple permissive policies for role \`authenticated\` for action \`INSERT\`. Policies include \`{profiles_admin_all,profiles_insert_own}\` |
| 24 | `profiles` | Table \`public.profiles\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{profiles_admin_all,profiles_select_own}\` |
| 25 | `profiles` | Table \`public.profiles\` has multiple permissive policies for role \`authenticated\` for action \`UPDATE\`. Policies include \`{profiles_admin_all,profiles_update_own}\` |
| 26 | `store_settings` | Table \`public.store_settings\` has multiple permissive policies for role \`authenticated\` for action \`SELECT\`. Policies include \`{store_settings_admin_write,store_settings_read_public}\` |

## public_bucket_allows_listing - 1 finding(s) - severity P1

| # | Object | Detail |
|---|---|---|
| 1 | `storage.bucket :: product-images` | Public bucket `product-images` has 1 broad SELECT policy on `storage.objects` (product_images_public_read), allowing clients to list all files. Public buckets don't need this for object URL access and it may expose more ... |

## rls_policy_always_true - 1 finding(s) - severity P0

| # | Object | Detail |
|---|---|---|
| 1 | `events :: events_insert_any` | Table `public.events` has an RLS policy `events_insert_any` for `INSERT` that allows unrestricted access (WITH CHECK clause is always true). This effectively bypasses row-level security for anon, authenticated. |

## unindexed_foreign_keys - 5 finding(s) - severity P1

| # | Object | Detail |
|---|---|---|
| 1 | `cart_items :: cart_items_product_id_fkey` | Table \`public.cart_items\` has a foreign key \`cart_items_product_id_fkey\` without a covering index. This can lead to suboptimal query performance. |
| 2 | `cart_items :: cart_items_weight_id_fkey` | Table \`public.cart_items\` has a foreign key \`cart_items_weight_id_fkey\` without a covering index. This can lead to suboptimal query performance. |
| 3 | `complaint_messages :: complaint_messages_author_id_fkey` | Table \`public.complaint_messages\` has a foreign key \`complaint_messages_author_id_fkey\` without a covering index. This can lead to suboptimal query performance. |
| 4 | `orders :: orders_address_id_fkey` | Table \`public.orders\` has a foreign key \`orders_address_id_fkey\` without a covering index. This can lead to suboptimal query performance. |
| 5 | `orders :: orders_delivery_slot_id_fkey` | Table \`public.orders\` has a foreign key \`orders_delivery_slot_id_fkey\` without a covering index. This can lead to suboptimal query performance. |

## unused_index - 21 finding(s) - severity P3

| # | Object | Detail |
|---|---|---|
| 1 | `addresses` | Index \`addresses_user_idx\` on table \`public.addresses\` has not been used |
| 2 | `products` | Index \`products_category_idx\` on table \`public.products\` has not been used |
| 3 | `orders` | Index \`orders_status_idx\` on table \`public.orders\` has not been used |
| 4 | `orders` | Index \`orders_user_idx\` on table \`public.orders\` has not been used |
| 5 | `orders` | Index \`orders_slot_date_idx\` on table \`public.orders\` has not been used |
| 6 | `orders` | Index \`orders_pincode_idx\` on table \`public.orders\` has not been used |
| 7 | `order_items` | Index \`order_items_order_idx\` on table \`public.order_items\` has not been used |
| 8 | `order_items` | Index \`order_items_product_idx\` on table \`public.order_items\` has not been used |
| 9 | `complaints` | Index \`complaints_user_idx\` on table \`public.complaints\` has not been used |
| 10 | `complaints` | Index \`complaints_status_idx\` on table \`public.complaints\` has not been used |
| 11 | `complaint_attachments` | Index \`complaint_attachments_cid_idx\` on table \`public.complaint_attachments\` has not been used |
| 12 | `events` | Index \`events_created_idx\` on table \`public.events\` has not been used |
| 13 | `events` | Index \`events_event_idx\` on table \`public.events\` has not been used |
| 14 | `notification_outbox` | Index \`outbox_unread_idx\` on table \`public.notification_outbox\` has not been used |
| 15 | `offers` | Index \`offers_active_idx\` on table \`public.offers\` has not been used |
| 16 | `complaints` | Index \`complaints_order_id_idx\` on table \`public.complaints\` has not been used |
| 17 | `notification_outbox` | Index \`notification_outbox_user_idx\` on table \`public.notification_outbox\` has not been used |
| 18 | `cart_items` | Index \`cart_items_user_idx\` on table \`public.cart_items\` has not been used |
| 19 | `order_status_history` | Index \`order_status_history_order_idx\` on table \`public.order_status_history\` has not been used |
| 20 | `complaint_messages` | Index \`complaint_messages_complaint_idx\` on table \`public.complaint_messages\` has not been used |
| 21 | `notifications` | Index \`notifications_user_idx\` on table \`public.notifications\` has not been used |

## Status legend

| Status | Meaning |
|---|---|
| OPEN | not yet addressed |
| FIXED | remediation applied and verified against the live project |
| ACCEPTED | deliberate, documented reason to keep as-is |

## Remediation status

### T01 — P0/P1 security hardening
Migration `supabase/migrations/20261016_001_p0_security_hardening.sql` — applied + verified 2026-10-16.

| Lint | Object | Status |
|---|---|---|
| `rls_policy_always_true` | `public.events :: events_insert_any` | **FIXED** — policy dropped; anon/authenticated inserts now raise 42501 |
| `public_bucket_allows_listing` | `storage.objects :: product_images_public_read` | **FIXED** — policy dropped; public object URLs unaffected |
| `anon_security_definer_function_executable` | 6 of 7 trigger functions | **FIXED** — EXECUTE revoked. `check_username_available` deliberately kept (needs `authenticated`; deferred to T05) |
| `authenticated_security_definer_function_executable` | 6 trigger functions | **FIXED** — EXECUTE revoked |
| `auth_leaked_password_protection` | project auth config | **ACCEPTED** — Free plan limitation; `PATCH /config/auth` returns HTTP 402 ("available on Pro Plans and up"); no SQL path; revisit on upgrade |

### T02 — Roles + account status
Migration `supabase/migrations/20261016_002_roles_and_account_status.sql` — applied + verified 2026-10-16.

* **FIXED:** `authenticated_security_definer_function_executable` for `admin_grant_admin`, `admin_revoke_admin`, `admin_list_admins` — EXECUTE revoked; superseded by `owner_grant_role` / `owner_revoke_role`.
* **ADDED (intentional, gated):** `owner_grant_role`, `owner_revoke_role`, `admin_set_account_status` — new `authenticated_security_definer_function_executable` entries; gated server-side (OWNER / `can_manage_users()`) and reachable only by `authenticated` + `service_role`.
* New tables `public.account_status_history` and `public.audit_logs` carry RLS with SELECT-only policies (no client writes).
* `public.place_order_app` gained the account-status checkout guard (ACTIVE → unchanged; SUSPENDED/BLOCKED/DELETED → 42501).

### T03 — RLS restructure (the 44 performance findings)
Migration `supabase/migrations/20261016_003_rls_restructure.sql` — applied + verified 2026-10-16 (idempotent; applied twice, both HTTP 201).

| Lint | Before | After | Status |
|---|---|---|---|
| `auth_rls_initplan` | 18 | 0 | **FIXED** — every policy `auth.uid()` rewritten to `(select auth.uid())` (one InitPlan per statement) |
| `multiple_permissive_policies` | 26 | 0 | **FIXED** — permissive pairs merged per (table, cmd, role); catalogue reads split `to anon` / `to authenticated` so a single role never has two SELECT policies |
| `unindexed_foreign_keys` | 5 | 5 | **UNCHANGED** — no new FK index gaps introduced |
| `unused_index` | 22 | 20 | **IMPROVED** — net −2 |

**Deliberate access changes (the only diffs, all verified by the 8-identity probe matrix):**
1. `orders_update_own` **dropped** — no client updates `orders`; customer cancellation to become a dedicated `cancel_order` RPC (TODO).
2. `profiles_admin_all` **dropped** — admins no longer hold blanket UPDATE on `profiles` (status changes go through `admin_set_account_status`).
3. Capability writes narrowed from the coarse `is_admin()` to `can_manage_catalogue()` / `can_manage_store()` / `can_manage_complaints()` / `can_view_orders()` (least privilege per design §2.4). Observable diffs: `DELIVERY`/`SUPPORT` lose catalogue write; `MANAGER` loses store write; `DELIVERY` loses staff profile read.

**New helper `private.current_profile_role()` (SECURITY DEFINER):** the `profiles_update_own` role guard could not use a self-referential subquery — combined with the `(select auth.uid())` InitPlan in the `profiles_select` qual, Postgres raised `42P17 infinite recursion detected in policy for relation "profiles"` on *every* profile UPDATE. Delegating the current-role lookup to a DEFINER helper preserves the guard (role escalation → 42501) and is recursion-free.

**Data integrity (unchanged):** profiles 4, orders 1, events 0, storage objects 0, `user_roles` 2. No leftover probe users; no temporary grants.

### T04 — Index remediation + admin reporting RPCs
Migration `supabase/migrations/20261016_004_indexes_and_reporting.sql` — applied + verified 2026-10-16 (idempotent; applied twice, both HTTP 201).

**Indexes added (5 — closes all 5 P1 `unindexed_foreign_keys`):**
`cart_items_product_idx`, `cart_items_weight_idx`, `complaint_messages_author_idx`, `orders_address_idx`, `orders_delivery_slot_idx`.

**Indexes dropped (3 — all `idx_scan=0`, no client filters them; verified by grep of `src/` + `admin-android/`):**
`orders_pincode_idx`, `events_created_idx`, `events_event_idx`.

| Lint | After T03 | After T04 | Status |
|---|---|---|---|
| `unindexed_foreign_keys` | 5 | **0** | **FIXED** |
| `unused_index` | 20 | 20 | Composition changed: −3 dropped, +5 new FK (unused until traffic), −2 now used (`complaints_status_idx`, `complaints_user_idx` — used by the new reporting RPCs). Net 0. |
| security total | 15 | 18 | +3 — the 3 new intentional, gated RPCs below. No other new finding. |

**New reporting RPCs (`SECURITY DEFINER`, `STABLE`, fully-qualified names, `EXECUTE` to `authenticated` + `service_role` only — the 3 new `authenticated_security_definer_function_executable` entries):**

| RPC | Gate | Purpose |
|---|---|---|
| `public.admin_dashboard_counts()` | `private.is_staff()` | Real dashboard counters (pending/active/ready/out-for-delivery/delivered-today/cancelled/today's orders & sales/complaints) |
| `public.admin_sales_today(p_date date default null)` | `private.can_manage_users()` | Server-authoritative money for a day (gross/discounts/adjustments/refunds/delivery revenue/net/AOV) |
| `public.admin_search_users(p_query text, p_filter text, p_limit int, p_offset int)` | `can_manage_users() or can_manage_complaints()` | Fast user search + filters (ACTIVE/SUSPENDED/BLOCKED/HAS_ACTIVE_ORDER/HAS_COMPLAINT/NEW_CUSTOMER); limit clamped 1..100, offset ≥ 0 |

**Two design-SQL bugs fixed:**
1. `admin_sales_today.refunds` was structurally always 0 (design CTE filtered out CANCELLED then summed CANCELLED). Now computed from cancelled orders (placed-on-`d` cohort) in a separate subquery.
2. `admin_dashboard_counts.delivered_today` used `placed_at`. Now uses `delivery_date = (now() AT TIME ZONE 'Asia/Kolkata')::date` (no `delivered_at` column exists). **[ASSUMPTION]** `delivery_date` is the committed delivery day; owner to confirm.

**Deferred (not done):** §9.3 `pg_trgm` GIN index — extension not installed, 4 profiles = over-indexing. Recorded as a deferred option in the migration header.

### T05 — SECURITY DEFINER hardening
Migration `supabase/migrations/20261016_005_definer_hardening.sql` — applied + verified 2026-10-16 (idempotent; applied twice, both HTTP 201).

**`search_path` pinned to `''` on all 27 `public` functions** (verified: zero rows where `prosecdef and proconfig <> 'search_path=""'`, and zero rows pinned to a non-empty path). No `prosecdef` changed; **no EXECUTE grant changed** — T01/T02/T03 ACLs intact (the 7 trigger functions remain `service_role`-only; `gen_order_number` still anon-revoked; `place_order` 5-arg still service_role-only). Bodies were already fully qualified (`public.*`, `private.*`, `auth.*`); built-ins resolve via `pg_catalog`.

**`check_username_available` hardened** (kept DEFINER + anon EXECUTE by ruling — genuine public signup functionality; INVOKER impossible because `anon` has no SELECT on `profiles`):
- `search_path = ''`, fully qualified; converted `sql` → `plpgsql` to allow validation (signature unchanged).
- Input validated before touching the table: `null` / empty-after-trim / `<3` / `>32` / non-`^[a-z0-9_]+$` → `22023` (no longer an arbitrary-scan oracle).
- Exact **case-insensitive equality** (`lower(username) = lower(trim(p_username))`), never LIKE/prefix.
- Returns **boolean only** (no username/id echo).

| Lint | After T04 | After T05 | Status |
|---|---|---|---|
| `anon_security_definer_function_executable` | 1 | 1 | **ACCEPTED** — `check_username_available` is an intentional public signup availability check; DEFINER is required because `anon` lacks SELECT on `profiles`; input-validated and boolean-only (see above) |
| `authenticated_security_definer_function_executable` | 15 | 15 | unchanged |
| security total | 18 | 18 | no new finding |
| performance total | 20 | 20 | no new finding |

**Residual risk (measured, not assumed):** `has_schema_privilege('anon','public','CREATE')` = **false**; `has_schema_privilege('authenticated','public','CREATE')` = **false** (both have `USAGE` = true). So neither client role can create objects in `public`; combined with the `search_path=''` pins, a hijack via a shadow object is not possible.

**Money-path regression (proves `product_weights`/`offers` still resolve after pinning):** empty cart → `22023 'cart is empty'`; SUSPENDED → `42501 'account is not permitted to place orders'`; a real customerB cart order (Apple 1kg −17%, Banana 1kg −15%, Coriander 15.00) → subtotal **225.60**, fee **20.00**, total **245.60** — matches hand computation; a direct 5-arg `place_order` (2× Apple + 1× Coriander) → **313.80 / 20.00 / 333.80** — matches. All rolled back; data unchanged (profiles 4, orders 1, events 0, storage 0, user_roles 2).

### T13 — close the direct-INSERT surface on `orders` / `order_items`
Migration `supabase/migrations/20261016_006_close_order_insert.sql` — applied + verified 2026-10-16 (idempotent; applied twice, both HTTP 201).

> **Provenance:** this finding is **not** from the three Advisor exports above (the count stays 98). It was surfaced by QA (`software-qa-engineer`) during RLS-isolation testing and is tracked here because it is a genuine client-can-write-money defect that the master brief §10 forbids.

**Finding (reproduced live, rollback-scoped).** Two permissive `INSERT` policies let any `authenticated` customer write order rows straight through PostgREST, bypassing `public.place_order`:

| Table | Policy | `with_check` |
|---|---|---|
| `public.orders` | `orders_insert_own` | `(select auth.uid()) = user_id` |
| `public.order_items` | `order_items_insert` | `EXISTS (select 1 from orders o where o.id = order_items.order_id and o.user_id = (select auth.uid()))` |

A simulated authenticated customer (`SET LOCAL ROLE authenticated` + `request.jwt.claims`) ran a direct `INSERT` into `public.orders` with `total = 0.01` and it **SUCCEEDED** — i.e. the client could mint an order with an arbitrary price and no server-side re-pricing. `src/services/order-service.ts:14` already *claimed* "there is no INSERT policy on `orders`, `order_items` or `order_status_history` for customers"; the DB had drifted from that contract.

**Fix.** Drop both policies. Order placement then has exactly one write path — `public.place_order_app → public.place_order`, both `SECURITY DEFINER` owned by `postgres`. Because `public.orders` / `public.order_items` are owned by `postgres` and `relforcerowsecurity` is `false`, the DEFINER body bypasses RLS on its own `INSERT`s, so checkout is unaffected.

**Proof (empirical, not assumed — all batches end in `ROLLBACK`).**

| # | Condition | Probe | Result |
|---|---|---|---|
| 1 | policies **PRESENT** | authenticated direct `INSERT orders` (`total=0.01`) | **SUCCEEDED** — vulnerability reproduced |
| 2 | policies **DROPPED** (in-txn) | authenticated direct `INSERT orders` | **BLOCKED** `42501` `new row violates row-level security policy for table "orders"` |
| 3 | policies **DROPPED** (in-txn) | `place_order_app(...)` as the same customer | **SUCCEEDED** — returned `order_id` |
| 4 | post-rollback | probe address / probe orders | `0 / 0`; both policies restored → nothing persisted |

**Post-migration verification (persistent change).**

| # | Probe | Result |
|---|---|---|
| 1 | `pg_policies` count for both policies | `0 / 0` |
| 2 | authenticated direct `INSERT orders` | **BLOCKED** `42501` |
| 3 | authenticated direct `INSERT order_items` **on the caller's own order** | **BLOCKED** `42501` |
| 4 | `place_order_app(...)` legit checkout | **SUCCEEDED** — `order_id` returned |
| 5 | post-rollback (probe orders / addresses) | `0 / 0` |

**Blast-radius check (grep, not assumed):** every reference to `orders` / `order_items` in `src/` (web) and `admin-android/` (Kotlin) is a `SELECT` (or a `SELECT` embed); there is no client `INSERT`/`UPDATE`/`DELETE` on either table. Customer checkout uses `supabase.rpc("place_order")`; admin lifecycle writes go through the `admin_*` RPCs. `order_status_history` already had no customer `INSERT` policy (correct).

**Migration ledger.** `supabase_migrations.schema_migrations` was missing all six manual `20261016_00{1..6}` migrations (only the 15 CLI-era rows were present). Recorded (idempotent, `on conflict (version) do nothing`) with `version = 20261016_00X`, `name` = the file's descriptive part, `statements` = the file body verbatim, `created_by = 'platform-remediation (management-api)'`. Note: the filenames use an `NNN` suffix rather than the CLI's 14-digit convention, so they were applied through the Management API and are recorded here for provenance rather than for CLI `db push` de-duplication.

