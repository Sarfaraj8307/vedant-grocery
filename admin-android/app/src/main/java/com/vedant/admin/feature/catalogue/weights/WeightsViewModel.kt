package com.vedant.admin.feature.catalogue.weights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft
import com.vedant.admin.domain.repository.WeightRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "Weights"

/**
 * Drives the inline weight-variant editor for one product.
 *
 * A save returns the **server-confirmed variant**, which replaces the row (or is
 * appended for a create). A failure leaves the list untouched and keeps the form
 * open so nothing the operator typed is lost.
 */
class WeightsViewModel(private val weightRepository: WeightRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WeightsUiState())
    val uiState: StateFlow<WeightsUiState> = _uiState.asStateFlow()

    private var started = false

    fun start(productId: String) {
        if (started) return
        started = true
        _uiState.update { it.copy(productId = productId) }
        load()
    }

    fun reload() = load()

    fun openCreate() {
        _uiState.update { it.copy(editing = WeightEditState(), actionError = null) }
    }

    fun openEdit(weight: ProductWeight) {
        _uiState.update { it.copy(editing = WeightEditState.from(weight), actionError = null) }
    }

    fun onLabelChange(value: String) {
        _uiState.update { state -> state.copy(editing = state.editing?.copy(label = value, fieldError = null, error = null)) }
    }

    fun onGramsChange(value: String) {
        _uiState.update { state -> state.copy(editing = state.editing?.copy(grams = value)) }
    }

    fun onPriceChange(value: String) {
        _uiState.update { state -> state.copy(editing = state.editing?.copy(price = value, fieldError = null)) }
    }

    fun onAvailableChange(available: Boolean) {
        _uiState.update { state -> state.copy(editing = state.editing?.copy(isAvailable = available)) }
    }

    fun cancelEditor() {
        if (_uiState.value.editing?.isSaving == true) return
        _uiState.update { it.copy(editing = null) }
    }

    fun save() {
        val state = _uiState.value
        val edit = state.editing ?: return
        if (edit.isSaving) return

        val label = edit.label.trim()
        if (label.isBlank()) {
            _uiState.update { it.copy(editing = edit.copy(fieldError = "A label is required.")) }
            return
        }
        val price = runCatching { Money.parse(edit.price) }.getOrNull()
        if (price == null) {
            _uiState.update { it.copy(editing = edit.copy(fieldError = "Enter a valid price.")) }
            return
        }
        val gramsText = edit.grams.trim()
        val grams = gramsText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (gramsText.isNotEmpty() && grams == null) {
            _uiState.update { it.copy(editing = edit.copy(fieldError = "Weight in grams must be a whole number.")) }
            return
        }

        val draft = ProductWeightDraft(
            id = edit.id,
            productId = state.productId,
            label = label,
            grams = grams,
            price = price,
            isAvailable = edit.isAvailable,
        )

        _uiState.update { it.copy(editing = edit.copy(isSaving = true, fieldError = null, error = null)) }
        viewModelScope.launch {
            when (val outcome = weightRepository.saveWeight(draft)) {
                is Outcome.Success -> {
                    AppLog.i(TAG, "saved variant ${outcome.value.id}")
                    _uiState.update { current ->
                        val others = current.weights.filterNot { it.id == outcome.value.id }
                        val merged = (others + outcome.value).sortedWith(
                            compareBy({ it.grams ?: Int.MAX_VALUE }, { it.label }),
                        )
                        current.copy(weights = merged, editing = null, phase = ContentPhase.Data)
                    }
                }
                is Outcome.Failure -> _uiState.update { current ->
                    current.copy(editing = current.editing?.copy(isSaving = false, error = outcome.error))
                }
            }
        }
    }

    fun delete(weightId: String) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = weightId, actionError = null) }
        viewModelScope.launch {
            when (val outcome = weightRepository.deleteWeight(weightId)) {
                is Outcome.Success -> _uiState.update { current ->
                    current.copy(
                        weights = current.weights.filterNot { it.id == weightId },
                        deletingId = null,
                        editing = current.editing?.takeIf { it.id != weightId },
                    )
                }
                is Outcome.Failure -> _uiState.update { it.copy(deletingId = null, actionError = outcome.error) }
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    private fun load() {
        val productId = _uiState.value.productId
        if (productId.isBlank()) return
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = weightRepository.listWeights(productId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        phase = if (outcome.value.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                        weights = outcome.value,
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
