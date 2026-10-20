package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.SlotRemoteSource
import com.vedant.admin.data.remote.dto.DeliverySlotDto
import com.vedant.admin.data.remote.dto.DeliveryOrderDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.domain.model.DeliverySlotDraft
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T21 acceptance, unit-tested with a **fake** PostgREST (`FakeSlotRemote`) and a
 * fake RPC seam (`FakeAdminRpc`). Live slot reads and `admin_slot_load` are
 * **[BLOCKED]** (no admin creds — R1), so these tests prove the *repository
 * contract*:
 *
 *  - the window is half-open on the authoritative `starts_at`;
 *  - the `admin_slot_load` rows normalise (booked/capacity/remaining/isFull);
 *  - a write returns the server-confirmed row; a gone row is a `Failure`;
 *  - an inverted window or a zero capacity is refused before the server.
 */
class SlotRepositoryImplTest {

    private val remote = FakeSlotRemote()
    private val rpc = FakeAdminRpc()
    private val repository = SlotRepositoryImpl(remote = remote, adminRpc = rpc)

    private val from = Instant.parse("2026-10-11T00:00:00Z")

    private fun slotDto(id: String = "s1") = DeliverySlotDto(
        id = id,
        name = "Morning",
        startsAt = "2026-10-11T02:30:00Z",
        endsAt = "2026-10-11T04:30:00Z",
        capacity = 25,
    )

    private fun draft(id: String? = null) = DeliverySlotDraft(
        id = id,
        name = "Morning",
        startsAt = Instant.parse("2026-10-11T02:30:00Z"),
        endsAt = Instant.parse("2026-10-11T04:30:00Z"),
        capacity = 25,
    )

    // --- reads --------------------------------------------------------------

    @Test
    fun `listSlots queries the half-open window ending days later`() = runTest {
        remote.slots = listOf(slotDto())

        val result = repository.listSlots(from, days = 7)

        assertTrue(result is Outcome.Success)
        assertEquals(1, (result as Outcome.Success).value.size)
        assertEquals(Instant.parse("2026-10-18T00:00:00Z"), remote.lastTo)
    }

    @Test
    fun `slotLoad normalises the RPC rows`() = runTest {
        rpc.loadRows = listOf(SlotLoadDto(slotId = "s1", capacity = 25, booked = 18))

        val result = repository.slotLoad(from, days = 7)

        assertTrue(result is Outcome.Success)
        val load = (result as Outcome.Success).value.single()
        assertEquals(18, load.booked)
        assertEquals(25, load.capacity)
        assertEquals(7, load.remaining)
        assertFalse(load.isFull)
    }

    @Test
    fun `slotLoad marks a slot at capacity as full`() = runTest {
        rpc.loadRows = listOf(SlotLoadDto(slotId = "s1", capacity = 10, booked = 10))

        val result = repository.slotLoad(from)

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.single().isFull)
    }

    @Test
    fun `slotLoad reads an alias column when the RPC shape differs`() = runTest {
        // The real column name is BLOCKED; the mapper must survive `booked_count`.
        rpc.loadRows = listOf(SlotLoadDto(slotId = "s1", maxOrders = 20, bookedCount = 5))

        val result = repository.slotLoad(from)

        assertTrue(result is Outcome.Success)
        val load = (result as Outcome.Success).value.single()
        assertEquals(20, load.capacity)
        assertEquals(5, load.booked)
    }

    // --- writes -------------------------------------------------------------

    @Test
    fun `ensureSlots calls the RPC with the window`() = runTest {
        val result = repository.ensureSlots(from, days = 3)

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("ensure:$from:3"), rpc.calls)
    }

    @Test
    fun `saveSlot inserts when the draft has no id`() = runTest {
        val result = repository.saveSlot(draft())

        assertTrue(result is Outcome.Success)
        assertEquals("new-slot", (result as Outcome.Success).value.id)
        assertEquals(1, remote.inserted.size)
        assertTrue(remote.updated.isEmpty())
    }

    @Test
    fun `saveSlot writes both members of pair 3 and pair 4`() = runTest {
        repository.saveSlot(draft())

        val sent = remote.inserted.single()
        // pair 3: capacity == max_orders
        assertEquals(sent.capacity, sent.maxOrders)
        // pair 4: starts_at/ends_at authoritative, start_time/end_time IST mirror present
        assertEquals("2026-10-11T02:30:00Z", sent.startsAt)
        assertEquals("08:00:00", sent.startTime) // 02:30Z == 08:00 IST
    }

    @Test
    fun `saveSlot updates when the draft has an id`() = runTest {
        val result = repository.saveSlot(draft(id = "s1"))

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("s1"), remote.updated.map { it.first })
        assertTrue(remote.inserted.isEmpty())
    }

    @Test
    fun `saveSlot is a Failure when an updated slot is gone`() = runTest {
        remote.updateMissing = true

        val result = repository.saveSlot(draft(id = "s1"))

        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun `saveSlot refuses an inverted window before the server`() = runTest {
        val bad = DeliverySlotDraft(
            startsAt = Instant.parse("2026-10-11T10:00:00Z"),
            endsAt = Instant.parse("2026-10-11T08:00:00Z"),
            capacity = 10,
        )

        val result = repository.saveSlot(bad)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        assertTrue(remote.inserted.isEmpty())
    }

    @Test
    fun `saveSlot refuses a zero capacity`() = runTest {
        val result = repository.saveSlot(draft().copy(capacity = 0))

        assertTrue(result is Outcome.Failure)
        assertTrue(remote.inserted.isEmpty())
    }

    @Test
    fun `deleteSlot delegates`() = runTest {
        val result = repository.deleteSlot("s1")

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("s1"), remote.deleted)
    }

    // --- fakes --------------------------------------------------------------

    private class FakeSlotRemote : SlotRemoteSource {
        var slots: List<DeliverySlotDto> = emptyList()
        var lastTo: Instant? = null
        var updateMissing = false
        val inserted = mutableListOf<DeliverySlotDto>()
        val updated = mutableListOf<Pair<String, DeliverySlotDto>>()
        val deleted = mutableListOf<String>()

        override suspend fun listSlots(from: Instant, to: Instant): List<DeliverySlotDto> {
            lastTo = to
            return slots
        }

        override suspend fun insertSlot(fields: DeliverySlotDto): DeliverySlotDto {
            inserted += fields
            return fields.copy(id = "new-slot")
        }

        override suspend fun updateSlot(slotId: String, fields: DeliverySlotDto): DeliverySlotDto? {
            updated += slotId to fields
            return if (updateMissing) null else fields.copy(id = slotId)
        }

        override suspend fun deleteSlot(slotId: String) {
            deleted += slotId
        }
    }

    private class FakeAdminRpc : AdminRpc {
        val calls = mutableListOf<String>()
        var loadRows: List<SlotLoadDto> = emptyList()

        override suspend fun isAdmin(): Boolean = true

        override suspend fun updateOrderStatus(orderId: String, status: String, reason: String?) = Unit

        override suspend fun rejectOrderItem(itemId: String, reason: String) = Unit

        override suspend fun restoreOrderItem(itemId: String) = Unit

        override suspend fun ensureDeliverySlots(from: String, days: Int) {
            calls += "ensure:$from:$days"
        }

        override suspend fun deliveryOrders(onlyMine: Boolean): List<DeliveryOrderDto> = emptyList()
        override suspend fun adminSlotLoad(from: String, days: Int): List<SlotLoadDto> {
            calls += "load:$from:$days"
            return loadRows
        }
    }
}
