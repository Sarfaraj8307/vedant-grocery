package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * One offer on a product (`offers`).
 *
 * Live columns (`docs/CONTRACT.md` §3.4): `title`, `price`, `discount_percent`,
 * `starts_at`, `ends_at`, `is_active`, `product_id`. Either a flat [price] or a
 * [discountPercent] may be set; both may be null. The window is optional.
 */
data class Offer(
    val id: String,
    val productId: String,
    val title: String,
    val price: Money?,
    val discountPercent: Int?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {

    /** True before the window opens. */
    fun isScheduled(now: Instant): Boolean = startsAt?.let { it > now } == true

    /** True after the window closes. */
    fun isExpired(now: Instant): Boolean = endsAt?.let { it < now } == true

    /** True when the offer is enabled and inside its window. */
    fun isLive(now: Instant): Boolean = isActive && !isScheduled(now) && !isExpired(now)

    /**
     * A short state word for the list. `now` is passed in so the function stays
     * pure and testable.
     */
    fun statusLabel(now: Instant): String = when {
        !isActive -> "Disabled"
        isExpired(now) -> "Expired"
        isScheduled(now) -> "Scheduled"
        else -> "Active"
    }

    /** What the offer gives, e.g. `20% off` or `₹60.00`. */
    val valueLabel: String
        get() = discountPercent?.let { "$it% off" } ?: price?.format() ?: "—"
}

/** The editable shape of an offer. */
data class OfferDraft(
    val id: String? = null,
    val productId: String,
    val title: String,
    val price: Money? = null,
    val discountPercent: Int? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val isActive: Boolean = true,
) {

    val isCreate: Boolean get() = id.isNullOrBlank()

    companion object {
        fun from(offer: Offer): OfferDraft = OfferDraft(
            id = offer.id,
            productId = offer.productId,
            title = offer.title,
            price = offer.price,
            discountPercent = offer.discountPercent,
            startsAt = offer.startsAt,
            endsAt = offer.endsAt,
            isActive = offer.isActive,
        )
    }
}
