package com.vedant.admin.feature.catalogue.categories

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.Category
import com.vedant.admin.ui.components.ContentPhase

/** The add/edit form for one category, shown in a bottom sheet. */
data class CategoryEditState(
    val id: String? = null,
    val name: String = "",
    val glyph: String = "",
    val sortOrder: String = "0",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val nameError: String? = null,
    val error: AppError? = null,
) {

    val isCreate: Boolean get() = id == null

    companion object {
        fun from(category: Category): CategoryEditState = CategoryEditState(
            id = category.id,
            name = category.name,
            glyph = category.glyph.orEmpty(),
            sortOrder = category.sortOrder.toString(),
            isActive = category.isActive,
        )
    }
}

/**
 * Immutable state for the categories screen.
 *
 * [deletingId] and [edit.isSaving] block a double-submit; a failed write leaves
 * the list exactly as the server last returned it and surfaces [actionError].
 */
data class CategoriesUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val categories: List<Category> = emptyList(),
    val loadError: AppError? = null,
    val edit: CategoryEditState? = null,
    val pendingDelete: Category? = null,
    val deletingId: String? = null,
    val actionError: AppError? = null,
) {

    val isBusy: Boolean get() = edit?.isSaving == true || deletingId != null
}
