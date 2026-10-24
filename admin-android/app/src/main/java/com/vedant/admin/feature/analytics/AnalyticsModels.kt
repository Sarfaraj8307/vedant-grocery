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
 * The reporting window.
 *
 * [days] is the length of the inclusive range ending today, so `TODAY` is a
 * single-day range — the same query shape as the others, which is what keeps
 * today's numbers and last month's numbers directly comparable.
 */
enum class ReportRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ;

    /** Short caption under the totals, e.g. "1 day" / "7 days". */
    val caption: String get() = if (days == 1) "1 day" else "$days days"
}

/**
 * One day's money, straight from `admin_sales_range()`.
 *
 * A day with no orders still appears, with zeros — the server generates the date
 * series, so an absent day would mean "the query is broken", never "nothing sold".
 */
data class DayTotal(
    val date: String,
    val orders: Int,
    val gross: Money,
    val discounts: Money,
    val adjustments: Money,
    val refunds: Money,
    val deliveryRevenue: Money,
    val net: Money,
    val aov: Money,
)

/**
 * One order in the history list, with every figure the server holds.
 *
 * This is the "full transparency" part of reporting: the totals above are an
 * aggregate, and this is the evidence behind them. Nothing here is recomputed on
 * the device, so the sum of this list reconciles with the totals row.
 */
data class OrderHistoryRow(
    val orderId: String,
    val status: String,
    val placedAt: String?,
    val finalTotal: Money,
    val subtotal: Money,
    val deliveryFee: Money,
    val adjustmentAmount: Money,
    val itemCount: Int,
    val customerName: String,
)

/**
 * Everything the analytics screen shows, already resolved.
 *
 * The money totals are the **sum of [dailyTotals]**, which the server computed
 * per day; the app adds them only to show one range total, and it adds them as
 * integer paise so the sum cannot drift by rounding.
 *
 * [pipeline] and the complaint counts are *live* operational figures from
 * `admin_dashboard_counts()` — they describe "right now", not the range. The
 * screen labels them accordingly so the two kinds of number are never confused.
 */
data class AnalyticsSnapshot(
    val pipeline: List<PipelineCount>,
    val orders: Int,
    val gross: Money,
    val discounts: Money,
    val adjustments: Money,
    val refunds: Money,
    val deliveryRevenue: Money,
    val net: Money,
    val aov: Money,
    val dailyTotals: List<DayTotal>,
    val history: List<OrderHistoryRow>,
    val pendingComplaints: Int,
    val openComplaints: Int,
) {
    /** The busiest day in the range, or `null` when nothing sold. */
    val bestDay: DayTotal? get() = dailyTotals.maxByOrNull { it.net.paise }

    companion object {
        val EMPTY = AnalyticsSnapshot(
            pipeline = emptyList(),
            orders = 0,
            gross = Money.ZERO,
            discounts = Money.ZERO,
            adjustments = Money.ZERO,
            refunds = Money.ZERO,
            deliveryRevenue = Money.ZERO,
            net = Money.ZERO,
            aov = Money.ZERO,
            dailyTotals = emptyList(),
            history = emptyList(),
            pendingComplaints = 0,
            openComplaints = 0,
        )
    }
}
