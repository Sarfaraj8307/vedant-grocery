package com.vedant.admin.feature.catalogue.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.repository.OfferRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

private const val TAG = "Offers"

/**
 * Drives the offers screen for one product.
 *
 * The window and the discount are validated locally for fast feedback (the
 * repository validates them again, and the server a third time). Every write
 * returns the **server-confirmed row**; the list is re-read afterwards.
 */
class OffersViewModel(private val offerRepository: OfferRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OffersUiState())
    val uiState: StateFlow<OffersUiState> = _uiState.asStateFlow()

    private var started = false

    fun start(productId: String?) {
        if (started) return
        started = true
        _uiState.update { it.copy(productId = productId?.takeIf { it.isNotBlank() }) }
        load()
    }

    fun reload() = load()

    // --- Editor -------------------------------------------------------------

    fun openCreate() {
        _uiState.update { it.copy(editor = OfferEditState(), actionError = null) }
    }

    fun openEdit(offer: Offer) {
        _uiState.update { it.copy(editor = OfferEditState.from(offer), actionError = null) }
    }

    fun onTitleChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(title = value, fieldError = null, error = null)) }
    }

    fun onDiscountChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(discountPercent = value, fieldError = null)) }
    }

    fun onPriceChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(price = value, fieldError = null)) }
    }

    fun onStartsAtChange(instant: Instant?) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(startsAt = instant, fieldError = null)) }
    }

    fun onEndsAtChange(instant: Instant?) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(endsAt = instant, fieldError = null)) }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(isActive = active)) }
    }

    fun dismissEditor() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    fun save() {
        val state = _uiState.value
        val edit = state.editor ?: return
        if (edit.isSaving) return

        val productId = state.productId
        if (productId.isNullOrBlank()) {
            _uiState.update { it.copy(editor = edit.copy(error = AppError.Validation("Open offers from a product."))) }
            return
        }

        val title = edit.title.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "A title is required.")) }
            return
        }

        val discountText = edit.discountPercent.trim()
        val discount = discountText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (discountText.isNotEmpty() && (discount == null || discount !in 1..99)) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "A discount must be between 1% and 99%.")) }
            return
        }

        val priceText = edit.price.trim()
        val price = priceText.takeIf { it.isNotEmpty() }?.let { runCatching { Money.parse(it) }.getOrNull() }
        if (priceText.isNotEmpty() && price == null) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "Enter a valid price.")) }
            return
        }

        if (discount == null && price == null) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "Set a discount % or a price.")) }
            return
        }

        if (edit.startsAt != null && edit.endsAt != null && edit.endsAt <= edit.startsAt) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "The end date must be after the start date.")) }
            return
        }

        val draft = OfferDraft(
            id = edit.id,
            productId = productId,
            title = title,
            price = price,
            discountPercent = discount,
            startsAt = edit.startsAt,
            endsAt = edit.endsAt,
            isActive = edit.isActive,
        )

        _uiState.update { it.copy(editor = edit.copy(isSaving = true, fieldError = null, error = null)) }
        viewModelScope.launch {
            val outcome = if (edit.isCreate) {
                offerRepository.createOffer(draft)
            } else {
                offerRepository.updateOffer(draft)
            }
            when (outcome) {
                is Outcome.Success -> {
                    AppLog.i(TAG, "saved offer ${outcome.value.id}")
                    _uiState.update { it.copy(editor = null) }
                    load()
                }
                is Outcome.Failure -> _uiState.update { current ->
                    current.copy(editor = current.editor?.copy(isSaving = false, error = outcome.error))
                }
            }
        }
    }

    // --- Delete -------------------------------------------------------------

    fun delete(offerId: String) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = offerId, actionError = null) }
        viewModelScope.launch {
            when (val outcome = offerRepository.deleteOffer(offerId)) {
                is Outcome.Success -> _uiState.update { current ->
                    current.copy(
                        offers = current.offers.filterNot { it.id == offerId },
                        deletingId = null,
                    )
                }
                is Outcome.Failure -> _uiState.update { it.copy(deletingId = null, actionError = outcome.error) }
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
            when (val outcome = offerRepository.listOffers(_uiState.value.productId)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        phase = if (outcome.value.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                        offers = outcome.value,
                        now = AppClock.now(),
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
