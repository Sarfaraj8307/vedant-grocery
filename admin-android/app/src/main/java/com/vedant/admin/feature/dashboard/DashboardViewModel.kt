package com.vedant.admin.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.error.ErrorMapper
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DashboardCountsDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The dashboard's at-a-glance numbers, mapped off the wire DTO.
 *
 * Every field is nullable **on purpose**: `null` means "the server has not
 * told us", and the screen renders an em dash for it. Defaulting a missing
 * count to `0` would be a small lie the owner might act on ("no pending
 * orders" when in fact the read failed), so absence stays absence all the way
 * to the pixel.
 */
data class DashboardCounts(
    val todaysOrders: Int? = null,
    val todaysSales: Money? = null,
    val pending: Int? = null,
    val active: Int? = null,
    val ready: Int? = null,
    val outForDelivery: Int? = null,
    val deliveredToday: Int? = null,
)

private fun DashboardCountsDto.toCounts() = DashboardCounts(
    todaysOrders = todaysOrders,
    todaysSales = todaysSales,
    pending = pending,
    active = active,
    ready = ready,
    outForDelivery = outForDelivery,
    deliveredToday = deliveredToday,
)

/**
 * Order acceptance for the dashboard (§9).
 *
 * This is the "one click to stop taking orders" control. It is deliberately on
 * the dashboard rather than buried in store settings: stopping trade is an
 * operational decision an owner makes in seconds, and hunting through a settings
 * form while a queue backs up is the wrong design.
 *
 * [accepting] is `null` until the first read completes, and stays `null` if the
 * read fails — the UI must show "unknown" rather than defaulting to "open" and
 * letting the operator believe orders are live when they are not.
 *
 * [counts] follows the same rule. It is read-only reporting: there is no write
 * path here, so a stale figure can never corrupt anything.
 */
data class DashboardUiState(
    val accepting: Boolean? = null,
    val isSaving: Boolean = false,
    /** Set when closing, because the RPC requires a reason. */
    val message: String? = null,
    val messageIsError: Boolean = false,
    val counts: DashboardCounts? = null,
    val countsLoading: Boolean = false,
)

class DashboardViewModel(private val rpc: AdminRpc) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(countsLoading = it.counts == null) }

            // Two independent reads, so run them concurrently — the dashboard
            // should paint in the time of the slower call, not their sum.
            val countsDeferred = async {
                runCatching { rpc.dashboardCounts() }.getOrNull()
            }
            val accepting = rpc.isAcceptingOrders()
            val counts = countsDeferred.await()

            _uiState.update {
                it.copy(
                    accepting = accepting,
                    // A failed counts read keeps whatever was last shown rather
                    // than blanking the card; only a first-ever failure leaves
                    // it null, which the screen renders as unknown.
                    counts = counts?.toCounts() ?: it.counts,
                    countsLoading = false,
                )
            }
        }
    }

    /**
     * Flip acceptance. Closing requires [reason]; the server rejects a close
     * without one (`22023`), so we check first to give a local message instead of
     * a round trip that ends in an error.
     */
    fun setAccepting(accepting: Boolean, reason: String?) {
        if (_uiState.value.isSaving) return
        val trimmed = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (!accepting && trimmed == null) {
            _uiState.update {
                it.copy(message = "A reason is required to stop taking orders.", messageIsError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching { rpc.setOrderAcceptance(accepting, trimmed) }
                .onSuccess {
                    // Re-read: the server owns the state, never assume the flip worked.
                    val now = rpc.isAcceptingOrders()
                    _uiState.update {
                        it.copy(
                            accepting = now,
                            isSaving = false,
                            message = if (accepting) {
                                "Accepting orders again."
                            } else {
                                "Orders are now closed. Existing orders continue normally."
                            },
                            messageIsError = false,
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = ErrorCopy.of(ErrorMapper.map(t)),
                            messageIsError = true,
                        )
                    }
                }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
