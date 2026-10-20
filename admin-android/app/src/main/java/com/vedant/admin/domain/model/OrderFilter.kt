package com.vedant.admin.domain.model

/**
 * The list query: an optional status facet and a free-text search.
 *
 * Both are applied **server-side** so pagination stays correct — filtering a
 * page client-side would silently hide matches on other pages.
 */
data class OrderFilter(
    val status: OrderStatus? = null,
    val query: String = "",
) {

    val isActive: Boolean get() = status != null || query.isNotBlank()

    /** The trimmed search term, or `null` when there is nothing to search. */
    val searchTerm: String? get() = query.trim().takeIf { it.isNotEmpty() }

    companion object {
        val NONE = OrderFilter()
    }
}
