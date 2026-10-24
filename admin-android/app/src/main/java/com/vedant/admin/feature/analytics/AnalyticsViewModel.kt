package com.vedant.admin.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DashboardCountsDto
import com.vedant.admin.data.remote.dto.OrderHistoryDto
import com.vedant.admin.data.remote.dto.SalesTodayDto
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Immutable state for the analytics screen. */
data class AnalyticsUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val snapshot: AnalyticsSnapshot = AnalyticsSnapshot.EMPTY,
    val range: ReportRange = ReportRange.TODAY,
    val isRefreshing: Boolean = false,
)

/**
 * Loads reporting numbers for a chosen window.
 *
 * Three RPCs, all gated on `private.can_manage_*()` server-side, so the screen is
 * read-only by construction: there is no write path here to authorise.
 *
 *  • `admin_dashboard_counts()`  — the live pipeline and complaint counts.
 *  • `admin_sales_range()`       — per-day money for the window (the source of
 *                                  every total on the screen).
 *  • `admin_order_history()`     — the individual orders behind those totals.
 *
 * All money is taken as the server computed it. This class only *sums* the
 * per-day rows into a range total, and it does that in integer paise, so a
 * displayed figure cannot drift from the database by rounding.
 *
 * The pipeline is deliberately **not** range-filtered: "how many orders are
 * stuck right now" is a present-tense question, and answering it for last month
 * would be meaningless. The screen labels that section "Right now" so the two
 * kinds of number are never confused.
 */
class AnalyticsViewModel(private val rpc: AdminRpc) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun refresh() = load()

    /** Switch the window. Re-reads rather than filtering locally — the server owns the arithmetic. */
    fun selectRange(range: ReportRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        load()
    }

    private fun load() {
        val range = _uiState.value.range
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = it.phase == ContentPhase.Data,
                    phase = if (it.phase == ContentPhase.Data) it.phase else ContentPhase.Loading,
                )
            }

            val to = AppClock.todayIstDate()
            val from = shiftDays(to, -(range.days - 1))

            runCatching {
                Triple(
                    rpc.dashboardCounts(),
                    rpc.salesRange(from, to),
                    rpc.orderHistory(from, to, HISTORY_LIMIT, 0),
                )
            }
                .onSuccess { (counts, days, history) ->
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Data,
                            snapshot = combine(counts, days, history),
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(phase = ContentPhase.Error, isRefreshing = false) }
                }
        }
    }

    private companion object {
        /** Enough to audit a day; a month's orders are paged by scrolling, not loaded whole. */
        const val HISTORY_LIMIT = 100
    }
}

/** `YYYY-MM-DD` shifted by [days] (negative = earlier), in the store's calendar. */
private fun shiftDays(isoDate: String, days: Int): String {
    val date = LocalDate.parse(isoDate)
    return date.plus(DatePeriod(days = days)).toString()
}

/**
 * Merges the three RPC results into one snapshot.
 *
 * The range totals are the **sum of the per-day rows**, not a second server call,
 * so the totals row and the per-day list can never disagree — they are the same
 * numbers added up. The sum is in integer paise, which is exact.
 *
 * Order matters for the pipeline: it is listed in the order an order actually
 * moves through the store, so the bars read top-to-bottom as a story rather than
 * as an arbitrary list of numbers.
 */
private fun combine(
    counts: DashboardCountsDto?,
    days: List<SalesTodayDto>,
    history: List<OrderHistoryDto>,
): AnalyticsSnapshot {
    val dayTotals = days.map { row ->
        DayTotal(
            date = row.orderDate.orEmpty(),
            orders = row.orders ?: 0,
            gross = row.gross ?: Money.ZERO,
            discounts = row.discounts ?: Money.ZERO,
            adjustments = row.adjustments ?: Money.ZERO,
            refunds = row.refunds ?: Money.ZERO,
            deliveryRevenue = row.deliveryRevenue ?: Money.ZERO,
            net = row.net ?: Money.ZERO,
            aov = row.aov ?: Money.ZERO,
        )
    }

    val orders = dayTotals.sumOf { it.orders }
    val gross = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.gross }
    val discounts = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.discounts }
    val adjustments = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.adjustments }
    val refunds = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.refunds }
    val deliveryRevenue = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.deliveryRevenue }
    val net = dayTotals.fold(Money.ZERO) { acc, d -> acc + d.net }

    // AOV is net / orders. Computed from the already-exact totals, in paise.
    val aov = if (orders > 0) Money(net.paise / orders) else Money.ZERO

    return AnalyticsSnapshot(
        pipeline = listOf(
            PipelineCount("Pending", counts?.pending ?: 0, PipelineAccent.ATTENTION),
            PipelineCount("Active", counts?.active ?: 0, PipelineAccent.NEUTRAL),
            PipelineCount("Ready", counts?.ready ?: 0, PipelineAccent.NEUTRAL),
            PipelineCount("Out for delivery", counts?.outForDelivery ?: 0, PipelineAccent.NEUTRAL),
            PipelineCount("Delivered today", counts?.deliveredToday ?: 0, PipelineAccent.POSITIVE),
            PipelineCount("Cancelled", counts?.cancelled ?: 0, PipelineAccent.NEGATIVE),
        ),
        orders = orders,
        gross = gross,
        discounts = discounts,
        adjustments = adjustments,
        refunds = refunds,
        deliveryRevenue = deliveryRevenue,
        net = net,
        aov = aov,
        dailyTotals = dayTotals,
        history = history.map { row ->
            OrderHistoryRow(
                orderId = row.orderId,
                status = row.orderStatus.orEmpty(),
                placedAt = row.placedAt,
                finalTotal = row.finalTotal ?: Money.ZERO,
                subtotal = row.subtotal ?: Money.ZERO,
                deliveryFee = row.deliveryFee ?: Money.ZERO,
                adjustmentAmount = row.adjustmentAmount ?: Money.ZERO,
                itemCount = row.itemCount ?: 0,
                customerName = row.customerName.orEmpty(),
            )
        },
        pendingComplaints = counts?.pendingComplaints ?: 0,
        openComplaints = counts?.openComplaints ?: 0,
    )
}
