package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money

/**
 * One row of the courier's queue, as `public.delivery_orders()` returned it.
 *
 * The server has already decided *which* orders this caller may see (its own
 * assignments for a DELIVERY role, the whole active queue for a dispatcher), so
 * there is no client-side authorization in this model — only presentation.
 *
 * [latitude]/[longitude] are nullable on purpose: an address captured before
 * coordinates existed has none, and `orders.address_id` is `ON DELETE SET NULL`.
 * The UI must fall back to a text-address navigation intent rather than invent a
 * coordinate (brief §58 forbids fake GPS).
 */
data class DeliveryOrder(
    val orderId: String,
    val orderNumber: String?,
    val status: OrderStatus?,
    val statusRaw: String,
    val customerName: String?,
    val customerPhone: String?,
    val addressLine1: String?,
    val city: String?,
    val pincode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val itemCount: Long,
    val amount: Money,
    val assignedToName: String?,
) {

    /** True when we have a real coordinate to hand to a navigation app. */
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    /** One-line address for display and for the text fallback intent. */
    val addressLine: String
        get() = listOfNotNull(
            addressLine1?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() },
            pincode?.takeIf { it.isNotBlank() },
        ).joinToString(", ")

    val statusLabel: String get() = status?.label ?: statusRaw

    /** The courier may start the run once the order is picked and packed. */
    val canStartDelivery: Boolean get() = status == OrderStatus.READY_FOR_DELIVERY

    /** Once on the road, the only forward action is completion. */
    val canMarkDelivered: Boolean get() = status == OrderStatus.OUT_FOR_DELIVERY
}
