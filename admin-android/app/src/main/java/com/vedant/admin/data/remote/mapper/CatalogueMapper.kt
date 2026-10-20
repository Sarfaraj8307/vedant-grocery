package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.CategoryDto
import com.vedant.admin.data.remote.dto.OfferDto
import com.vedant.admin.data.remote.dto.ProductDto
import com.vedant.admin.data.remote.dto.ProductWeightDto
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.model.Product
import com.vedant.admin.domain.model.ProductDraft
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft

private const val TAG = "CatalogueMapper"

/**
 * DTO ↔ domain mapping for the catalogue, and the **only** place the §10
 * legacy/duplicate column policy is applied.
 *
 * Read policy (authoritative → fallback):
 *  - pair 1 `base_price` → `price`
 *  - pair 2 `quantity_limit` → `max_order_qty`
 *  - pair 5 `glyph` + `accent` → `icon`
 *
 * Write policy (every pair written, so the ambiguity stays harmless):
 *  - `toWriteFields()` sets **both** `base_price` and `price` to the same value
 *  - `toWriteFields()` sets **both** `quantity_limit` and `max_order_qty` equal
 *  - `toWriteFields()` writes `glyph` + `accent` + `slug` and mirrors `icon := glyph`
 *
 * The names `price`, `max_order_qty`, `icon` exist **only** in this file and the
 * DTOs — no domain model, ViewModel or composable may name them.
 */

// --- Product ----------------------------------------------------------------

fun ProductDto.toDomain(): Product = Product(
    id = id.orEmpty(),
    name = name?.takeIf { it.isNotBlank() } ?: "Product",
    slug = slug?.takeIf { it.isNotBlank() },
    categoryId = categoryId?.takeIf { it.isNotBlank() },
    description = description?.takeIf { it.isNotBlank() },
    // Pair 1: base_price is authoritative; price is the legacy fallback.
    basePrice = (basePrice ?: price).orZero("base_price"),
    discountPrice = discountPrice,
    hasOffer = hasOffer.orFalse("has_offer"),
    isActive = isActive.orTrue("is_active"),
    isFeatured = isFeatured.orFalse("is_featured"),
    stock = stock,
    // Pair 2: quantity_limit is authoritative; max_order_qty is the fallback.
    quantityLimit = quantityLimit ?: maxOrderQty,
    unit = unit?.takeIf { it.isNotBlank() },
    unitLabel = unitLabel?.takeIf { it.isNotBlank() },
    availability = availability.orTrue("availability"),
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    glyph = glyph?.takeIf { it.isNotBlank() },
    accent = accent,
    createdAt = parseInstantOrNull(createdAt),
    updatedAt = parseInstantOrNull(updatedAt),
)

/**
 * The write payload for a create/update — **both members of both pairs set**.
 *
 * `has_offer` is deliberately omitted: it is a denormalised flag maintained
 * server-side from the `offers` table, so the client must not overwrite it.
 * `created_at`/`updated_at` are omitted (server-managed); with
 * `explicitNulls = false` they never reach the wire.
 */
fun ProductDraft.toWriteFields(): ProductDto = ProductDto(
    id = id?.takeIf { it.isNotBlank() },
    name = name.trim(),
    slug = slug?.takeIf { it.isNotBlank() } ?: slugify(name),
    categoryId = categoryId?.takeIf { it.isNotBlank() },
    description = description?.trim()?.takeIf { it.isNotEmpty() },
    basePrice = basePrice,
    price = basePrice, // pair 1: write both, same value
    discountPrice = discountPrice,
    isActive = isActive,
    isFeatured = isFeatured,
    stock = stock,
    quantityLimit = quantityLimit,
    maxOrderQty = quantityLimit, // pair 2: write both, same value
    unit = unit?.trim()?.takeIf { it.isNotEmpty() },
    unitLabel = unitLabel?.trim()?.takeIf { it.isNotEmpty() },
    availability = availability,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    glyph = glyph?.takeIf { it.isNotBlank() },
    accent = accent,
)

// --- Category ---------------------------------------------------------------

fun CategoryDto.toDomain(): Category = Category(
    id = id.orEmpty(),
    name = name?.takeIf { it.isNotBlank() } ?: "Category",
    slug = slug?.takeIf { it.isNotBlank() },
    // Pair 5: glyph is authoritative; icon is only a last-resort fallback.
    glyph = glyph?.takeIf { it.isNotBlank() } ?: icon?.takeIf { it.isNotBlank() },
    accent = accent,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    sortOrder = sortOrder ?: 0,
    isActive = isActive.orTrue("is_active"),
    createdAt = parseInstantOrNull(createdAt),
    updatedAt = parseInstantOrNull(updatedAt),
)

/**
 * Pair 5 write policy (`docs/CONTRACT.md` §8): write `slug` + `glyph` + `accent`,
 * and **mirror `icon := glyph`** so no surface that ever reads `icon` sees a
 * blank — while `glyph` stays the single source of truth.
 */
fun CategoryDraft.toWriteFields(): CategoryDto = CategoryDto(
    id = id?.takeIf { it.isNotBlank() },
    name = name.trim(),
    slug = slug?.takeIf { it.isNotBlank() } ?: slugify(name),
    glyph = glyph?.takeIf { it.isNotBlank() },
    icon = glyph?.takeIf { it.isNotBlank() }, // mirror, never the reverse
    accent = accent,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    sortOrder = sortOrder,
    isActive = isActive,
)

// --- ProductWeight ----------------------------------------------------------

fun ProductWeightDto.toDomain(): ProductWeight = ProductWeight(
    id = id.orEmpty(),
    productId = productId.orEmpty(),
    label = label?.takeIf { it.isNotBlank() } ?: "Variant",
    grams = grams,
    price = price.orZero("price"),
    isAvailable = isAvailable.orTrue("is_available"),
    createdAt = parseInstantOrNull(createdAt),
    updatedAt = parseInstantOrNull(updatedAt),
)

fun ProductWeightDraft.toWriteFields(): ProductWeightDto = ProductWeightDto(
    id = id?.takeIf { it.isNotBlank() },
    productId = productId,
    label = label.trim(),
    grams = grams,
    price = price,
    isAvailable = isAvailable,
)

// --- Offer ------------------------------------------------------------------

fun OfferDto.toDomain(): Offer = Offer(
    id = id.orEmpty(),
    productId = productId.orEmpty(),
    title = title?.takeIf { it.isNotBlank() } ?: "Offer",
    price = price,
    discountPercent = discountPercent,
    startsAt = parseInstantOrNull(startsAt),
    endsAt = parseInstantOrNull(endsAt),
    isActive = isActive.orTrue("is_active"),
    createdAt = parseInstantOrNull(createdAt),
    updatedAt = parseInstantOrNull(updatedAt),
)

fun OfferDraft.toWriteFields(): OfferDto = OfferDto(
    id = id?.takeIf { it.isNotBlank() },
    productId = productId,
    title = title.trim(),
    price = price,
    discountPercent = discountPercent,
    startsAt = startsAt?.toString(),
    endsAt = endsAt?.toString(),
    isActive = isActive,
)

// --- Helpers ----------------------------------------------------------------

private fun Money?.orZero(field: String): Money {
    if (this == null) {
        AppLog.w(TAG, "expected money column '$field' was absent; rendering 0")
        return Money.ZERO
    }
    return this
}

private fun Boolean?.orTrue(field: String): Boolean {
    if (this == null) {
        AppLog.w(TAG, "expected boolean column '$field' was absent; defaulting true")
        return true
    }
    return this
}

private fun Boolean?.orFalse(field: String): Boolean {
    if (this == null) {
        AppLog.w(TAG, "expected boolean column '$field' was absent; defaulting false")
        return false
    }
    return this
}

/**
 * A URL-safe slug derived from a name. Used only when the operator left the slug
 * blank; the PWA routes categories/products by `id`, so this is cosmetic.
 */
internal fun slugify(text: String): String =
    text.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "item" }
