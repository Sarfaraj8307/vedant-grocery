/**
 * Hand-maintained types mirroring the **verified live** Supabase schema.
 * Source of truth: docs/BACKEND_CONTRACT.md (read out of the live database on
 * 2026-10-07 via information_schema / pg_policy / pg_proc introspection).
 *
 * Regenerating with `supabase gen types` is preferred once the Supabase CLI is
 * authenticated in this workspace; until then this file is authoritative and
 * must be updated in the same change as any migration.
 *
 * Money is `numeric(12,2)` in Postgres, which the PostgREST client surfaces as
 * a JS `number`. It is exact in the database; treat it as rupees-with-paise and
 * round to 2dp at every display boundary (see lib/money.ts).
 */

/**
 * Mirrors the live Postgres `order_status` enum exactly.
 *
 * The value names are the database's, not the UI's — `PENDING` is shown to the
 * customer as "Order received", `READY_FOR_DELIVERY` as "Ready to dispatch".
 * Keeping the wire values identical to the enum means no translation layer, and
 * a status the database adds later surfaces as an unknown value rather than
 * silently rendering as the wrong stage. Labels live in `lib/format.ts`.
 */
export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "PROCESSING"
  | "READY_FOR_DELIVERY"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "CANCELLED";

/** Mirrors the live `complaint_status` enum (note: IN_PROGRESS, not IN_REVIEW). */
export type ComplaintStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export type ComplaintAuthorRole = "CUSTOMER" | "ADMIN";

export type MediaType = "image/jpeg" | "image/png" | "image/webp";

export type ProfileRow = {
  id: string;
  username: string;
  first_name: string;
  last_name: string;
  email: string | null;
  phone: string | null;
  created_at: string;
  updated_at: string;
};

export type CategoryRow = {
  id: string;
  name: string;
  glyph: string;
  accent: number;
  image_url: string | null;
  sort_order: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
};

export type ProductRow = {
  id: string;
  category_id: string;
  name: string;
  description: string;
  glyph: string;
  accent: number;
  image_url: string | null;
  base_price: number;
  unit_label: string;
  availability: boolean;
  quantity_limit: number;
  created_at: string;
  updated_at: string;
};

export type ProductWeightRow = {
  id: string;
  product_id: string;
  label: string;
  grams: number;
  price: number;
  is_available: boolean;
  created_at: string;
  updated_at: string;
};

export type OfferRow = {
  id: string;
  product_id: string;
  title: string;
  price: number | null;
  discount_percent: number | null;
  starts_at: string | null;
  ends_at: string | null;
  is_active: boolean;
  created_at: string;
  updated_at: string;
};

export type DeliverySlotRow = {
  id: string;
  starts_at: string;
  ends_at: string;
  capacity: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
};

export type AddressRow = {
  id: string;
  user_id: string;
  label: string;
  full_name: string;
  phone: string;
  house_number: string;
  street: string;
  landmark: string;
  city: string;
  state: string;
  postal_code: string;
  latitude: number | null;
  longitude: number | null;
  is_default: boolean;
  created_at: string;
  updated_at: string;
};

export type CartItemRow = {
  id: string;
  user_id: string;
  product_id: string;
  weight_id: string | null;
  quantity: number;
  created_at: string;
  updated_at: string;
};

export type OrderRow = {
  id: string;
  user_id: string;
  request_id: string;
  address_id: string | null;
  delivery_slot_id: string | null;
  status: OrderStatus;
  original_total: number;
  delivery_charge: number;
  adjustment_amount: number;
  adjustment_reason: string | null;
  adjusted_by: string | null;
  adjusted_at: string | null;
  final_total: number;
  /**
   * The order timestamp. The live column is `placed_at` (it is NOT NULL with a
   * `now()` default); there is **no** `orders.created_at`. Ordering or rendering
   * by `created_at` returns PostgREST `42703` and silently empties the list.
   */
  placed_at: string;
  updated_at: string;
  /**
   * Set by `customer_cancel_order` (022) and by `admin_update_order_status` when
   * a staff member cancels. `cancelled_at` is what the customer's cancellation
   * banner shows, so it must stay in the select.
   */
  cancelled_at: string | null;
  cancel_reason: string | null;
};

export type OrderItemRow = {
  id: string;
  order_id: string;
  product_id: string | null;
  product_name: string;
  weight_label: string;
  qty: number;
  unit_price: number;
  line_total: number;
  rejected: boolean;
  rejection_reason: string | null;
  created_at: string;
};

export type OrderStatusHistoryRow = {
  id: string;
  order_id: string;
  old_status: OrderStatus | null;
  new_status: OrderStatus;
  changed_by: string;
  reason: string | null;
  created_at: string;
};

export type ComplaintRow = {
  id: string;
  user_id: string;
  order_id: string | null;
  subject: string;
  description: string;
  status: ComplaintStatus;
  resolution: string | null;
  resolved_by: string | null;
  resolved_at: string | null;
  created_at: string;
  updated_at: string;
};

export type ComplaintMessageRow = {
  id: string;
  complaint_id: string;
  author_id: string;
  author_role: ComplaintAuthorRole;
  body: string;
  created_at: string;
};

export type ComplaintAttachmentRow = {
  id: string;
  complaint_id: string;
  storage_path: string;
  media_type: MediaType;
  created_at: string;
};

export type NotificationRow = {
  id: string;
  user_id: string;
  title: string;
  body: string;
  read_at: string | null;
  created_at: string;
};

/* -------------------------------------------------------------------------- */
/* Composed / application-level shapes                                         */
/* -------------------------------------------------------------------------- */

/**
 * A catalogue product together with the data needed to price and display it.
 * Assembled in `lib/catalogue.ts` from a single nested PostgREST select so the
 * storefront never issues N+1 queries.
 */
export type CatalogueProduct = ProductRow & {
  category: Pick<CategoryRow, "id" | "name" | "glyph" | "accent"> | null;
  weights: ProductWeightRow[];
  /** The best live offer, chosen with the same rule `place_order` uses. */
  activeOffer: OfferRow | null;
};

/**
 * Fully resolved pricing for one product/weight combination, computed the same
 * way `public.place_order` computes it. This is a **preview only** — the server
 * remains the sole authority for what the customer is charged.
 */
export type PriceBreakdown = {
  /** Undiscounted price for the chosen weight (or `base_price`). */
  listPrice: number;
  /** What the customer pays per unit after the best live offer. */
  unitPrice: number;
  /** Absolute rupees saved per unit. 0 when there is no offer. */
  savingPerUnit: number;
  /** Percentage saving rounded for display; 0 when there is no offer. */
  savingPercent: number;
  currency: "INR";
  /** True when `unitPrice < listPrice`. */
  isDiscounted: boolean;
};
