package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft
import com.vedant.admin.domain.repository.WeightRepository

private const val TAG = "WeightRepository"

/**
 * The real [WeightRepository], backed by PostgREST `product_weights`.
 *
 * [saveWeight] inserts when the draft has no id and updates otherwise — the
 * editor edits variants inline and should not branch. Either way it returns the
 * row PostgREST echoed, never the draft.
 */
class WeightRepositoryImpl(private val remote: CatalogueRemoteSource) : WeightRepository {

    override suspend fun listWeights(productId: String): Outcome<List<ProductWeight>> = outcomeOf {
        require(productId.isNotBlank()) { "A product is required." }
        remote.listWeights(productId).map { it.toDomain() }
    }

    override suspend fun saveWeight(draft: ProductWeightDraft): Outcome<ProductWeight> = outcomeOf {
        require(draft.productId.isNotBlank()) { "A product is required." }
        require(draft.label.isNotBlank()) { "A weight label is required." }
        require(draft.grams == null || draft.grams > 0) { "Weight in grams must be positive." }

        val fields = draft.toWriteFields()
        val saved = if (draft.isCreate) {
            AppLog.i(TAG, "createWeight product=${draft.productId} label=${draft.label}")
            remote.insertWeight(fields)
        } else {
            val id = draft.id.orEmpty()
            AppLog.i(TAG, "updateWeight id=$id")
            remote.updateWeight(id, fields) ?: throw notFound(id)
        }
        saved.toDomain()
    }

    override suspend fun deleteWeight(weightId: String): Outcome<Unit> = outcomeOf {
        remote.deleteWeight(weightId)
    }

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Weight variant $id no longer exists.")
}
