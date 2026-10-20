package com.vedant.admin.feature.settings.store

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.ui.components.ActionState
import com.vedant.admin.ui.components.ContentPhase

/** The editable fields, so a validation error can be attached to one of them. */
enum class StoreSettingsField {
    STORE_NAME,
    PHONE,
    WHATSAPP,
    DELIVERY_FEE,
    FREE_DELIVERY_THRESHOLD,
    MIN_ORDER_AMOUNT,
}

/**
 * The form's text fields, kept as strings so a half-typed number is never lost or
 * coerced. Money fields are parsed only at save time, where a bad value becomes a
 * field error rather than a silent zero.
 */
data class StoreSettingsForm(
    val storeName: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val isOpen: Boolean = true,
    val deliveryFee: String = "",
    val freeDeliveryThreshold: String = "",
    val minOrderAmount: String = "",
)

/** The server's row as editable text — the baseline for [StoreSettingsUiState.isDirty]. */
fun StoreSettings.toForm(): StoreSettingsForm = StoreSettingsForm(
    storeName = storeName,
    phone = phone.orEmpty(),
    whatsapp = whatsapp.orEmpty(),
    isOpen = isOpen,
    deliveryFee = deliveryFee.toPlainString(),
    freeDeliveryThreshold = freeDeliveryThreshold.toPlainString(),
    minOrderAmount = minOrderAmount.toPlainString(),
)

/**
 * Immutable state for the store-settings screen.
 *
 * [saved] is the **server's** row, replaced only by the row PostgREST echoed after
 * a save. [form] is what the operator is editing; [isDirty] compares the two so
 * the Save button is honest about whether there is anything to send.
 */
data class StoreSettingsUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val loadError: AppError? = null,
    val saved: StoreSettings? = null,
    val form: StoreSettingsForm = StoreSettingsForm(),
    val fieldErrors: Map<StoreSettingsField, String> = emptyMap(),
    val saveState: ActionState = ActionState.Idle,
) {

    /** True while the name is the known-wrong live value (PRD R6). */
    val nameMismatch: Boolean
        get() = form.storeName.trim().equals(StoreSettings.SUSPECT_STORE_NAME, ignoreCase = true)

    val isSaving: Boolean get() = saveState is ActionState.InFlight

    val saveError: AppError? get() = (saveState as? ActionState.Failed)?.error

    val isDirty: Boolean get() = saved != null && form != saved.toForm()

    fun errorFor(field: StoreSettingsField): String? = fieldErrors[field]
}
