package com.vedant.admin.feature.catalogue.hub

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductQuery
import com.vedant.admin.ui.components.ContentPhase

/**
 * Immutable state for the catalogue hub — the scannable product list.
 *
 * [phase] is one of the four explicit content states (§13); there is no implicit
 * "empty list means empty". [isRefreshing] / [isLoadingMore] are separate from
 * [phase] so a refresh keeps the rows on screen and a failed "load more" never
 * blanks the list.
 */
data class CatalogueHubUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: ProductQuery = ProductQuery.NONE,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /** A failure that replaced the list (first page). */
    val error: AppError? = null,
    /** A failure that only affected "load more". */
    val loadMoreError: AppError? = null,
    /** The product whose availability toggle is in flight, if any. */
    val togglingProductId: String? = null,
    /** A failure from a quick action (availability toggle), shown as a banner. */
    val actionError: AppError? = null,
) {

    val isFiltered: Boolean get() = query.isActive

    val isEmptyBecauseFiltered: Boolean get() = phase == ContentPhase.Empty && isFiltered

    /** The category name for a product, or `null` when it is uncategorised. */
    fun categoryName(categoryId: String?): String? =
        categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
}
