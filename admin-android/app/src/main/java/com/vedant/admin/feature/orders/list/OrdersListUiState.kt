package com.vedant.admin.feature.orders.list

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderSummary
import com.vedant.admin.ui.components.ContentPhase

/**
 * Immutable state for the orders list.
 *
 * [phase] is one of the four explicit content states (§13); there is no implicit
 * "empty list means empty state". [isRefreshing] and [isLoadingMore] are separate
 * from [phase] so a pull-to-refresh keeps the current rows on screen while it
 * runs, and a failed "load more" never blanks the list.
 */
data class OrdersListUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val orders: List<OrderSummary> = emptyList(),
    val filter: OrderFilter = OrderFilter(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /** A failure that replaced the list (first page). */
    val error: AppError? = null,
    /** A failure that only affected "load more" — the list stays visible. */
    val loadMoreError: AppError? = null,
) {

    val isFiltered: Boolean get() = filter.isActive

    val isEmptyBecauseFiltered: Boolean get() = phase == ContentPhase.Empty && isFiltered
}
