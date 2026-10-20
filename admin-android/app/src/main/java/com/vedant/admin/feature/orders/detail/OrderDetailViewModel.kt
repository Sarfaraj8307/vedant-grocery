package com.vedant.admin.feature.orders.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderItem
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.repository.OrderRepository
import com.vedant.admin.domain.usecase.OrderTransitions
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OrderDetail"

/**
 * Drives the order detail screen and its three mutations.
 *
 * Every rule the brief calls "non-negotiable" lives here, not in the composable:
 *  - **Never fake success** — a mutation's result is only rendered once the
 *    repository returns the server's re-fetched row. On failure the existing
 *    [OrderDetailUiState.order] is left exactly as it was.
 *  - **No double-submit** — while a status change is [ActionState.InFlight] the
 *    actions are refused and the buttons disable themselves.
 *  - **The UI's transition list is a convenience** — a move the client would not
 *    offer is never sent, and a move the server rejects is surfaced, not assumed.
 */
class OrderDetailViewModel(private val orderRepository: OrderRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    private var orderId: String? = null

    /** Idempotent: binds the screen to an order id and loads it once. */
    fun start(id: String) {
        if (orderId == id) return
        orderId = id
        load()
    }

    fun reload() = load()

    // --- Status workflow ----------------------------------------------------

    /**
     * Applies a status change. [reason] is required for a cancellation and is
     * validated locally for fast feedback — the server validates it again.
     */
    fun advanceTo(status: OrderStatus, reason: String? = null) {
        val state = _uiState.value
        if (state.statusAction is ActionState.InFlight) return

        val current = state.order?.status ?: return
        if (!OrderTransitions.canTransition(current, status)) {
            AppLog.w(TAG, "ignoring transition $current -> $status (not offered)")
            return
        }
        OrderTransitions.reasonProblem(status, reason)?.let { problem ->
            _uiState.update { it.copy(statusAction = ActionState.Failed(AppError.Validation(problem))) }
            return
        }

        applyStatus(status, reason?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun applyStatus(status: OrderStatus, reason: String?) {
        val id = orderId ?: return
        _uiState.update {
            it.copy(
                statusAction = ActionState.InFlight,
                pendingStatus = status,
                itemActionError = null,
            )
        }
        viewModelScope.launch {
            when (val outcome = orderRepository.updateStatus(id, status, reason)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        phase = ContentPhase.Data,
                        order = outcome.value,
                        loadError = null,
                        statusAction = ActionState.Idle,
                        pendingStatus = null,
                    )
                }
                // Failure: the order is untouched, so the UI does not move.
                is Outcome.Failure -> _uiState.update {
                    it.copy(statusAction = ActionState.Failed(outcome.error), pendingStatus = null)
                }
            }
        }
    }

    // --- Item reject / restore ----------------------------------------------

    fun openRejectSheet(item: OrderItem) {
        if (_uiState.value.order?.itemsAdjustable != true) return
        _uiState.update {
            it.copy(
                rejectSheet = RejectSheetState(itemId = item.id, itemName = item.productName),
                itemActionError = null,
            )
        }
    }

    fun onRejectReasonChange(reason: String) {
        _uiState.update { state ->
            state.copy(rejectSheet = state.rejectSheet?.copy(reason = reason, reasonError = null, error = null))
        }
    }

    fun dismissRejectSheet() {
        _uiState.update { it.copy(rejectSheet = null) }
    }

    fun submitReject() {
        val sheet = _uiState.value.rejectSheet ?: return
        if (sheet.isSubmitting) return

        val reason = sheet.reason.trim()
        if (reason.isEmpty()) {
            _uiState.update { it.copy(rejectSheet = sheet.copy(reasonError = "A reason is required.")) }
            return
        }

        val id = orderId ?: return
        _uiState.update { it.copy(rejectSheet = sheet.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            when (val outcome = orderRepository.rejectItem(sheet.itemId, reason)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(order = outcome.value, rejectSheet = null, itemActionError = null)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(rejectSheet = it.rejectSheet?.copy(isSubmitting = false, error = outcome.error))
                }
            }
        }
    }

    fun restoreItem(itemId: String) {
        if (_uiState.value.itemActionInFlightId != null) return
        val id = orderId ?: return
        _uiState.update { it.copy(itemActionInFlightId = itemId, itemActionError = null) }
        viewModelScope.launch {
            when (val outcome = orderRepository.restoreItem(itemId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(order = outcome.value, itemActionInFlightId = null, itemActionError = null)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(itemActionInFlightId = null, itemActionError = outcome.error)
                }
            }
        }
    }

    /** Clears a surfaced failure once the operator has read it. */
    fun dismissActionError() {
        _uiState.update { it.copy(statusAction = ActionState.Idle, itemActionError = null) }
    }

    // --- Load ---------------------------------------------------------------

    private fun load() {
        val id = orderId ?: return
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = orderRepository.getOrder(id)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(phase = ContentPhase.Data, order = outcome.value, loadError = null)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = outcome.error)
                }
            }
        }
    }
}
