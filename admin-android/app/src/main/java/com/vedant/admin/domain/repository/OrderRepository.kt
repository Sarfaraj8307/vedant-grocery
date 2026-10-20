package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.DashboardCounts
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.model.OrderSummary

/**
 * Order reads and the three admin mutations.
 *
 * **Every mutation returns the re-fetched server row** (`Outcome<Order>`), never
 * the request and never a locally constructed post-mutation state. That is how
 * "never fake success" is enforced in code: the ViewModel has nothing to render
 * until the server answers, and on a rejection it surfaces the mapped
 * [com.vedant.admin.core.error.AppError] while leaving the UI unchanged.
 */
interface OrderRepository {

    /** Newest-first page of orders matching [filter]. */
    suspend fun listOrders(
        filter: OrderFilter,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Outcome<List<OrderSummary>>

    /** The full order, with its items and status history. */
    suspend fun getOrder(orderId: String): Outcome<Order>

    /**
     * `admin_update_order_status`. On success returns the server's updated row;
     * on a rejected transition returns `Failure` and the order is untouched.
     */
    suspend fun updateStatus(orderId: String, status: OrderStatus, reason: String?): Outcome<Order>

    /** Reject one line. Returns the re-fetched order with the server's totals. */
    suspend fun rejectItem(itemId: String, reason: String): Outcome<Order>

    /** Undo a rejection. Returns the re-fetched order with the server's totals. */
    suspend fun restoreItem(itemId: String): Outcome<Order>

    /** The `order_status_history` timeline for one order. */
    suspend fun statusHistory(orderId: String): Outcome<List<OrderStatusEvent>>

    /** Counts of today's orders by status (server-side `count` reads). */
    suspend fun dashboardCounts(): Outcome<DashboardCounts>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
