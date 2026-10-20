# Future Admin App — Android Application Contract

> **Purpose:** This document defines the contract for the **separate Android admin application** that will manage Vedant Fruit & Vegetables store operations. The customer PWA (this repository) deliberately contains **no admin UI, no admin screens, and no admin secrets**. The admin app will consume the same Supabase backend.

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Supabase Backend                         │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │  Customer   │  │    Admin     │  │   Shared Data   │    │
│  │   Tables    │  │    Tables    │  │     Tables      │    │
│  │  (RLS-gated)│  │ (admin-only) │  │  (catalogue,    │    │
│  │             │  │              │  │    slots, etc)  │    │
│  └─────────────┘  └──────────────┘  └─────────────────┘    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Admin-Only RPCs & Policies              │  │
│  │  • admin_update_order_status()                       │  │
│  │  • admin_reject_order_item()                         │  │
│  │  • private.is_admin() ← authorization boundary       │  │
│  │  • private.admin_users ← admin roster                │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
         ▲                          ▲
         │                          │
    Customer PWA               Admin Android App
    (this repo)                (separate project)
    • No admin UI              • Full admin dashboard
    • No admin secrets         • Admin authentication
    • Read-only order view     • Order management
    • Cart/checkout only       • Catalogue management
```

---

## 2. Admin Authentication & Authorization

### 2.1 Admin Roster

Admins are stored in `private.admin_users`:

```sql
CREATE TABLE private.admin_users (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);
```

**To grant admin access:**
1. Create the user in Supabase Auth (email/password or Google OAuth)
2. Insert their `auth.uid()` into `private.admin_users`
3. The `private.is_admin()` function will now return `true` for that user

```sql
-- Example: grant admin to a user
INSERT INTO private.admin_users (user_id)
VALUES ('<auth.uid-of-the-user>');
```

### 2.2 Authorization Boundary

All admin-write policies call `private.is_admin()`:

```sql
CREATE OR REPLACE FUNCTION private.is_admin() RETURNS boolean
  SECURITY DEFINER SET search_path = ''
  STABLE LANGUAGE sql AS $$
    SELECT EXISTS (
      SELECT 1 FROM private.admin_users a
      WHERE a.user_id = (SELECT auth.uid())
    );
  $$;
```

**The Android app must:**
- Authenticate users via Supabase Auth (same project: `bmdmgycsrnffcgxmukxm`)
- Never assume admin status client-side — always rely on RLS enforcement
- Handle `403 Permission Denied` gracefully (user not in `admin_users`)

---

## 3. Admin-Only RPCs

### 3.1 `admin_update_order_status(p_order_id uuid, p_status text, p_reason text)`

**Purpose:** Transition an order through the state machine.

**Valid statuses:**
- `ORDER_RECEIVED` → `OWNER_ACCEPTED` (accept the order)
- `OWNER_ACCEPTED` → `PREPARING` (start packing)
- `PREPARING` → `READY` (packed, waiting for dispatch)
- `READY` → `OUT_FOR_DELIVERY` (driver has the order)
- `OUT_FOR_DELIVERY` → `DELIVERED` (complete)
- Any → `CANCELLED` (cancel with reason)

**Signature:**
```sql
CREATE OR REPLACE FUNCTION public.admin_update_order_status(
  p_order_id uuid,
  p_status text,
  p_reason text DEFAULT NULL
) RETURNS uuid
  SECURITY DEFINER SET search_path = ''
  LANGUAGE plpgsql AS $$
  BEGIN
    -- Gates on private.is_admin()
    -- Validates status transition
    -- Inserts order_status_history row
    -- Updates orders.status
    RETURN p_order_id;
  END;
  $$;
```

**Android app usage:**
```kotlin
// Example Kotlin call via supabase-kt
val result = supabase.rpc("admin_update_order_status") {
    parameter("p_order_id", orderId)
    parameter("p_status", "OWNER_ACCEPTED")
    parameter("p_reason", null)
}
```

### 3.2 `admin_reject_order_item(p_item_id uuid, p_reason text)`

**Purpose:** Reject a specific line item (e.g., out of stock, poor quality).

**Effects:**
- Sets `order_items.rejected = true`
- Sets `order_items.rejection_reason = p_reason`
- Recalculates `orders.adjustment_amount` (negative, reduces total)
- Recalculates `orders.final_total`
- Triggers a notification to the customer

**Signature:**
```sql
CREATE OR REPLACE FUNCTION public.admin_reject_order_item(
  p_item_id uuid,
  p_reason text
) RETURNS void
  SECURITY DEFINER SET search_path = ''
  LANGUAGE plpgsql AS $$
  BEGIN
    -- Gates on private.is_admin()
    -- Validates item belongs to an active order
    -- Updates order_items + orders
    -- Inserts notification for customer
  END;
  $$;
```

**Android app usage:**
```kotlin
supabase.rpc("admin_reject_order_item") {
    parameter("p_item_id", orderItemId)
    parameter("p_reason", "Out of stock - will refund")
}
```

---

## 4. Admin Dashboard Features (Android App)

### 4.1 Order Management

| Feature | Backend Source | Notes |
|---|---|---|
| Order list (filter by status) | `orders` table, RLS `orders_read_self_or_admin` | Show all orders, not just own |
| Order detail | `orders` + `order_items` + `order_status_history` | Full timeline |
| Accept order | `admin_update_order_status(..., 'OWNER_ACCEPTED')` | First action on new orders |
| Reject item | `admin_reject_order_item()` | Adjusts bill automatically |
| Update status | `admin_update_order_status()` | Full state machine |
| Cancel order | `admin_update_order_status(..., 'CANCELLED', reason)` | Requires reason |

### 4.2 Catalogue Management

| Feature | Backend Source | Notes |
|---|---|---|
| Product CRUD | `products` table, `*_admin_*` policies | INSERT/UPDATE/DELETE gated on `is_admin()` |
| Category CRUD | `categories` table | Same |
| Weight variants | `product_weights` table | Backend-supplied variants (100g, 500g, 1kg, etc.) |
| Offers CRUD | `offers` table | `price` or `discount_percent`, date ranges |
| Toggle availability | `products.availability`, `product_weights.is_available` | Soft hide, not delete |
| Upload images | `product-images` bucket (public) | Store path in `products.image_url` |

### 4.3 Delivery Slot Management

| Feature | Backend Source | Notes |
|---|---|---|
| View slots | `delivery_slots` table | `starts_at`, `ends_at`, `capacity`, `is_active` |
| Create slots | INSERT into `delivery_slots` | Recurring generation recommended |
| Deactivate slots | UPDATE `delivery_slots.is_active = false` | Don't delete (FK constraints) |
| View capacity | COUNT orders per slot | `place_order` checks capacity |

**Critical:** The current seed data only covers `2026-10-04` to `2026-10-10`. The admin app **must** implement recurring slot generation (e.g., "generate next 14 days, 9am–8pm, 30-min slots, capacity 20").

### 4.4 Customer Support

| Feature | Backend Source | Notes |
|---|---|---|
| Complaint list | `complaints` table, RLS `complaints_read_self_or_admin` | Filter by status |
| Reply to complaint | INSERT into `complaint_messages` (`author_role = 'ADMIN'`) | Threaded messages |
| Resolve complaint | UPDATE `complaints.status = 'RESOLVED'` | Set `resolved_at`, `resolved_by` |
| View attachments | `complaint_attachments` + `complaint-images` bucket | RLS-gated |

### 4.5 Admin User Management

| Feature | Backend Source | Notes |
|---|---|---|
| View admins | `private.admin_users` | Not directly accessible; query via Edge Function or service role |
| Grant admin | INSERT into `private.admin_users` | Requires service-role key or Edge Function |
| Revoke admin | DELETE from `private.admin_users` | Same |

**Note:** `private` schema is not accessible from client SDKs. The admin app should use a Supabase Edge Function for admin roster management.

---

## 5. Storage Buckets

### 5.1 `product-images` (public)

- **Purpose:** Catalogue photography
- **Access:** Public read, admin write (RLS-gated)
- **Usage:** Upload image → get public URL → update `products.image_url` or `categories.image_url`
- **Current state:** All `image_url` fields are `NULL` — customer app renders glyph+accent plinth

### 5.2 `complaint-images` (private)

- **Purpose:** Customer complaint evidence
- **Access:** RLS-gated (owner or admin)
- **Usage:** Customer uploads → admin views in complaint thread

---

## 6. Notifications Architecture

### 6.1 Customer Notifications

Stored in `notifications` table:

```sql
CREATE TABLE notifications (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id),
  title text NOT NULL,
  body text NOT NULL,
  read_at timestamptz,
  created_at timestamptz DEFAULT now()
);
```

**Admin app triggers notifications for:**
- Order status changes (`admin_update_order_status`)
- Item rejections (`admin_reject_order_item`)
- Complaint replies (manual INSERT into `complaint_messages` should trigger)

**Implementation:** The admin app should INSERT into `notifications` after each action, or use a database trigger.

### 6.2 Push Notifications (Future)

The customer PWA supports web push architecture. The admin app could:
- Use Supabase Edge Functions + Firebase Cloud Messaging
- Trigger push on order status change
- Customer PWA subscribes via service worker

---

## 7. Recommended Android Tech Stack

| Layer | Recommendation | Rationale |
|---|---|---|
| **Language** | Kotlin | Official Android language, Supabase has Kotlin SDK |
| **Architecture** | MVVM + Compose | Modern, declarative UI |
| **Supabase Client** | `supabase-kt` | Official Kotlin Multiplatform SDK |
| **Local Cache** | Room + DataStore | Offline-first order list, settings |
| **Navigation** | Compose Navigation | Type-safe, back-stack handling |
| **DI** | Hilt | Standard Android DI |
| **Networking** | Ktor (via supabase-kt) | Built into Supabase client |
| **Image Loading** | Coil | Compose-native, efficient |
| **Background Sync** | WorkManager | Periodic order polling, slot generation |

---

## 8. Security Considerations

### 8.1 Never Use Service-Role Key in Android App

- **Service-role key bypasses RLS** — it should never ship in a client app
- Admin authorization is via `private.admin_users` + RLS policies
- If admin roster management is needed, use an Edge Function with service-role

### 8.2 RLS Is the Authorization Boundary

- The Android app must not assume admin status client-side
- All writes are gated by `private.is_admin()` in RLS policies
- Handle `403 Permission Denied` gracefully

### 8.3 Secrets Management

- Supabase URL and anon key are safe in the app (public by design)
- Service-role key (if needed for Edge Functions) stays server-side
- Never log or display session tokens

---

## 9. Current Backend Status (as of 2026-10-07)

| Component | Status | Notes |
|---|---|---|
| **Migrations applied** | 8 | All in `docs/BACKEND_CONTRACT.md` |
| **Admin RPCs** | IMPLEMENTED | `admin_update_order_status`, `admin_reject_order_item` |
| **Admin roster** | EMPTY | `private.admin_users` has 0 rows — **BLOCKED** |
| **RLS policies** | 45 policies | All verified, admin writes gated on `is_admin()` |
| **Storage buckets** | 2 | `product-images` (public), `complaint-images` (private) |
| **Delivery slots** | STALE | Seed data expires 2026-10-10 — **BLOCKED** |
| **Catalogue images** | NULL | All `products.image_url` / `categories.image_url` are NULL |

---

## 10. First-Time Admin Setup (Owner Action)

**To enable the admin app:**

1. **Create admin user in Supabase Auth:**
   - Go to Supabase Dashboard → Authentication → Users
   - Add user (email/password or invite)
   - Note the `User ID` (UUID)

2. **Grant admin role:**
   ```sql
   INSERT INTO private.admin_users (user_id)
   VALUES ('<User-ID-from-step-1>');
   ```

3. **Verify admin access:**
   ```sql
   SELECT private.is_admin(); -- Should return true for that user's session
   ```

4. **Generate delivery slots (critical):**
   ```sql
   -- Example: generate next 14 days, 9am–8pm, 30-min slots, capacity 20
   -- (Full migration provided separately)
   ```

5. **Add store contact info:**
   - Set environment variables in customer app:
     - `NEXT_PUBLIC_STORE_PHONE`
     - `NEXT_PUBLIC_STORE_WHATSAPP`
     - `NEXT_PUBLIC_STORE_ADDRESS`

---

## 11. Migration Proposal: Recurring Delivery Slots

The following migration should be applied to enable ongoing checkout:

```sql
-- supabase/migrations/20261007_delivery_slot_recurrence.sql
-- Proposed, NOT YET APPLIED

-- Generate slots for the next 90 days
-- 9:00 AM to 8:00 PM, 30-minute slots, capacity 20 per slot

DO $$
DECLARE
  start_date date := CURRENT_DATE + INTERVAL '1 day';
  end_date date := CURRENT_DATE + INTERVAL '90 days';
  slot_start timestamptz;
  slot_end timestamptz;
  current_date_iter date := start_date;
BEGIN
  WHILE current_date_iter <= end_date LOOP
    -- Generate slots from 9:00 to 19:30 (last slot starts at 19:30, ends 20:00)
    FOR hour IN 9..19 LOOP
      FOR minute IN ARRAY [0, 30] LOOP
        IF NOT (hour = 19 AND minute = 30) THEN -- Skip 19:30 if you want last slot at 19:00
          slot_start := (current_date_iter || ' ' || hour || ':' || minute || ':00')::timestamptz;
          slot_end := slot_start + INTERVAL '30 minutes';
          
          INSERT INTO delivery_slots (starts_at, ends_at, capacity, is_active)
          VALUES (slot_start, slot_end, 20, true)
          ON CONFLICT DO NOTHING;
        END IF;
      END LOOP;
    END LOOP;
    
    current_date_iter := current_date_iter + INTERVAL '1 day';
  END LOOP;
END $$;
```

---

## 12. Handoff Checklist for Admin App Developer

- [ ] Read `docs/BACKEND_CONTRACT.md` — authoritative schema/RLS/RPC contract
- [ ] Read this document — admin app requirements and patterns
- [ ] Obtain Supabase project credentials (`bmdmgycsrnffcgxmukxm`, region `ap-southeast-1`)
- [ ] Grant first admin user in `private.admin_users`
- [ ] Apply slot-recurrence migration (critical for checkout to work)
- [ ] Set up `supabase-kt` in Android project
- [ ] Implement order list → order detail → status update flow
- [ ] Implement item rejection with reason
- [ ] Implement catalogue CRUD (products, categories, weights, offers)
- [ ] Implement slot management (view, create, deactivate)
- [ ] Implement complaint thread replies
- [ ] Test RLS: verify non-admin users cannot access admin RPCs
- [ ] Deploy to Play Store (internal testing track first)

---

## 13. Future Enhancements (Post-MVP)

| Feature | Priority | Notes |
|---|---|---|
| **Analytics dashboard** | Medium | Orders/day, revenue, popular products |
| **Inventory tracking** | High | Track stock levels, low-stock alerts |
| **Driver assignment** | Medium | Assign orders to delivery drivers |
| **Bulk slot generation UI** | High | Admin app feature to generate slots |
| **Image upload from Android** | High | Camera integration for product photos |
| **Push notifications to admin** | Low | New order alerts |
| **Multi-admin support** | Medium | Role-based permissions (manager vs packer) |
| **Export orders to CSV** | Low | Accounting integration |

---

**End of Document**

This contract is versioned alongside the customer PWA. Any schema changes affecting admin capabilities must be reflected here.

</content>