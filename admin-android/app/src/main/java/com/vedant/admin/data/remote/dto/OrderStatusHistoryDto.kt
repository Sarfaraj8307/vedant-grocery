package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of an `order_status_history` row — one step of the order timeline.
 *
 * **Column availability: BLOCKED (no admin creds — R1).** The shape mirrors the
 * PWA's select (`id, order_id, old_status, new_status, changed_by, reason,
 * created_at`). Status endpoints are `String` (not the enum) so an unknown value
 * is tolerated rather than fatal (§5.2).
 */
@Serializable
data class OrderStatusHistoryDto(
    val id: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("old_status") val oldStatus: String? = null,
    @SerialName("new_status") val newStatus: String? = null,
    @SerialName("changed_by") val changedBy: String? = null,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
