package com.vedant.admin.data.repository

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.AdminRpc
import com.vedant.admin.data.remote.SlotRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.data.remote.mapper.toWriteFields
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.domain.repository.SlotRepository
import kotlinx.datetime.Instant

private const val TAG = "SlotRepository"
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The real [SlotRepository].
 *
 * It composes two replaceable collaborators:
 *  - [SlotRemoteSource] — the PostgREST **reads/writes** on `delivery_slots`.
 *  - [AdminRpc]         — `ensure_delivery_slots` and `admin_slot_load`.
 *
 * Every write returns the row PostgREST echoed, never the draft.
 */
class SlotRepositoryImpl(
    private val remote: SlotRemoteSource,
    private val adminRpc: AdminRpc,
) : SlotRepository {

    override suspend fun listSlots(from: Instant, days: Int): Outcome<List<DeliverySlot>> = outcomeOf {
        val to = windowEnd(from, days)
        remote.listSlots(from, to).map { it.toDomain() }
    }

    override suspend fun slotLoad(from: Instant, days: Int): Outcome<List<SlotLoad>> = outcomeOf {
        adminRpc.adminSlotLoad(from.toString(), days).map { it.toDomain() }
    }

    override suspend fun ensureSlots(from: Instant, days: Int): Outcome<Unit> = outcomeOf {
        AppLog.i(TAG, "ensureSlots from=$from days=$days")
        adminRpc.ensureDeliverySlots(from.toString(), days)
    }

    override suspend fun saveSlot(draft: DeliverySlotDraft): Outcome<DeliverySlot> = outcomeOf {
        require(draft.endsAt > draft.startsAt) { "A slot must end after it starts." }
        require(draft.capacity > 0) { "A slot's capacity must be at least 1." }

        val fields = draft.toWriteFields()
        val saved = if (draft.isCreate) {
            AppLog.i(TAG, "createSlot ${draft.startsAt}..${draft.endsAt}")
            remote.insertSlot(fields)
        } else {
            val id = draft.id.orEmpty()
            AppLog.i(TAG, "updateSlot id=$id")
            remote.updateSlot(id, fields) ?: throw notFound(id)
        }
        saved.toDomain()
    }

    override suspend fun deleteSlot(slotId: String): Outcome<Unit> = outcomeOf {
        remote.deleteSlot(slotId)
    }

    /** Exclusive upper bound of the `[from, from + days)` window. */
    private fun windowEnd(from: Instant, days: Int): Instant =
        Instant.fromEpochMilliseconds(from.toEpochMilliseconds() + days.coerceAtLeast(1) * MILLIS_PER_DAY)

    private fun notFound(id: String): NoSuchElementException =
        NoSuchElementException("Slot $id no longer exists.")
}
