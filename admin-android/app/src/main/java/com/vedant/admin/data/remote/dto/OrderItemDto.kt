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
