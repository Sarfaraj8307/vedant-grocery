package com.vedant.admin.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.domain.model.AppearanceMode
import com.vedant.admin.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the appearance screen (§8).
 *
 * The persisted value is the single source of truth: the screen renders the
 * repository's mode and writes through to it. Nothing is stored locally here —
 * a change the repository refuses to persist simply never appears selected.
 */
class AppearanceViewModel(
    private val appearanceRepository: AppearanceRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = appearanceRepository.mode
        .map { mode -> AppearanceUiState(mode = mode) }
        .stateIn(
            scope = viewModelScope,
            // Eagerly, not WhileSubscribed: the appearance screen reads this
            // exactly once (it is opened, tapped, and closed), and a "stop
            // timeout" race would show a stale selection after a write.
            started = SharingStarted.Eagerly,
            initialValue = AppearanceUiState(mode = appearanceRepository.mode.value),
        )

    /**
     * Persists [mode]. Re-picking the current mode is a no-op, so the radio
     * cannot "save" what is already saved (and the row never shows a spinner
     * for a choice that changes nothing).
     */
    fun select(mode: AppearanceMode) {
        if (uiState.value.mode == mode) return
        viewModelScope.launch {
            appearanceRepository.setMode(mode)
        }
    }
}
