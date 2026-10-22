package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of an `order_items` row.
 *
 * **Column availability: BLOCKED (no admin creds — R1)** — `order_items` is
 * RLS-hidden, so its live columns could not be enumerated (`docs/CONTRACT.md`
 * §10). The fields here are the ones the customer PWA selects verbatim
 * (`src/services/order-service.ts` `ORDER_DETAIL_SELECT`) plus the dual-column
 * safety net; every field is nullable so a renamed column decodes to `null`
 * instead of throwing.
 *
 * The quantity field is **`qty`**, never `quantity` — the live column is `qty`
 * and the sibling `cart_items` table is the one that uses `quantity`. Getting
 * this wrong would silently zero every line quantity.
 */
@Serializable
data class OrderItemDto(
    val id: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("weight_label") val weightLabel: String? = null,
    val qty: Int? = null,
    @SerialName("unit_price") @Serializable(with = MoneySerializer::class) val unitPrice: Money? = null,
    @SerialName("line_total") @Serializable(with = MoneySerializer::class) val lineTotal: Money? = null,
    val rejected: Boolean? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * Minimal projection for "which order does this item belong to?".
 *
 * WHY THIS EXISTS: the lookup selects only `order_id`, but it used to decode into
 * [OrderItemDto], whose `id` is a **required** field. kotlinx.serialization threw
 * `MissingFieldException: id` on every call, so rejecting or restoring an item
 * failed before the RPC was ever reached — the server was fine all along.
 *
 * Decoding a narrow select into a wide DTO is the underlying mistake; a DTO that
 * declares exactly the columns being read cannot drift.
 */
@kotlinx.serialization.Serializable
data class OrderIdRefDto(
    @SerialName("order_id") val orderId: String? = null,
)
