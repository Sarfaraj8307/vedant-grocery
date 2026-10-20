package com.vedant.admin.feature.catalogue.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "Categories"

/**
 * Drives the categories screen: list, add/edit sheet and delete confirmation.
 *
 * Every write returns the **server-confirmed row**; the list is rebuilt from the
 * confirmed row rather than patched locally. On a failure the list is untouched
 * and the error is surfaced.
 */
class CategoriesViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun reload() = load()

    // --- Editor -------------------------------------------------------------

    fun openCreate() {
        _uiState.update { it.copy(edit = CategoryEditState(), actionError = null) }
    }

    fun openEdit(category: Category) {
        _uiState.update { it.copy(edit = CategoryEditState.from(category), actionError = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { state -> state.copy(edit = state.edit?.copy(name = value, nameError = null, error = null)) }
    }

    fun onGlyphChange(value: String) {
        _uiState.update { state -> state.copy(edit = state.edit?.copy(glyph = value)) }
    }

    fun onSortOrderChange(value: String) {
        _uiState.update { state -> state.copy(edit = state.edit?.copy(sortOrder = value)) }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.update { state -> state.copy(edit = state.edit?.copy(isActive = active)) }
    }

    fun dismissEditor() {
        if (_uiState.value.edit?.isSaving == true) return
        _uiState.update { it.copy(edit = null) }
    }

    fun save() {
        val edit = _uiState.value.edit ?: return
        if (edit.isSaving) return

        val name = edit.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(edit = edit.copy(nameError = "A name is required.")) }
            return
        }

        val draft = CategoryDraft(
            id = edit.id,
            name = name,
            glyph = edit.glyph.trim().takeIf { it.isNotEmpty() },
            sortOrder = edit.sortOrder.trim().toIntOrNull() ?: 0,
            isActive = edit.isActive,
        )

        _uiState.update { it.copy(edit = edit.copy(isSaving = true, error = null, nameError = null)) }
        viewModelScope.launch {
            val outcome = if (edit.isCreate) {
                categoryRepository.createCategory(draft)
            } else {
                categoryRepository.updateCategory(draft)
            }
            when (outcome) {
                is Outcome.Success -> {
                    AppLog.i(TAG, "saved category ${outcome.value.id}")
                    _uiState.update { it.copy(edit = null) }
                    load()
                }
                is Outcome.Failure -> _uiState.update { state ->
                    state.copy(edit = state.edit?.copy(isSaving = false, error = outcome.error))
                }
            }
        }
    }

    // --- Delete -------------------------------------------------------------

    fun requestDelete(category: Category) {
        _uiState.update { it.copy(pendingDelete = category, actionError = null) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val category = _uiState.value.pendingDelete ?: return
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(pendingDelete = null, deletingId = category.id, actionError = null) }
        viewModelScope.launch {
            when (val outcome = categoryRepository.deleteCategory(category.id)) {
                is Outcome.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            categories = state.categories.filterNot { it.id == category.id },
                            deletingId = null,
                        )
                    }
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(deletingId = null, actionError = outcome.error)
                }
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    // --- Load ---------------------------------------------------------------

    private fun load() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = categoryRepository.listCategories()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        phase = if (outcome.value.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                        categories = outcome.value,
                        loadError = null,
                    )
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = outcome.error)
                }
            }
        }
    }
}
