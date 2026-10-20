package com.vedant.admin.domain.model

import com.vedant.admin.core.time.AppClock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * One bookable delivery slot.
 *
 * ### Pair 4 (`docs/CONTRACT.md` §7)
 * `starts_at`/`ends_at` (`timestamptz`) are **authoritative** — a unique index,
 * the recurrence job and `admin_slot_load(p_from, p_days)` all key on them.
 * `start_time`/`end_time` (`time`) are the Asia/Kolkata wall-clock mirrors and
 * are written by the mapper, never modelled here.
 *
 * ### Pair 3
 * `capacity` is authoritative; `max_orders` is the mirror, written equal.
 */
data class DeliverySlot(
    val id: String,
    val name: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {

    /** e.g. `08:00–10:00` in the store's own zone. */
    val windowLabel: String get() = "${AppClock.wallClock(startsAt)}–${AppClock.wallClock(endsAt)}"

    /** e.g. `11 Oct 2026`. */
    val dayLabel: String get() = AppClock.formatDate(startsAt)

    /** The IST calendar day the slot falls on — used to pre-fill the editor. */
    val day: LocalDate get() = AppClock.toIst(startsAt).date

    /** `HH:mm` IST start, for the editor's time field. */
    val startTimeIst: String get() = AppClock.wallClock(startsAt)

    /** `HH:mm` IST end, for the editor's time field. */
    val endTimeIst: String get() = AppClock.wallClock(endsAt)

    /** e.g. `Morning 08:00–10:00`. */
    val title: String get() = name?.takeIf { it.isNotBlank() }?.let { "$it $windowLabel" } ?: windowLabel
}

/**
 * Utilisation of one slot over a window, from the `admin_slot_load` RPC.
 *
 * **The RPC's exact result shape is BLOCKED (no admin creds — R1)**, so the
 * mapper reads several plausible column names and this model carries only the
 * normalised result.
 */
data class SlotLoad(
    val slotId: String,
    val name: String?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val capacity: Int,
    val booked: Int,
) {

    /** Places left, never negative. */
    val remaining: Int get() = (capacity - booked).coerceAtLeast(0)

    /** 0f..1f, for a utilisation bar. */
    val utilisation: Float
        get() = if (capacity <= 0) 0f else (booked.toFloat() / capacity).coerceIn(0f, 1f)

    /** True when the slot is at or over capacity — it must read as visually distinct. */
    val isFull: Boolean get() = capacity > 0 && booked >= capacity

    /** e.g. `18 / 25`. */
    val utilisationLabel: String get() = "$booked / $capacity"

    /** e.g. `08:00–10:00` when the RPC supplied a time. */
    val windowLabel: String?
        get() = if (startsAt != null && endsAt != null) {
            "${AppClock.wallClock(startsAt)}–${AppClock.wallClock(endsAt)}"
        } else {
            null
        }
}

/**
 * The editable shape of a slot.
 *
 * The mapper expands this to **both** `starts_at`/`ends_at` (UTC) and
 * `start_time`/`end_time` (IST wall-clock), and writes `capacity` and
 * `max_orders` equal (pairs 3 & 4).
 */
data class DeliverySlotDraft(
    val id: String? = null,
    val name: String? = null,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
) {

    val isCreate: Boolean get() = id.isNullOrBlank()

    companion object {
        fun from(slot: DeliverySlot): DeliverySlotDraft = DeliverySlotDraft(
            id = slot.id,
            name = slot.name,
            startsAt = slot.startsAt,
            endsAt = slot.endsAt,
            capacity = slot.capacity,
            sortOrder = slot.sortOrder,
            isActive = slot.isActive,
        )
    }
}
