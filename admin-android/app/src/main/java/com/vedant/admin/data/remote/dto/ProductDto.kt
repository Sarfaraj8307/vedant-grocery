package com.vedant.admin.data.remote.dto

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.money.MoneySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `products` row — **including both members of both legacy
 * pairs**. This is the only place the names `price` and `max_order_qty` exist
 * (`docs/CONTRACT.md` §7; `ARCHITECTURE.md` §10).
 *
 * Pairs:
 *  - **1** `base_price` (read) ↔ `price` (fallback). Writes set **both** equal.
 *  - **2** `quantity_limit` (read) ↔ `max_order_qty` (fallback). Writes set **both** equal.
 *
 * Every field is nullable so a column the live schema does not have decodes to
 * `null` rather than throwing (`ignoreUnknownKeys` + tolerant decode, §5.2).
 * `availability` is a **boolean** in the live schema (`src/types/database.ts:73`).
 */
@Serializable
data class ProductDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String? = null,

    // --- Pair 1: base_price is authoritative; price is the legacy mirror -------
    @SerialName("base_price") @Serializable(with = MoneySerializer::class) val basePrice: Money? = null,
    @SerialName("price") @Serializable(with = MoneySerializer::class) val price: Money? = null,
    @SerialName("discount_price") @Serializable(with = MoneySerializer::class) val discountPrice: Money? = null,

    @SerialName("has_offer") val hasOffer: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("is_featured") val isFeatured: Boolean? = null,
    val stock: Int? = null,

    // --- Pair 2: quantity_limit is authoritative; max_order_qty is the mirror ---
    @SerialName("quantity_limit") val quantityLimit: Int? = null,
    @SerialName("max_order_qty") val maxOrderQty: Int? = null,

    val unit: String? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    val availability: Boolean? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val glyph: String? = null,
    val accent: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
