package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `categories` row — **including `icon`**, the pair-5 legacy
 * column (`docs/CONTRACT.md` §8).
 *
 * `glyph` + `accent` are authoritative; `icon` is empty/stale and is only ever
 * **mirrored from `glyph`** on write. The name `icon` must not escape this DTO.
 */
@Serializable
data class CategoryDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val glyph: String? = null,
    /** Pair 5 legacy column — read for completeness, written only as `:= glyph`. */
    val icon: String? = null,
    val accent: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
