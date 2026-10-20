package com.vedant.admin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of one row of `admin_slot_load(p_from, p_days)`.
 *
 * **The RPC's exact result columns are BLOCKED (no admin creds — R1)** — the
 * function body is not introspectable and cannot be called without an admin
 * session. Rather than guess one name, this DTO declares every plausible alias
 * (tolerant decode, §5.2) and the mapper picks the first non-null. Once T02
 * enumerates the real shape, the extras can be pruned.
 */
@Serializable
data class SlotLoadDto(
    val id: String? = null,
    @SerialName("slot_id") val slotId: String? = null,
    val name: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,

    val capacity: Int? = null,
    @SerialName("max_orders") val maxOrders: Int? = null,

    // Plausible names for "how many are booked"; the mapper takes the first present.
    val booked: Int? = null,
    @SerialName("booked_count") val bookedCount: Int? = null,
    val orders: Int? = null,
    @SerialName("order_count") val orderCount: Int? = null,
    val used: Int? = null,
    val load: Int? = null,
    val remaining: Int? = null,

    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)
