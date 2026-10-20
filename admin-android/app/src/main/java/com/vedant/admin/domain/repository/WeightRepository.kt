package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.ProductWeight
import com.vedant.admin.domain.model.ProductWeightDraft

/**
 * Reads and writes for a product's weight variants (`product_weights`).
 *
 * A single [saveWeight] handles both create and update — the draft's `isCreate`
 * decides — because the editor edits the variants inline and should not have to
 * branch. The write always returns the **server-confirmed row**.
 */
interface WeightRepository {

    /** Every variant of one product, ordered by `grams` (server-side). */
    suspend fun listWeights(productId: String): Outcome<List<ProductWeight>>

    /** Inserts when the draft has no id, updates otherwise. Returns the stored row. */
    suspend fun saveWeight(draft: ProductWeightDraft): Outcome<ProductWeight>

    /** Removes a variant. A missing row is not an error. */
    suspend fun deleteWeight(weightId: String): Outcome<Unit>
}
