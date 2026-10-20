package com.vedant.admin.feature.catalogue.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.repository.CategoryRepository
import com.vedant.admin.domain.repository.ProductRepository
import com.vedant.admin.domain.repository.WeightRepository
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ProductEditor"

/**
 * Drives the product editor.
 *
 * ### Never fake success
 * A save does not clear the form: it calls the repository and adopts the row the
 * server echoed (`Outcome<Product>`), which becomes the new [ProductEditorUiState.baseline].
 * On failure the draft is left exactly as the operator typed it and the error is
 * surfaced on the save bar.
 *
 * ### Images are only "set" once stored
 * An upload returns the **server-confirmed URL**, which is then persisted on the
 * product row (built from the last confirmed [ProductEditorUiState.baseline], so
 * unrelated unsaved edits are never smuggled in). The image counts as set only
 * when that write returns.
 */
class ProductEditorViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductEditorUiState())
    val uiState: StateFlow<ProductEditorUiState> = _uiState.asStateFlow()

    private var started = false

    /** The bytes of the last picked image, kept so a failed upload can be retried. */
    private var pendingImage: ByteArray? = null
    private var pendingMime: String = "image/jpeg"

    /** Idempotent bind: `null` starts a create, an id loads an existing product. */
    fun start(productId: String?) {
        if (started) return
        started = true
        loadCategories()
        if (productId.isNullOrBlank()) {
            _uiState.update { it.copy(phase = ContentPhase.Data, productId = null, baseline = null) }
        } else {
            load(productId)
        }
    }

    // --- Field edits --------------------------------------------------------

    fun onNameChange(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(name = value), nameError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(description = value)) }
    }

    fun onCategoryChange(categoryId: String?) {
        _uiState.update { it.copy(draft = it.draft.copy(categoryId = categoryId)) }
    }

    fun onBasePriceChange(text: String) {
        _uiState.update { it.copy(basePriceText = text, priceError = null) }
    }

    fun onDiscountPriceChange(text: String) {
        _uiState.update { it.copy(discountPriceText = text, priceError = null) }
    }

    fun onUnitChange(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(unit = value)) }
    }

    fun onUnitLabelChange(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(unitLabel = value)) }
    }

    fun onStockChange(text: String) {
        _uiState.update { it.copy(stockText = text) }
    }

    fun onQuantityLimitChange(text: String) {
        _uiState.update { it.copy(quantityLimitText = text) }
    }

    fun onGlyphChange(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(glyph = value)) }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.update { it.copy(draft = it.draft.copy(isActive = active)) }
    }

    fun onAvailabilityChange(available: Boolean) {
        _uiState.update { it.copy(draft = it.draft.copy(availability = available)) }
    }

    fun onFeaturedChange(featured: Boolean) {
        _uiState.update { it.copy(draft = it.draft.copy(isFeatured = featured)) }
    }

    // --- Save ---------------------------------------------------------------

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isUploading) return

        val name = state.draft.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(nameError = "A name is required.") }
            return
        }

        val basePrice = runCatching { Money.parse(state.basePriceText) }.getOrNull()
        if (basePrice == null) {
            _uiState.update { it.copy(priceError = "Enter a valid price, for example 40.00") }
            return
        }

        val discountText = state.discountPriceText.trim()
        val discount = discountText.takeIf { it.isNotEmpty() }?.let { runCatching { Money.parse(it) }.getOrNull() }
        if (discountText.isNotEmpty() && discount == null) {
            _uiState.update { it.copy(priceError = "Enter a valid discount price.") }
            return
        }

        val stockText = state.stockText.trim()
        val stock = stockText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (stockText.isNotEmpty() && stock == null) {
            _uiState.update { it.copy(priceError = "Stock must be a whole number.") }
            return
        }

        val limitText = state.quantityLimitText.trim()
        val quantityLimit = limitText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (limitText.isNotEmpty() && quantityLimit == null) {
            _uiState.update { it.copy(priceError = "Order limit must be a whole number.") }
            return
        }

        val draft = state.draft.copy(
            name = name,
            basePrice = basePrice,
            discountPrice = discount,
            stock = stock,
            quantityLimit = quantityLimit,
        )

        _uiState.update { it.copy(saveState = ActionState.InFlight, nameError = null, priceError = null) }
        viewModelScope.launch {
            val outcome = if (state.isCreate) {
                productRepository.createProduct(draft)
            } else {
                productRepository.updateProduct(draft)
            }
            when (outcome) {
                is Outcome.Success -> {
                    val confirmed = ProductDraft.from(outcome.value)
                    AppLog.i(TAG, "saved product ${outcome.value.id}")
                    _uiState.update {
                        it.copy(
                            saveState = ActionState.Idle,
                            phase = ContentPhase.Data,
                            productId = outcome.value.id,
                            draft = confirmed,
                            baseline = confirmed,
                            basePriceText = confirmed.basePrice.toPlainString(),
                            discountPriceText = confirmed.discountPrice?.toPlainString().orEmpty(),
                            stockText = confirmed.stock?.toString().orEmpty(),
                            quantityLimitText = confirmed.quantityLimit?.toString().orEmpty(),
                            savedOnce = true,
                        )
                    }
                }
                // Failure: the draft is untouched, so the operator loses no typing.
                is Outcome.Failure -> _uiState.update {
                    it.copy(saveState = ActionState.Failed(outcome.error))
                }
            }
        }
    }

    fun dismissSaveError() {
        _uiState.update { it.copy(saveState = ActionState.Idle) }
    }

    // --- Image --------------------------------------------------------------

    fun onImagePicked(bytes: ByteArray, mimeType: String) {
        val state = _uiState.value
        if (state.isUploading) return
        val productId = state.productId
        if (productId == null) {
            _uiState.update {
                it.copy(upload = UploadState.Failed(AppError.Validation("Save the product before adding an image.")))
            }
            return
        }
        pendingImage = bytes
        pendingMime = mimeType
        uploadAndPersist(productId, bytes, mimeType)
    }

    /** Re-attempts the last upload after a failure. */
    fun retryUpload() {
        val bytes = pendingImage ?: return
        val productId = _uiState.value.productId ?: return
        if (_uiState.value.isUploading) return
        uploadAndPersist(productId, bytes, pendingMime)
    }

    /** The picker could not read the chosen file. */
    fun onImageUnreadable() {
        _uiState.update {
            it.copy(upload = UploadState.Failed(AppError.Validation("That image couldn't be read.")))
        }
    }

    fun dismissUploadError() {
        _uiState.update { it.copy(upload = UploadState.Idle) }
    }

    fun removeImage() {
        val state = _uiState.value
        val productId = state.productId ?: return
        if (!state.hasImage || state.isUploading) return
        _uiState.update { it.copy(upload = UploadState.InFlight(0f)) }
        viewModelScope.launch {
            when (val outcome = productRepository.clearImage(productId)) {
                is Outcome.Success -> {
                    val cleared = outcome.value.imageUrl
                    _uiState.update {
                        it.copy(
                            upload = UploadState.Idle,
                            draft = it.draft.copy(imageUrl = cleared),
                            baseline = it.baseline?.copy(imageUrl = cleared),
                        )
                    }
                }
                is Outcome.Failure -> _uiState.update { it.copy(upload = UploadState.Failed(outcome.error)) }
            }
        }
    }

    /**
     * Uploads, then persists the returned URL on the row built from the last
     * **confirmed** baseline — so a successful upload is only "set" once the
     * product write returns, and unrelated unsaved edits are not sent.
     */
    private fun uploadAndPersist(productId: String, bytes: ByteArray, mimeType: String) {
        _uiState.update { it.copy(upload = UploadState.InFlight(0f)) }
        viewModelScope.launch {
            val upload = productRepository.uploadImage(productId, bytes, mimeType) { progress ->
                _uiState.update { current ->
                    if (current.upload is UploadState.InFlight) {
                        current.copy(upload = UploadState.InFlight(progress.coerceIn(0f, 1f)))
                    } else {
                        current
                    }
                }
            }
            when (upload) {
                is Outcome.Failure -> _uiState.update { it.copy(upload = UploadState.Failed(upload.error)) }
                is Outcome.Success -> persistImageUrl(upload.value)
            }
        }
    }

    private suspend fun persistImageUrl(url: String) {
        val baseline = _uiState.value.baseline
        if (baseline == null) {
            _uiState.update { it.copy(upload = UploadState.Failed(AppError.Validation("Save the product first."))) }
            return
        }
        val withImage = baseline.copy(imageUrl = url)
        when (val saved = productRepository.updateProduct(withImage)) {
            is Outcome.Success -> {
                val confirmed = ProductDraft.from(saved.value)
                _uiState.update {
                    it.copy(
                        upload = UploadState.Idle,
                        draft = it.draft.copy(imageUrl = confirmed.imageUrl),
                        baseline = confirmed,
                    )
                }
            }
            is Outcome.Failure -> _uiState.update { it.copy(upload = UploadState.Failed(saved.error)) }
        }
    }

    // --- Load ---------------------------------------------------------------

    private fun loadCategories() {
        viewModelScope.launch {
            when (val outcome = categoryRepository.listCategories()) {
                is Outcome.Success -> _uiState.update { it.copy(categories = outcome.value) }
                is Outcome.Failure -> Unit
            }
        }
    }

    private fun load(productId: String) {
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = productRepository.getProduct(productId)) {
                is Outcome.Success -> {
                    val product = outcome.value
                    val draft = ProductDraft.from(product)
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Data,
                            productId = product.id,
                            draft = draft,
                            baseline = draft,
                            basePriceText = draft.basePrice.toPlainString(),
                            discountPriceText = draft.discountPrice?.toPlainString().orEmpty(),
                            stockText = draft.stock?.toString().orEmpty(),
                            quantityLimitText = draft.quantityLimit?.toString().orEmpty(),
                            loadError = null,
                        )
                    }
                    loadWeights(product.id)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = outcome.error)
                }
            }
        }
    }

    fun reload() {
        val id = _uiState.value.productId ?: return
        load(id)
    }

    private fun loadWeights(productId: String) {
        viewModelScope.launch {
            when (val outcome = weightRepository.listWeights(productId)) {
                is Outcome.Success -> _uiState.update { it.copy(weights = outcome.value) }
                is Outcome.Failure -> Unit
            }
        }
    }

    /** Refreshes the variant summary after returning from the weights screen. */
    fun refreshWeights() {
        val id = _uiState.value.productId ?: return
        loadWeights(id)
    }
}
