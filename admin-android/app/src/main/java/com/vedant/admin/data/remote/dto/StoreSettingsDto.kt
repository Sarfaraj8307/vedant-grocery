package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of the single `store_settings` row.
 *
 * This table is **anon-visible** (`docs/CONTRACT.md` §3.6), so unlike the
 * RLS-hidden tables this DTO names the real columns. Every field is still
 * nullable so a decode is tolerant of a missing column (`ignoreUnknownKeys` +
 * `explicitNulls = false`), and money is decoded through [MoneySerializer] so a
 * `numeric` value never becomes a `Double`.
 */
@Serializable
data class StoreSettingsDto(
    /**
     * The singleton's primary key. Decoded through [FlexibleIdSerializer] because
     * the live column is the integer `1` (`id=1 CHECK id=1`), which a plain
     * `String?` cannot accept — that mismatch is what stopped the Store-settings
     * screen from loading.
     */
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    @SerialName("store_name") val storeName: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    @SerialName("is_open") val isOpen: Boolean? = null,
    @SerialName("delivery_fee") @Serializable(with = MoneySerializer::class) val deliveryFee: Money? = null,
    @SerialName("free_delivery_threshold") @Serializable(with = MoneySerializer::class)
    val freeDeliveryThreshold: Money? = null,
    @SerialName("min_order_amount") @Serializable(with = MoneySerializer::class) val minOrderAmount: Money? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * The **write** payload for the settings row.
 *
 * Only the editable columns are present (PRD P1-11): `store_name`, `phone`,
 * `whatsapp`, `is_open`, `delivery_fee`, `free_delivery_threshold`,
 * `min_order_amount`. `id` and `updated_at` are deliberately absent — the row is
 * targeted by a filter on `id`, and `updated_at` is server-managed.
 */
@Serializable
data class StoreSettingsUpdateDto(
    @SerialName("store_name") val storeName: String,
    val phone: String? = null,
    val whatsapp: String? = null,
    @SerialName("is_open") val isOpen: Boolean,
    @SerialName("delivery_fee") @Serializable(with = MoneySerializer::class) val deliveryFee: Money,
    @SerialName("free_delivery_threshold") @Serializable(with = MoneySerializer::class)
    val freeDeliveryThreshold: Money,
    @SerialName("min_order_amount") @Serializable(with = MoneySerializer::class) val minOrderAmount: Money,
)
