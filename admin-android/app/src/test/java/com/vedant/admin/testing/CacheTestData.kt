package com.vedant.admin.testing

import com.vedant.admin.core.money.Money
import com.vedant.admin.data.local.CacheStore
import com.vedant.admin.data.local.ReadRetryScheduler
import com.vedant.admin.data.remote.dto.OrderDto
import kotlinx.datetime.Instant

/**
 * Fakes for the offline cache and the safe-read retry.
 *
 * The cache is an in-memory map — the DataStore-backed implementation is thin
 * enough that what matters is the **policy**: only reads are cached, only a
 * network failure falls back, and only reads are ever retried.
 */

/** An in-memory [CacheStore]. */
class FakeCacheStore : CacheStore {

    val entries = mutableMapOf<String, String>()
    val times = mutableMapOf<String, Instant>()
    var clearCalls = 0

    override suspend fun readRaw(key: String): String? = entries[key]

    override suspend fun writeRaw(key: String, json: String) {
        entries[key] = json
        times[key] = Instant.fromEpochMilliseconds(0L)
    }

    override suspend fun writtenAt(key: String): Instant? = times[key]

    override suspend fun clear() {
        clearCalls++
        entries.clear()
        times.clear()
    }
}

/** A [ReadRetryScheduler] that records what was asked of it. */
class FakeReadRetryScheduler : ReadRetryScheduler {

    var scheduled = 0
    var cancelled = 0

    override fun scheduleSafeReadRetry() {
        scheduled++
    }

    override fun cancel() {
        cancelled++
    }
}

/** A raw order row for the cache tests. */
fun testOrderDto(
    id: String = "o1",
    status: String? = "PENDING",
    finalTotal: Money = Money(15_200),
): OrderDto = OrderDto(
    id = id,
    userId = "4bf3e5a5-2a93-44e7-b4f7-6e1e71180299",
    status = status,
    finalTotal = finalTotal,
    originalTotal = Money(15_200),
    adjustmentAmount = Money.ZERO,
)
