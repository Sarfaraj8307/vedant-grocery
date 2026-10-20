package com.vedant.admin.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.error.ErrorMapper
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.domain.model.DeliveryIssueReport
import com.vedant.admin.domain.model.DeliveryIssueType
import com.vedant.admin.domain.model.DeliveryOrder
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the courier queue. */
data class DeliveryUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val orders: List<DeliveryOrder> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    /** Non-null while a status change is in flight, so buttons can lock. */
    val busyOrderId: String? = null,
    /** A one-shot message for the operator (e.g. a refused transition). */
    val actionMessage: String? = null,

    // --- Report issue (§25) ---------------------------------------------------
    /** The order the issue dialog is open for; `null` means the dialog is closed. */
    val issueOrderId: String? = null,
    /** The draft issue type in the open dialog. */
    val issueType: DeliveryIssueType = DeliveryIssueType.DEFAULT,
    /** The draft reason in the open dialog. */
    val issueReason: String = "",
    /** The inline validation/refusal copy for the open dialog. */
    val issueError: String? = null,
    /** True while the report is being written, so the dialog locks. */
    val issueBusy: Boolean = false,
)

/**
 * Drives the courier queue (§21).
 *
 * Everything authoritative happens server-side: `delivery_orders()` decides which
 * rows this caller may see, and `admin_update_order_status()` re-validates the
 * transition and recomputes any money. This ViewModel only sequences those calls
 * and renders what comes back — it never filters for authorization and never
 * assumes a transition succeeded.
 */
class DeliveryViewModel(private val rpc: AdminRpc) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryUiState())
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    private var started = false

    /** Idempotent first load, so a recomposition does not re-fetch. */
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
                    error = null,
                )
            }
            runCatching { rpc.deliveryOrders(onlyMine = true) }
                .onSuccess { rows ->
                    val orders = rows.map { it.toDomain() }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            phase = if (orders.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                            orders = orders,
                            error = null,
                        )
                    }
                }
                .onFailure { t ->
                    val error = ErrorMapper.map(t)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            phase = ContentPhase.Error,
                            error = error,
                        )
                    }
                }
        }
    }

    /**
     * Moves one order forward. The status string is the server's enum value; the
     * RPC refuses an illegal transition with `22023`, which is surfaced to the
     * operator rather than swallowed.
     */
    fun advance(orderId: String, to: String) {
        if (_uiState.value.busyOrderId != null) return // one mutation at a time
        viewModelScope.launch {
            _uiState.update { it.copy(busyOrderId = orderId, actionMessage = null) }
            runCatching {
                rpc.updateOrderStatus(orderId = orderId, status = to, reason = null)
            }.onSuccess {
                _uiState.update { it.copy(busyOrderId = null) }
                // Re-read rather than patching locally: the server owns the row.
                load()
            }.onFailure { t ->
                val error = ErrorMapper.map(t)
                _uiState.update {
                    it.copy(
                        busyOrderId = null,
                        actionMessage = ErrorCopy.of(error),
                    )
                }
            }
        }
    }

    // --- Report issue (§25) ---------------------------------------------------

    /** Opens the issue dialog for [orderId] with a fresh draft. */
    fun openIssueReport(orderId: String) {
        _uiState.update {
            if (it.issueBusy) {
                it // a submit is in flight; the dialog already owns the screen
            } else {
                it.copy(
                    issueOrderId = orderId,
                    issueType = DeliveryIssueType.DEFAULT,
                    issueReason = "",
                    issueError = null,
                )
            }
        }
    }

    /** Closes the dialog and drops the draft. Ignored while a submit is in flight. */
    fun dismissIssueReport() {
        _uiState.update {
            if (it.issueBusy) {
                it
            } else {
                it.copy(
                    issueOrderId = null,
                    issueReason = "",
                    issueError = null,
                )
            }
        }
    }

    /** Updates the dialog's selected type. */
    fun onIssueTypeSelected(type: DeliveryIssueType) {
        _uiState.update { if (it.issueBusy) it else it.copy(issueType = type, issueError = null) }
    }

    /** Updates the dialog's reason text. */
    fun onIssueReasonChange(reason: String) {
        _uiState.update { if (it.issueBusy) it else it.copy(issueReason = reason, issueError = null) }
    }

    /**
     * Validates the draft and writes it to `delivery_issues`.
     *
     * Validation first: a blank reason never leaves the device, so the
     * operator gets an inline explanation instead of a server round-trip that
     * would fail anyway. On success the dialog closes and the queue's banner
     * confirms the report — the row is served to OWNER/ADMIN by RLS. On failure
     * the dialog stays open with the reason, because "reported" is exactly the
     * kind of success that must never be faked.
     */
    fun submitIssueReport() {
        val current = _uiState.value
        val orderId = current.issueOrderId ?: return
        if (current.issueBusy) return

        val reason = DeliveryIssueReport.validateReason(current.issueReason)
        if (reason == null) {
            _uiState.update {
                it.copy(issueError = "Add a short reason so the store team knows what happened.")
            }
            return
        }

        _uiState.update { it.copy(issueBusy = true, issueError = null) }
        viewModelScope.launch {
            runCatching {
                rpc.reportDeliveryIssue(orderId = orderId, type = current.issueType.wire, reason = reason)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        issueBusy = false,
                        issueOrderId = null,
                        issueReason = "",
                        issueError = null,
                        actionMessage = "Issue reported — the store team can see it on this order.",
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        issueBusy = false,
                        issueError = ErrorCopy.of(ErrorMapper.map(t)),
                    )
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(actionMessage = null) }
}

/** Maps the RPC row to the domain model. Money is the server's own value. */
private fun DeliveryOrderDto.toDomain(): DeliveryOrder = DeliveryOrder(
    orderId = orderId,
    orderNumber = orderNumber,
    status = OrderStatus.fromWire(status),
    statusRaw = status.orEmpty(),
    customerName = customerName?.takeIf { it.isNotBlank() },
    customerPhone = customerPhone?.takeIf { it.isNotBlank() },
    addressLine1 = addressLine1?.takeIf { it.isNotBlank() },
    city = addressCity?.takeIf { it.isNotBlank() },
    pincode = addressPincode?.takeIf { it.isNotBlank() },
    latitude = latitude,
    longitude = longitude,
    itemCount = itemCount ?: 0L,
    amount = finalTotal ?: total ?: Money.ZERO,
    assignedToName = assignedToName?.takeIf { it.isNotBlank() },
)
