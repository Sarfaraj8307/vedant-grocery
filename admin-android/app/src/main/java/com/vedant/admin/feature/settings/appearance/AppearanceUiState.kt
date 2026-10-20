package com.vedant.admin.feature.settings.appearance

import com.vedant.admin.domain.model.AppearanceMode

/** Immutable state for the appearance screen. */
data class AppearanceUiState(
    /** The currently persisted choice. */
    val mode: AppearanceMode = AppearanceMode.DEFAULT,
    /** True while the chosen mode is being written to storage. */
    val isSaving: Boolean = false,
)
