package com.vedant.admin.data.remote.mapper

import com.vedant.admin.data.remote.dto.DeliverySlotDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.domain.model.DeliverySlotDraft
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T21 acceptance — pairs 3 & 4.
 *
 * The `starts_at`/`ends_at` (`timestamptz`) values are authoritative; the
 * `start_time`/`end_time` mirrors must be the **IST wall-clock**. The fixture
 * below is the live sample row from `docs/CONTRACT.md` §3.5:
 * `starts_at=02:30Z` ⇄ `start_time=08:00` (UTC+5:30).
 */
class SlotMapperTest {

    private val startsAt = Instant.parse("2026-10-11T02:30:00Z")
    private val endsAt = Instant.parse("2026-10-11T04:30:00Z")

    @Test
    fun `starts_at and ends_at are read authoritatively`() {
        val slot = DeliverySlotDto(
            id = "s1",
            startsAt = "2026-10-11T02:30:00Z",
            endsAt = "2026-10-11T04:30:00Z",
            capacity = 25,
        ).toDomain()
        assertEquals(startsAt, slot.startsAt)
        assertEquals(endsAt, slot.endsAt)
        assertEquals(25, slot.capacity)
    }

    @Test
    fun `capacity is read and max_orders is the fallback`() {
        assertEquals(25, DeliverySlotDto(capacity = 25, maxOrders = 9).toDomain().capacity)
        assertEquals(25, DeliverySlotDto(maxOrders = 25).toDomain().capacity)
    }

    @Test
    fun `write emits starts_at as UTC and start_time as the IST wall clock`() {
        val fields = DeliverySlotDraft(
            id = "s1",
            name = "Morning",
            startsAt = startsAt,
            endsAt = endsAt,
            capacity = 25,
        ).toWriteFields()

        assertEquals("2026-10-11T02:30:00Z", fields.startsAt)
        assertEquals("2026-10-11T04:30:00Z", fields.endsAt)
        // 02:30Z + 5:30 = 08:00 IST; 04:30Z + 5:30 = 10:00 IST.
        assertEquals("08:00:00", fields.startTime)
        assertEquals("10:00:00", fields.endTime)
    }

    @Test
    fun `write sets capacity and max_orders equal`() {
        val fields = DeliverySlotDraft(startsAt = startsAt, endsAt = endsAt, capacity = 25).toWriteFields()
        assertEquals(25, fields.capacity)
        assertEquals(25, fields.maxOrders)
    }

    // --- Slot load ---------------------------------------------------------

    @Test
    fun `slot load reads booked from whichever alias the RPC used`() {
        assertEquals(18, SlotLoadDto(slotId = "s1", capacity = 25, booked = 18).toDomain().booked)
        assertEquals(18, SlotLoadDto(slotId = "s1", capacity = 25, orders = 18).toDomain().booked)
        assertEquals(18, SlotLoadDto(slotId = "s1", capacity = 25, bookedCount = 18).toDomain().booked)
    }

    @Test
    fun `slot load derives booked from remaining when only remaining is present`() {
        val load = SlotLoadDto(slotId = "s1", capacity = 25, remaining = 7).toDomain()
        assertEquals(18, load.booked)
    }

    @Test
    fun `a full slot is visually distinct`() {
        val full = SlotLoadDto(slotId = "s1", capacity = 25, booked = 25).toDomain()
        assertTrue(full.isFull)
        assertEquals(0, full.remaining)
        assertEquals("25 / 25", full.utilisationLabel)

        val roomy = SlotLoadDto(slotId = "s2", capacity = 25, booked = 5).toDomain()
        assertFalse(roomy.isFull)
        assertEquals(0.2f, roomy.utilisation, 0.0001f)
    }
}
