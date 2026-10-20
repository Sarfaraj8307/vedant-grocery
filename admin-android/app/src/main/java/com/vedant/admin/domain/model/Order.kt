package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * One order, as the server returned it.
 *
 * Money is [Money] (integer paise) and is **display-only**: every amount here is
 * the server's own value, never recomputed locally (§5.1). `finalTotal` in
 * particular is rendered exactly as it arrived — the client has no arithmetic
 * that could disagree with `place_order` / `admin_reject_order_item`.
 *
 * ### Nullable `status` / `createdAt`
 * §5.2 mandates tolerant decode: an unknown `order_status` wire value or a
 * malformed timestamp must never crash a list. `status` is therefore nullable
 * (the raw value is kept in [statusRaw] so the UI can show it with a neutral
 * badge), and [createdAt] is nullable rather than fabricated. The class diagram
 * shows these as non-null; §5.2's "unknown enum → null (no crash)" is the
 * governing rule, so the deviation is deliberate and documented.
 */
data class Order(
    val id: String,
    /** `null` when the wire value is not a known [OrderStatus]; see [statusRaw]. */
    val status: OrderStatus?,
    /** The raw `status` string, always present, for tolerant display. */
    val statusRaw: String,
    val originalTotal: Money,
    val adjustmentAmount: Money,
    val finalTotal: Money,
    val deliveryCharge: Money,
    /** Unverified column (BLOCKED) — nullable rather than assumed. */
    val subtotal: Money?,
    /** Pair 6: read `delivery_slot_id`, fall back to `slot_id`. */
    val deliverySlotId: String?,
    /** Short form of `orders.user_id` — the customer reference. */
    val customerRef: String?,
    /** Only present when the optional `profiles` embed resolved; else `null`. */
    val customerName: String?,
    /**
     * The customer's contact number, taken from `orders.address_snapshot.phone`.
     *
     * This is the **only** source of customer contact for staff: DELIVERY cannot
     * read `public.profiles` (correctly — T03 removed that), and it does not need
     * to, because the snapshot captured at placement time already carries the
     * number the courier must dial (§12).
     *
     * Null when the snapshot has no phone (older rows) — the UI hides the call
     * affordance rather than offering a button that cannot work.
     */
    val customerPhone: String? = null,
    /** Delivery address, flattened tolerantly from the `address_snapshot` jsonb. */
    val addressLines: List<String>,
    val adjustmentReason: String?,
    val adjustedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val items: List<OrderItem>,
    val history: List<OrderStatusEvent> = emptyList(),
) {

    val itemCount: Int get() = items.size

    /** True when the server applied any adjustment (rejection) to this order. */
    val hasAdjustment: Boolean get() = !adjustmentAmount.isZero

    /** Items may only be rejected/restored while the order is non-terminal. */
    val itemsAdjustable: Boolean get() = status?.isTerminal == false

    /** Wording for the status, tolerating an unknown wire value. */
    val statusLabel: String get() = status?.label ?: statusRaw
}

/**
 * One line of an order.
 *
 * Quantity is [qty] — the live column is `order_items.qty`; there is no
 * `quantity` on this table (§5.3, pair policy). [weightLabel] is the snapshot
 * taken at checkout, so it is a plain string, not a weight id.
 */
data class OrderItem(
    val id: String,
    val productId: String?,
    val productName: String,
    val weightLabel: String?,
    val qty: Int,
    val unitPrice: Money,
    val lineTotal: Money,
    val rejected: Boolean,
    val rejectionReason: String?,
)

/**
 * One entry of `order_status_history` — the timeline the detail screen renders.
 *
 * Both endpoints are nullable for tolerant decode; [toStatusRaw] is always
 * present.
 */
data class OrderStatusEvent(
    val id: String,
    val fromStatus: OrderStatus?,
    val fromStatusRaw: String?,
    val toStatus: OrderStatus?,
    val toStatusRaw: String,
    val changedBy: String?,
    val reason: String?,
    val createdAt: Instant?,
)
