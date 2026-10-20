package com.vedant.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the live Postgres `order_status` enum **exactly**.
 *
 * The wire values are the database's. Customer-facing wording lives in [label],
 * not in the enum name — `PENDING` is shown as "Order received", not "Pending
 * payment". Keeping the names identical to the enum means there is no
 * translation layer to drift.
 */
@Serializable
enum class OrderStatus {
    @SerialName("PENDING")
    PENDING,

    @SerialName("CONFIRMED")
    CONFIRMED,

    @SerialName("PROCESSING")
    PROCESSING,

    @SerialName("READY_FOR_DELIVERY")
    READY_FOR_DELIVERY,

    @SerialName("OUT_FOR_DELIVERY")
    OUT_FOR_DELIVERY,

    @SerialName("DELIVERED")
    DELIVERED,

    @SerialName("CANCELLED")
    CANCELLED,
    ;

    /** Wording shown to staff and (via the same shared wording) to customers. */
    val label: String
        get() = when (this) {
            PENDING -> "Order received"
            CONFIRMED -> "Confirmed by store"
            PROCESSING -> "Being packed"
            READY_FOR_DELIVERY -> "Ready to dispatch"
            OUT_FOR_DELIVERY -> "Out for delivery"
            DELIVERED -> "Delivered"
            CANCELLED -> "Cancelled"
        }

    /** True for states no transition may leave. Mirrors `order_transition_allowed`. */
    val isTerminal: Boolean
        get() = this == DELIVERED || this == CANCELLED

    /**
     * The actions the UI may offer from this state.
     *
     * This is a *presentation* convenience only. The authoritative gate is
     * `private.order_transition_allowed` in Postgres, enforced inside
     * `admin_update_order_status`. If the two ever disagree, the server wins and
     * the app surfaces the rejection — the UI never assumes success.
     */
    val nextStatuses: List<OrderStatus>
        get() = when (this) {
            PENDING -> listOf(CONFIRMED, CANCELLED)
            CONFIRMED -> listOf(PROCESSING, CANCELLED)
            PROCESSING -> listOf(READY_FOR_DELIVERY, CANCELLED)
            READY_FOR_DELIVERY -> listOf(OUT_FOR_DELIVERY, CANCELLED)
            OUT_FOR_DELIVERY -> listOf(DELIVERED, CANCELLED)
            DELIVERED, CANCELLED -> emptyList()
        }

    companion object {
        /** Display order for the tracker and the status filter. */
        val flow: List<OrderStatus> = listOf(
            PENDING, CONFIRMED, PROCESSING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERED,
        )

        /** Tolerant decode: an unknown enum value must not crash the list. */
        fun fromWire(value: String?): OrderStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}