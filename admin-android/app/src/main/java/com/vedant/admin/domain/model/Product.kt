package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * One catalogue product.
 *
 * Money is [Money] (integer paise). [basePrice] is the **authoritative** price
 * column (`products.base_price`); the legacy `products.price` mirror exists only
 * inside the DTO/mapper (pair 1, `docs/CONTRACT.md` §7). No field here carries a
 * legacy column name.
 *
 * ### `availability` is a boolean
 * The live column is `products.availability boolean` — the customer PWA uses it
 * as a truthiness flag (`if (!product.availability) return "Out of stock"`,
 * `.filter((p) => p.availability)`), so the domain models it as a boolean, not a
 * string. This corrects the architecture sketch, which guessed `String?`; live
 * evidence wins (`src/types/database.ts:73`).
 */
data class Product(
    val id: String,
    val name: String,
    val slug: String?,
    val categoryId: String?,
    val description: String?,
    val basePrice: Money,
    val discountPrice: Money?,
    val hasOffer: Boolean,
    val isActive: Boolean,
    val isFeatured: Boolean,
    val stock: Int?,
    val quantityLimit: Int?,
    val unit: String?,
    val unitLabel: String?,
    val availability: Boolean,
    val imageUrl: String?,
    val glyph: String?,
    val accent: Int?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {

    /** True when a discount is present and actually lower than the base price. */
    val hasDiscount: Boolean get() = discountPrice != null && discountPrice < basePrice

    /** The price a customer would pay today. Display-only; the server re-prices. */
    val effectivePrice: Money get() = if (hasDiscount) discountPrice!! else basePrice

    /** Whole-percent saving, or `null` when there is no discount. */
    val discountPercent: Int?
        get() {
            if (!hasDiscount || basePrice.paise <= 0L) return null
            val diff = basePrice.paise - effectivePrice.paise
            return ((diff * 100) / basePrice.paise).toInt()
        }

    /** Short availability wording for a list row. */
    val availabilityLabel: String
        get() = when {
            !isActive -> "Hidden"
            !availability -> "Out of stock"
            stock == null -> "In stock"
            stock <= 0 -> "Out of stock"
            stock <= LOW_STOCK -> "Low · $stock"
            else -> "$stock in stock"
        }

    /** The `unit_label` suffix, e.g. `per kg`. */
    val unitSuffix: String
        get() = unitLabel?.takeIf { it.isNotBlank() }?.let { "per $it" } ?: "per unit"

    /** The emoji to show when there is no image. */
    val displayGlyph: String
        get() = glyph?.takeIf { it.isNotBlank() } ?: name.take(1).uppercase()

    /** Listed to customers only when both flags agree. */
    val isListed: Boolean get() = isActive && availability

    companion object {
        const val LOW_STOCK = 5
    }
}

/**
 * The editable shape of a product — what the editor submits.
 *
 * [id] is `null` for a create. The legacy-pair expansion is **not** done here
 * (that would put column names in the domain); it lives in
 * `data/remote/mapper/CatalogueMapper.kt` as `toWriteFields()`.
 */
data class ProductDraft(
    val id: String? = null,
    val name: String,
    val slug: String? = null,
    val categoryId: String? = null,
    val description: String? = null,
    val basePrice: Money,
    val discountPrice: Money? = null,
    val isActive: Boolean = true,
    val isFeatured: Boolean = false,
    val stock: Int? = null,
    val quantityLimit: Int? = null,
    val unit: String? = null,
    val unitLabel: String? = null,
    val availability: Boolean = true,
    val imageUrl: String? = null,
    val glyph: String? = null,
    val accent: Int? = null,
) {

    val isCreate: Boolean get() = id.isNullOrBlank()

    companion object {
        /** Pre-fills the editor from a server row. */
        fun from(product: Product): ProductDraft = ProductDraft(
            id = product.id,
            name = product.name,
            slug = product.slug,
            categoryId = product.categoryId,
            description = product.description,
            basePrice = product.basePrice,
            discountPrice = product.discountPrice,
            isActive = product.isActive,
            isFeatured = product.isFeatured,
            stock = product.stock,
            quantityLimit = product.quantityLimit,
            unit = product.unit,
            unitLabel = product.unitLabel,
            availability = product.availability,
            imageUrl = product.imageUrl,
            glyph = product.glyph,
            accent = product.accent,
        )
    }
}

/**
 * The catalogue-hub query: an optional category facet, a free-text search and an
 * optional availability facet. Applied **server-side** so a filter never hides a
 * match that simply was not on the loaded page.
 */
data class ProductQuery(
    val categoryId: String? = null,
    val search: String = "",
    val availability: Boolean? = null,
) {

    val searchTerm: String? get() = search.trim().takeIf { it.isNotEmpty() }

    val isActive: Boolean get() = categoryId != null || searchTerm != null || availability != null

    companion object {
        val NONE = ProductQuery()
    }
}
