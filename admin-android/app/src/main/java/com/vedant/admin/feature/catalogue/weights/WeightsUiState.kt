package com.vedant.admin.feature.catalogue.weights

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.ui.components.ContentPhase

/** The inline form for one weight variant. `id == null` means "create". */
data class WeightEditState(
    val id: String? = null,
    val label: String = "",
    val grams: String = "",
    val price: String = "",
    val isAvailable: Boolean = true,
    val isSaving: Boolean = false,
    val fieldError: String? = null,
    val error: AppError? = null,
) {

    val isCreate: Boolean get() = id == null

    companion object {
        fun from(weight: ProductWeight): WeightEditState = WeightEditState(
            id = weight.id,
            label = weight.label,
            grams = weight.grams?.toString().orEmpty(),
            price = weight.price.toPlainString(),
            isAvailable = weight.isAvailable,
        )
    }
}

/**
 * Immutable state for the weight-variant editor.
 *
 * [editing] drives which row is expanded into a form (or the trailing "new
 * variant" form when its id is null). A failed save leaves the list untouched.
 */
data class WeightsUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val productId: String = "",
    val weights: List<ProductWeight> = emptyList(),
    val loadError: AppError? = null,
    val editing: WeightEditState? = null,
    val deletingId: String? = null,
    val actionError: AppError? = null,
) {

    val isBusy: Boolean get() = editing?.isSaving == true || deletingId != null
}
