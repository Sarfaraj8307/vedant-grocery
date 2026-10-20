package com.vedant.admin.data.remote

import com.vedant.admin.data.remote.dto.DeliverySlotDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant

/**
 * The **PostgREST seam** for `delivery_slots`. An interface so
 * `SlotRepositoryImpl` is unit-testable with a fake — live slot access is
 * **[BLOCKED]** (no admin creds).
 *
 * The window bound is applied on `starts_at` (the authoritative, indexed column
 * — pair 4), never on the `start_time` mirror.
 */
interface SlotRemoteSource {

    /** Slots whose `starts_at` falls in `[from, to)`, ordered by start. */
    suspend fun listSlots(from: Instant, to: Instant): List<DeliverySlotDto>

    suspend fun insertSlot(fields: DeliverySlotDto): DeliverySlotDto

    suspend fun updateSlot(slotId: String, fields: DeliverySlotDto): DeliverySlotDto?

    suspend fun deleteSlot(slotId: String)
}

/** The real [SlotRemoteSource], backed by PostgREST. */
class SupabaseSlotRemoteSource(private val client: SupabaseClient) : SlotRemoteSource {

    override suspend fun listSlots(from: Instant, to: Instant): List<DeliverySlotDto> =
        client.postgrest.from(TABLE_SLOTS)
            .select(columns = Columns.raw(ALL_COLUMNS)) {
                filter {
                    gte("starts_at", from.toString())
                    lt("starts_at", to.toString())
                }
                order("starts_at", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun insertSlot(fields: DeliverySlotDto): DeliverySlotDto =
        client.postgrest.from(TABLE_SLOTS)
            .insert(fields) { select() }
            .decodeSingle()

    override suspend fun updateSlot(slotId: String, fields: DeliverySlotDto): DeliverySlotDto? =
        client.postgrest.from(TABLE_SLOTS)
            .update(fields.copy(id = null)) {
                filter { eq("id", slotId) }
                select()
            }
            .decodeSingleOrNull()

    override suspend fun deleteSlot(slotId: String) {
        client.postgrest.from(TABLE_SLOTS).delete { filter { eq("id", slotId) } }
    }

    private companion object {
        const val ALL_COLUMNS = "*"
        const val TABLE_SLOTS = "delivery_slots"
    }
}
