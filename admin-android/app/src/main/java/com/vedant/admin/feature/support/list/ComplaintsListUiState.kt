package com.vedant.admin.feature.support.list

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.Complaint
import com.vedant.admin.domain.model.ComplaintFilter
import com.vedant.admin.ui.components.ContentPhase

/**
 * Immutable state for the complaints list.
 *
 * [phase] is one of the four explicit content states (§13) — there is no implicit
 * "empty list means empty state". [isRefreshing] and [isLoadingMore] are separate
 * from [phase] so a pull-to-refresh keeps the current rows on screen, and a
 * failed "load more" never blanks the list.
 */
data class ComplaintsListUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val complaints: List<Complaint> = emptyList(),
    val filter: ComplaintFilter = ComplaintFilter.NONE,
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
