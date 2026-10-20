package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.Category
import com.vedant.admin.domain.model.CategoryDraft
import com.vedant.admin.domain.repository.CategoryRepository

private const val TAG = "CategoryRepository"

/**
 * The real [CategoryRepository], backed by PostgREST `categories`.
 *
 * Every write returns the row PostgREST echoed (`Returning.Representation`), so
 * the UI only ever renders the server's stored row. The pair-5 mirror
 * (`icon := glyph`) is applied by `CategoryDraft.toWriteFields()`.
 */
class CategoryRepositoryImpl(private val remote: CatalogueRemoteSource) : CategoryRepository {

    override suspend fun listCategories(): Outcome<List<Category>> = outcomeOf {
        remote.listCategories().map { it.toDomain() }
    }

    override suspend fun createCategory(draft: CategoryDraft): Outcome<Category> = outcomeOf {
        require(draft.isCreate) { "This category already exists." }
        require(draft.name.isNotBlank()) { "A category name is required." }
        AppLog.i(TAG, "createCategory name=${draft.name}")
        remote.insertCategory(draft.toWriteFields()).toDomain()
    }

    override suspend fun updateCategory(draft: CategoryDraft): Outcome<Category> = outcomeOf {
        val id = draft.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A category id is required.")
        require(draft.name.isNotBlank()) { "A category name is required." }
        AppLog.i(TAG, "updateCategory id=$id")
        remote.updateCategory(id, draft.toWriteFields())?.toDomain() ?: throw notFound(id)
    }

    override suspend fun deleteCategory(categoryId: String): Outcome<Unit> = outcomeOf {
        remote.deleteCategory(categoryId)
    }

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Category $id no longer exists.")
}
