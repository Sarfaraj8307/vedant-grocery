package com.vedant.admin.core.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Time, in one place.
 *
 * The rule (§13): instants are **stored and compared in UTC**, and **displayed
 * in Asia/Kolkata**. Delivery-slot `start_time`/`end_time` columns are IST
 * wall-clock `time` values, so they are written and read through here too.
 *
 * Everything is a pure function of its input except [now], so the whole object
 * is unit-testable without a device clock.
 */
object AppClock {

    /** The store's time zone. All user-facing times are shown in this zone. */
    val IST: TimeZone = TimeZone.of("Asia/Kolkata")

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    fun now(): Instant = Clock.System.now()

    fun nowIst(): LocalDateTime = now().toLocalDateTime(IST)

    fun toIst(instant: Instant): LocalDateTime = instant.toLocalDateTime(IST)

    /** Turns an IST wall-clock into the UTC instant it denotes. */
    fun toInstant(istWallClock: LocalDateTime): Instant = istWallClock.toInstant(IST)

    /** Midnight today, IST, as a UTC instant — the lower bound of "today". */
    fun startOfTodayIstUtc(): Instant {
        val nowIst = nowIst()
        return LocalDateTime(nowIst.year, nowIst.monthNumber, nowIst.dayOfMonth, 0, 0, 0)
            .toInstant(IST)
    }

    /** Midnight tomorrow, IST, as a UTC instant — the upper bound of "today". */
    fun endOfTodayIstUtc(): Instant {
        val start = startOfTodayIstUtc()
        return Instant.fromEpochMilliseconds(start.toEpochMilliseconds() + MILLIS_PER_DAY)
    }

    /** `11 Oct 2026, 16:42` — 24-hour, unambiguous for delivery windows. */
    fun formatDateTime(instant: Instant): String {
        val local = toIst(instant)
        return "${formatDate(local)}, ${formatTime(local)}"
    }

    /** `11 Oct 2026`. */
    fun formatDate(instant: Instant): String = formatDate(toIst(instant))

    /** `16:42`. */
    fun formatTime(instant: Instant): String = formatTime(toIst(instant))

    private fun formatDate(local: LocalDateTime): String =
        "${local.dayOfMonth} ${MONTHS[local.monthNumber - 1]} ${local.year}"

    private fun formatTime(local: LocalDateTime): String =
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"

    /** `16:42` for an instant, used when showing a slot's wall-clock. */
    fun wallClock(instant: Instant): String = formatTime(toIst(instant))

    /** `08:00:00` — the literal PostgREST expects for a `time` column. */
    fun toPostgrestTime(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:" +
            "${time.minute.toString().padStart(2, '0')}:" +
            "${time.second.toString().padStart(2, '0')}"

    /** Tolerant `time` parse: `08:00:00` → [LocalTime]; junk → `null`. */
    fun parseLocalTime(text: String?): LocalTime? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalTime.parse(trimmed) }.getOrNull()
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
