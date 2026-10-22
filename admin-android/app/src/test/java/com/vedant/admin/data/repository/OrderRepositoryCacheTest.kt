package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.local.CacheKeys
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
import com.vedant.admin.testing.FakeCacheStore
import com.vedant.admin.testing.FakeReadRetryScheduler
import com.vedant.admin.testing.testOrderDto
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T24 acceptance for the offline cache policy, proven against fakes — the live
 * project is **[BLOCKED]** (no admin creds).
 *
 * The rules that matter:
 *  - only the **unfiltered first page** is cached;
 *  - only a **network** failure falls back to the cache;
 *  - a **mutation is never cached**;
 *  - a network failure **schedules a safe-read retry**.
 */
class OrderRepositoryCacheTest {

    private val remote = FakeOrderRemote()
    private val adminRpc = FakeAdminRpc()
    private val cache = FakeCacheStore()
    private val retry = FakeReadRetryScheduler()
    private val repository = OrderRepositoryImpl(
        remote = remote,
        adminRpc = adminRpc,
        cache = cache,
        retry = retry,
    )

    @Test
    fun `a successful first page is cached`() = runTest {
        remote.listResult = listOf(testOrderDto(id = "o1"))

        val result = repository.listOrders(OrderFilter.NONE, page = 0)

        assertTrue(result is Outcome.Success)
        assertTrue(cache.entries.containsKey(CacheKeys.ORDERS_FIRST_PAGE))
    }

    @Test
    fun `a network failure on the first page falls back to the cache`() = runTest {
        remote.listResult = listOf(testOrderDto(id = "o1"), testOrderDto(id = "o2"))
        repository.listOrders(OrderFilter.NONE, page = 0) // warms the cache

        remote.listError = IOException("no route to host")
        val result = repository.listOrders(OrderFilter.NONE, page = 0)

        assertTrue("the cached page is served", result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
        assertEquals("the retry is scheduled", 1, retry.scheduled)
    }

    @Test
    fun `a non-network failure does not fall back to the cache`() = runTest {
        remote.listResult = listOf(testOrderDto(id = "o1"))
        repository.listOrders(OrderFilter.NONE, page = 0) // warms the cache

        remote.listError = RestException("42501", "permission denied", 403, "")
        val result = repository.listOrders(OrderFilter.NONE, page = 0)

        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.Unauthorized, (result as Outcome.Failure).error)
        assertEquals("an auth failure is not a cache miss", 0, retry.scheduled)
    }

    @Test
    fun `a filtered page is never cached`() = runTest {
        remote.listResult = listOf(testOrderDto(id = "o1"))

        repository.listOrders(OrderFilter(status = OrderStatus.PENDING), page = 0)

        assertFalse(cache.entries.containsKey(CacheKeys.ORDERS_FIRST_PAGE))
    }

    @Test
    fun `a network failure with no cache is a failure`() = runTest {
        remote.listError = IOException("offline")

        val result = repository.listOrders(OrderFilter.NONE, page = 0)

        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.Network, (result as Outcome.Failure).error)
        assertEquals("a retry is still scheduled", 1, retry.scheduled)
    }

    @Test
    fun `a mutation is never written to the cache`() = runTest {
        remote.order = testOrderDto(id = "o1", status = "CONFIRMED")

        val result = repository.updateStatus("o1", OrderStatus.CONFIRMED, null)

        assertTrue(result is Outcome.Success)
        assertTrue("no write may ever become a cache entry", cache.entries.isEmpty())
    }

    // --- Fakes --------------------------------------------------------------

    private class FakeOrderRemote : OrderRemoteSource {
        var listResult: List<OrderDto> = emptyList()
        var listError: Throwable? = null
        var order: OrderDto? = testOrderDto()

        override suspend fun listOrders(
            orderFilter: OrderFilter,
            page: Int,
            pageSize: Int,
        ): List<OrderDto> {
            listError?.let { throw it }
            return listResult
        }

        override suspend fun getOrder(orderId: String): OrderDto? = order

        override suspend fun statusHistory(orderId: String): List<OrderStatusHistoryDto> = emptyList()

        override suspend fun orderIdForItem(itemId: String): String? = "o1"

        override suspend fun statusCountsToday(): Map<String, Int> = emptyMap()
    }

    private class FakeAdminRpc : AdminRpc {
        override suspend fun isAdmin(): Boolean = true
        override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) = Unit
        override suspend fun rejectOrderItem(itemId: String, reason: String) = Unit
        override suspend fun restoreOrderItem(itemId: String) = Unit
        override suspend fun ensureDeliverySlots(from: String, days: Int) = Unit
        override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> = emptyList()
        override suspend fun isAcceptingOrders(): Boolean? = true

        override suspend fun setOrderAcceptance(accepting: Boolean, reason: String?) = Unit

        override suspend fun reportDeliveryIssue(orderId: String, type: String, reason: String) = Unit

        override suspend fun dashboardCounts(): DashboardCountsDto? = null

        override suspend fun salesToday(): SalesTodayDto? = null

        override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> = emptyList()
    }
}
