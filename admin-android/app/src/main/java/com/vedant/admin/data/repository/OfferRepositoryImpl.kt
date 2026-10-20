package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.CatalogueRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft
import com.vedant.admin.domain.repository.OfferRepository

private const val TAG = "OfferRepository"

/**
 * The real [OfferRepository], backed by PostgREST `offers`.
 *
 * The window and the discount value are validated **here**, before the server
 * sees them, so an obviously invalid offer fails fast as a `Validation` error
 * rather than a raw `23514`. Every write returns the row PostgREST echoed.
 */
class OfferRepositoryImpl(private val remote: CatalogueRemoteSource) : OfferRepository {

    override suspend fun listOffers(productId: String?): Outcome<List<Offer>> = outcomeOf {
        remote.listOffers(productId?.takeIf { it.isNotBlank() }).map { it.toDomain() }
    }

    override suspend fun createOffer(draft: OfferDraft): Outcome<Offer> = outcomeOf {
        require(draft.isCreate) { "This offer already exists." }
        validate(draft)
        AppLog.i(TAG, "createOffer product=${draft.productId} title=${draft.title}")
        remote.insertOffer(draft.toWriteFields()).toDomain()
    }

    override suspend fun updateOffer(draft: OfferDraft): Outcome<Offer> = outcomeOf {
        val id = draft.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("An offer id is required.")
        validate(draft)
        AppLog.i(TAG, "updateOffer id=$id")
        remote.updateOffer(id, draft.toWriteFields())?.toDomain() ?: throw notFound(id)
    }

    override suspend fun deleteOffer(offerId: String): Outcome<Unit> = outcomeOf {
        remote.deleteOffer(offerId)
    }

    /** Fails fast on the three things the server would reject anyway. */
    private fun validate(draft: OfferDraft) {
        require(draft.productId.isNotBlank()) { "A product is required." }
        require(draft.title.isNotBlank()) { "An offer title is required." }
        require(draft.discountPercent == null || draft.discountPercent in 1..99) {
            "A discount must be between 1% and 99%."
        }
        require(draft.startsAt == null || draft.endsAt == null || draft.startsAt < draft.endsAt) {
            "The end date must be after the start date."
        }
    }

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Offer $id no longer exists.")
}
