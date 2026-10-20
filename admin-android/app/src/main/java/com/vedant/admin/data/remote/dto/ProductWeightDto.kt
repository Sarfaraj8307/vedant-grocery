package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `product_weights` row (`docs/CONTRACT.md` §3.3). No legacy
 * pair: `price` here is the variant's own price, so it maps 1:1.
 */
@Serializable
data class ProductWeightDto(
    val id: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val label: String? = null,
    val grams: Int? = null,
    @Serializable(with = MoneySerializer::class) val price: Money? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
