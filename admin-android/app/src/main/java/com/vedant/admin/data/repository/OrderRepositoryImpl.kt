package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.local.CacheKeys
import com.vedant.admin.data.local.CacheStore
import com.vedant.admin.data.local.ReadRetryScheduler
import com.vedant.admin.data.local.read
import com.vedant.admin.data.local.write
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.OrderRemoteSource
import com.vedant.admin.data.remote.dto.OrderDto
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toSummary
import com.vedant.admin.domain.model.DashboardCounts
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.model.OrderSummary
import com.vedant.admin.domain.repository.OrderRepository
import kotlinx.serialization.builtins.ListSerializer

private const val TAG = "OrderRepository"

/** The snapshot serializer for a page of raw order rows. */
private val ORDER_PAGE_SERIALIZER = ListSerializer(OrderDto.serializer())

/**
 * The real [OrderRepository].
 *
 * It composes two replaceable collaborators:
 *  - [OrderRemoteSource] — the PostgREST **reads** (list/detail/history/counts).
 *  - [AdminRpc]          — the three **mutations** (`admin_update_order_status`,
 *    `admin_reject_order_item`, `admin_restore_order_item`).
 *
 * ### Never fake success
 * A mutation does not return the request: it calls the RPC, then **re-fetches**
 * the order and returns the server's row. If the RPC throws (e.g. `42501`, an
 * illegal transition, a missing reason) the exception propagates as an
 * `Outcome.Failure` and **nothing is re-fetched** — so the ViewModel has no new
 * state to render and the UI stays exactly where it was.
 *
 * ### Offline (T24)
 * Only the **unfiltered first page** of the list is cached, and only a **network**
 * failure falls back to it — never an auth or validation error. A fallback is
 * still an `Outcome.Success`, and the persistent offline banner (driven by
 * connectivity) is what tells the operator the rows are saved rather than live.
 * A mutation is never cached and never queued: if the server cannot be reached
 * the UI disables the action.
 */
class OrderRepositoryImpl(
    private val remote: OrderRemoteSource,
    private val adminRpc: AdminRpc,
    private val cache: CacheStore? = null,
    private val retry: ReadRetryScheduler? = null,
) : OrderRepository {

    override suspend fun listOrders(
        filter: OrderFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<OrderSummary>> {
        val outcome = outcomeOf { remote.listOrders(filter, page, pageSize) }
        return when (outcome) {
            is Outcome.Success -> {
                cacheFirstPage(filter, page, outcome.value)
                Outcome.Success(outcome.value.map { it.toSummary() })
            }

            is Outcome.Failure -> {
                val cached = cachedFirstPage(filter, page, outcome.error)
                if (cached != null) Outcome.Success(cached.map { it.toSummary() }) else outcome
            }
        }
    }

    /** Stores the unfiltered first page — the one a cold start would show. */
    private suspend fun cacheFirstPage(filter: OrderFilter, page: Int, rows: List<OrderDto>) {
        val store = cache ?: return
        if (page != 0 || filter.isActive) return
        store.write(CacheKeys.ORDERS_FIRST_PAGE, ORDER_PAGE_SERIALIZER, rows)
    }

    /**
     * The offline fallback: only for a **network** failure on the unfiltered
     * first page. It also schedules a safe-read retry, so the snapshot is
     * refreshed as soon as a network is available again.
     */
    private suspend fun cachedFirstPage(
        filter: OrderFilter,
        page: Int,
        error: AppError,
    ): List<OrderDto>? {
        if (error != AppError.Network || page != 0 || filter.isActive) return null
        retry?.scheduleSafeReadRetry()
        return cache?.read(CacheKeys.ORDERS_FIRST_PAGE, ORDER_PAGE_SERIALIZER)
    }

    override suspend fun getOrder(orderId: String): Outcome<Order> = outcomeOf {
        require(orderId.isNotBlank()) { "An order id is required." }
        remote.getOrder(orderId)?.toDomain() ?: throw notFound("Order", orderId)
    }

    override suspend fun updateStatus(
        orderId: String,
        status: OrderStatus,
        reason: String?,
    ): Outcome<Order> = outcomeOf {
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        AppLog.i(TAG, "updateStatus order=$orderId -> ${status.name}")
        adminRpc.updateOrderStatus(orderId, status.name, cleanReason)
        refetch(orderId)
    }

    override suspend fun rejectItem(itemId: String, reason: String): Outcome<Order> = outcomeOf {
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "A rejection reason is required." }
        val orderId = remote.orderIdForItem(itemId) ?: throw notFound("Item", itemId)
        AppLog.i(TAG, "rejectItem item=$itemId order=$orderId")
        adminRpc.rejectOrderItem(itemId, cleanReason)
        refetch(orderId)
    }

    override suspend fun restoreItem(itemId: String): Outcome<Order> = outcomeOf {
        val orderId = remote.orderIdForItem(itemId) ?: throw notFound("Item", itemId)
        AppLog.i(TAG, "restoreItem item=$itemId order=$orderId")
        adminRpc.restoreOrderItem(itemId)
        refetch(orderId)
    }

    override suspend fun statusHistory(orderId: String): Outcome<List<OrderStatusEvent>> = outcomeOf {
        remote.statusHistory(orderId).map { it.toDomain() }
    }

    override suspend fun dashboardCounts(): Outcome<DashboardCounts> = outcomeOf {
        val raw = remote.statusCountsToday()
        val byStatus = raw.mapNotNull { (wire, count) ->
            OrderStatus.fromWire(wire)?.let { it to count }
        }.toMap()
        DashboardCounts(countsByStatus = byStatus, total = raw.values.sum())
    }

    /** Re-reads the server's row after a mutation. Never returns a local guess. */
    private suspend fun refetch(orderId: String): Order =
        remote.getOrder(orderId)?.toDomain() ?: throw notFound("Order", orderId)

    private fun notFound(entity: String, id: String): NoSuchElementException =
        NoSuchElementException("$entity $id no longer exists.")
}
