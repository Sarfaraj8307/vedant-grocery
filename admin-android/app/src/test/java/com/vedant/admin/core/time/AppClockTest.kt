package com.vedant.admin.core.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AppClock` is the single owner of the "store in UTC, display in Asia/Kolkata"
 * rule (architecture §13). It is also what the delivery-slot mapper uses to write
 * the legacy `start_time`/`end_time` `time` columns as IST wall-clock (pair 4).
 *
 * Every assertion here is a pure function of its input, so the suite needs no
 * device clock. The one time-dependent pair (`startOfToday`/`endOfToday`) is
 * asserted by invariant (midnight in IST, exactly 24 h apart, ordered around now)
 * rather than by a hard-coded date.
 */
class AppClockTest {

    private val ist = AppClock.IST

    @Test
    fun `the store time zone is India Standard Time at plus five thirty`() {
        // 00:00 UTC on 1 Jan 2026 is 05:30 IST the same day.
        val utcMidnight = Instant.parse("2026-01-01T00:00:00Z")
        val local = AppClock.toIst(utcMidnight)
        assertEquals(5, local.hour)
        assertEquals(30, local.minute)
        assertEquals(ist.id, "Asia/Kolkata")
    }

    @Test
    fun `an instant is displayed in IST wall clock`() {
        // 11:12 UTC -> 16:42 IST.
        val instant = Instant.parse("2026-10-11T11:12:00Z")
        assertEquals("11 Oct 2026, 16:42", AppClock.formatDateTime(instant))
        assertEquals("11 Oct 2026", AppClock.formatDate(instant))
        assertEquals("16:42", AppClock.formatTime(instant))
        assertEquals("16:42", AppClock.wallClock(instant))
    }

    @Test
    fun `a late-UTC instant rolls into the next IST day`() {
        // 20:00 UTC on 11 Oct -> 01:30 IST on 12 Oct.
        val instant = Instant.parse("2026-10-11T20:00:00Z")
        assertEquals("12 Oct 2026, 01:30", AppClock.formatDateTime(instant))
    }

    @Test
    fun `an IST wall clock converts back to the exact UTC instant`() {
        val wallClock = LocalDateTime(2026, 10, 11, 16, 42, 0)
        assertEquals(Instant.parse("2026-10-11T11:12:00Z"), AppClock.toInstant(wallClock))
    }

    @Test
    fun `a postgrest time literal is zero padded to seconds`() {
        assertEquals("08:00:00", AppClock.toPostgrestTime(LocalTime(8, 0, 0)))
        assertEquals("09:05:07", AppClock.toPostgrestTime(LocalTime(9, 5, 7)))
    }

    @Test
    fun `a tolerant time parse reads a live literal`() {
        assertEquals(LocalTime(8, 0, 0), AppClock.parseLocalTime("08:00:00"))
        assertEquals(LocalTime(10, 30, 0), AppClock.parseLocalTime(" 10:30:00 "))
    }

    @Test
    fun `a junk or blank time parses to null rather than throwing`() {
        assertNull(AppClock.parseLocalTime(null))
        assertNull(AppClock.parseLocalTime(""))
        assertNull(AppClock.parseLocalTime("half past eight"))
    }

    @Test
    fun `start of today is IST midnight expressed as a UTC instant`() {
        val start = AppClock.startOfTodayIstUtc()
        val local = AppClock.toIst(start)
        assertEquals(0, local.hour)
        assertEquals(0, local.minute)
        assertEquals(0, local.second)
        // And it is in the past (or exactly now at the stroke of midnight).
        assertTrue(start <= AppClock.now())
    }

    @Test
    fun `end of today is exactly one day after the start`() {
        val start = AppClock.startOfTodayIstUtc()
        val end = AppClock.endOfTodayIstUtc()
        assertEquals(86_400_000L, end.toEpochMilliseconds() - start.toEpochMilliseconds())
    }

    @Test
    fun `now falls inside the today window`() {
        val start = AppClock.startOfTodayIstUtc()
        val end = AppClock.endOfTodayIstUtc()
        val now = AppClock.now()
        assertTrue(now >= start)
        assertTrue(now < end)
    }
}
