package com.vedant.admin.feature.catalogue.offers

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.Offer
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.datetime.Instant

/** The add/edit form for one offer, shown in a bottom sheet. */
data class OfferEditState(
    val id: String? = null,
    val title: String = "",
    val discountPercent: String = "",
    val price: String = "",
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val fieldError: String? = null,
    val error: AppError? = null,
) {

    val isCreate: Boolean get() = id == null

    companion object {
        fun from(offer: Offer): OfferEditState = OfferEditState(
            id = offer.id,
            title = offer.title,
            discountPercent = offer.discountPercent?.toString().orEmpty(),
            price = offer.price?.toPlainString().orEmpty(),
            startsAt = offer.startsAt,
            endsAt = offer.endsAt,
            isActive = offer.isActive,
        )
    }
}

/**
 * Immutable state for the offers screen.
 *
 * [now] is captured when the list loads so the Active/Scheduled/Expired labels
 * are consistent within one render (and so the screen is a pure function of its
 * state rather than of the wall clock).
 */
data class OffersUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val productId: String? = null,
    val offers: List<Offer> = emptyList(),
    val now: Instant = AppClock.now(),
    val loadError: AppError? = null,
    val editor: OfferEditState? = null,
    val deletingId: String? = null,
    val actionError: AppError? = null,
) {

    val isBusy: Boolean get() = editor?.isSaving == true || deletingId != null
}
