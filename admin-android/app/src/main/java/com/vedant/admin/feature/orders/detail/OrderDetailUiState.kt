package com.vedant.admin.feature.orders.detail

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.usecase.OrderTransitions
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase

/** Local state of the "reject item" bottom sheet. */
data class RejectSheetState(
    val itemId: String,
    val itemName: String,
    val reason: String = "",
    val reasonError: String? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

/**
 * Immutable state for the order detail screen.
 *
 * [order] is the **server's** row, re-fetched after every mutation — never a
 * locally computed one. `final_total` and the line totals rendered from it are
 * whatever the server returned (§5.1).
 */
data class OrderDetailUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val order: Order? = null,
    val loadError: AppError? = null,
    val statusAction: ActionState = ActionState.Idle,
    /** The status currently being applied, so the right button shows the spinner. */
    val pendingStatus: OrderStatus? = null,
    val rejectSheet: RejectSheetState? = null,
    /** The item whose reject/restore is in flight, if any. */
    val itemActionInFlightId: String? = null,
    val itemActionError: AppError? = null,
) {

    /** The actions to *offer*; the server remains the authority. */
    val nextStatuses: List<OrderStatus> get() = OrderTransitions.nextStatuses(order?.status)

    val isStatusInFlight: Boolean get() = statusAction is ActionState.InFlight

    /** Items may only be adjusted while the order is non-terminal. */
    val itemsAdjustable: Boolean get() = order?.itemsAdjustable == true
}
