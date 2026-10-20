package com.vedant.admin.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.core.error.ErrorMapper
import com.vedant.admin.data.remote.AdminRpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 */
data class DashboardUiState(
    val accepting: Boolean? = null,
    val isSaving: Boolean = false,
    /** Set when closing, because the RPC requires a reason. */
    val message: String? = null,
    val messageIsError: Boolean = false,
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
            val accepting = rpc.isAcceptingOrders()
            _uiState.update { it.copy(accepting = accepting) }
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
