package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * The single-row store configuration (`store_settings`).
 *
 * Columns are **anon-visible** (`docs/CONTRACT.md` §3.6) so this row is real, not
 * guessed: `delivery_fee`, `free_delivery_threshold`, `id`, `is_open`,
 * `min_order_amount`, `phone`, `store_name`, `updated_at`, `whatsapp`.
 *
 * ### The `store_name` problem (PRD R6)
 * The live row reads **"Fresh Mart"** on a store branded *Vedant Fruit &
 * Vegetables*. The app must **not** silently rewrite it — that would hide a data
 * problem behind a guess. Instead [isNameSuspect] flags it, and the screen shows
 * the value as-is and makes the mismatch impossible to miss so the owner can
 * correct it deliberately.
 */
data class StoreSettings(
    val id: String,
    val storeName: String,
    val phone: String?,
    val whatsapp: String?,
    val isOpen: Boolean,
    val deliveryFee: Money,
    val freeDeliveryThreshold: Money,
    val minOrderAmount: Money,
    val updatedAt: Instant?,
) {

    /** True when the name is the known-wrong placeholder — surfaced, never auto-fixed. */
    val isNameSuspect: Boolean
        get() = storeName.trim().equals(SUSPECT_STORE_NAME, ignoreCase = true)

    companion object {
        /** The wrong live value (PRD R6). Compared case-insensitively. */
        const val SUSPECT_STORE_NAME = "Fresh Mart"
    }
}

/**
 * The editable subset of [StoreSettings], ready to write.
 *
 * [id] identifies the single row so the UPDATE targets it; the fields are exactly
 * the ones the screen may change (PRD P1-11). `updated_at` is server-managed and
 * deliberately absent.
 */
data class StoreSettingsDraft(
    val id: String,
    val storeName: String,
    val phone: String?,
    val whatsapp: String?,
    val isOpen: Boolean,
    val deliveryFee: Money,
    val freeDeliveryThreshold: Money,
    val minOrderAmount: Money,
)
