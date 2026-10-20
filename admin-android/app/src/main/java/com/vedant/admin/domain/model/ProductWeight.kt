package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * One weight variant of a product (`product_weights`).
 *
 * Live columns (`docs/CONTRACT.md` §3.3): `grams`, `label`, `price`,
 * `is_available`. `price` here is the variant's own price — it is **not** a
 * legacy pair, so it maps 1:1 to [price].
 */
data class ProductWeight(
    val id: String,
    val productId: String,
    val label: String,
    val grams: Int?,
    val price: Money,
    val isAvailable: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {

    /** e.g. `₹40.00 / kg` — a per-kilo unit rate derived for display only. */
    val unitRateLabel: String?
        get() {
            val gramsValue = grams ?: return null
            if (gramsValue <= 0) return null
            val perKgPaise = price.paise * 1000L / gramsValue
            return "${Money(perKgPaise).format()} / kg"
        }
}

/** The editable shape of a weight variant. */
data class ProductWeightDraft(
    val id: String? = null,
    val productId: String,
    val label: String,
    val grams: Int? = null,
    val price: Money,
    val isAvailable: Boolean = true,
) {

    val isCreate: Boolean get() = id.isNullOrBlank()

    companion object {
        fun from(weight: ProductWeight): ProductWeightDraft = ProductWeightDraft(
            id = weight.id,
            productId = weight.productId,
            label = weight.label,
            grams = weight.grams,
            price = weight.price,
            isAvailable = weight.isAvailable,
        )
    }
}
