package com.vedant.admin.domain.model

/**
 * Counts of today's orders by status, for the dashboard.
 *
 * Built from server-side `count` reads — never by counting a loaded list, which
 * would be a different number as soon as pagination kicks in.
 */
data class DashboardCounts(
    val countsByStatus: Map<OrderStatus, Int>,
    /** Total orders counted, including any whose status is not a known enum. */
    val total: Int,
) {

    fun countOf(status: OrderStatus): Int = countsByStatus[status] ?: 0

    companion object {
        val EMPTY = DashboardCounts(emptyMap(), 0)
    }
}
