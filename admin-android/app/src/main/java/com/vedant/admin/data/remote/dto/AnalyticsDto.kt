package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `public.admin_dashboard_counts()`.
 *
 * The RPC returns a single row as a one-element list, so the client takes
 * `firstOrNull()` and treats a missing row as "no counts" rather than throwing —
 * an empty database is a legitimate state for a new store.
 */
@Serializable
data class DashboardCountsDto(
    val pending: Int? = null,
    val active: Int? = null,
    val ready: Int? = null,
    @SerialName("out_for_delivery") val outForDelivery: Int? = null,
    @SerialName("delivered_today") val deliveredToday: Int? = null,
    val cancelled: Int? = null,
    @SerialName("todays_orders") val todaysOrders: Int? = null,
    @SerialName("todays_sales")
    @Serializable(with = MoneySerializer::class)
    val todaysSales: Money? = null,
    @SerialName("pending_complaints") val pendingComplaints: Int? = null,
    @SerialName("open_complaints") val openComplaints: Int? = null,
)

/**
 * One row of `public.admin_sales_today()` **or** `public.admin_sales_range()`.
 *
 * Both RPCs return the identical column set — `admin_sales_today` is simply the
 * one-day case of `admin_sales_range` — so a single DTO serves both. Keeping one
 * shape means a figure on the screen is parsed the same way whether it came from
 * a one-day read or a 30-day read.
 *
 * `refunds` is the cancelled/rejected value handed back — it is reported so the
 * owner sees gross and net side by side rather than a single flattering number.
 */
@Serializable
data class SalesTodayDto(
    @SerialName("order_date") val orderDate: String? = null,
    val orders: Int? = null,
    @Serializable(with = MoneySerializer::class) val gross: Money? = null,
    @Serializable(with = MoneySerializer::class) val discounts: Money? = null,
    @Serializable(with = MoneySerializer::class) val adjustments: Money? = null,
    @Serializable(with = MoneySerializer::class) val refunds: Money? = null,
    @SerialName("delivery_revenue")
    @Serializable(with = MoneySerializer::class)
    val deliveryRevenue: Money? = null,
    @Serializable(with = MoneySerializer::class) val net: Money? = null,
    @Serializable(with = MoneySerializer::class) val aov: Money? = null,
)

/**
 * One row of `public.admin_order_history(p_from, p_to, p_limit, p_offset)`.
 *
 * Full transparency for reconciliation: every figure the server holds for the
 * order is carried through, including the adjustment (rejected-item) amount and
 * the item count, so the owner can explain any total without opening the order.
 *
 * `customerName` is `""` when the order has no linked profile — the RPC
 * coalesces it, so the client never has to decide what a null name means.
 */
@Serializable
data class OrderHistoryDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_status") val orderStatus: String? = null,
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("final_total")
    @Serializable(with = MoneySerializer::class)
    val finalTotal: Money? = null,
    @Serializable(with = MoneySerializer::class) val subtotal: Money? = null,
    @SerialName("delivery_fee")
    @Serializable(with = MoneySerializer::class)
    val deliveryFee: Money? = null,
    @SerialName("adjustment_amount")
    @Serializable(with = MoneySerializer::class)
    val adjustmentAmount: Money? = null,
    @SerialName("item_count") val itemCount: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
)
