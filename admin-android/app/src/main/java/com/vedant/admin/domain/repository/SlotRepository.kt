package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.SlotLoad
import kotlinx.datetime.Instant

/**
 * Reads and writes for delivery slots, plus the two slot RPCs.
 *
 * ### Pair 4 lives below this contract
 * `starts_at`/`ends_at` are the authoritative window; the IST wall-clock
 * `start_time`/`end_time` mirror and the `capacity`/`max_orders` mirror are
 * written by `data/remote/mapper/SlotMapper`, so nothing here names a legacy
 * column.
 *
 * Every write returns the server-confirmed row.
 */
interface SlotRepository {

    /** Active-and-inactive slots whose window starts in `[from, from + days)`. */
    suspend fun listSlots(from: Instant, days: Int = DEFAULT_DAYS): Outcome<List<DeliverySlot>>

    /**
     * Utilisation per slot over the window, from `admin_slot_load`.
     *
     * The RPC's result shape is **BLOCKED** (no admin creds), so the mapper reads
     * every plausible alias; this contract only promises the normalised model.
     */
    suspend fun slotLoad(from: Instant, days: Int = DEFAULT_DAYS): Outcome<List<SlotLoad>>

    /** `ensure_delivery_slots` — materialises the recurring slots for the window. */
    suspend fun ensureSlots(from: Instant, days: Int = DEFAULT_DAYS): Outcome<Unit>

    /** Inserts when the draft has no id, updates otherwise. Returns the stored row. */
    suspend fun saveSlot(draft: DeliverySlotDraft): Outcome<DeliverySlot>

    /** Removes a slot. A missing row is not an error. */
    suspend fun deleteSlot(slotId: String): Outcome<Unit>

    companion object {
        const val DEFAULT_DAYS = 7
    }
}
