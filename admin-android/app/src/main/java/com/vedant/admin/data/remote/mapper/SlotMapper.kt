package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.data.remote.dto.DeliverySlotDto
import com.vedant.admin.data.remote.dto.SlotLoadDto
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.SlotLoad
import kotlinx.datetime.Instant

private const val TAG = "SlotMapper"

/**
 * DTO ↔ domain mapping for `delivery_slots`, and the only place pairs 3 and 4
 * are applied (`docs/CONTRACT.md` §7).
 *
 *  - **Pair 4.** `starts_at`/`ends_at` (`timestamptz`) are authoritative — a
 *    unique index, the recurrence job and `admin_slot_load` key on them.
 *    `start_time`/`end_time` (`time`) are the Asia/Kolkata wall-clock mirrors,
 *    derived from the instants on write.
 *  - **Pair 3.** `capacity` is authoritative; `max_orders` is written equal.
 */

fun DeliverySlotDto.toDomain(): DeliverySlot {
    // Pair 4: starts_at/ends_at are authoritative. A slot with neither is a
    // schema anomaly — we log and fall back to the epoch rather than fabricate a
    // plausible-looking time.
    val start = parseInstantOrNull(startsAt) ?: missingInstant("starts_at")
    val end = parseInstantOrNull(endsAt) ?: parseInstantOrNull(startsAt) ?: missingInstant("ends_at")

    return DeliverySlot(
        id = id.orEmpty(),
        name = name?.takeIf { it.isNotBlank() },
        startsAt = start,
        endsAt = end,
        // Pair 3: capacity is authoritative; max_orders is the fallback.
        capacity = (capacity ?: maxOrders) ?: 0,
        sortOrder = sortOrder ?: 0,
        isActive = isActive ?: true,
        createdAt = parseInstantOrNull(createdAt),
        updatedAt = parseInstantOrNull(updatedAt),
    )
}

/**
 * Pair 4 write policy: `starts_at`/`ends_at` as UTC `timestamptz`, plus
 * `start_time`/`end_time` as the IST wall-clock derived from them; and pair 3:
 * `capacity` and `max_orders` written equal.
 */
fun DeliverySlotDraft.toWriteFields(): DeliverySlotDto = DeliverySlotDto(
    id = id?.takeIf { it.isNotBlank() },
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    startsAt = startsAt.toString(),
    endsAt = endsAt.toString(),
    startTime = AppClock.toPostgrestTime(AppClock.toIst(startsAt).time),
    endTime = AppClock.toPostgrestTime(AppClock.toIst(endsAt).time),
    capacity = capacity,
    maxOrders = capacity, // pair 3: write both, same value
    sortOrder = sortOrder,
    isActive = isActive,
)

/**
 * Maps one `admin_slot_load` row.
 *
 * The RPC's exact result shape is **BLOCKED (no admin creds — R1)**, so the
 * booked count is read from the first plausible alias present, and capacity from
 * `capacity` or `max_orders`.
 */
fun SlotLoadDto.toDomain(): SlotLoad {
    val resolvedCapacity = (capacity ?: maxOrders) ?: 0
    val resolvedBooked = booked ?: bookedCount ?: orders ?: orderCount ?: used ?: load
        ?: remaining?.let { (resolvedCapacity - it).coerceAtLeast(0) }
        ?: 0

    return SlotLoad(
        slotId = (slotId ?: id).orEmpty(),
        name = name?.takeIf { it.isNotBlank() },
        startsAt = parseInstantOrNull(startsAt),
        endsAt = parseInstantOrNull(endsAt),
        capacity = resolvedCapacity,
        booked = resolvedBooked,
    )
}

private fun missingInstant(field: String): Instant {
    AppLog.w(TAG, "expected NOT NULL column '$field' was absent; falling back to the epoch")
    return Instant.fromEpochMilliseconds(0)
}
