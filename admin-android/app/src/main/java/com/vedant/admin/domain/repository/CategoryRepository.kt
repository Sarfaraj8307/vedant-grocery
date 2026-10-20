package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.CategoryDraft

/**
 * Catalogue reads and writes for categories.
 *
 * Pair 5 (`docs/CONTRACT.md` §8): `glyph` + `accent` are authoritative and the
 * legacy `icon` column is **mirrored from `glyph`** on write — that mirroring
 * lives in `data/remote/mapper/CatalogueMapper`, never here.
 *
 * Every write returns the server-confirmed row.
 */
interface CategoryRepository {

    /** All categories, ordered by `sort_order` (server-side). */
    suspend fun listCategories(): Outcome<List<Category>>

    /** Inserts a new category and returns the row the server stored. */
    suspend fun createCategory(draft: CategoryDraft): Outcome<Category>

    /** Updates an existing category and returns the row the server stored. */
    suspend fun updateCategory(draft: CategoryDraft): Outcome<Category>

    /** Removes a category. A missing row is not an error. */
    suspend fun deleteCategory(categoryId: String): Outcome<Unit>
}
