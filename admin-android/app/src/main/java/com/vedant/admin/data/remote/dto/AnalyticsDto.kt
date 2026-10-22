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
 * One row of `public.admin_sales_today()`.
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
