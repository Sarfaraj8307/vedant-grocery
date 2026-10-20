package com.vedant.admin.feature.settings.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.StoreSettingsDraft
import com.vedant.admin.domain.repository.StoreSettingsRepository
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the store-settings screen.
 *
 * The rule that governs it: **the row is the server's**. [StoreSettingsUiState.saved]
 * is only ever replaced by the row PostgREST echoed after a save, and on a
 * rejection the existing row is left exactly as it was — the UI does not move.
 * Local validation exists for fast feedback only; the server validates again.
 *
 * The `store_name` mismatch is **surfaced, not fixed**: this ViewModel never
 * rewrites a suspect name on its own — it only reports [StoreSettingsUiState.nameMismatch]
 * so the screen can make the problem visible.
 */
class StoreSettingsViewModel(
    private val repository: StoreSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreSettingsUiState())
    val uiState: StateFlow<StoreSettingsUiState> = _uiState.asStateFlow()

    private var started = false

    /** Idempotent: a recomposition (or a config change) does not re-fetch. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    fun reload() = load()

    // --- Field edits --------------------------------------------------------

    fun onStoreNameChange(value: String) = edit(StoreSettingsField.STORE_NAME) { it.copy(storeName = value) }

    fun onPhoneChange(value: String) = edit(StoreSettingsField.PHONE) { it.copy(phone = value) }

    fun onWhatsappChange(value: String) = edit(StoreSettingsField.WHATSAPP) { it.copy(whatsapp = value) }

    fun onOpenChange(value: Boolean) = edit(null) { it.copy(isOpen = value) }

    fun onDeliveryFeeChange(value: String) = edit(StoreSettingsField.DELIVERY_FEE) { it.copy(deliveryFee = value) }

    fun onFreeDeliveryThresholdChange(value: String) =
        edit(StoreSettingsField.FREE_DELIVERY_THRESHOLD) { it.copy(freeDeliveryThreshold = value) }

    fun onMinOrderAmountChange(value: String) =
        edit(StoreSettingsField.MIN_ORDER_AMOUNT) { it.copy(minOrderAmount = value) }

    private fun edit(field: StoreSettingsField?, transform: (StoreSettingsForm) -> StoreSettingsForm) {
        _uiState.update { state ->
            state.copy(
                form = transform(state.form),
                fieldErrors = if (field == null) state.fieldErrors else state.fieldErrors - field,
                saveState = ActionState.Idle,
            )
        }
    }

    /** Clears a surfaced save failure once the operator has read it. */
    fun dismissSaveError() {
        _uiState.update { it.copy(saveState = ActionState.Idle) }
    }

    // --- Save ---------------------------------------------------------------

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val saved = state.saved ?: return

        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, saveState = ActionState.Idle) }
            return
        }

        val draft = StoreSettingsDraft(
            id = saved.id,
            storeName = state.form.storeName.trim(),
            phone = state.form.phone.trim().takeIf { it.isNotEmpty() },
            whatsapp = state.form.whatsapp.trim().takeIf { it.isNotEmpty() },
            isOpen = state.form.isOpen,
            deliveryFee = Money.parse(state.form.deliveryFee.trim()),
            freeDeliveryThreshold = Money.parse(state.form.freeDeliveryThreshold.trim()),
            minOrderAmount = Money.parse(state.form.minOrderAmount.trim()),
        )

        _uiState.update { it.copy(saveState = ActionState.InFlight, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            when (val outcome = repository.saveSettings(draft)) {
                // Only the server's echoed row becomes the new baseline.
                is Outcome.Success -> _uiState.update {
                    it.copy(saved = outcome.value, form = outcome.value.toForm(), saveState = ActionState.Idle)
                }

                is Outcome.Failure -> _uiState.update { it.copy(saveState = ActionState.Failed(outcome.error)) }
            }
        }
    }

    // --- Load ---------------------------------------------------------------

    private fun load() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            when (val outcome = repository.getSettings()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        phase = ContentPhase.Data,
                        saved = outcome.value,
                        form = outcome.value.toForm(),
                        loadError = null,
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = outcome.error)
                }
            }
        }
    }

    // --- Validation ---------------------------------------------------------

    private fun validate(form: StoreSettingsForm): Map<StoreSettingsField, String> {
        val errors = mutableMapOf<StoreSettingsField, String>()

        if (form.storeName.isBlank()) {
            errors[StoreSettingsField.STORE_NAME] = "A store name is required."
        }
        validatePhone(form.phone, StoreSettingsField.PHONE, errors, "phone number")
        validatePhone(form.whatsapp, StoreSettingsField.WHATSAPP, errors, "WhatsApp number")
        validateMoney(form.deliveryFee, StoreSettingsField.DELIVERY_FEE, errors, "delivery fee")
        validateMoney(
            form.freeDeliveryThreshold,
            StoreSettingsField.FREE_DELIVERY_THRESHOLD,
            errors,
            "free-delivery threshold",
        )
        validateMoney(form.minOrderAmount, StoreSettingsField.MIN_ORDER_AMOUNT, errors, "minimum order")
        return errors
    }

    private fun validateMoney(
        text: String,
        field: StoreSettingsField,
        errors: MutableMap<StoreSettingsField, String>,
        label: String,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            errors[field] = "Enter the $label."
            return
        }
        val parsed = runCatching { Money.parse(trimmed) }.getOrNull()
        when {
            parsed == null -> errors[field] = "Enter a valid amount."
            parsed.isNegative -> errors[field] = "The $label cannot be negative."
        }
    }

    private fun validatePhone(
        text: String,
        field: StoreSettingsField,
        errors: MutableMap<StoreSettingsField, String>,
        label: String,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return // optional
        val allowed = trimmed.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }
        if (!allowed || trimmed.count { it.isDigit() } < 6) {
            errors[field] = "Enter a valid $label."
        }
    }
}
