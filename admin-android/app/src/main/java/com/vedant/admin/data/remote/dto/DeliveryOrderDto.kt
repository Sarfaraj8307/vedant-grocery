package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of one row of `public.delivery_orders(p_only_mine boolean)`.
 *
 * The RPC is the **only** source of the delivery queue. It is `SECURITY DEFINER`,
 * gated on `private.can_manage_orders()`, and it already narrows the result to
 * the caller's own assignments when the caller's role is `DELIVERY` — so the
 * client never has to (and never should) filter for authorization itself.
 *
 * Customer contact comes from `orders.address_snapshot`, not from `profiles`:
 * DELIVERY deliberately cannot read `profiles` (migration 003), and does not need
 * to, because the snapshot captured name + phone at placement time.
 *
 * Every field is nullable-tolerant so a renamed column degrades to `null` rather
 * than throwing and emptying the queue.
 */
@Serializable
data class DeliveryOrderDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: String? = null,
    val status: String? = null,
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("delivery_date") val deliveryDate: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("address_line1") val addressLine1: String? = null,
    @SerialName("address_city") val addressCity: String? = null,
    @SerialName("address_pincode") val addressPincode: String? = null,
    /** Destination coordinates, joined from `addresses` (nullable — see the RPC). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("item_count") val itemCount: Long? = null,
    @Serializable(with = MoneySerializer::class) val total: Money? = null,
    @SerialName("final_total") @Serializable(with = MoneySerializer::class) val finalTotal: Money? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
)
