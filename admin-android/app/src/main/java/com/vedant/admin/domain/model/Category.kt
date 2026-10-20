package com.vedant.admin.domain.model

import kotlinx.datetime.Instant

/**
 * One catalogue category.
 *
 * ### Pair 5 (`docs/CONTRACT.md` §7–§8)
 * The customer PWA renders `glyph` (visual) + `accent` (colour) and **never**
 * reads `icon` or `slug`; the live `icon` column is empty or stale (Fruits has
 * `icon="🥬"` on the *Fruits* row). So [glyph] and [accent] are the authoritative
 * domain fields and `icon` is not modelled at all — the mapper mirrors
 * `icon := glyph` on write, and nothing above the data layer ever sees `icon`.
 */
data class Category(
    val id: String,
    val name: String,
    val slug: String?,
    val glyph: String?,
    val accent: Int?,
    val imageUrl: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {

    /** The emoji to render, falling back to the name's initial. */
    val displayGlyph: String
        get() = glyph?.takeIf { it.isNotBlank() } ?: name.take(1).uppercase()
}

/** The editable shape of a category. */
data class CategoryDraft(
    val id: String? = null,
    val name: String,
    val slug: String? = null,
    val glyph: String? = null,
    val accent: Int? = null,
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
) {

    val isCreate: Boolean get() = id.isNullOrBlank()

    companion object {
        fun from(category: Category): CategoryDraft = CategoryDraft(
            id = category.id,
            name = category.name,
            slug = category.slug,
            glyph = category.glyph,
            accent = category.accent,
            imageUrl = category.imageUrl,
            sortOrder = category.sortOrder,
            isActive = category.isActive,
        )
    }
}
