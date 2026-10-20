package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a `delivery_slots` row — **both members of pairs 3 and 4**.
 *
 *  - Pair 4: `starts_at`/`ends_at` (`timestamptz`, authoritative) and
 *    `start_time`/`end_time` (`time`, the IST wall-clock mirror). The names
 *    `starts_at`/`start_time` exist only here and in `SlotMapper`.
 *  - Pair 3: `capacity` (authoritative) and `max_orders` (mirror).
 */
@Serializable
data class DeliverySlotDto(
    val id: String? = null,
    val name: String? = null,

    // --- Pair 4 ---------------------------------------------------------------
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,

    // --- Pair 3 ---------------------------------------------------------------
    val capacity: Int? = null,
    @SerialName("max_orders") val maxOrders: Int? = null,

    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
