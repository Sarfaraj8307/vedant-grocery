package com.vedant.admin.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorMapper
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
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
                        actionMessage = com.vedant.admin.core.error.ErrorCopy.of(error),
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
