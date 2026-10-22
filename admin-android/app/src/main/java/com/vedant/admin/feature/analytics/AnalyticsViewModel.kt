package com.vedant.admin.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DashboardCountsDto
import com.vedant.admin.data.remote.dto.SalesTodayDto
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the analytics screen. */
data class AnalyticsUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val snapshot: AnalyticsSnapshot = AnalyticsSnapshot.EMPTY,
    val isRefreshing: Boolean = false,
)

/**
 * Loads today's numbers (brief §28 — reporting).
 *
 * Two RPCs, both gated on `private.can_manage_orders()`, so the screen is
 * read-only by construction: there is no write path here to authorise.
 *
 * All money is taken as the server computed it — this class performs no
 * arithmetic on amounts, so a displayed figure cannot drift from the database.
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

    private fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = it.phase == ContentPhase.Data,
                    phase = if (it.phase == ContentPhase.Data) it.phase else ContentPhase.Loading,
                )
            }
            runCatching { rpc.dashboardCounts() to rpc.salesToday() }
                .onSuccess { (counts, sales) ->
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Data,
                            snapshot = combine(counts, sales),
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(phase = ContentPhase.Error, isRefreshing = false) }
                }
        }
    }
}

/**
 * Merges the two RPC rows into one snapshot.
 *
 * Order matters: the pipeline is listed in the order an order actually moves
 * through the store, so the bars read top-to-bottom as a story rather than as an
 * arbitrary list of numbers.
 */
private fun combine(
    counts: DashboardCountsDto?,
    sales: SalesTodayDto?,
): AnalyticsSnapshot = AnalyticsSnapshot(
    pipeline = listOf(
        PipelineCount("Pending", counts?.pending ?: 0, PipelineAccent.ATTENTION),
        PipelineCount("Active", counts?.active ?: 0, PipelineAccent.NEUTRAL),
        PipelineCount("Ready", counts?.ready ?: 0, PipelineAccent.NEUTRAL),
        PipelineCount("Out for delivery", counts?.outForDelivery ?: 0, PipelineAccent.NEUTRAL),
        PipelineCount("Delivered today", counts?.deliveredToday ?: 0, PipelineAccent.POSITIVE),
        PipelineCount("Cancelled", counts?.cancelled ?: 0, PipelineAccent.NEGATIVE),
    ),
    todaysOrders = counts?.todaysOrders ?: sales?.orders ?: 0,
    todaysSales = counts?.todaysSales ?: sales?.net ?: Money.ZERO,
    gross = sales?.gross ?: Money.ZERO,
    discounts = sales?.discounts ?: Money.ZERO,
    adjustments = sales?.adjustments ?: Money.ZERO,
    refunds = sales?.refunds ?: Money.ZERO,
    deliveryRevenue = sales?.deliveryRevenue ?: Money.ZERO,
    net = sales?.net ?: Money.ZERO,
    aov = sales?.aov ?: Money.ZERO,
    pendingComplaints = counts?.pendingComplaints ?: 0,
    openComplaints = counts?.openComplaints ?: 0,
)
