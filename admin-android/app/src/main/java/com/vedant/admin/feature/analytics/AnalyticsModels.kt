package com.vedant.admin.feature.analytics

import com.vedant.admin.core.money.Money

/**
 * One number in the operational pipeline.
 *
 * [label] is what the owner reads; [count] is the value. Kept separate from the
 * bar drawing so the ordering and the colour are decided in one place.
 */
data class PipelineCount(
    val label: String,
    val count: Int,
    val accentKey: PipelineAccent,
)

enum class PipelineAccent { NEUTRAL, ATTENTION, POSITIVE, NEGATIVE }

/**
 * Everything the analytics screen shows, already resolved.
 *
 * Assembled server-side by `admin_dashboard_counts()` + `admin_sales_today()` —
 * the app does no arithmetic on money, so a figure on screen always matches what
 * the database computed.
 */
data class AnalyticsSnapshot(
    val pipeline: List<PipelineCount>,
    val todaysOrders: Int,
    val todaysSales: Money,
    val gross: Money,
    val discounts: Money,
    val adjustments: Money,
    val refunds: Money,
    val deliveryRevenue: Money,
    val net: Money,
    val aov: Money,
    val pendingComplaints: Int,
    val openComplaints: Int,
) {
    companion object {
        val EMPTY = AnalyticsSnapshot(
            pipeline = emptyList(),
            todaysOrders = 0,
            todaysSales = Money.ZERO,
            gross = Money.ZERO,
            discounts = Money.ZERO,
            adjustments = Money.ZERO,
            refunds = Money.ZERO,
            deliveryRevenue = Money.ZERO,
            net = Money.ZERO,
            aov = Money.ZERO,
            pendingComplaints = 0,
            openComplaints = 0,
        )
    }
}
