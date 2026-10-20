package com.vedant.admin.domain.model

import com.vedant.admin.core.money.Money
import kotlinx.datetime.Instant

/**
 * A single row of the orders list — the minimum needed to render a dense,
 * scannable line without loading every order's items.
 *
 * The list never re-sorts: the server returns newest-first and that order is
 * preserved, so a null [createdAt] cannot reorder the list.
 */
data class OrderSummary(
    val id: String,
    val status: OrderStatus?,
    val statusRaw: String,
    val customerRef: String?,
    val customerName: String?,
    val createdAt: Instant?,
    val itemCount: Int,
    val finalTotal: Money,
) {

    /**
     * A short, human-typable handle for the order.
     *
     * Orders may carry a human `request_id` (e.g. `ORD-261011-ABD95`) or a raw
     * uuid; this keeps the last segment so a row stays readable either way.
     */
    val shortId: String
        get() = id.substringAfterLast('-').ifBlank { id }.take(8).uppercase()

    val statusLabel: String get() = status?.label ?: statusRaw

    val customerLabel: String
        get() = customerName?.takeIf { it.isNotBlank() } ?: customerRef?.let { "Customer $it" } ?: "Customer"
}
