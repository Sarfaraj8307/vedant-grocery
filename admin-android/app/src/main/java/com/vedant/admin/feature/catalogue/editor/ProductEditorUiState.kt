package com.vedant.admin.feature.catalogue.editor

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase

/** The state of the image slot: idle, uploading (with real progress), or failed. */
sealed interface UploadState {
    data object Idle : UploadState
    data class InFlight(val progress: Float) : UploadState
    data class Failed(val error: AppError) : UploadState
}

/**
 * Immutable state for the product editor.
 *
 * The raw text of the money/quantity fields is kept alongside the parsed
 * [ProductDraft] so a half-typed amount (`4`, `4.`, `4.0`) never throws and never
 * silently becomes a different number. [baseline] is the last **server-confirmed**
 * draft, so [isDirty] is a real comparison against the server rather than a guess.
 */
data class ProductEditorUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val productId: String? = null,
    val draft: ProductDraft = ProductDraft(name = "", basePrice = Money.ZERO),
    val baseline: ProductDraft? = null,
    val categories: List<Category> = emptyList(),
    val weights: List<ProductWeight> = emptyList(),
    val basePriceText: String = "",
    val discountPriceText: String = "",
    val stockText: String = "",
    val quantityLimitText: String = "",
    val nameError: String? = null,
    val priceError: String? = null,
    val saveState: ActionState = ActionState.Idle,
    val loadError: AppError? = null,
    val upload: UploadState = UploadState.Idle,
    /** True once the server has confirmed a save in this session. */
    val savedOnce: Boolean = false,
) {

    val isCreate: Boolean get() = productId == null

    val isSaving: Boolean get() = saveState is ActionState.InFlight

    val isUploading: Boolean get() = upload is UploadState.InFlight

    val hasImage: Boolean get() = !draft.imageUrl.isNullOrBlank()

    /**
     * True when the draft differs from the last server-confirmed row. For a
     * create there is no baseline, so "dirty" means the operator has typed a name.
     */
    val isDirty: Boolean
        get() = if (baseline == null) draft.name.isNotBlank() else draft != baseline

    val canSave: Boolean get() = draft.name.isNotBlank() && isDirty && !isSaving && !isUploading

    val categoryName: String?
        get() = categories.firstOrNull { it.id == draft.categoryId }?.name
}
