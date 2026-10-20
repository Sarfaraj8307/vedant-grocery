package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.domain.model.OfferDraft

/**
 * Reads and writes for product offers.
 *
 * An offer is either a flat [com.vedant.admin.domain.model.Offer.price] or a
 * [com.vedant.admin.domain.model.Offer.discountPercent], optionally bounded by a
 * start/end window. The window and the value are validated here before the
 * server sees them, so an obviously invalid offer fails fast as a
 * `Validation` error instead of a raw `23514`.
 *
 * Every write returns the server-confirmed row.
 */
interface OfferRepository {

    /** Offers for one product, or every offer when [productId] is null. */
    suspend fun listOffers(productId: String? = null): Outcome<List<Offer>>

    /** Inserts a new offer and returns the row the server stored. */
    suspend fun createOffer(draft: OfferDraft): Outcome<Offer>

    /** Updates an existing offer and returns the row the server stored. */
    suspend fun updateOffer(draft: OfferDraft): Outcome<Offer>

    /** Removes an offer. A missing row is not an error. */
    suspend fun deleteOffer(offerId: String): Outcome<Unit>
}
