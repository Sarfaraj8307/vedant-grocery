package com.vedant.admin.testing

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.DashboardCounts
import com.vedant.admin.domain.model.Order
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderItem
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.OrderStatusEvent
import com.vedant.admin.domain.model.OrderSummary
import com.vedant.admin.domain.repository.OrderRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Instant

/**
 * Test data and a fake repository, shared by the ViewModel tests.
 *
 * The fake lets a test drive each of the three mutations to a chosen
 * `Outcome`, and — via [FakeOrderRepository.gate] — hold a mutation open so the
 * in-flight state is observable. That is what makes "a double-submit is blocked"
 * and "a failed change leaves the order untouched" assertable without a device,
 * a network, or admin credentials (the live RPC path stays **[BLOCKED]**, R1).
 */

private val FIXED_INSTANT = Instant.parse("2026-10-11T10:00:00Z")

fun testItem(
    id: String = "i1",
    productName: String = "Tomatoes",
    weightLabel: String? = "1 kg",
    qty: Int = 2,
    unitPrice: Money = Money(4_000),
    lineTotal: Money = Money(8_000),
    rejected: Boolean = false,
    rejectionReason: String? = null,
): OrderItem = OrderItem(
    id = id,
    productId = "p1",
    productName = productName,
    weightLabel = weightLabel,
    qty = qty,
    unitPrice = unitPrice,
    lineTotal = lineTotal,
    rejected = rejected,
    rejectionReason = rejectionReason,
)

fun testOrder(
    id: String = "o1",
    status: OrderStatus? = OrderStatus.PENDING,
    statusRaw: String = status?.name ?: "PENDING",
    originalTotal: Money = Money(15_200),
    adjustmentAmount: Money = Money.ZERO,
    finalTotal: Money = Money(15_200),
    deliveryCharge: Money = Money(4_000),
    items: List<OrderItem> = listOf(testItem()),
    history: List<OrderStatusEvent> = emptyList(),
): Order = Order(
    id = id,
    status = status,
    statusRaw = statusRaw,
    originalTotal = originalTotal,
    adjustmentAmount = adjustmentAmount,
    finalTotal = finalTotal,
    deliveryCharge = deliveryCharge,
    subtotal = null,
    deliverySlotId = "slot-1",
    customerRef = "cust-1",
    customerName = "Asha",
    addressLines = listOf("12 Market Road", "Pune 411001"),
    adjustmentReason = null,
    adjustedAt = null,
    createdAt = FIXED_INSTANT,
    updatedAt = null,
    items = items,
    history = history,
)

fun testSummary(
    id: String = "o1",
    status: OrderStatus? = OrderStatus.PENDING,
    finalTotal: Money = Money(15_200),
): OrderSummary = OrderSummary(
    id = id,
    status = status,
    statusRaw = status?.name ?: "PENDING",
    customerRef = "cust-1",
    customerName = "Asha",
    createdAt = FIXED_INSTANT,
    itemCount = 1,
    finalTotal = finalTotal,
)

/** A scriptable [OrderRepository] for the ViewModel tests. */
class FakeOrderRepository : OrderRepository {

    var getOrderResult: Outcome<Order> = Outcome.Success(testOrder())
    var updateStatusResult: Outcome<Order> = Outcome.Success(testOrder())
    var rejectResult: Outcome<Order> = Outcome.Success(testOrder())
    var restoreResult: Outcome<Order> = Outcome.Success(testOrder())

    /** One `Outcome` per page index; a missing index yields an empty page. */
    var listResults: List<Outcome<List<OrderSummary>>> = emptyList()

    var getOrderCalls = 0
    var updateStatusCalls = 0
    var rejectCalls = 0
    var restoreCalls = 0
    val listRequests = mutableListOf<Pair<OrderFilter, Int>>()

    /**
     * When non-null, a mutation suspends on it. Complete it to let the request
     * finish — this is how a test holds a mutation "in flight".
     */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun listOrders(
        filter: OrderFilter,
        page: Int,
        pageSize: Int,
    ): Outcome<List<OrderSummary>> {
        listRequests += filter to page
        return listResults.getOrElse(page) { Outcome.Success(emptyList()) }
    }

    override suspend fun getOrder(orderId: String): Outcome<Order> {
        getOrderCalls++
        return getOrderResult
    }

    override suspend fun updateStatus(
        orderId: String,
        status: OrderStatus,
        reason: String?,
    ): Outcome<Order> {
        updateStatusCalls++
        gate?.await()
        return updateStatusResult
    }

    override suspend fun rejectItem(itemId: String, reason: String): Outcome<Order> {
        rejectCalls++
        gate?.await()
        return rejectResult
    }

    override suspend fun restoreItem(itemId: String): Outcome<Order> {
        restoreCalls++
        gate?.await()
        return restoreResult
    }

    override suspend fun statusHistory(orderId: String): Outcome<List<OrderStatusEvent>> =
        Outcome.Success(emptyList())

    override suspend fun dashboardCounts(): Outcome<DashboardCounts> =
        Outcome.Success(DashboardCounts.EMPTY)
}
