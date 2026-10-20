package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of an `offers` row (`docs/CONTRACT.md` §3.4). No legacy pair.
 * `price` and `discount_percent` are both nullable in the live schema.
 */
@Serializable
data class OfferDto(
    val id: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val title: String? = null,
    @Serializable(with = MoneySerializer::class) val price: Money? = null,
    @SerialName("discount_percent") val discountPercent: Int? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
