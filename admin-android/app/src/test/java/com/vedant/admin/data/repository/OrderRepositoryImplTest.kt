package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.OrderRemoteSource
import com.vedant.admin.data.remote.dto.OrderDto
import com.vedant.admin.data.remote.dto.DashboardCountsDto
import com.vedant.admin.data.remote.dto.SalesTodayDto
import com.vedant.admin.data.remote.dto.OrderStatusHistoryDto
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.domain.model.OrderFilter
import com.vedant.admin.domain.model.OrderStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T14 acceptance, unit-tested with a **fake** PostgREST (`FakeRemote`) and a fake
 * RPC seam (`FakeAdminRpc`). Live RPC verification is **[BLOCKED]** — there are
 * no admin credentials (R1) — so these tests prove the *repository contract*:
 *
 *  - a rejected transition returns `Failure` and does **not** re-fetch, leaving
 *    the order unchanged;
 *  - a success returns the **re-fetched server row**;
 *  - a missing rejection reason is refused before any RPC call.
 */
class OrderRepositoryImplTest {

    private val remote = FakeRemote()
    private val adminRpc = FakeAdminRpc()
    private val repository = OrderRepositoryImpl(remote = remote, adminRpc = adminRpc)

    private fun orderDto(
        id: String = "o1",
        status: String? = "PENDING",
        finalTotal: Money = Money(15_200),
        adjustment: Money = Money.ZERO,
    ) = OrderDto(
        id = id,
        userId = "4bf3e5a5-2a93-44e7-b4f7-6e1e71180299",
        status = status,
        finalTotal = finalTotal,
        originalTotal = Money(15_200),
        adjustmentAmount = adjustment,
    )

    // --- updateStatus -------------------------------------------------------

    @Test
    fun `a rejected transition returns Failure and leaves the order unchanged`() = runTest {
        remote.orders["o1"] = orderDto(status = "PENDING")
        adminRpc.failure = RestException("42501", "permission denied", 403, "")

        val result = repository.updateStatus("o1", OrderStatus.CONFIRMED, null)

        assertTrue("expected a Failure", result is Outcome.Failure)
        assertEquals(AppError.Unauthorized, (result as Outcome.Failure).error)
        // The UI must not move: no re-fetch happened, so no new row exists.
        assertEquals(0, remote.getOrderCalls)
    }

    @Test
    fun `a successful transition returns the re-fetched server row`() = runTest {
        // The server's post-transition row — the only thing the UI may render.
        remote.orders["o1"] = orderDto(status = "CONFIRMED")

        val result = repository.updateStatus("o1", OrderStatus.CONFIRMED, null)

        assertTrue(result is Outcome.Success)
        assertEquals(OrderStatus.CONFIRMED, (result as Outcome.Success).value.status)
        assertEquals(1, remote.getOrderCalls)
        assertEquals(listOf("update:o1:CONFIRMED:null"), adminRpc.calls)
    }

    @Test
    fun `a blank cancellation reason is normalised away before the RPC`() = runTest {
        remote.orders["o1"] = orderDto(status = "CANCELLED")

        val result = repository.updateStatus("o1", OrderStatus.CANCELLED, "   ")

        assertTrue(result is Outcome.Success)
        // The client does not invent a reason; it sends null and lets the server reject.
        assertEquals(listOf("update:o1:CANCELLED:null"), adminRpc.calls)
    }

    // --- rejectItem / restoreItem -------------------------------------------

    @Test
    fun `rejectItem refuses a blank reason without calling the server`() = runTest {
        remote.itemToOrder["i1"] = "o1"

        val result = repository.rejectItem("i1", "   ")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        assertTrue("no RPC may be attempted", adminRpc.calls.isEmpty())
        assertEquals(0, remote.getOrderCalls)
    }

    @Test
    fun `rejectItem resolves the order, calls the RPC and returns server totals`() = runTest {
        remote.itemToOrder["i1"] = "o1"
        // Server's recomputed row: original 152.00, adjustment -132.00, final 20.00.
        remote.orders["o1"] = orderDto(
            status = "CONFIRMED",
            finalTotal = Money(2_000),
            adjustment = Money(-13_200),
        )

        val result = repository.rejectItem("i1", "damaged")

        assertTrue(result is Outcome.Success)
        val order = (result as Outcome.Success).value
        // Totals come from the fake server, never from local arithmetic.
        assertEquals(Money(2_000), order.finalTotal)
        assertEquals(Money(-13_200), order.adjustmentAmount)
        assertTrue(order.hasAdjustment)
        assertEquals(listOf("reject:i1:damaged"), adminRpc.calls)
    }

    @Test
    fun `rejectItem fails when the item cannot be found`() = runTest {
        val result = repository.rejectItem("missing", "damaged")

        assertTrue(result is Outcome.Failure)
        assertTrue(adminRpc.calls.isEmpty())
    }

    @Test
    fun `restoreItem resolves the order, calls the RPC and returns server totals`() = runTest {
        remote.itemToOrder["i1"] = "o1"
        remote.orders["o1"] = orderDto(status = "CONFIRMED", finalTotal = Money(15_200))

        val result = repository.restoreItem("i1")

        assertTrue(result is Outcome.Success)
        assertEquals(Money(15_200), (result as Outcome.Success).value.finalTotal)
        assertFalse((result.value).hasAdjustment)
        assertEquals(listOf("restore:i1"), adminRpc.calls)
    }

    // --- reads --------------------------------------------------------------

    @Test
    fun `getOrder returns Failure when the order does not exist`() = runTest {
        val result = repository.getOrder("nope")
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun `listOrders maps rows to summaries`() = runTest {
        remote.listResult = listOf(orderDto(id = "ORD-261011-ABD95"), orderDto(id = "ORD-261011-XYZ12"))

        val result = repository.listOrders(OrderFilter(), page = 0)

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `dashboardCounts maps raw statuses to the enum and totals them`() = runTest {
        remote.counts = mapOf("PENDING" to 3, "CONFIRMED" to 2, "NOT_A_STATUS" to 1)

        val result = repository.dashboardCounts()

        assertTrue(result is Outcome.Success)
        val counts = (result as Outcome.Success).value
        assertEquals(3, counts.countOf(OrderStatus.PENDING))
        assertEquals(2, counts.countOf(OrderStatus.CONFIRMED))
        assertEquals(0, counts.countOf(OrderStatus.DELIVERED))
        assertEquals(6, counts.total)
    }

    // --- Fakes --------------------------------------------------------------

    private class FakeRemote : OrderRemoteSource {
        val orders = mutableMapOf<String, OrderDto>()
        val itemToOrder = mutableMapOf<String, String>()
        var listResult: List<OrderDto> = emptyList()
        var counts: Map<String, Int> = emptyMap()
        var getOrderCalls = 0

        override suspend fun listOrders(
            orderFilter: OrderFilter,
            page: Int,
            pageSize: Int,
        ): List<OrderDto> = listResult

        override suspend fun getOrder(orderId: String): OrderDto? {
            getOrderCalls++
            return orders[orderId]
        }

        override suspend fun statusHistory(orderId: String): List<OrderStatusHistoryDto> = emptyList()

        override suspend fun orderIdForItem(itemId: String): String? = itemToOrder[itemId]

        override suspend fun statusCountsToday(): Map<String, Int> = counts
    }

    private class FakeAdminRpc : AdminRpc {
        var failure: Throwable? = null
        val calls = mutableListOf<String>()

        override suspend fun isAdmin(): Boolean = true

        override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) {
            failure?.let { throw it }
            calls += "update:$orderId:$status:$reason"
        }

        override suspend fun rejectOrderItem(itemId: String, reason: String) {
            failure?.let { throw it }
            calls += "reject:$itemId:$reason"
        }

        override suspend fun restoreOrderItem(itemId: String) {
            failure?.let { throw it }
            calls += "restore:$itemId"
        }

        override suspend fun ensureDeliverySlots(from: String, days: Int) {
            failure?.let { throw it }
            calls += "ensureSlots:$from:$days"
        }

        override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> = emptyList()
        override suspend fun isAcceptingOrders(): Boolean? = true

        override suspend fun setOrderAcceptance(accepting: Boolean, reason: String?) = Unit

        override suspend fun reportDeliveryIssue(orderId: String, type: String, reason: String) = Unit

        override suspend fun dashboardCounts(): DashboardCountsDto? = null

        override suspend fun salesToday(): SalesTodayDto? = null

        override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> {
            failure?.let { throw it }
            calls += "slotLoad:$from:$days"
            return emptyList()
        }
    }
}
